package com.japanlearn.app

import com.japanlearn.app.util.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderSchedulerTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `当前时间早于触发点 当天触发`() {
        val now = millis(2026, 9, 6, 10, 0)
        val delay = ReminderScheduler.nextTriggerDelayMillis(now, zone = zone)
        assertEquals(millis(2026, 9, 6, 20, 0) - now, delay)
    }

    @Test
    fun `当前时间晚于触发点 顺延到明天`() {
        val now = millis(2026, 9, 6, 21, 30)
        val delay = ReminderScheduler.nextTriggerDelayMillis(now, zone = zone)
        assertEquals(millis(2026, 9, 7, 20, 0) - now, delay)
    }

    @Test
    fun `恰好等于触发点 顺延到明天`() {
        val now = millis(2026, 9, 6, 20, 0)
        val delay = ReminderScheduler.nextTriggerDelayMillis(now, zone = zone)
        assertEquals(millis(2026, 9, 7, 20, 0) - now, delay)
    }

    @Test
    fun `默认时间为每天 20 点整`() {
        assertEquals(20, ReminderScheduler.DEFAULT_HOUR)
        assertEquals(0, ReminderScheduler.DEFAULT_MINUTE)
    }

    @Test
    fun `延迟始终为正且不超过 24 小时`() {
        val morning = ReminderScheduler.nextTriggerDelayMillis(millis(2026, 9, 6, 0, 1), zone = zone)
        val night = ReminderScheduler.nextTriggerDelayMillis(millis(2026, 9, 6, 23, 59), zone = zone)
        assertTrue(morning > 0)
        assertTrue(night > 0)
        assertTrue(morning < ReminderScheduler.DAY_LIMIT_MILLIS)
        assertTrue(night < ReminderScheduler.DAY_LIMIT_MILLIS)
    }
}
