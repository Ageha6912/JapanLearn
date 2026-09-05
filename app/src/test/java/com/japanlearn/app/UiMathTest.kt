package com.japanlearn.app

import com.japanlearn.app.domain.UiMath
import org.junit.Assert.assertEquals
import org.junit.Test

class UiMathTest {

    // ---------- dailyProgress ----------

    @Test
    fun dailyProgress_normalRatio() {
        assertEquals(0.5f, UiMath.dailyProgress(5, 10), 1e-6f)
    }

    @Test
    fun dailyProgress_clampsOverOne() {
        assertEquals(1f, UiMath.dailyProgress(15, 10), 1e-6f)
    }

    @Test
    fun dailyProgress_zeroTotalReturnsZero() {
        assertEquals(0f, UiMath.dailyProgress(3, 0), 1e-6f)
        assertEquals(0f, UiMath.dailyProgress(3, -1), 1e-6f)
    }

    @Test
    fun dailyProgress_negativeDoneClampsToZero() {
        assertEquals(0f, UiMath.dailyProgress(-2, 10), 1e-6f)
    }

    // ---------- barFraction ----------

    @Test
    fun barFraction_normalRatio() {
        assertEquals(0.25f, UiMath.barFraction(10, 40), 1e-6f)
    }

    @Test
    fun barFraction_zeroMaxReturnsZero() {
        assertEquals(0f, UiMath.barFraction(5, 0), 1e-6f)
    }

    @Test
    fun barFraction_valueAboveMaxClampsToOne() {
        assertEquals(1f, UiMath.barFraction(120, 60), 1e-6f)
    }

    // ---------- staggerDelayMs ----------

    @Test
    fun staggerDelay_growsByIndex() {
        assertEquals(0L, UiMath.staggerDelayMs(0, 55L, 8))
        assertEquals(110L, UiMath.staggerDelayMs(2, 55L, 8))
    }

    @Test
    fun staggerDelay_capsAtMaxSteps() {
        assertEquals(8 * 55L, UiMath.staggerDelayMs(30, 55L, 8))
    }

    @Test
    fun staggerDelay_negativeIndexClampsToZero() {
        assertEquals(0L, UiMath.staggerDelayMs(-3, 55L, 8))
    }
}
