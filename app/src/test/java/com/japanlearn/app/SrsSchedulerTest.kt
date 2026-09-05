package com.japanlearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsSchedulerTest {

    private val day = SrsScheduler.DAY_MILLIS
    private val now = 1_700_000_000_000L

    @Test
    fun `不认识 立即到期 以便本次会话重新出队`() {
        val next = SrsScheduler.next(SrsState.INITIAL, Mastery.UNKNOWN, now)
        assertEquals(now, next.dueAt)
        assertEquals(0, next.intervalDays)
        assertEquals(1, next.reviewCount)
        assertEquals(Mastery.UNKNOWN.level, next.mastery)
    }

    @Test
    fun `模糊 间隔 1 天`() {
        val next = SrsScheduler.next(SrsState.INITIAL, Mastery.FUZZY, now)
        assertEquals(now + day, next.dueAt)
        assertEquals(1, next.intervalDays)
    }

    @Test
    fun `熟悉 首次间隔 3 天`() {
        val next = SrsScheduler.next(SrsState.INITIAL, Mastery.KNOWN, now)
        assertEquals(now + 3 * day, next.dueAt)
        assertEquals(3, next.intervalDays)
    }

    @Test
    fun `熟练 首次间隔 7 天`() {
        val next = SrsScheduler.next(SrsState.INITIAL, Mastery.MASTERED, now)
        assertEquals(now + 7 * day, next.dueAt)
        assertEquals(7, next.intervalDays)
    }

    @Test
    fun `熟悉 间隔按 1_5 倍递增`() {
        val prev = SrsState(Mastery.KNOWN.level, intervalDays = 8, reviewCount = 3, dueAt = now)
        val next = SrsScheduler.next(prev, Mastery.KNOWN, now)
        assertEquals(12, next.intervalDays) // 8 * 1.5 = 12
        assertEquals(now + 12 * day, next.dueAt)
    }

    @Test
    fun `熟悉 递增结果不小于 3 天`() {
        val prev = SrsState(Mastery.KNOWN.level, intervalDays = 1, reviewCount = 2, dueAt = now)
        val next = SrsScheduler.next(prev, Mastery.KNOWN, now)
        assertEquals(3, next.intervalDays) // max(3, 1)
    }

    @Test
    fun `熟练 间隔按 2 倍递增`() {
        val prev = SrsState(Mastery.MASTERED.level, intervalDays = 10, reviewCount = 4, dueAt = now)
        val next = SrsScheduler.next(prev, Mastery.MASTERED, now)
        assertEquals(20, next.intervalDays)
    }

    @Test
    fun `间隔不超过 60 天上限`() {
        val prev = SrsState(Mastery.MASTERED.level, intervalDays = 59, reviewCount = 10, dueAt = now)
        val next = SrsScheduler.next(prev, Mastery.MASTERED, now)
        assertEquals(SrsScheduler.MAX_INTERVAL_DAYS, next.intervalDays)
    }

    @Test
    fun `累计复习次数单调递增`() {
        val prev = SrsState(Mastery.FUZZY.level, intervalDays = 1, reviewCount = 5, dueAt = now)
        val next = SrsScheduler.next(prev, Mastery.FUZZY, now)
        assertEquals(6, next.reviewCount)
    }

    @Test
    fun `熟练且间隔达到 21 天视为已掌握`() {
        val mastered = SrsState(Mastery.MASTERED.level, intervalDays = SrsScheduler.MASTERED_INTERVAL_DAYS, reviewCount = 5, dueAt = now)
        val notYet = SrsState(Mastery.MASTERED.level, intervalDays = 7, reviewCount = 1, dueAt = now)
        assertTrue(SrsScheduler.isMastered(mastered))
        assertFalse(SrsScheduler.isMastered(notYet))
        assertFalse(SrsScheduler.isMastered(SrsState.INITIAL))
    }
}
