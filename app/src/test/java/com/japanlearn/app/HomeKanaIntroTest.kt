package com.japanlearn.app

import com.japanlearn.app.domain.HomeKanaIntro
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeKanaIntroTest {

    @Test
    fun `未跳过时显示横幅`() {
        assertTrue(HomeKanaIntro.shouldShow(dismissed = false))
    }

    @Test
    fun `点过暂时跳过则不再显示`() {
        assertFalse(HomeKanaIntro.shouldShow(dismissed = true))
    }
}
