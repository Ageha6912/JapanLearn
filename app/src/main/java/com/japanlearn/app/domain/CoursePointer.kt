package com.japanlearn.app.domain

/** 课程单元进度的纯视图（与 Room 行解耦）。 */
data class CourseUnitProgress(val unit: Int, val total: Int, val learned: Int) {
    val isComplete: Boolean get() = total > 0 && learned >= total
    val progress: Float get() = if (total > 0) learned.toFloat() / total else 0f
}

/**
 * 当前单元指针（PRD §19.8）：第一个未完成的单元，全部完成则停在最后一个单元。
 * 手动覆盖优先，格式 "N5:3"（空或非法 = 自动跟随）。
 */
object CoursePointer {

    fun currentUnit(rows: List<CourseUnitProgress>): Int {
        val sorted = rows.filter { it.total > 0 }.sortedBy { it.unit }
        if (sorted.isEmpty()) return 1
        return sorted.firstOrNull { !it.isComplete }?.unit ?: sorted.last().unit
    }

    /** 解析手动覆盖；级别不匹配、格式错误或单元越界时返回 null（= 自动）。 */
    fun parseOverride(raw: String?, level: String): Int? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(":")
        if (parts.size != 2 || parts[0] != level) return null
        val unit = parts[1].toIntOrNull() ?: return null
        return if (CourseCatalog.isValidUnit(unit)) unit else null
    }

    fun formatOverride(level: String, unit: Int?): String =
        if (unit == null) "" else "$level:$unit"
}
