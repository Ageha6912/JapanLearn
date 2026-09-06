package com.japanlearn.app.domain

/** 桌面小组件「今日任务」的状态计算（纯函数，便于单元测试）。 */
object WidgetMath {

    data class TodayState(
        val newWordsLeft: Int,
        val grammarLeft: Int,
        val dueReviews: Int,
        val streak: Int,
    ) {
        val totalTasks: Int get() = newWordsLeft + grammarLeft + dueReviews
        val allDone: Boolean get() = totalTasks == 0
    }

    /**
     * @param newTarget 每日新词目标（设置值）
     * @param newDone 今日已完成新词数
     * @param grammarTarget 每日新语法目标
     * @param grammarDone 今日已完成语法数
     * @param dueReviews 当前到期待复习条数
     */
    fun compute(
        newTarget: Int,
        newDone: Int,
        grammarTarget: Int,
        grammarDone: Int,
        dueReviews: Int,
        streak: Int,
    ): TodayState = TodayState(
        newWordsLeft = (newTarget - newDone).coerceAtLeast(0),
        grammarLeft = (grammarTarget - grammarDone).coerceAtLeast(0),
        dueReviews = dueReviews.coerceAtLeast(0),
        streak = streak.coerceAtLeast(0),
    )
}
