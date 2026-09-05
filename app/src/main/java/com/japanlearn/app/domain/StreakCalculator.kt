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
}
