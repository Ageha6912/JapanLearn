package com.japanlearn.app.domain

/**
 * 学习目标与个性化每日计划（PRD §19.6）：级别 + 可选目标日期驱动每日新词档位推荐。
 * 纯函数，便于单元测试；不触碰 SRS 调度与复习限流。
 */
object StudyPlanner {

    /** 每日新词档位（PRD §17.3）。 */
    val DAILY_TIERS = listOf(5, 10, 15, 20)

    /** 目标级别未设置时的持久化取值。 */
    const val LEVEL_NONE = ""

    /** 周校准周期：距上次自动应用推荐 ≥ 7 天才再次自动应用。 */
    const val RECALIBRATE_AFTER_DAYS = 7L

    /** 目标倒推结果。 */
    data class Plan(
        /** 距目标日剩余天数；未设日期为 null，目标日当天及以后 ≤ 0。 */
        val daysRemaining: Int?,
        /** 推荐的每日新词档位；未设日期或目标已达成时为 null。 */
        val recommendedTier: Int?,
        /** 按剩余天数倒推超出最大档位，或目标日已过仍有剩余。 */
        val overdue: Boolean,
    )

    /** 合法级别白名单，非法或缺失值回退为未设置。 */
    fun normalizeLevel(raw: String?): String = if (raw == "N5" || raw == "N4") raw else LEVEL_NONE

    /** 目标进度百分比 0..100，向下取整；总量为 0 时为 0。 */
    fun progressPercent(learned: Int, total: Int): Int =
        if (total <= 0) 0 else (learned * 100 / total).coerceIn(0, 100)

    /**
     * 按目标日期倒推：剩余量 ÷ 剩余天数向上取整，推荐「不小于所需的最小档位」并封顶 20。
     * 未设日期只报告剩余天数；目标已达成不再推荐；日期已过仍有剩余记 overdue 且不推荐。
     */
    fun plan(remainingWords: Int, targetEpochDay: Long, todayEpochDay: Long): Plan {
        if (targetEpochDay <= 0) {
            return Plan(daysRemaining = null, recommendedTier = null, overdue = false)
        }
        val days = (targetEpochDay - todayEpochDay).toInt()
        if (remainingWords <= 0) {
            return Plan(daysRemaining = days, recommendedTier = null, overdue = false)
        }
        if (days <= 0) {
            return Plan(daysRemaining = days, recommendedTier = null, overdue = true)
        }
        val needed = (remainingWords + days - 1) / days
        val tier = DAILY_TIERS.firstOrNull { it >= needed }
        return if (tier != null) {
            Plan(daysRemaining = days, recommendedTier = tier, overdue = false)
        } else {
            Plan(daysRemaining = days, recommendedTier = DAILY_TIERS.last(), overdue = true)
        }
    }

    /** 从未校准过（0）立即校准；一周内不重复，满七天重新校准。 */
    fun shouldRecalibrate(lastAppliedEpochDay: Long, todayEpochDay: Long): Boolean =
        lastAppliedEpochDay <= 0 || todayEpochDay - lastAppliedEpochDay >= RECALIBRATE_AFTER_DAYS
}
