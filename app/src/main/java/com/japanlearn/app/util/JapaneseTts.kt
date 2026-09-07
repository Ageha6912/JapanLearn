package com.japanlearn.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * MVP 音频方案（PRD §17.4）：使用系统 TTS（ja-JP），零成本、离线可用。
 * 初始化完成前收到的请求会被暂存，就绪后自动播放。
 * 三态状态（等待/就绪/失败）供 UI 判断引导：
 * - 就绪但缺日语语音数据 → 引导下载语音包
 * - 初始化失败或超时（常见于无 TTS 引擎的设备）→ 引导安装语音引擎
 */
class JapaneseTts(context: Context) {

    enum class State { WAITING, READY, FAILED }

    /** 点击发音时 UI 应采取的动作（纯函数，便于单元测试）。 */
    enum class Action { SPEAK, GUIDE_VOICE_DATA, GUIDE_ENGINE }

    private var tts: TextToSpeech? = null
    private var pending: String? = null

    @Volatile
    private var state = State.WAITING

    init {
        initInternal(context)
    }

    /** 重新尝试初始化（引擎慢启动或刚安装语音引擎后调用）。 */
    fun retryInit(context: Context) {
        Log.i(TAG, "retry init")
        tts?.shutdown()
        pending = null
        state = State.WAITING
        initInternal(context)
    }

    private fun initInternal(context: Context) {
        tts = TextToSpeech(context) { status ->
            state = if (status == TextToSpeech.SUCCESS) State.READY else State.FAILED
            Log.i(TAG, "TTS init finished: state=$state")
            if (state == State.READY) {
                tts?.language = Locale.JAPAN
                pending?.let { doSpeak(it) }
            }
            pending = null
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

    /** 实时查询日语语音是否可用（下载语音包返回后即可得到最新结果）。 */
    fun hasJapanese(): Boolean = japaneseAvailableIn(tts?.availableLanguages ?: emptySet())

    fun speak(text: String) {
        when (state) {
            State.READY -> doSpeak(text)
            State.WAITING -> pending = text
            State.FAILED -> Unit // 无引擎，无声音；引导由 UI 层负责
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

    companion object {
        const val INIT_TIMEOUT_MS = 1500L
        private const val TAG = "JapaneseTts"

        /** 语言可用性判定（纯函数）：任一日语语音即可，与地区无关。 */
        fun japaneseAvailableIn(available: Set<Locale>): Boolean =
            available.any { it.language == "ja" }

        /** 发音点击的决策（纯函数）：正常发音 / 引导下载数据 / 引导安装引擎。 */
        fun decideAction(state: State, hasJapanese: Boolean): Action = when {
            state == State.WAITING -> Action.SPEAK // 初始化中，文本暂存等就绪
            state == State.READY && hasJapanese -> Action.SPEAK
            state == State.READY -> Action.GUIDE_VOICE_DATA
            else -> Action.GUIDE_ENGINE
        }
    }
}
