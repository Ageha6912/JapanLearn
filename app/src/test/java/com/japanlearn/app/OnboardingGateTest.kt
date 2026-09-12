package com.japanlearn.app

import com.japanlearn.app.domain.OnboardingGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGateTest {

    @Test
    fun `已完成过引导不再显示`() {
        assertFalse(OnboardingGate.shouldShow(onboardingDone = true, learnedWords = 0, learnedGrammar = 0))
    }

    @Test
    fun `零进度且未看过引导时显示`() {
        assertTrue(OnboardingGate.shouldShow(onboardingDone = false, learnedWords = 0, learnedGrammar = 0))
    }

    @Test
    fun `已有单词进度的老用户不打扰`() {
        assertFalse(OnboardingGate.shouldShow(onboardingDone = false, learnedWords = 12, learnedGrammar = 0))
    }

    @Test
    fun `已有语法进度的老用户不打扰`() {
        assertFalse(OnboardingGate.shouldShow(onboardingDone = false, learnedWords = 0, learnedGrammar = 3))
    }
}
