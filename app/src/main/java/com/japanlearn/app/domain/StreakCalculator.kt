package com.japanlearn.app.domain

import java.time.LocalDate

/**
 * 连续学习天数：以“今天或昨天”为终点向前回溯的连续学习日数量。
 */
object StreakCalculator {

    fun streak(studyDates: Set<String>, today: LocalDate): Int {
        var cursor = today
        if (today.toString() !in studyDates) {
            cursor = today.minusDays(1)
            if (cursor.toString() !in studyDates) return 0
        }
        var count = 0
        while (cursor.toString() in studyDates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** 历史最长连续学习天数（PRD §19.8 学习成果页）：在全量学习日期里找最长连续段。 */
    fun longestStreak(studyDates: Set<String>): Int {
        if (studyDates.isEmpty()) return 0
        val sorted = studyDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
        }
        return longest
    }
}
