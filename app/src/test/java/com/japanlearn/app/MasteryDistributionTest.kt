package com.japanlearn.app

import com.japanlearn.app.domain.MasteryDistribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteryDistributionTest {

    @Test
    fun `stability 小于 1 归为新学`() {
        assertEquals(MasteryDistribution.Bucket.FRESH, MasteryDistribution.classify(0.0))
        assertEquals(MasteryDistribution.Bucket.FRESH, MasteryDistribution.classify(0.99))
    }

    @Test
    fun `stability 达到 1 归为巩固中`() {
        assertEquals(MasteryDistribution.Bucket.CONSOLIDATING, MasteryDistribution.classify(1.0))
        assertEquals(MasteryDistribution.Bucket.CONSOLIDATING, MasteryDistribution.classify(20.9))
    }

    @Test
    fun `stability 达到 21 归为已掌握且与 isMastered 阈值一致`() {
        assertEquals(MasteryDistribution.Bucket.MASTERED, MasteryDistribution.classify(21.0))
        assertEquals(MasteryDistribution.Bucket.MASTERED, MasteryDistribution.classify(40.0))
        assertEquals(
            com.japanlearn.app.domain.SrsScheduler.MASTERED_INTERVAL_DAYS.toDouble(),
            MasteryDistribution.MASTERED_THRESHOLD,
            0.0,
        )
    }

    @Test
    fun `空列表快照全零`() {
        val s = MasteryDistribution.snapshot(emptyList())
        assertEquals(0, s.total)
        assertEquals(listOf(0f, 0f, 0f), s.fractions())
    }

    @Test
    fun `快照按档计数且占比和为一`() {
        val s = MasteryDistribution.snapshot(listOf(0.0, 0.5, 1.0, 5.0, 21.0, 30.0))
        assertEquals(2, s.fresh)
        assertEquals(2, s.consolidating)
        assertEquals(2, s.mastered)
        assertEquals(6, s.total)
        val f = s.fractions()
        assertEquals(2f / 6f, f[0], 1e-6f)
        assertEquals(2f / 6f, f[1], 1e-6f)
        assertEquals(2f / 6f, f[2], 1e-6f)
        assertTrue(f.sum() in 0.99f..1.01f)
    }

    @Test
    fun `全未掌握时已掌握占比为零`() {
        val s = MasteryDistribution.snapshot(listOf(0.0, 2.0, 20.0))
        assertEquals(0, s.mastered)
        assertEquals(0f, s.fractions()[2], 0f)
    }
}
