package com.japanlearn.app.domain

/**
 * 掌握程度：与 PRD §7.7 SRS 规则一一对应。
 */
enum class Mastery(val level: Int, val label: String) {
    UNKNOWN(0, "不认识"),
    FUZZY(1, "模糊"),
    KNOWN(2, "熟悉"),
    MASTERED(3, "熟练");

    companion object {
        fun fromLevel(level: Int): Mastery = entries.first { it.level == level }
    }
}

/** SRS 当前状态（存储于 UserProgress）。 */
data class SrsState(
    val mastery: Int,
    val intervalDays: Int,
    val reviewCount: Int,
    val dueAt: Long,
) {
    companion object {
        val INITIAL = SrsState(mastery = 0, intervalDays = 0, reviewCount = 0, dueAt = 0L)
    }
}
