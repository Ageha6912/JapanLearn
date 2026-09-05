package com.japanlearn.app.domain

/**
 * 简单 SRS 调度器（PRD §17.5）：
 * - 不认识：到期时间 = 现在（由会话层在本次会话内重新出队），并记入错题本
 * - 模糊：1 天
 * - 熟悉：max(3 天, 上次间隔 × 1.5)
 * - 熟练：max(7 天, 上次间隔 × 2)，间隔上限 60 天
 */
object SrsScheduler {

    const val DAY_MILLIS: Long = 86_400_000L
    const val MAX_INTERVAL_DAYS: Int = 60

    /** 间隔达到该天数（且掌握度熟练）视为“已掌握”，用于统计。 */
    const val MASTERED_INTERVAL_DAYS: Int = 21

    fun next(state: SrsState, mastery: Mastery, nowMillis: Long): SrsState {
        val reviewCount = state.reviewCount + 1
        return when (mastery) {
            Mastery.UNKNOWN -> SrsState(
                mastery = Mastery.UNKNOWN.level,
                intervalDays = 0,
                reviewCount = reviewCount,
                dueAt = nowMillis,
            )

            Mastery.FUZZY -> SrsState(
                mastery = Mastery.FUZZY.level,
                intervalDays = 1,
                reviewCount = reviewCount,
                dueAt = nowMillis + DAY_MILLIS,
            )

            Mastery.KNOWN -> {
                val interval = minOf(MAX_INTERVAL_DAYS, maxOf(3, state.intervalDays * 3 / 2))
                SrsState(Mastery.KNOWN.level, interval, reviewCount, nowMillis + interval * DAY_MILLIS)
            }

            Mastery.MASTERED -> {
                val interval = minOf(MAX_INTERVAL_DAYS, maxOf(7, state.intervalDays * 2))
                SrsState(Mastery.MASTERED.level, interval, reviewCount, nowMillis + interval * DAY_MILLIS)
            }
        }
    }

    fun isMastered(state: SrsState): Boolean =
        state.mastery >= Mastery.MASTERED.level && state.intervalDays >= MASTERED_INTERVAL_DAYS
}
