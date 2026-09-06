package com.japanlearn.app

import com.japanlearn.app.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** 主题模式持久化解析（设置卡片外观切换）。 */
class ThemeModeTest {

    @Test
    fun `合法值正常解析`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromRaw("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromRaw("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromRaw("DARK"))
    }

    @Test
    fun `缺失或非法值回退跟随系统`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromRaw(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromRaw(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromRaw("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromRaw("LEGACY"))
    }
}
