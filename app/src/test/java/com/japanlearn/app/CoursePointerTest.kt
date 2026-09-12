package com.japanlearn.app

import com.japanlearn.app.domain.CheckpointBuilder
import com.japanlearn.app.domain.CourseCatalog
import com.japanlearn.app.domain.CoursePointer
import com.japanlearn.app.domain.CourseUnitProgress
import com.japanlearn.app.domain.QuizKind
import com.japanlearn.app.domain.QuizWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoursePointerTest {

    private fun row(unit: Int, total: Int, learned: Int) = CourseUnitProgress(unit, total, learned)

    @Test
    fun `当前单元是第一个未完成的单元`() {
        val rows = listOf(row(1, 10, 10), row(2, 10, 4), row(3, 10, 0))
        assertEquals(2, CoursePointer.currentUnit(rows))
    }

    @Test
    fun `全部完成时停在最后一个单元`() {
        val rows = listOf(row(1, 10, 10), row(2, 10, 10))
        assertEquals(2, CoursePointer.currentUnit(rows))
    }

    @Test
    fun `无数据时从第一单元开始`() {
        assertEquals(1, CoursePointer.currentUnit(emptyList()))
    }

    @Test
    fun `乱序输入也按单元号排序`() {
        val rows = listOf(row(3, 10, 0), row(1, 10, 10), row(2, 10, 10))
        assertEquals(3, CoursePointer.currentUnit(rows))
    }

    @Test
    fun `覆盖解析匹配级别与单元`() {
        assertEquals(3, CoursePointer.parseOverride("N5:3", "N5"))
        assertEquals(null, CoursePointer.parseOverride("N5:3", "N4"))
    }

    @Test
    fun `覆盖为空或非法时回退自动`() {
        assertEquals(null, CoursePointer.parseOverride("", "N5"))
        assertEquals(null, CoursePointer.parseOverride(null, "N5"))
        assertEquals(null, CoursePointer.parseOverride("N5:x", "N5"))
        assertEquals(null, CoursePointer.parseOverride("N5:12", "N5"))
        assertEquals(null, CoursePointer.parseOverride("N5:0", "N5"))
    }

    @Test
    fun `覆盖格式化与清除`() {
        assertEquals("N4:7", CoursePointer.formatOverride("N4", 7))
        assertEquals("", CoursePointer.formatOverride("N4", null))
    }

    @Test
    fun `单元目录覆盖 1 到 11`() {
        assertTrue(CourseCatalog.isValidUnit(1))
        assertTrue(CourseCatalog.isValidUnit(11))
        assertEquals(11, CourseCatalog.UNITS_PER_LEVEL)
    }
}

class CheckpointBuilderTest {

    private fun word(id: String, ja: String, kana: String, zh: String, romaji: String) =
        QuizWord(id = id, ja = ja, kana = kana, zh = zh, romaji = romaji)

    private val pool = listOf(
        word("w1", "私", "わたし", "我", "watashi"),
        word("w2", "食べる", "たべる", "吃", "taberu"),
        word("w3", "学校", "がっこう", "学校", "gakkou"),
        word("w4", "行く", "いく", "去", "iku"),
        word("w5", "水", "みず", "水", "mizu"),
        word("w6", "新しい", "あたらしい", "新的", "atarashii"),
        word("w7", "先生", "せんせい", "老师", "sensei"),
        word("w8", "猫", "ねこ", "猫", "neko"),
    )

    @Test
    fun `题量不足一个池时循环取词不崩溃`() {
        val quizzes = CheckpointBuilder.build(pool, count = 10, random = kotlin.random.Random(3))
        assertEquals(10, quizzes.size)
        quizzes.forEach { q ->
            if (q.isTypeAnswer) {
                assertTrue(q.acceptedAnswers.isNotEmpty())
            } else {
                assertTrue(q.options.isNotEmpty())
                assertTrue(q.answerIndex in q.options.indices)
            }
        }
    }

    @Test
    fun `固定种子下出现多种题型`() {
        var sawType = false
        var sawAudio = false
        for (seed in 1..20) {
            val quizzes = CheckpointBuilder.build(pool, count = 30, random = kotlin.random.Random(seed))
            sawType = sawType || quizzes.any { it.kind == QuizKind.WORD_TYPE_KANA }
            sawAudio = sawAudio || quizzes.any { it.kind == QuizKind.AUDIO_WORD_JP_TO_CN }
        }
        assertTrue(sawType)
        assertTrue(sawAudio)
    }

    @Test
    fun `空池或非法题量返回空`() {
        assertTrue(CheckpointBuilder.build(emptyList(), 10).isEmpty())
        assertTrue(CheckpointBuilder.build(pool, 0).isEmpty())
    }
}
