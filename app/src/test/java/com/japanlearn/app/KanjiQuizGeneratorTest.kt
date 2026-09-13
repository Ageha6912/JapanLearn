package com.japanlearn.app

import com.japanlearn.app.domain.KanjiQuizGenerator
import com.japanlearn.app.domain.KanjiQuizVariant
import com.japanlearn.app.domain.KanjiQuizVariantPicker
import com.japanlearn.app.domain.QuizKanji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KanjiQuizGeneratorTest {

    private val pool = listOf(
        QuizKanji("k1", "日", "日；太阳", on = listOf("ニチ", "ジツ"), kun = listOf("ひ", "か")),
        QuizKanji("k2", "月", "月", on = listOf("ゲツ", "ガツ"), kun = listOf("つき")),
        QuizKanji("k3", "火", "火", on = listOf("カ"), kun = listOf("ひ")),
        QuizKanji("k4", "水", "水", on = listOf("スイ"), kun = listOf("みず")),
        QuizKanji("k5", "木", "树", on = listOf("モク", "ボク"), kun = listOf("き")),
        QuizKanji("k6", "金", "金；钱", on = listOf("キン", "コン"), kun = listOf("かね")),
    )

    @Test
    fun `看字选义 题干为汉字 选项含正确释义`() {
        val quiz = KanjiQuizGenerator.toZh(pool[0], pool, Random(1))
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.options.contains("日；太阳"))
        assertEquals("日；太阳", quiz.options[quiz.answerIndex])
        assertTrue(quiz.question.contains("日"))
    }

    @Test
    fun `看义选字 选项均为汉字`() {
        val quiz = KanjiQuizGenerator.toChar(pool[0], pool, Random(2))
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.options.contains("日"))
        assertEquals("日", quiz.options[quiz.answerIndex])
        assertTrue(quiz.options.all { it.length == 1 })
    }

    @Test
    fun `看字选读音 正确项为音训展示串`() {
        val quiz = KanjiQuizGenerator.toReading(pool[0], pool, Random(3))
        assertEquals("音：ニチ・ジツ／訓：ひ・か", quiz.options[quiz.answerIndex])
        assertTrue(quiz.options.size == 4)
    }

    @Test
    fun `看读音选字 同读音干扰项被排除`() {
        // 「火」训读也是ひ，不应作为「日」的干扰项
        val quiz = KanjiQuizGenerator.fromReading(pool[0], pool, Random(4))
        assertEquals("日", quiz.options[quiz.answerIndex])
        assertTrue("火" !in quiz.options)
        assertTrue(quiz.options.size == 4)
    }

    @Test
    fun `听音选字 audioText 优先训读`() {
        val quiz = KanjiQuizGenerator.audio(pool[0], pool, Random(5))
        assertEquals("ひ", quiz.audioText)
        assertEquals("日", quiz.options[quiz.answerIndex])
    }

    @Test
    fun `听音选字 无训读时用音读`() {
        val target = QuizKanji("k9", "駅", "车站", on = listOf("エキ"), kun = emptyList())
        val quiz = KanjiQuizGenerator.audio(target, pool, Random(6))
        assertEquals("エキ", quiz.audioText)
    }

    @Test
    fun `池不足时退化为更少选项但答案仍在`() {
        val small = pool.take(2)
        val quiz = KanjiQuizGenerator.toZh(small[0], small, Random(7))
        assertTrue(quiz.options.size in 2..4)
        assertEquals("日；太阳", quiz.options[quiz.answerIndex])
    }

    @Test
    fun `变体选择 均匀覆盖五种`() {
        assertEquals(KanjiQuizVariant.TO_ZH, KanjiQuizVariantPicker.pick(0.0))
        assertEquals(KanjiQuizVariant.TO_CHAR, KanjiQuizVariantPicker.pick(0.25))
        assertEquals(KanjiQuizVariant.TO_READING, KanjiQuizVariantPicker.pick(0.45))
        assertEquals(KanjiQuizVariant.FROM_READING, KanjiQuizVariantPicker.pick(0.65))
        assertEquals(KanjiQuizVariant.AUDIO, KanjiQuizVariantPicker.pick(0.9, canAudio = true))
    }

    @Test
    fun `变体选择 不可听音时回退看字选义`() {
        assertEquals(KanjiQuizVariant.TO_ZH, KanjiQuizVariantPicker.pick(0.9, canAudio = false))
    }

    @Test
    fun `音训展示 仅音读时不写训读段`() {
        val target = QuizKanji("k8", "社", "公司", on = listOf("シャ"), kun = emptyList())
        assertEquals("音：シャ", target.readingDisplay())
    }
}
