package com.japanlearn.app

import com.japanlearn.app.domain.DrillBuilder
import com.japanlearn.app.domain.DrillEntry
import com.japanlearn.app.domain.DrillGrammarExercise
import com.japanlearn.app.domain.QuizKind
import com.japanlearn.app.domain.QuizKana
import com.japanlearn.app.domain.QuizWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillBuilderTest {

    private fun word(id: String, ja: String, kana: String, zh: String, romaji: String) =
        QuizWord(id = id, ja = ja, kana = kana, zh = zh, romaji = romaji)

    private val words = listOf(
        word("w1", "私", "わたし", "我", "watashi"),
        word("w2", "食べる", "たべる", "吃", "taberu"),
        word("w3", "学校", "がっこう", "学校", "gakkou"),
        word("w4", "行く", "いく", "去", "iku"),
        word("w5", "水", "みず", "水", "mizu"),
        word("w6", "先生", "せんせい", "老师", "sensei"),
    )

    private val kana = listOf(
        QuizKana("k1", "あ", "ア", "a"),
        QuizKana("k2", "い", "イ", "i"),
        QuizKana("k3", "う", "ウ", "u"),
        QuizKana("k4", "え", "エ", "e"),
    )

    private val grammarExercises = mapOf(
        "g1" to listOf(
            DrillGrammarExercise("彼＿学生です。", listOf("は", "が", "を", "に"), 1),
        ),
    )

    @Test
    fun `空错题池返回空`() {
        assertTrue(DrillBuilder.build(emptyList(), words, kana, grammarExercises).isEmpty())
        assertTrue(DrillBuilder.build(listOf(DrillEntry("word", "w1")), words, kana, grammarExercises, count = 0).isEmpty())
    }

    @Test
    fun `三类错题各出对应题型`() {
        val entries = listOf(
            DrillEntry("word", "w1"),
            DrillEntry("kana", "k1"),
            DrillEntry("grammar", "g1"),
        )
        val questions = DrillBuilder.build(entries, words, kana, grammarExercises, count = 10, random = kotlin.random.Random(5))
        assertEquals(3, questions.size)
        val kinds = questions.map { it.quiz.kind }.toSet()
        assertTrue(kinds.contains(QuizKind.KANA_TO_ROMAJI))
        assertTrue(kinds.contains(QuizKind.GRAMMAR_FILL))
        assertTrue(kinds.any { it == QuizKind.WORD_JP_TO_CN || it == QuizKind.AUDIO_WORD_JP_TO_CN || it == QuizKind.WORD_CN_TO_JP || it == QuizKind.KANA_TO_KANJI || it == QuizKind.WORD_TYPE_KANA })
    }

    @Test
    fun `已删除内容的错题自动跳过`() {
        val entries = listOf(
            DrillEntry("word", "deleted-word"),
            DrillEntry("kana", "deleted-kana"),
            DrillEntry("grammar", "deleted-grammar"),
            DrillEntry("word", "w2"),
        )
        val questions = DrillBuilder.build(entries, words, kana, grammarExercises, count = 10, random = kotlin.random.Random(1))
        assertEquals(1, questions.size)
        assertEquals("w2", questions.first().contentId)
    }

    @Test
    fun `题量超池时循环取不越界`() {
        val entries = (1..20).map { DrillEntry("word", "w${(it % 6) + 1}") }
        val questions = DrillBuilder.build(entries, words, kana, emptyMap(), count = 10, random = kotlin.random.Random(2))
        assertEquals(10, questions.size)
        assertTrue(questions.all { it.quiz.answerText.isNotBlank() })
    }

    @Test
    fun `语法错题使用自带练习`() {
        val questions = DrillBuilder.build(
            listOf(DrillEntry("grammar", "g1")),
            words, kana, grammarExercises, count = 5, random = kotlin.random.Random(9),
        )
        assertEquals(1, questions.size)
        val quiz = questions.first().quiz
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.options.contains("が"))
        assertEquals(quiz.options.indexOf("が"), quiz.answerIndex)
    }

    @Test
    fun `语法练习缺失时跳过`() {
        val questions = DrillBuilder.build(
            listOf(DrillEntry("grammar", "g-none")),
            words, kana, grammarExercises, count = 5, random = kotlin.random.Random(9),
        )
        assertTrue(questions.isEmpty())
    }

    @Test
    fun `固定种子下 word 错题会出现多种变体`() {
        val entries = (1..30).map { DrillEntry("word", "w${(it % 6) + 1}") }
        var sawAudio = false
        var sawType = false
        for (seed in 1..20) {
            val questions = DrillBuilder.build(entries, words, kana, emptyMap(), count = 30, random = kotlin.random.Random(seed))
            sawAudio = sawAudio || questions.any { it.quiz.audioText != null }
            sawType = sawType || questions.any { it.quiz.isTypeAnswer }
        }
        assertTrue(sawAudio)
        assertTrue(sawType)
    }
}
