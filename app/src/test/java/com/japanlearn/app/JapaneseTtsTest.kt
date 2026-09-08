package com.japanlearn.app

import com.japanlearn.app.util.JapaneseTts
import com.japanlearn.app.util.JapaneseTts.Action
import com.japanlearn.app.util.JapaneseTts.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** 日语 TTS 可用性判定与发音引导决策（v0.4.3 实测 setLanguage 结果）。 */
class JapaneseTtsTest {

    private val langAvailable = android.speech.tts.TextToSpeech.LANG_COUNTRY_AVAILABLE
    private val langMissingData = android.speech.tts.TextToSpeech.LANG_MISSING_DATA
    private val langNotSupported = android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED

    @Test
    fun `日语可用性判定 setLanguage 结果语义`() {
        // 与 JapaneseTts.japaneseUsable / japaneseMissingData 同一规则
        fun usable(status: Int) = status >= android.speech.tts.TextToSpeech.LANG_AVAILABLE
        fun missingData(status: Int) = status == langMissingData
        assertTrue(usable(langAvailable))
        assertFalse(usable(langMissingData))
        assertFalse(usable(langNotSupported))
        assertTrue(missingData(langMissingData))
        assertFalse(missingData(langNotSupported))
    }

    @Test
    fun `发音点击决策 五种场景`() {
        // 初始化中：先照常暂存（就绪后自动播放）
        assertEquals(Action.SPEAK, JapaneseTts.decideAction(State.WAITING, false, false))
        // 引擎正常且日语可用：直接发音
        assertEquals(Action.SPEAK, JapaneseTts.decideAction(State.READY, true, false))
        // 引擎正常但缺日语语音数据：引导下载数据
        assertEquals(Action.GUIDE_VOICE_DATA, JapaneseTts.decideAction(State.READY, false, true))
        // 引擎正常但不支持日语（如中文引擎，读日文只读汉字跳过假名）：引导安装引擎
        assertEquals(Action.GUIDE_ENGINE, JapaneseTts.decideAction(State.READY, false, false))
        // 初始化失败 / 超时：引导安装引擎
        assertEquals(Action.GUIDE_ENGINE, JapaneseTts.decideAction(State.FAILED, false, false))
    }
}
