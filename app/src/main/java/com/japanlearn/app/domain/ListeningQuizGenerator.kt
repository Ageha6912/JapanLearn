package com.japanlearn.app.domain

import kotlin.random.Random

/** 听力题型（PRD §19.7）：音→义 / 音→形 / 句→义。 */
enum class ListeningKind { WORD_AUDIO, WORD_DICTATION, SENTENCE_AUDIO }

data class ListeningSentence(
    val id: String,
    val ja: String,
    val zh: String,
    val scene: String = "",
)

/** 一道听力题；word 类题答错同步错题本，句子题只计对错。 */
data class ListeningQuestion(
    val kind: ListeningKind,
    val quiz: Quiz,
    val contentType: String,
    val contentId: String,
)

/**
 * 听力配比策略（PRD §19.7）：听音辨词 40% / 听写假名 30% / 听句选义 30%。
 * 听写仅当已学词 ≥ [DICTATION_MIN_LEARNED] 个才出现，否则该区间回退听音辨词；
 * 句库为空时听句区间也回退听音辨词。
 */
object ListeningMixPolicy {
    const val AUDIO_WORD_RATIO = 0.4
    const val DICTATION_RATIO = 0.3
    const val DICTATION_MIN_LEARNED = 5

    fun pickKind(
        roll: Double,
        learnedCount: Int,
        sentencesAvailable: Boolean = true,
    ): ListeningKind = when {
        roll < AUDIO_WORD_RATIO -> ListeningKind.WORD_AUDIO
        roll < AUDIO_WORD_RATIO + DICTATION_RATIO ->
            if (learnedCount >= DICTATION_MIN_LEARNED) ListeningKind.WORD_DICTATION else ListeningKind.WORD_AUDIO
        sentencesAvailable -> ListeningKind.SENTENCE_AUDIO
        else -> ListeningKind.WORD_AUDIO
    }
}

/**
 * 听力会话出题（纯函数）：
 * - 听音辨词：该级别全池，已学词优先排前
 * - 听写假名：仅已学词，不显示词形与释义
 * - 听句选义：句子全库轮抽，干扰项同场景优先
 */
object ListeningQuizGenerator {

    /** 生成一轮听力题；词池为空或 count 非正时返回空列表。 */
    fun buildSession(
        wordPool: List<QuizWord>,
        learnedIds: Set<String>,
        sentences: List<ListeningSentence>,
        count: Int,
        random: Random = Random.Default,
    ): List<ListeningQuestion> {
        if (wordPool.isEmpty() || count <= 0) return emptyList()
        val learned = wordPool.filter { it.id in learnedIds }.shuffled(random)
        val unlearned = wordPool.filter { it.id !in learnedIds }.shuffled(random)
        val audioOrder = learned + unlearned
        val sentenceOrder = sentences.shuffled(random)
        var audioIdx = 0
        var sentenceIdx = 0
        return List(count) {
            when (ListeningMixPolicy.pickKind(random.nextDouble(), learned.size, sentenceOrder.isNotEmpty())) {
                ListeningKind.WORD_AUDIO -> {
                    val target = audioOrder[audioIdx % audioOrder.size]
                    audioIdx++
                    ListeningQuestion(
                        ListeningKind.WORD_AUDIO,
                        wordAudioQuiz(target, wordPool, random),
                        contentType = "word",
                        contentId = target.id,
                    )
                }
                ListeningKind.WORD_DICTATION -> {
                    val target = learned[it % learned.size]
                    ListeningQuestion(
                        ListeningKind.WORD_DICTATION,
                        dictationQuiz(target),
                        contentType = "word",
                        contentId = target.id,
                    )
                }
                ListeningKind.SENTENCE_AUDIO -> {
                    val target = sentenceOrder[sentenceIdx % sentenceOrder.size]
                    sentenceIdx++
                    ListeningQuestion(
                        ListeningKind.SENTENCE_AUDIO,
                        sentenceQuiz(target, sentences, random),
                        contentType = "sentence",
                        contentId = target.id,
                    )
                }
            }
        }
    }

    /** 听音辨词：播单词 → 四选一中文释义。 */
    fun wordAudioQuiz(target: QuizWord, pool: List<QuizWord>, random: Random = Random.Default): Quiz =
        QuizGenerator.wordQuiz(target, pool, WordQuizDirection.JP_TO_CN, random, audio = true)

    /** 听写假名：播单词 → 键入假名/罗马音；接受词库 romaji 原文。 */
    fun dictationQuiz(target: QuizWord): Quiz {
        val accepted = listOf(target.kana, target.romaji.lowercase())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return Quiz(
            kind = QuizKind.WORD_TYPE_KANA,
            question = "听发音，写出这个词的假名",
            subQuestion = null,
            options = emptyList(),
            answerIndex = -1,
            audioText = target.ja,
            acceptedAnswers = accepted,
            inputPrompt = "用假名或罗马音作答",
        )
    }

    /** 听句选义：播整句 → 三选一中文意思；干扰项同场景优先，取满 2 个。 */
    fun sentenceQuiz(
        target: ListeningSentence,
        pool: List<ListeningSentence>,
        random: Random = Random.Default,
    ): Quiz {
        val answerText = target.zh
        val sameScene = pool.asSequence()
            .filter { it.id != target.id && it.zh != answerText && it.scene == target.scene }
            .map { it.zh }
            .distinct()
            .shuffled(random)
        val others = pool.asSequence()
            .filter { it.id != target.id && it.zh != answerText && it.scene != target.scene }
            .map { it.zh }
            .distinct()
            .shuffled(random)
        val distractors = (sameScene + others).take(2).toList()
        val options = (distractors + answerText).shuffled(random)
        return Quiz(
            kind = QuizKind.AUDIO_SENTENCE_TO_ZH,
            question = "听句子，选出正确的意思",
            subQuestion = null,
            options = options,
            answerIndex = options.indexOf(answerText),
            audioText = target.ja,
        )
    }
}
