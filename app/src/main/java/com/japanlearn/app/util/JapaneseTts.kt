package com.japanlearn.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * MVP 音频方案（PRD §17.4）：使用系统 TTS（ja-JP），零成本、离线可用。
 *
 * 引擎选择与可用性判定（v0.4.3）：
 * - 若设备装有 Google TTS 则显式使用它——无 GMS 的 ROM 常把中文引擎设为默认，
 *   中文引擎读日文只读汉字、跳过假名（如「私は学生です」只读出「私、学生」）
 * - 可用性以 setLanguage(Locale.JAPAN) 的返回值实测，不信任 availableLanguages
 *   （部分引擎会谎报支持日语）
 * - 三态状态（等待/就绪/失败）+ 初始化超时兜底，供 UI 判断引导路径
 */
class JapaneseTts(private val context: Context) {

    enum class State { WAITING, READY, FAILED }

    /** 点击发音时 UI 应采取的动作（纯函数，便于单元测试）。 */
    enum class Action { SPEAK, GUIDE_VOICE_DATA, GUIDE_ENGINE }

    private var tts: TextToSpeech? = null
    private var pending: String? = null
    private var japaneseVoice: android.speech.tts.Voice? = null

    @Volatile
    private var state = State.WAITING

    /** setLanguage(Locale.JAPAN) 的实测结果（TextToSpeech.LANG_* 常量）。 */
    @Volatile
    private var japaneseStatus: Int = TextToSpeech.LANG_NOT_SUPPORTED

    private val listener = TextToSpeech.OnInitListener { status ->
        state = if (status == TextToSpeech.SUCCESS) State.READY else State.FAILED
        if (state == State.READY) {
            applyJapaneseLanguage()
        }
        Log.i(TAG, "TTS init finished: state=$state japaneseStatus=$japaneseStatus")
        if (state == State.READY && japaneseUsable()) {
            pending?.let { doSpeak(it) }
        }
        pending = null
    }

    init {
        initInternal()
    }

    /** 重新尝试初始化（引擎慢启动、用户刚安装 Google TTS 后调用）。 */
    fun retryInit() {
        Log.i(TAG, "retry init")
        tts?.shutdown()
        pending = null
        state = State.WAITING
        initInternal()
    }

    private fun initInternal() {
        val engine = if (isGoogleTtsInstalled()) GOOGLE_TTS else null
        Log.i(TAG, "init with engine=${engine ?: "system default"}")
        tts = if (engine != null) {
            TextToSpeech(context, listener, engine)
        } else {
            TextToSpeech(context, listener)
        }
        // 部分 ROM 没有 TTS 引擎，onInit 永不回调；超时视为失败，让 UI 能引导安装
        Handler(Looper.getMainLooper()).postDelayed({
            if (state == State.WAITING) {
                state = State.FAILED
                Log.i(TAG, "TTS init timeout (${INIT_TIMEOUT_MS}ms) -> FAILED")
            }
        }, INIT_TIMEOUT_MS)
    }

    fun currentState(): State = state

    /** 日语语音实测可用（引擎支持日语且语音数据已下载）。 */
    fun japaneseUsable(): Boolean = japaneseStatus >= TextToSpeech.LANG_AVAILABLE

    /** 引擎支持日语但语音数据未下载。 */
    fun japaneseMissingData(): Boolean = japaneseStatus == TextToSpeech.LANG_MISSING_DATA

    /** 点击时重测语言可用性（用户下载语音数据返回后立即生效）。 */
    fun refreshJapaneseStatus() {
        if (state == State.READY) applyJapaneseLanguage()
        Log.i(TAG, "refresh status: state=$state japaneseStatus=$japaneseStatus")
    }

    private fun applyJapaneseLanguage() {
        val engine = tts ?: run {
            japaneseVoice = null
            japaneseStatus = TextToSpeech.LANG_NOT_SUPPORTED
            return
        }
        val languageStatus = engine.setLanguage(Locale.JAPAN)
        japaneseVoice = engine.voices
            .orEmpty()
            .firstOrNull { it.locale.language == Locale.JAPANESE.language }
        japaneseStatus = verifiedJapaneseStatus(languageStatus, japaneseVoice != null)
        japaneseVoice?.let { engine.voice = it }
        Log.i(
            TAG,
            "Japanese language check: setLanguage=$languageStatus voice=${japaneseVoice?.name ?: "none"} verified=$japaneseStatus",
        )
    }

    fun speak(text: String) {
        when (state) {
            State.READY -> doSpeak(text)
            State.WAITING -> pending = text
            State.FAILED -> Unit // 无可用引擎；引导由 UI 层负责
        }
    }

    private fun doSpeak(text: String) {
        tts?.setSpeechRate(0.9f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${text.hashCode()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        state = State.FAILED
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

        /**
         * Some engines report Locale.JAPAN as available while exposing no Japanese voice
         * (for example a Chinese engine that only reads the kanji in a sentence).
         */
        fun verifiedJapaneseStatus(languageStatus: Int, hasJapaneseVoice: Boolean): Int = when {
            languageStatus == TextToSpeech.LANG_MISSING_DATA -> TextToSpeech.LANG_MISSING_DATA
            languageStatus >= TextToSpeech.LANG_AVAILABLE && hasJapaneseVoice -> languageStatus
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }

        /** 发音点击的决策（纯函数）：正常发音 / 引导下载数据 / 引导安装引擎。 */
        fun decideAction(state: State, japaneseUsable: Boolean, japaneseMissingData: Boolean): Action = when {
            state == State.WAITING -> Action.SPEAK // 初始化中，文本暂存等就绪
            state == State.READY && japaneseUsable -> Action.SPEAK
            state == State.READY && japaneseMissingData -> Action.GUIDE_VOICE_DATA
            else -> Action.GUIDE_ENGINE
        }
    }
}
