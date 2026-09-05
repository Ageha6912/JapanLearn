package com.japanlearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    @Test
    fun `今天学习了 连击为连续天数`() {
        val today = LocalDate.of(2026, 9, 5)
        val dates = setOf("2026-09-05", "2026-09-04", "2026-09-03")
        assertEquals(3, StreakCalculator.streak(dates, today))
    }

    @Test
    fun `今天还没学 但昨天学了 连击仍延续`() {
        val today = LocalDate.of(2026, 9, 5)
        val dates = setOf("2026-09-04", "2026-09-03", "2026-09-02")
        assertEquals(3, StreakCalculator.streak(dates, today))
    }

    @Test
    fun `昨天也没学 连击归零`() {
        val today = LocalDate.of(2026, 9, 5)
        val dates = setOf("2026-09-03", "2026-09-02")
        assertEquals(0, StreakCalculator.streak(dates, today))
    }

    @Test
    fun `中间断档后 只统计最近连续段`() {
        val today = LocalDate.of(2026, 9, 5)
        val dates = setOf("2026-09-05", "2026-09-04", "2026-09-01", "2026-08-31")
        assertEquals(2, StreakCalculator.streak(dates, today))
    }

    @Test
    fun `从未学习 连击为零`() {
        val today = LocalDate.of(2026, 9, 5)
        assertEquals(0, StreakCalculator.streak(emptySet(), today))
    }
}
