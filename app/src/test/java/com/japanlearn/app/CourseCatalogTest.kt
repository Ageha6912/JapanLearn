package com.japanlearn.app

import com.japanlearn.app.domain.CourseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseCatalogTest {

    @Test
    fun `单元号 1 到 11 合法`() {
        for (unit in 1..CourseCatalog.UNITS_PER_LEVEL) {
            assertTrue(CourseCatalog.isValidUnit(unit))
        }
    }

    @Test
    fun `越界单元号不合法`() {
        assertFalse(CourseCatalog.isValidUnit(0))
        assertFalse(CourseCatalog.isValidUnit(12))
        assertFalse(CourseCatalog.isValidUnit(-1))
    }

    @Test
    fun `11 个单元都有非空且互异的标题`() {
        val titles = (1..CourseCatalog.UNITS_PER_LEVEL).map { CourseCatalog.unitTitle(it) }
        assertTrue(titles.all { it.isNotEmpty() })
        assertEquals(CourseCatalog.UNITS_PER_LEVEL, titles.toSet().size)
    }
}
