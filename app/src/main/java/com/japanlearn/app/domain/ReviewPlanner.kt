package com.japanlearn.app.domain

/**
 * 每日复习限流（PRD §17.3）：到期量超过每日上限时截断，余量顺延次日。
 */
object ReviewPlanner {

    fun remainingToday(reviewsDoneToday: Int, dailyCap: Int): Int =
        (dailyCap - reviewsDoneToday).coerceAtLeast(0)

    /** 从到期队列中取今日应复习的部分。 */
    fun <T> capDue(dueItems: List<T>, reviewsDoneToday: Int, dailyCap: Int): List<T> {
        val remaining = remainingToday(reviewsDoneToday, dailyCap)
        return dueItems.take(remaining)
    }
}
