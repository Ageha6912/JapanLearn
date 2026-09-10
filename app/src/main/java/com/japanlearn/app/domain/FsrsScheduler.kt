package com.japanlearn.app.domain

/**
 * FSRS 调度器：vendor 精简移植 ts-fsrs **v5.4.2** / commit **bb71e35** 的 long-term scheduler
 *（`enable_short_term = false`、`enable_fuzz = false`）。
 *
 * 评级：不认识→Again(1) / 模糊→Hard(2) / 熟悉→Good(3) / 熟练→Easy(4)。按钮文案不改。
 * Again 仍更新 S/D/lapses，然后覆盖 `dueAt = now`（会话重出队，PRD §17.5）。
 */
object FsrsScheduler : Scheduler {

    override fun next(state: SrsState, mastery: Mastery, nowMillis: Long): SrsState {
        val grade = when (mastery) {
            Mastery.UNKNOWN -> 1
            Mastery.FUZZY -> 2
            Mastery.KNOWN -> 3
            Mastery.MASTERED -> 4
        }
        val elapsed = elapsedDays(state, nowMillis)
        val (difficulty, stability) = FsrsAlgorithm.nextState(
            state.difficulty,
            state.stability,
            elapsed,
            grade,
        )
        val interval = FsrsAlgorithm.nextInterval(stability)
        val dueAt = if (mastery == Mastery.UNKNOWN) {
            nowMillis
        } else {
            nowMillis + interval * SrsScheduler.DAY_MILLIS
        }
        return SrsState(
            mastery = mastery.level,
            intervalDays = if (mastery == Mastery.UNKNOWN) 0 else interval,
            reviewCount = state.reviewCount + 1,
            dueAt = dueAt,
            stability = stability,
            difficulty = difficulty,
            lapses = state.lapses + if (mastery == Mastery.UNKNOWN) 1 else 0,
            fsrsState = "Review",
            lastReviewedAt = nowMillis,
        )
    }

    override fun isMastered(state: SrsState): Boolean =
        state.stability >= SrsScheduler.MASTERED_INTERVAL_DAYS

    private fun elapsedDays(state: SrsState, nowMillis: Long): Double {
        if (state.fsrsState == "New" || state.lastReviewedAt == null) return 0.0
        return ((nowMillis - state.lastReviewedAt).toDouble() / SrsScheduler.DAY_MILLIS)
            .coerceAtLeast(0.0)
    }
}
