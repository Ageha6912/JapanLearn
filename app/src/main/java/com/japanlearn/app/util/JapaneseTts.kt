package com.japanlearn.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * MVP 音频方案（PRD §17.4）：使用系统 TTS（ja-JP），零成本、离线可用。
 * 初始化完成前收到的请求会被暂存，就绪后自动播放。
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
}
