package com.japanlearn.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 发音状态跟踪：由 TTS 引擎回调驱动的真假切换，供 UI 做「正在发音」的状态提示。
 * 独立成类是为了能在 JVM 单元测试里直接驱动状态机。
 */
class SpeakingTracker {
    private val _speaking = MutableStateFlow(false)

    /** 是否有语句正在播报。 */
    val speaking: StateFlow<Boolean> = _speaking

    /** 一条语句开始播报。 */
    fun onStart() {
        _speaking.value = true
    }

    /** 播报落定：正常结束、出错或被打断都算。 */
    fun onSettled() {
        _speaking.value = false
    }

    /** 引擎重建 / 关闭时归零，避免状态悬挂在 true。 */
    fun reset() {
        _speaking.value = false
    }
}

/**
 * 系统日语 TTS。
 *
 * - 有 Google TTS 就按包名初始化，避开中文默认引擎只读汉字
 * - 每次发音前重新 setLanguage + 选已安装的 ja-JP 本地 voice
 * - 不信任 availableLanguages；以 setLanguage 返回值 + voice 列表实测
 * - **非 Google 引擎一律不信任日语**：中文 ROM 默认引擎会谎报可用并只读汉字
 * - 仅网络 voice 视为未装好离线数据，引导下载而不是硬播
 */
class JapaneseTts(private val context: Context) {

    enum class State { WAITING, READY, FAILED }

    enum class Action { SPEAK, GUIDE_VOICE_DATA, GUIDE_ENGINE }

    enum class VoiceProbe { INSTALLED, MISSING_DATA, NONE, UNKNOWN }

    data class VoiceCandidate(
        val language: String,
        val iso3: String = "",
        val country: String = "",
        val quality: Int = 0,
        val networkRequired: Boolean = false,
        val notInstalled: Boolean = false,
        val name: String = "",
    )

    /** 设置页可选的已安装日语 voice。 */
    data class VoiceOption(
        val name: String,
        val label: String,
        val quality: Int,
    )

    private var tts: TextToSpeech? = null
    private var pending: String? = null
    private var japaneseVoice: Voice? = null

    private val speakingTracker = SpeakingTracker()

    /** 是否有语句正在播报（发音按钮据此做主色 + 呼吸提示）。 */
    val speaking: StateFlow<Boolean> get() = speakingTracker.speaking

    @Volatile
    private var generation = 0

    @Volatile
    private var state = State.WAITING

    @Volatile
    private var japaneseStatus: Int = TextToSpeech.LANG_NOT_SUPPORTED

    @Volatile
    private var usingGoogleTts = false

    @Volatile
    private var preferredVoiceName: String? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener { }
        .build()

    private val listener = TextToSpeech.OnInitListener { status ->
        // 由 initInternal 包一层 generation，过期回调直接丢掉
        state = if (status == TextToSpeech.SUCCESS) State.READY else State.FAILED
        if (state == State.READY) {
            applyAudioAttributes()
            applyJapaneseLanguage()
            tts?.setOnUtteranceProgressListener(utteranceListener)
        }
        Log.i(TAG, "TTS init finished: state=$state japaneseStatus=$japaneseStatus")
        if (state == State.READY && japaneseUsable()) {
            pending?.let { doSpeak(it) }
        }
        pending = null
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.i(TAG, "utterance start id=$utteranceId")
            speakingTracker.onStart()
        }

