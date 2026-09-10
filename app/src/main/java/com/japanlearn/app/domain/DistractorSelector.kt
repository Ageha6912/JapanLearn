package com.japanlearn.app.domain

import kotlin.random.Random

/**
 * 干扰项分桶取样：先耗尽同 pos，再同 cat，再其余。只在桶内 shuffle。
 */
object DistractorSelector {

    fun pick(
        target: QuizWord,
        pool: List<QuizWord>,
        answerOf: (QuizWord) -> String,
        count: Int,
        random: Random,
        excludeSameKana: Boolean,
        eligible: (QuizWord) -> Boolean = { true },
    ): List<String> {
        val answer = answerOf(target)
        val base = pool.filter { it.id != target.id }
            .filter(eligible)
            .filter { !excludeSameKana || it.kana != target.kana }
            .filter { answerOf(it) != answer }
        val samePos = base.filter { it.pos.isNotEmpty() && it.pos == target.pos }
        val samePosIds = samePos.map { it.id }.toSet()
        val sameCat = base.filter { it.cat.isNotEmpty() && it.cat == target.cat && it.id !in samePosIds }
        val used = samePosIds + sameCat.map { it.id }
        val rest = base.filter { it.id !in used }
        val ordered = samePos.shuffled(random) + sameCat.shuffled(random) + rest.shuffled(random)
        return ordered.map(answerOf).distinct().take(count)
    }
}
