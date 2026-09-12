package com.japanlearn.app

import com.japanlearn.app.domain.ListeningKind
import com.japanlearn.app.domain.ListeningQuizGenerator
import com.japanlearn.app.domain.ListeningSentence
import com.japanlearn.app.domain.QuizWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningQuizGeneratorTest {

    private fun word(id: String, ja: String, kana: String, zh: String, romaji: String = "") =
        QuizWord(id = id, ja = ja, kana = kana, zh = zh, romaji = romaji)

    private val pool = listOf(
        word("w1", "私", "わたし", "我", "watashi"),
        word("w2", "食べる", "たべる", "吃", "taberu"),
        word("w3", "学校", "がっこう", "学校", "gakkou"),
        word("w4", "行く", "いく", "去", "iku"),
        word("w5", "水", "みず", "水", "mizu"),
        word("w6", "本", "ほん", "书", "hon"),
        word("w7", "猫", "ねこ", "猫", "neko"),
        word("w8", "先生", "せんせい", "老师", "sensei"),
    )

    private val sentences = listOf(
        ListeningSentence("s1", "すみません、これをください。", "不好意思，请给我这个。", "便利店"),
        ListeningSentence("s2", "一番近い駅はどこですか。", "最近的电车站在哪里？", "交通"),
        ListeningSentence("s3", "熱があるので、休みます。", "我发烧了，要休息。", "就医"),
        ListeningSentence("s4", "こちらの商品は少しお高いです。", "这个商品有点贵。", "购物"),
        ListeningSentence("s5", "駅前のコンビニでおにぎりを買いました。", "在车站前的便利店买了饭团。", "便利店"),
        ListeningSentence("s6", "この薬を一日三回飲んでください。", "这个药一天请服用三次。", "就医"),
    )

    // ---- ListeningMixPolicy ----

    @Test
    fun `前四成出听音辨词`() {
        assertEquals(ListeningKind.WORD_AUDIO, com.japanlearn.app.domain.ListeningMixPolicy.pickKind(0.39, 10))
        assertEquals(ListeningKind.WORD_AUDIO, com.japanlearn.app.domain.ListeningMixPolicy.pickKind(0.0, 0))
    }

    @Test
    fun `中间三成出听写`() {
        assertEquals(
            ListeningKind.WORD_DICTATION,
            com.japanlearn.app.domain.ListeningMixPolicy.pickKind(0.5, 10),
        )
    }

    @Test
    fun `已学词不足时听写回退为听音辨词`() {
        assertEquals(
            ListeningKind.WORD_AUDIO,
            com.japanlearn.app.domain.ListeningMixPolicy.pickKind(0.5, 4),
        )
    }

    @Test
    fun `后三成出听句选义`() {
        assertEquals(
            ListeningKind.SENTENCE_AUDIO,
            com.japanlearn.app.domain.ListeningMixPolicy.pickKind(0.8, 10),
        )
    }

    @Test
    fun `句库为空时听句回退为听音辨词`() {
        assertEquals(
            ListeningKind.WORD_AUDIO,
            com.japanlearn.app.domain.ListeningMixPolicy.pickKind(0.8, 10, sentencesAvailable = false),
        )
    }

    // ---- buildSession ----

    @Test
    fun `题量与请求一致且全部带音频`() {
        val session = ListeningQuizGenerator.buildSession(pool, setOf("w1", "w2"), sentences, count = 10)
        assertEquals(10, session.size)
        assertTrue(session.all { it.quiz.audioText != null })
    }

    @Test
    fun `词池为空时返回空会话`() {
        assertTrue(ListeningQuizGenerator.buildSession(emptyList(), emptySet(), sentences, count = 10).isEmpty())
        assertTrue(ListeningQuizGenerator.buildSession(pool, setOf("w1"), sentences, count = 0).isEmpty())
    }

    @Test
    fun `听写题只从已学词出题`() {
        val learned = setOf("w1", "w2", "w3", "w4", "w5", "w6", "w7", "w8")
        var found = false
        for (seed in 1..10) {
            val session = ListeningQuizGenerator.buildSession(pool, learned, sentences, 40, kotlin.random.Random(seed))
            val dictations = session.filter { it.kind == ListeningKind.WORD_DICTATION }
            if (dictations.isNotEmpty()) found = true
            assertTrue(dictations.all { it.contentId in learned })
        }
        assertTrue(found)
    }

    @Test
    fun `固定种子下三种题型都会出现`() {
        val learned = setOf("w1", "w2", "w3", "w4", "w5", "w6", "w7", "w8")
        val session = ListeningQuizGenerator.buildSession(pool, learned, sentences, 30, kotlin.random.Random(7))
        val kinds = session.map { it.kind }.toSet()
        assertTrue(kinds.contains(ListeningKind.WORD_AUDIO))
        assertTrue(kinds.contains(ListeningKind.WORD_DICTATION))
        assertTrue(kinds.contains(ListeningKind.SENTENCE_AUDIO))
    }

    // ---- 各题型内容 ----

    @Test
    fun `听写题接受假名与罗马音且不泄露词形`() {
        val quiz = ListeningQuizGenerator.dictationQuiz(pool.first())
        assertTrue(quiz.isTypeAnswer)
        assertEquals("私", quiz.audioText)
        assertEquals(null, quiz.subQuestion)
        assertTrue(quiz.acceptedAnswers.contains("わたし"))
        assertTrue(quiz.acceptedAnswers.contains("watashi"))
    }

    @Test
    fun `听音辨词四选一且含正确答案`() {
        val quiz = ListeningQuizGenerator.wordAudioQuiz(pool.first(), pool)
        assertEquals(4, quiz.options.size)
        assertTrue(quiz.options.contains("我"))
        assertEquals("私", quiz.audioText)
        assertEquals("我", quiz.answerText)
    }

    @Test
    fun `听句选义三选一且同场景干扰优先`() {
        // s1（便利店）有两个同场景候选 s5 与目标外句子；干扰项应优先取同场景
        val quiz = ListeningQuizGenerator.sentenceQuiz(sentences.first(), sentences, kotlin.random.Random(1))
        assertEquals(3, quiz.options.size)
        assertTrue(quiz.options.contains(sentences.first().zh))
        assertTrue(quiz.options.contains(sentences[4].zh))
        assertEquals("すみません、これをください。", quiz.audioText)
    }

    @Test
    fun `句库过小时选项不越界`() {
        val tiny = listOf(
            ListeningSentence("s1", "こんにちは。", "你好。", "日常"),
            ListeningSentence("s2", "さようなら。", "再见。", "日常"),
        )
        val quiz = ListeningQuizGenerator.sentenceQuiz(tiny.first(), tiny)
        assertEquals(2, quiz.options.size)
        assertTrue(quiz.options.contains("你好。"))
    }
}
