package com.japanlearn.app.domain

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

/**
 * ts-fsrs v5.4.2 / commit bb71e35 的 FSRSAlgorithm.next_state + 间隔公式精简移植。
 * MIT。enable_short_term=false、enable_fuzz=false；maximum_interval 现网 60（上游默认 36500）。
 * 包版本走 FSRS-6 默认 21 个权重。
 */
internal object FsrsAlgorithm {

    const val S_MIN = 0.001
    const val REQUEST_RETENTION = 0.9
    const val MAXIMUM_INTERVAL = 60
    const val FSRS6_DEFAULT_DECAY = 0.1542

    val DEFAULT_W: DoubleArray = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
        1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
        1.8729, 0.5425, 0.0912, 0.0658, FSRS6_DEFAULT_DECAY,
    )

    private val decay: Double = -DEFAULT_W[20]
    private val factor: Double = roundTo(exp(ln(0.9) / decay) - 1.0, 8)
    private val intervalModifier: Double =
        roundTo((REQUEST_RETENTION.pow(1.0 / decay) - 1.0) / factor, 8)

    fun initStability(g: Int): Double = maxOf(DEFAULT_W[g - 1], 0.1)

    fun initDifficulty(g: Int): Double {
        val d = DEFAULT_W[4] - exp((g - 1) * DEFAULT_W[5]) + 1.0
        return clamp(roundTo(d, 8), 1.0, 10.0)
    }

    fun forgettingCurve(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        return roundTo((1.0 + factor * elapsedDays / stability).pow(decay), 8)
    }

    fun nextInterval(stability: Double): Int {
        val raw = kotlin.math.round(stability * intervalModifier).toInt()
        return raw.coerceIn(1, MAXIMUM_INTERVAL)
    }

    fun nextState(difficulty: Double, stability: Double, elapsedDays: Double, grade: Int): Pair<Double, Double> {
        if (difficulty == 0.0 && stability == 0.0) {
            return initDifficulty(grade) to initStability(grade)
        }
        val r = forgettingCurve(elapsedDays, stability)
        val newS = if (grade == 1) {
            val afterFail = nextForgetStability(difficulty, stability, r)
            clamp(roundTo(stability, 8), S_MIN, afterFail)
        } else {
            nextRecallStability(difficulty, stability, r, grade)
        }
        return nextDifficulty(difficulty, grade) to newS
    }

    private fun nextDifficulty(d: Double, g: Int): Double {
        val delta = -DEFAULT_W[6] * (g - 3)
        val damped = roundTo(delta * (10.0 - d) / 9.0, 8)
        val next = d + damped
        val reverted = roundTo(DEFAULT_W[7] * initDifficulty(4) + (1.0 - DEFAULT_W[7]) * next, 8)
        return clamp(reverted, 1.0, 10.0)
    }

    private fun nextRecallStability(d: Double, s: Double, r: Double, g: Int): Double {
        val hardPenalty = if (g == 2) DEFAULT_W[15] else 1.0
        val easyBound = if (g == 4) DEFAULT_W[16] else 1.0
        val value = s * (
            1.0 + exp(DEFAULT_W[8]) * (11.0 - d) * s.pow(-DEFAULT_W[9]) *
                (exp((1.0 - r) * DEFAULT_W[10]) - 1.0) * hardPenalty * easyBound
            )
        return roundTo(clamp(value, S_MIN, 36500.0), 8)
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val value = DEFAULT_W[11] * d.pow(-DEFAULT_W[12]) *
            ((s + 1.0).pow(DEFAULT_W[13]) - 1.0) * exp((1.0 - r) * DEFAULT_W[14])
        return roundTo(clamp(value, S_MIN, 36500.0), 8)
    }

    private fun clamp(x: Double, min: Double, max: Double): Double = x.coerceIn(min, max)

    private fun roundTo(x: Double, digits: Int): Double {
        var f = 1.0
        repeat(digits) { f *= 10.0 }
        return round(x * f) / f
    }
}
