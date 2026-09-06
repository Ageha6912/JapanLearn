package com.japanlearn.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * MVP 音频方案（PRD §17.4）：使用系统 TTS（ja-JP），零成本、离线可用。
 * 初始化完成前收到的请求会被暂存，就绪后自动播放。
 * 缺少日语语音数据时由 UI 层经 [needsVoiceData] 检测并引导用户下载（v0.3.1）。
 */
class JapaneseTts(context: Context) {

    private var tts: TextToSpeech? = null
    private var pending: String? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.JAPAN
                pending?.let { doSpeak(it) }
            }
            pending = null
        }
    }

    /**
     * TTS 已就绪但缺少日语语音数据。实时查询（而非初始化时缓存）：
     * 用户从语音数据下载页返回后，再次点击即可得到最新状态。
     */
    fun needsVoiceData(): Boolean = ready && !japaneseAvailableIn(tts?.availableLanguages ?: emptySet())

    fun speak(text: String) {
        if (ready) doSpeak(text) else pending = text
    }

    private fun doSpeak(text: String) {
        tts?.setSpeechRate(0.9f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${text.hashCode()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    companion object {
        /** 语言可用性判定（纯函数）：任一日语语音即可，与地区无关。 */
        fun japaneseAvailableIn(available: Set<Locale>): Boolean =
            available.any { it.language == "ja" }
    }
}
