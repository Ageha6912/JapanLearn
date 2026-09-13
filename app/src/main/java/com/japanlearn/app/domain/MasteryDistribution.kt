package com.japanlearn.app.domain

/**
 * 已学词掌握度分档（PRD §19.13）。
 * 阈值与 [FsrsScheduler.isMastered] 对齐：stability >= 21 视为已掌握。
 * 纯函数，不触碰 Room / SRS 调度。
 */
object MasteryDistribution {

    const val MASTERED_THRESHOLD = 21.0

    /** 巩固中下限：stability ≥ 1 视为已有稳定记忆痕迹；以下归为新学/刚重置。 */
    const val CONSOLIDATING_THRESHOLD = 1.0

    enum class Bucket { FRESH, CONSOLIDATING, MASTERED }

    data class Snapshot(
        val fresh: Int = 0,
        val consolidating: Int = 0,
        val mastered: Int = 0,
    ) {
        val total: Int get() = fresh + consolidating + mastered

        /** 分段条各档占比（0..1）；total 为 0 时全 0。 */
        fun fractions(): List<Float> {
            if (total <= 0) return listOf(0f, 0f, 0f)
            val t = total.toFloat()
            return listOf(fresh / t, consolidating / t, mastered / t)
        }
    }

    fun classify(stability: Double): Bucket = when {
        stability >= MASTERED_THRESHOLD -> Bucket.MASTERED
        stability >= CONSOLIDATING_THRESHOLD -> Bucket.CONSOLIDATING
        else -> Bucket.FRESH
    }

    fun snapshot(stabilities: List<Double>): Snapshot {
        var fresh = 0
        var consolidating = 0
        var mastered = 0
        for (s in stabilities) {
            when (classify(s)) {
                Bucket.FRESH -> fresh++
                Bucket.CONSOLIDATING -> consolidating++
                Bucket.MASTERED -> mastered++
            }
        }
        return Snapshot(fresh, consolidating, mastered)
    }
}
