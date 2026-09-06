package com.japanlearn.app.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 复习提醒调度计算（纯函数，便于单元测试）：每天固定 hour:minute 触发一次。
 */
object ReminderScheduler {

    const val DEFAULT_HOUR = 20
    const val DEFAULT_MINUTE = 0
    const val DAY_LIMIT_MILLIS = 24 * 60 * 60 * 1000L

    /** 距下一次触发时刻的延迟毫秒数（触发时刻已过今天则顺延到明天）。 */
    fun nextTriggerDelayMillis(
        nowMillis: Long,
        hour: Int = DEFAULT_HOUR,
        minute: Int = DEFAULT_MINUTE,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(zone).toInstant().toEpochMilli() - nowMillis
    }
}
