package com.japanlearn.app.domain

import kotlin.random.Random

/** 一道突击题（与 Room 解耦）。 */
data class DrillQuestion(val contentType: String, val contentId: String, val quiz: Quiz)

/** 语法自带练习的纯视图。 */
data class DrillGrammarExercise(val question: String, val options: List<String>, val answerIndex: Int)

/** 错题条目纯视图。 */
data class DrillEntry(val contentType: String, val contentId: String)

/**
 * 错题突击出题（PRD §19.10）：每轮从错题池洗牌循环取，不足 [count] 全出。
 * word → 混合题型；kana → romaji 四选一；grammar → 自带练习；kanji → 汉字专项混合题（§19.14）。
 * 内容已删除的条目自动跳过。判分由调用方走 recordAuxAnswer（答对移除、答错 +1，不推 SRS）。
 */
object DrillBuilder {

    fun build(
        entries: List<DrillEntry>,
        words: List<QuizWord>,
        kana: List<QuizKana>,
        grammarExercises: Map<String, List<DrillGrammarExercise>>,
        kanji: List<QuizKanji> = emptyList(),
        count: Int = 10,
        random: Random = Random.Default,
    ): List<DrillQuestion> {
        if (entries.isEmpty() || count <= 0) return emptyList()
        val wordById = words.associateBy { it.id }
        val kanaById = kana.associateBy { it.id }
        val kanjiById = kanji.associateBy { it.id }
        val generated = entries.mapNotNull { entry ->
            when (entry.contentType) {
                "word" -> wordById[entry.contentId]?.let { wordQuestion(it, words, random) }
                "kana" -> kanaById[entry.contentId]?.let { kanaQuestion(it, kana, random) }
                "grammar" -> grammarExercises[entry.contentId]?.randomOrNull(random)?.let { ex ->
                    DrillQuestion(
                        "grammar", entry.contentId,
                        QuizGenerator.grammarQuiz(ex.question, ex.options, ex.answerIndex, random),
                    )
                }
                "kanji" -> kanjiById[entry.contentId]?.let { kanjiQuestion(it, kanji, random) }
                else -> null
            }
        }.shuffled(random)
        return generated.take(count)
    }

    private fun wordQuestion(target: QuizWord, pool: List<QuizWord>, random: Random): DrillQuestion {
        val direction = WordQuizDirection.random(random)
        val hasKanji = KanjiQuizPolicy.hasKanjiForm(target.ja, target.kana)
        val quiz = when (
            QuizVariantPicker.pick(
                direction, hasKanji,
                random.nextDouble(), random.nextDouble(), random.nextDouble(),
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
        return DrillQuestion("word", target.id, quiz)
    }

    private fun kanaQuestion(target: QuizKana, pool: List<QuizKana>, random: Random): DrillQuestion =
        DrillQuestion("kana", target.id, QuizGenerator.kanaQuiz(target, pool, random))

    private fun kanjiQuestion(target: QuizKanji, pool: List<QuizKanji>, random: Random): DrillQuestion {
        val canAudio = target.primaryReading().isNotEmpty()
        val variant = KanjiQuizVariantPicker.pick(random.nextDouble(), canAudio)
        val quiz = KanjiQuizGenerator.build(target, pool, variant, random)
        return DrillQuestion("kanji", target.id, quiz)
    }
}