        override fun onDone(utteranceId: String?) {
            Log.i(TAG, "utterance done id=$utteranceId")
            speakingTracker.onSettled()
            abandonFocus()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            Log.w(TAG, "utterance error id=$utteranceId")
            speakingTracker.onSettled()
            abandonFocus()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "utterance error id=$utteranceId code=$errorCode")
            speakingTracker.onSettled()
            abandonFocus()
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            Log.i(TAG, "utterance stop id=$utteranceId interrupted=$interrupted")
            speakingTracker.onSettled()
        }
    }

    init {
        initInternal()
    }

    fun retryInit() {
        Log.i(TAG, "retry init")
        pending = null
        speakingTracker.reset()
        state = State.WAITING
        japaneseVoice = null
        japaneseStatus = TextToSpeech.LANG_NOT_SUPPORTED
        val old = tts
        tts = null
        old?.shutdown()
        initInternal()
    }

    private fun initInternal() {
        val gen = ++generation
        val engineName = if (isGoogleTtsInstalled()) GOOGLE_TTS else null
        usingGoogleTts = engineName != null
        Log.i(TAG, "init with engine=${engineName ?: "system default"} google=$usingGoogleTts gen=$gen")
        val onInit = TextToSpeech.OnInitListener { status ->
            if (gen != generation) return@OnInitListener
            listener.onInit(status)
        }
        tts = if (engineName != null) {
            TextToSpeech(context, onInit, engineName)
        } else {
            TextToSpeech(context, onInit)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (gen != generation) return@postDelayed
            if (state == State.WAITING) {
                state = State.FAILED
                Log.i(TAG, "TTS init timeout (${INIT_TIMEOUT_MS}ms) -> FAILED")
            }
        }, INIT_TIMEOUT_MS)
    }

    fun currentState(): State = state

    fun japaneseUsable(): Boolean = japaneseStatus >= TextToSpeech.LANG_AVAILABLE

    fun japaneseMissingData(): Boolean = japaneseStatus == TextToSpeech.LANG_MISSING_DATA

    fun refreshJapaneseStatus() {
        if (state == State.READY) applyJapaneseLanguage()
        Log.i(TAG, "refresh status: state=$state japaneseStatus=$japaneseStatus voice=${japaneseVoice?.name}")
    }

    private fun applyAudioAttributes() {
        try {
            tts?.setAudioAttributes(audioAttributes)
        } catch (e: Exception) {
            Log.w(TAG, "setAudioAttributes failed: ${e.message}")
        }
    }

    private fun applyJapaneseLanguage() {
        val engine = tts ?: run {
            japaneseVoice = null
            japaneseStatus = TextToSpeech.LANG_NOT_SUPPORTED
            return
        }
        if (!usingGoogleTts) {
            // 中文 ROM 默认引擎：init 成功、setLanguage(ja) 可能返回可用，
            // 但实际只读汉字跳过假名（真机实测「休みの間に…」只念「休間習」）。
            // 不去探测，直接判不可用，点击时引导安装 Google TTS。
            japaneseVoice = null
            japaneseStatus = TextToSpeech.LANG_NOT_SUPPORTED
            Log.i(TAG, "non-Google engine, treat Japanese as unsupported")
            return
        }
        val languageStatus = setJapaneseLanguage(engine)
        val (voice, probe) = pickInstalledJapaneseVoice(engine, preferredVoiceName)
        japaneseVoice = voice
        japaneseStatus = verifiedJapaneseStatus(languageStatus, probe, usingGoogleTts)
        try {
            voice?.let { engine.voice = it }
        } catch (e: Exception) {
            Log.w(TAG, "setVoice failed: ${e.message}")
        }
        Log.i(
            TAG,
            "Japanese language check: setLanguage=$languageStatus probe=$probe voice=${voice?.name ?: "none"} verified=$japaneseStatus",
        )
    }

    /** 每次点击都重套日语，避免被其它 App 改走默认语言后静音或读成中文。 */
    fun speak(text: String) {
        when (state) {
            State.READY -> {
                applyJapaneseLanguage()
                if (japaneseUsable()) doSpeak(text)
                else Log.i(TAG, "speak skipped: Japanese not usable status=$japaneseStatus")
            }
            State.WAITING -> pending = text
            State.FAILED -> Unit
        }
    }

    /** 设置用户偏好的日语 voice；null/空 = 自动选最高质量。下次发音立即生效。 */
    fun setPreferredVoice(name: String?) {
        preferredVoiceName = name?.takeIf { it.isNotBlank() }
        Log.i(TAG, "preferred voice = ${preferredVoiceName ?: "auto"}")
        if (state == State.READY) applyJapaneseLanguage()
    }

    fun currentVoiceName(): String? = japaneseVoice?.name

    /**
     * 列出已安装的本地日语 voice，供设置页选择。
     * 引擎未就绪或非 Google TTS 时返回空列表。
     */
    fun listInstalledJapaneseVoices(): List<VoiceOption> {
        if (state != State.READY || !usingGoogleTts) return emptyList()
        val engine = tts ?: return emptyList()
        val voices = try {
            engine.voices.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "voices() failed: ${e.message}")
            emptySet()
        }
        return voices
            .mapNotNull { v ->
                val loc = v.locale ?: return@mapNotNull null
                if (!isJapaneseLocale(loc.language.orEmpty(), iso3Of(loc))) return@mapNotNull null
                if (v.features.orEmpty().contains("notInstalled")) return@mapNotNull null
                if (v.isNetworkConnectionRequired) return@mapNotNull null
                VoiceOption(
                    name = v.name.orEmpty(),
                    label = voiceDisplayName(v.name.orEmpty()),
                    quality = v.quality,
                )
            }
            .sortedWith(compareByDescending<VoiceOption> { it.quality }.thenBy { it.name })
    }

    private fun doSpeak(text: String) {
        val engine = tts ?: return
        engine.setSpeechRate(0.9f)
        requestFocus()
        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        val id = "utterance_${text.hashCode()}_${System.nanoTime()}"
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        Log.i(TAG, "speak result=$result id=$id textLen=${text.length}")
        if (result != TextToSpeech.SUCCESS) abandonFocus()
    }

    fun shutdown() {
        generation++
        speakingTracker.reset()
        tts?.stop()
        tts?.shutdown()
        tts = null
        abandonFocus()
        state = State.FAILED
    }

    private fun requestFocus() {
        try {
            audioManager.requestAudioFocus(focusRequest)
        } catch (e: Exception) {
            Log.w(TAG, "audio focus request failed: ${e.message}")
        }
    }

    private fun abandonFocus() {
        try {
            audioManager.abandonAudioFocusRequest(focusRequest)
        } catch (e: Exception) {
            Log.w(TAG, "audio focus abandon failed: ${e.message}")
        }
    }

    private fun isGoogleTtsInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(GOOGLE_TTS, 0)
        true
    } catch (_: Exception) {
        false
    }

    companion object {
        const val INIT_TIMEOUT_MS = 1500L
        const val GOOGLE_TTS = "com.google.android.tts"
        private const val TAG = "JapaneseTts"

        fun verifiedJapaneseStatus(
            languageStatus: Int,
            probe: VoiceProbe,
            usingGoogleTts: Boolean,
        ): Int {
            // 非 Google 引擎（中文 ROM 默认 TTS）一律不信任：谎报日语可用却只读汉字
            if (!usingGoogleTts) return TextToSpeech.LANG_NOT_SUPPORTED
            return when {
                probe == VoiceProbe.INSTALLED ->
                    if (languageStatus >= TextToSpeech.LANG_AVAILABLE) languageStatus
                    else TextToSpeech.LANG_AVAILABLE
                languageStatus == TextToSpeech.LANG_MISSING_DATA || probe == VoiceProbe.MISSING_DATA ->
                    TextToSpeech.LANG_MISSING_DATA
                // Google TTS 偶发 voices() 为空但实际能发音，此时信任 setLanguage
                languageStatus >= TextToSpeech.LANG_AVAILABLE && probe != VoiceProbe.NONE ->
                    languageStatus
                else -> TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        fun decideAction(state: State, japaneseUsable: Boolean, japaneseMissingData: Boolean): Action = when {
            state == State.WAITING -> Action.SPEAK
            state == State.READY && japaneseUsable -> Action.SPEAK
            state == State.READY && japaneseMissingData -> Action.GUIDE_VOICE_DATA
            else -> Action.GUIDE_ENGINE
        }

        fun isJapaneseLocale(language: String, iso3: String = ""): Boolean {
            val lang = language.lowercase(Locale.ROOT)
            val t = iso3.lowercase(Locale.ROOT)
            return lang == "ja" || lang == "jpn" || t == "jpn"
        }

        fun isJapaneseCountry(country: String): Boolean {
            val c = country.uppercase(Locale.ROOT)
            return c == "JP" || c == "JPN"
        }

        fun classifyJapaneseVoices(voices: List<VoiceCandidate>): VoiceProbe {
            if (voices.isEmpty()) return VoiceProbe.UNKNOWN
            val ja = voices.filter { isJapaneseLocale(it.language, it.iso3) }
            if (ja.isEmpty()) return VoiceProbe.NONE
            // 离线 App：本地已安装才算可用；仅网络 voice 视为需下载数据
            if (ja.any { !it.notInstalled && !it.networkRequired }) return VoiceProbe.INSTALLED
            return VoiceProbe.MISSING_DATA
        }

        fun pickBestJapaneseVoice(
            voices: List<VoiceCandidate>,
            preferredName: String? = null,
        ): VoiceCandidate? {
            val usable = voices.filter { !it.notInstalled && isJapaneseLocale(it.language, it.iso3) }
            val local = usable.filter { !it.networkRequired }
            val pool = local.ifEmpty { usable }
            // 用户显式选过的 voice 优先（仅在仍是可用池内时生效）
            if (!preferredName.isNullOrBlank()) {
                pool.firstOrNull { it.name == preferredName }?.let { return it }
            }
            return pool.maxWithOrNull(
                compareBy<VoiceCandidate> { if (isJapaneseCountry(it.country)) 1 else 0 }
                    .thenBy { it.quality }
                    .thenBy { if (it.name.contains("ja", ignoreCase = true)) 1 else 0 },
            )
        }

        /** Google TTS voice 名 → 用户可读标签（ja-JP-Standard-A → 标准 A）。 */
        fun voiceDisplayName(name: String): String {
            val n = name.trim()
            if (n.isEmpty()) return "默认"
            // 常见 Google TTS 模式：ja-JP-<Quality>-<Letter>
            val parts = n.split('-')
            if (parts.size >= 3) {
                val quality = parts[parts.size - 2]
                val letter = parts.last()
                val qualityLabel = when (quality.lowercase(Locale.ROOT)) {
                    "standard" -> "标准"
                    "wavenet" -> "WaveNet"
                    "neural" -> "Neural"
                    "neural2" -> "Neural2"
                    "studio" -> "Studio"
                    "language" -> "默认"
                    else -> quality
                }
                if (letter.length == 1 && letter[0].isLetter()) {
                    return "$qualityLabel $letter"
                }
                if (qualityLabel != quality) return qualityLabel
            }
            return n.removePrefix("ja-JP-").removePrefix("ja-jp-").ifEmpty { n }
        }

        private fun iso3Of(locale: java.util.Locale): String = try {
            locale.isO3Language.orEmpty()
        } catch (_: Exception) {
            ""
        }

        private fun setJapaneseLanguage(engine: TextToSpeech): Int {
            val locales = listOf(Locale.JAPAN, Locale("ja", "JP"), Locale.JAPANESE)
            var best = TextToSpeech.LANG_NOT_SUPPORTED
            for (locale in locales) {
                val status = try {
                    engine.setLanguage(locale)
                } catch (e: Exception) {
                    Log.w(TAG, "setLanguage($locale) failed: ${e.message}")
                    TextToSpeech.LANG_NOT_SUPPORTED
                }
                if (status > best) best = status
                if (status >= TextToSpeech.LANG_AVAILABLE) return status
            }
            return best
        }

        private fun pickInstalledJapaneseVoice(
            engine: TextToSpeech,
            preferredName: String? = null,
        ): Pair<Voice?, VoiceProbe> {
            val voices = try {
                engine.voices.orEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "voices() failed: ${e.message}")
                emptySet()
            }
            Log.i(
                TAG,
                "voices count=${voices.size} defaultEngine=${engine.defaultEngine} currentVoice=${engine.voice?.name} preferred=$preferredName",
            )
            val mapped = voices.map { v ->
                val loc = v.locale
                VoiceCandidate(
                    language = loc?.language.orEmpty(),
                    iso3 = try { loc?.isO3Language.orEmpty() } catch (_: Exception) { "" },
                    country = loc?.country.orEmpty(),
                    quality = v.quality,
                    networkRequired = v.isNetworkConnectionRequired,
                    notInstalled = v.features.orEmpty().contains("notInstalled"),
                    name = v.name.orEmpty(),
                ) to v
            }
            val probe = classifyJapaneseVoices(mapped.map { it.first })
            val best = pickBestJapaneseVoice(mapped.map { it.first }, preferredName)
                ?: return null to probe
            return mapped.firstOrNull { it.first == best }?.second to probe
        }
    }
}
