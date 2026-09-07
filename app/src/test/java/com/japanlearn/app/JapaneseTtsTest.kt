package com.japanlearn.app

import com.japanlearn.app.util.JapaneseTts
import com.japanlearn.app.util.JapaneseTts.Action
import com.japanlearn.app.util.JapaneseTts.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** 日语语音可用性判定与发音引导决策（v0.3.1 / v0.4.1）。 */
class JapaneseTtsTest {

    @Test
    fun `任一日语语音即可含不同地区`() {
        assertTrue(JapaneseTts.japaneseAvailableIn(setOf(Locale.JAPAN)))
        assertTrue(JapaneseTts.japaneseAvailableIn(setOf(Locale("ja", "US"))))
        assertTrue(JapaneseTts.japaneseAvailableIn(setOf(Locale.US, Locale.JAPANESE)))
    }

    @Test
    fun `非日语语音不判定为可用`() {
        assertFalse(JapaneseTts.japaneseAvailableIn(setOf(Locale.US, Locale.SIMPLIFIED_CHINESE)))
        assertFalse(JapaneseTts.japaneseAvailableIn(emptySet()))
    }

    @Test
    fun `发音点击决策 四种状态`() {
        // 初始化中：先照常暂存（就绪后自动播放）
        assertEquals(Action.SPEAK, JapaneseTts.decideAction(State.WAITING, hasJapanese = false))
        // 就绪且有日语：直接发音
        assertEquals(Action.SPEAK, JapaneseTts.decideAction(State.READY, hasJapanese = true))
        // 就绪但缺日语语音包：引导下载数据
        assertEquals(Action.GUIDE_VOICE_DATA, JapaneseTts.decideAction(State.READY, hasJapanese = false))
        // 初始化失败（无引擎）：引导安装引擎
        assertEquals(Action.GUIDE_ENGINE, JapaneseTts.decideAction(State.FAILED, hasJapanese = false))
    }
}
