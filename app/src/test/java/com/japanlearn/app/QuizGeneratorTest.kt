package com.japanlearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizGeneratorTest {

    private fun word(id: String, ja: String, zh: String) = QuizWord(id, ja, ja, zh)

    private val pool = listOf(
        word("w1", "食べる", "吃"),
        word("w2", "飲む", "喝"),
        word("w3", "見る", "看"),
        word("w4", "買う", "买"),
        word("w5", "行く", "去"),
        word("w6", "来る", "来"),
    )

    @Test
    fun `日语到中文 生成四个选项且包含正确答案`() {
        val quiz = QuizGenerator.wordQuiz(pool[0], pool, WordQuizDirection.JP_TO_CN, Random(1))
        assertEquals(QuizGenerator.OPTION_COUNT, quiz.options.size)
        assertTrue(quiz.options.contains("吃"))
        assertEquals(quiz.options.indexOf("吃"), quiz.answerIndex)
        assertEquals(QuizKind.WORD_JP_TO_CN, quiz.kind)
    }

    @Test
    fun `选项互不重复`() {
        repeat(20) { seed ->
            val quiz = QuizGenerator.wordQuiz(pool[0], pool, WordQuizDirection.JP_TO_CN, Random(seed))
            assertEquals(quiz.options.size, quiz.options.toSet().size)
        }
    }

    @Test
    fun `中文到日语 正确答案带假名标注`() {
        val target = QuizWord("w1", "食べる", "たべる", "吃")
        val fullPool = listOf(target) + pool.drop(1)
        val quiz = QuizGenerator.wordQuiz(target, fullPool, WordQuizDirection.CN_TO_JP, Random(2))
        assertEquals(QuizKind.WORD_CN_TO_JP, quiz.kind)
        assertEquals("食べる（たべる）", quiz.answerText)
        assertTrue(quiz.options.contains(quiz.answerText))
    }

    @Test
    fun `干扰项不与正确答案含义重复`() {
        repeat(20) { seed ->
            val quiz = QuizGenerator.wordQuiz(pool[0], pool, WordQuizDirection.JP_TO_CN, Random(seed))
            val others = quiz.options.filterIndexed { i, _ -> i != quiz.answerIndex }
            assertTrue(others.none { it == "吃" })
        }
    }

    @Test
    fun `随机种子可复现选项顺序`() {
        val a = QuizGenerator.wordQuiz(pool[0], pool, WordQuizDirection.JP_TO_CN, Random(7))
        val b = QuizGenerator.wordQuiz(pool[0], pool, WordQuizDirection.JP_TO_CN, Random(7))
        assertEquals(a.options, b.options)
        val c = QuizGenerator.wordQuiz(pool[0], pool, WordQuizDirection.JP_TO_CN, Random(8))
        assertNotEquals(a.options, c.options)
    }

    @Test
    fun `五十音测验 生成四个罗马音选项`() {
        val kanaPool = listOf(
            QuizKana("k1", "あ", "ア", "a"),
            QuizKana("k2", "い", "イ", "i"),
            QuizKana("k3", "う", "ウ", "u"),
            QuizKana("k4", "え", "エ", "e"),
            QuizKana("k5", "お", "オ", "o"),
        )
        val quiz = QuizGenerator.kanaQuiz(kanaPool[0], kanaPool, Random(3))
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.options.contains("a"))
        assertEquals(quiz.options.indexOf("a"), quiz.answerIndex)
        assertTrue(quiz.question.contains("あ"))
    }

    @Test
    fun `语法填空 打乱选项后答案索引正确`() {
        val quiz = QuizGenerator.grammarQuiz(
            question = "私は学生＿＿。",
            options = listOf("です", "ます", "でした", "ません"),
            answerIndex = 0,
            random = Random(4),
        )
        assertEquals(4, quiz.options.size)
        assertEquals("です", quiz.answerText)
        assertEquals(quiz.options.indexOf("です"), quiz.answerIndex)
    }

    @Test
    fun `内容池过小也能生成不重复选项`() {
        val small = listOf(word("w1", "食べる", "吃"), word("w2", "飲む", "喝"))
        val quiz = QuizGenerator.wordQuiz(small[0], small, WordQuizDirection.JP_TO_CN, Random(5))
        assertEquals(2, quiz.options.size)
        assertEquals(quiz.options.toSet().size, quiz.options.size)
        assertTrue(quiz.options.contains("吃"))
    }

    @Test
    fun `听音选词变体 不暴露日文文本且携带音频字段`() {
        val quiz = QuizGenerator.wordQuiz(pool[0], pool, WordQuizDirection.JP_TO_CN, Random(9), audio = true)
        assertEquals(QuizKind.AUDIO_WORD_JP_TO_CN, quiz.kind)
        assertEquals("食べる", quiz.audioText)
        assertTrue(quiz.question.contains("听发音"))
        // 关键：题面和副题都不能出现日文原词，否则听音题失去意义
        assertTrue(!quiz.question.contains("食べる"))
        assertEquals(null, quiz.subQuestion)
        assertTrue(quiz.options.contains("吃"))
        assertEquals(quiz.options.indexOf("吃"), quiz.answerIndex)
        assertEquals(4, quiz.options.size)
        assertEquals(quiz.options.toSet().size, quiz.options.size)
    }

    @Test
    fun `听音标记不影响中文到日语方向`() {
        val quiz = QuizGenerator.wordQuiz(pool[0], pool, WordQuizDirection.CN_TO_JP, Random(10), audio = true)
        // CN_TO_JP 不做听音变体，仍为普通方向题
        assertEquals(QuizKind.WORD_CN_TO_JP, quiz.kind)
        assertEquals(null, quiz.audioText)
    }

    @Test
    fun `听音触发策略 仅日语到中文方向且命中概率时生效`() {
        assertTrue(AudioQuizPolicy.shouldUseAudio(WordQuizDirection.JP_TO_CN, roll = 0.29))
        assertFalse(AudioQuizPolicy.shouldUseAudio(WordQuizDirection.JP_TO_CN, roll = 0.31))
        assertFalse(AudioQuizPolicy.shouldUseAudio(WordQuizDirection.CN_TO_JP, roll = 0.0))
        assertTrue(AudioQuizPolicy.shouldUseAudio(WordQuizDirection.JP_TO_CN, roll = 0.05, chance = 0.5))
    }

    // ---------------- 汉字题型（v0.3） ----------------

    /** 汉字词池：ja 与 kana 不同形 */
    private val kanjiPool = listOf(
        QuizWord("w1", "食べる", "たべる", "吃"),
        QuizWord("w2", "飲む", "のむ", "喝"),
        QuizWord("w3", "見る", "みる", "看"),
        QuizWord("w4", "買う", "かう", "买"),
        QuizWord("w5", "行く", "いく", "去"),
        QuizWord("w6", "来る", "くる", "来"),
    )

    @Test
    fun `看假名选汉字 题干为假名 选项含正确汉字`() {
        repeat(20) { seed ->
            val quiz = QuizGenerator.kanjiQuiz(kanjiPool[0], kanjiPool, toKanji = true, random = Random(seed))
            assertEquals(QuizKind.KANA_TO_KANJI, quiz.kind)
            assertEquals(4, quiz.options.size)
            assertEquals(quiz.options.size, quiz.options.toSet().size)
            assertEquals("食べる", quiz.options[quiz.answerIndex])
            assertTrue(quiz.question.contains("たべる"))
            // 干扰项不与正确答案重复
            val others = quiz.options.filterIndexed { i, _ -> i != quiz.answerIndex }
            assertTrue(others.none { it == "食べる" })
        }
    }

    @Test
    fun `看汉字选读音 选项均为假名且含正确读音`() {
        repeat(20) { seed ->
            val quiz = QuizGenerator.kanjiQuiz(kanjiPool[0], kanjiPool, toKanji = false, random = Random(seed))
            assertEquals(QuizKind.KANJI_TO_KANA, quiz.kind)
            assertEquals(4, quiz.options.size)
            assertEquals(quiz.options.size, quiz.options.toSet().size)
            assertEquals("たべる", quiz.options[quiz.answerIndex])
            assertTrue(quiz.question.contains("食べる"))
            val kanaOnly = setOf("たべる", "のむ", "みる", "かう", "いく", "くる")
            assertTrue(quiz.options.all { it in kanaOnly })
        }
    }

    @Test
    fun `汉字题池不足时退化为更少选项但答案仍在`() {
        val small = kanjiPool.take(2)
        val quiz = QuizGenerator.kanjiQuiz(small[0], small, toKanji = true, random = Random(3))
        assertTrue(quiz.options.size in 2..4)
        assertEquals("食べる", quiz.options[quiz.answerIndex])
    }

    @Test
    fun `看假名选汉字 干扰项均为汉字形词汇`() {
        val mixed = kanjiPool + listOf(QuizWord("w7", "はさみ", "はさみ", "剪刀"))
        repeat(20) { seed ->
            val quiz = QuizGenerator.kanjiQuiz(kanjiPool[0], mixed, toKanji = true, random = Random(seed))
            assertTrue(quiz.options.none { it == "はさみ" })
        }
    }

    @Test
    fun `看假名选汉字 同音异形词不作干扰项`() {
        val target = QuizWord("hashi1", "箸", "はし", "筷子")
        val pool = listOf(
            target,
            QuizWord("hashi2", "橋", "はし", "桥"),
            QuizWord("w2", "飲む", "のむ", "喝"),
            QuizWord("w3", "見る", "みる", "看"),
            QuizWord("w4", "買う", "かう", "买"),
        )
        repeat(20) { seed ->
            val quiz = QuizGenerator.kanjiQuiz(target, pool, toKanji = true, random = Random(seed))
            assertTrue(quiz.options.none { it == "橋" })
        }
    }

    @Test
    fun `汉字触发策略 无汉字形不触发`() {
        assertFalse(KanjiQuizPolicy.hasKanjiForm("うれしい", "うれしい"))
        assertFalse(KanjiQuizPolicy.shouldUseKanji("うれしい", "うれしい", 0.0))
        // kana 与 ja 相同（写法一致）不触发
        assertFalse(KanjiQuizPolicy.hasKanjiForm("キリン", "キリン"))
    }

    @Test
    fun `汉字触发策略 有汉字形时按概率触发`() {
        assertTrue(KanjiQuizPolicy.hasKanjiForm("食べる", "たべる"))
        assertFalse(KanjiQuizPolicy.shouldUseKanji("食べる", "たべる", 0.25))
        assertTrue(KanjiQuizPolicy.shouldUseKanji("食べる", "たべる", 0.24))
        assertTrue(KanjiQuizPolicy.shouldUseKanji("食べる", "たべる", 0.9, chance = 0.95))
        assertTrue(KanjiQuizPolicy.shouldUseKanji(hasKanji = true, roll = 0.0))
        assertFalse(KanjiQuizPolicy.shouldUseKanji(hasKanji = false, roll = 0.0))
    }

    @Test
    fun `同词性干扰项优先耗尽`() {
        val target = QuizWord("t", "食べる", "たべる", "吃", pos = "动词", cat = "动作")
        val mixed = listOf(
            target,
            QuizWord("a", "飲む", "のむ", "喝", pos = "动词", cat = "动作"),
            QuizWord("b", "見る", "みる", "看", pos = "动词", cat = "动作"),
            QuizWord("c", "買う", "かう", "买", pos = "动词", cat = "动作"),
            QuizWord("d", "猫", "ねこ", "猫", pos = "名词", cat = "人物"),
            QuizWord("e", "犬", "いぬ", "狗", pos = "名词", cat = "人物"),
        )
        val verbs = setOf("喝", "看", "买")
        repeat(20) { seed ->
            val picked = DistractorSelector.pick(
                target, mixed, { it.zh }, 3, Random(seed), excludeSameKana = false,
            )
            assertTrue(picked.take(2).all { it in verbs })
        }
    }

    @Test
    fun `变体选择 hasKanji 且 kanjiRoll 为零则汉字题`() {
        assertEquals(
            WordQuizVariant.KANJI,
            QuizVariantPicker.pick(WordQuizDirection.JP_TO_CN, hasKanji = true, 0.0, 0.0, 0.0),
        )
        assertNotEquals(
            WordQuizVariant.KANJI,
            QuizVariantPicker.pick(WordQuizDirection.JP_TO_CN, hasKanji = false, 0.0, 0.99, 0.99),
        )
        assertNotEquals(
            WordQuizVariant.AUDIO,
            QuizVariantPicker.pick(WordQuizDirection.CN_TO_JP, hasKanji = false, 0.9, 0.0, 0.9),
        )
        assertEquals(
            WordQuizVariant.TYPE_KANA,
            QuizVariantPicker.pick(WordQuizDirection.CN_TO_JP, hasKanji = false, 0.9, 0.0, 0.0),
        )
    }

    @Test
    fun `打假名题 不暴露假名答案 且接受假名与罗马音`() {
        val target = QuizWord("w1", "食べる", "たべる", "吃", romaji = "taberu")
        val quiz = QuizGenerator.typeKanaQuiz(target)
        assertEquals(QuizKind.WORD_TYPE_KANA, quiz.kind)
        assertTrue(quiz.isTypeAnswer)
        assertEquals(-1, quiz.answerIndex)
        assertTrue(quiz.options.isEmpty())
        assertTrue(quiz.acceptedAnswers.contains("たべる"))
        assertTrue(quiz.acceptedAnswers.contains("taberu"))
        assertFalse(quiz.question.contains("たべる"))
        assertEquals("食べる", quiz.subQuestion)
        assertEquals("たべる", quiz.answerText)
    }
}
