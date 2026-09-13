package com.japanlearn.app

import com.japanlearn.app.domain.StudyPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlannerTest {

    // ---- plan：日期倒推 ----

    @Test
    fun `未设日期时只返回空计划`() {
        val plan = StudyPlanner.plan(remainingWords = 100, targetEpochDay = 0, todayEpochDay = 100)
        assertEquals(null, plan.daysRemaining)
        assertEquals(null, plan.recommendedTier)
        assertFalse(plan.overdue)
    }

    @Test
    fun `剩余量平分天数时取刚好档位`() {
        val plan = StudyPlanner.plan(remainingWords = 50, targetEpochDay = 110, todayEpochDay = 100)
        assertEquals(10, plan.daysRemaining)
        assertEquals(5, plan.recommendedTier)
        assertFalse(plan.overdue)
    }

    @Test
    fun `除不尽时向上取整再进档`() {
        // 51 词 / 10 天 → 每天 6 个 → 进到 10 的档位
        assertEquals(10, StudyPlanner.plan(remainingWords = 51, targetEpochDay = 110, todayEpochDay = 100).recommendedTier)
    }

    @Test
    fun `所需超过上限时封顶并标记偏紧`() {
        // 300 词 / 10 天 → 每天 30 个超出 20 上限
        val plan = StudyPlanner.plan(remainingWords = 300, targetEpochDay = 110, todayEpochDay = 100)
        assertEquals(20, plan.recommendedTier)
        assertTrue(plan.overdue)
    }

    @Test
    fun `日期已过仍有剩余时标记逾期且不推荐`() {
        val plan = StudyPlanner.plan(remainingWords = 10, targetEpochDay = 100, todayEpochDay = 100)
        assertTrue(plan.overdue)
        assertEquals(null, plan.recommendedTier)
        assertEquals(0, plan.daysRemaining)
    }

    @Test
    fun `目标已达成时不再推荐档位`() {
        val plan = StudyPlanner.plan(remainingWords = 0, targetEpochDay = 110, todayEpochDay = 100)
        assertEquals(null, plan.recommendedTier)
        assertFalse(plan.overdue)
        assertEquals(10, plan.daysRemaining)
    }

    @Test
    fun `剩余量远小于天数时取最小档位`() {
        assertEquals(5, StudyPlanner.plan(remainingWords = 3, targetEpochDay = 1100, todayEpochDay = 100).recommendedTier)
    }

    // ---- progressPercent ----

    @Test
    fun `进度百分比向下取整`() {
        assertEquals(41, StudyPlanner.progressPercent(211, 504))
    }

    @Test
    fun `总量为零时进度为零`() {
        assertEquals(0, StudyPlanner.progressPercent(0, 0))
    }

    @Test
    fun `全部学完为一百`() {
        assertEquals(100, StudyPlanner.progressPercent(504, 504))
    }

    @Test
    fun `进度不越过一百`() {
        assertEquals(100, StudyPlanner.progressPercent(600, 504))
    }

    // ---- normalizeLevel ----

    @Test
    fun `合法级别原样保留`() {
        assertEquals("N5", StudyPlanner.normalizeLevel("N5"))
        assertEquals("N4", StudyPlanner.normalizeLevel("N4"))
    }

    @Test
    fun `非法或缺失级别回退为未设置`() {
        assertEquals(StudyPlanner.LEVEL_NONE, StudyPlanner.normalizeLevel("N3"))
        assertEquals(StudyPlanner.LEVEL_NONE, StudyPlanner.normalizeLevel(null))
        assertEquals(StudyPlanner.LEVEL_NONE, StudyPlanner.normalizeLevel(""))
    }

    // ---- shouldRecalibrate：周校准 ----

    @Test
    fun `从未校准过时立即校准`() {
        assertTrue(StudyPlanner.shouldRecalibrate(lastAppliedEpochDay = 0, todayEpochDay = 100))
    }

    @Test
    fun `一周内不重复校准`() {
        assertFalse(StudyPlanner.shouldRecalibrate(lastAppliedEpochDay = 95, todayEpochDay = 100))
    }

    @Test
    fun `满七天重新校准`() {
        assertTrue(StudyPlanner.shouldRecalibrate(lastAppliedEpochDay = 93, todayEpochDay = 100))
    }

    @Test
    fun `汉字每日配额 无目标默认 2`() {
        assertEquals(2, StudyPlanner.kanjiDailyCount(dailyWordTier = 10, hasGoal = false))
        assertEquals(StudyPlanner.KANJI_DAILY_DEFAULT, StudyPlanner.kanjiDailyCount(20, hasGoal = false))
    }

    @Test
    fun `汉字每日配额 按档位折算 1 到 4`() {
        assertEquals(1, StudyPlanner.kanjiDailyCount(5, hasGoal = true))
        assertEquals(2, StudyPlanner.kanjiDailyCount(10, hasGoal = true))
        assertEquals(3, StudyPlanner.kanjiDailyCount(15, hasGoal = true))
        assertEquals(4, StudyPlanner.kanjiDailyCount(20, hasGoal = true))
    }
}
