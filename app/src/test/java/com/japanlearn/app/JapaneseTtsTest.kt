package com.japanlearn.app

import com.japanlearn.app.util.JapaneseTts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** 日语语音可用性判定（v0.3.1 语音包引导）。 */
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
}
