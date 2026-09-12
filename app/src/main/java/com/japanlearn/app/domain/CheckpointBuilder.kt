package com.japanlearn.app.domain

import kotlin.random.Random

/**
 * 单元检查点出题（PRD §19.8）：从单元词池出混合题型，
 * 复用与单词练习一致的变体选择（汉字/听音/打字/四选一）。
 */
object CheckpointBuilder {

    fun build(pool: List<QuizWord>, count: Int, random: Random = Random.Default): List<Quiz> {
        if (pool.isEmpty() || count <= 0) return emptyList()
        val shuffled = pool.shuffled(random)
        return List(count) { i ->
            val target = shuffled[i % shuffled.size]
            val direction = WordQuizDirection.random(random)
            val hasKanji = KanjiQuizPolicy.hasKanjiForm(target.ja, target.kana)
            when (
                QuizVariantPicker.pick(
                    direction,
                    hasKanji,
                    random.nextDouble(),
                    random.nextDouble(),
                    random.nextDouble(),
                )
            ) {
                WordQuizVariant.KANJI ->
                    QuizGenerator.kanjiQuiz(target, pool, toKanji = direction == WordQuizDirection.JP_TO_CN, random)
                WordQuizVariant.AUDIO ->
                    QuizGenerator.wordQuiz(target, pool, direction, random, audio = true)
                WordQuizVariant.TYPE_KANA ->
                    QuizGenerator.typeKanaQuiz(target)
                WordQuizVariant.MCQ ->
                    QuizGenerator.wordQuiz(target, pool, direction, random)
            }
        }
    }
}
