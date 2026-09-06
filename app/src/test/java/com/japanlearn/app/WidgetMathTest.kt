package com.japanlearn.app

import com.japanlearn.app.domain.WidgetMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 小组件「今日任务」状态计算（v0.3）。 */
class WidgetMathTest {

    @Test
    fun `剩余量为目标减已完成`() {
        val s = WidgetMath.compute(newTarget = 10, newDone = 4, grammarTarget = 3, grammarDone = 1, dueReviews = 5, streak = 3)
        assertEquals(6, s.newWordsLeft)
        assertEquals(2, s.grammarLeft)
        assertEquals(5, s.dueReviews)
        assertEquals(13, s.totalTasks)
        assertFalse(s.allDone)
    }

    @Test
    fun `超额完成钳制为零并显示全部完成`() {
        val s = WidgetMath.compute(newTarget = 10, newDone = 12, grammarTarget = 3, grammarDone = 3, dueReviews = 0, streak = 9)
        assertEquals(0, s.newWordsLeft)
        assertEquals(0, s.grammarLeft)
        assertEquals(0, s.totalTasks)
        assertTrue(s.allDone)
    }

    @Test
    fun `负数输入被钳制`() {
        val s = WidgetMath.compute(newTarget = 10, newDone = -1, grammarTarget = 3, grammarDone = 0, dueReviews = -5, streak = -1)
        assertEquals(11, s.newWordsLeft)
        assertEquals(0, s.dueReviews)
        assertEquals(0, s.streak)
    }
}
