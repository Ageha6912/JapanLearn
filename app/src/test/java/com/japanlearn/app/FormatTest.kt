package com.japanlearn.app

import com.japanlearn.app.util.formatStudyDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun underOneHour_showsMinutesOnly() {
        assertEquals("0m", formatStudyDuration(0))
        assertEquals("5m", formatStudyDuration(5 * 60))
    }

    @Test
    fun overOneHour_showsHoursAndMinutes() {
        assertEquals("1h5m", formatStudyDuration(3600 + 5 * 60))
        assertEquals("2h0m", formatStudyDuration(7200))
    }

    @Test
    fun negativeSeconds_clampsToZero() {
        assertEquals("0m", formatStudyDuration(-30))
    }
}
