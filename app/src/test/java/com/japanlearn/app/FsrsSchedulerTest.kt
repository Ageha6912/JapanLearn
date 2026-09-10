package com.japanlearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsSchedulerTest {

    private val now = 1_700_000_000_000L
    private val day = SrsScheduler.DAY_MILLIS

    @Test
    fun `新卡熟悉 间隔上升且进入 Review`() {
        val next = FsrsScheduler.next(SrsState.INITIAL, Mastery.KNOWN, now)
        assertTrue(next.intervalDays >= 1)
        assertTrue(next.dueAt > now)
        assertTrue(next.stability > 0.0)
        assertTrue(next.difficulty in 1.0..10.0)
        assertEquals("Review", next.fsrsState)
        assertEquals(1, next.reviewCount)
        assertEquals(Mastery.KNOWN.level, next.mastery)
    }

    @Test
    fun `新卡熟练间隔大于熟悉`() {
        val good = FsrsScheduler.next(SrsState.INITIAL, Mastery.KNOWN, now)
        val easy = FsrsScheduler.next(SrsState.INITIAL, Mastery.MASTERED, now)
        assertTrue(easy.intervalDays > good.intervalDays)
        assertTrue(easy.stability > good.stability)
    }

    @Test
    fun `不认识 更新稳定性后 dueAt 仍为现在`() {
        val learned = FsrsScheduler.next(SrsState.INITIAL, Mastery.KNOWN, now)
        val again = FsrsScheduler.next(
            learned.copy(lastReviewedAt = now),
            Mastery.UNKNOWN,
            now + day,
        )
        assertEquals(now + day, again.dueAt)
        assertEquals(0, again.intervalDays)
        assertEquals(1, again.lapses)
        assertTrue(again.stability > 0.0)
        assertTrue(again.stability <= learned.stability + 1e-6)
        assertEquals("Review", again.fsrsState)
    }

    @Test
    fun `stability 达到 21 视为已掌握`() {
        assertTrue(FsrsScheduler.isMastered(SrsState.INITIAL.copy(stability = 21.0)))
        assertTrue(!FsrsScheduler.isMastered(SrsState.INITIAL.copy(stability = 20.9)))
    }
}
