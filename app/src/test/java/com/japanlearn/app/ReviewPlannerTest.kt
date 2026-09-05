package com.japanlearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewPlannerTest {

    @Test
    fun `剩余额度为上限减去已完成`() {
        assertEquals(20, ReviewPlanner.remainingToday(reviewsDoneToday = 10, dailyCap = 30))
    }

    @Test
    fun `超额完成时剩余为零`() {
        assertEquals(0, ReviewPlanner.remainingToday(reviewsDoneToday = 35, dailyCap = 30))
    }

    @Test
    fun `到期列表按额度截断`() {
        val due = listOf(1, 2, 3, 4, 5)
        assertEquals(listOf(1, 2, 3), ReviewPlanner.capDue(due, reviewsDoneToday = 7, dailyCap = 10))
    }

    @Test
    fun `额度用尽时不再取任何到期项`() {
        val due = listOf(1, 2, 3)
        assertEquals(emptyList<Int>(), ReviewPlanner.capDue(due, reviewsDoneToday = 30, dailyCap = 30))
    }

    @Test
    fun `到期量小于剩余额度时全部取出`() {
        val due = listOf(1, 2)
        assertEquals(listOf(1, 2), ReviewPlanner.capDue(due, reviewsDoneToday = 0, dailyCap = 30))
    }
}
