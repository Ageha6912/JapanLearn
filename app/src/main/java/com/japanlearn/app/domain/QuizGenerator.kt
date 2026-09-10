package com.japanlearn.app.domain

import kotlin.random.Random

enum class QuizKind {
    WORD_JP_TO_CN, AUDIO_WORD_JP_TO_CN, WORD_CN_TO_JP,
    KANA_TO_ROMAJI, GRAMMAR_FILL, KANA_TO_KANJI, KANJI_TO_KANA,
    WORD_TYPE_KANA,
}

/**
 * 一道练习题。选择题用 [options] / [answerIndex]；打字题用 [acceptedAnswers]。
 */
data class Quiz(
    val kind: QuizKind,
    val question: String,
    val subQuestion: String?,
    val options: List<String>,
    val answerIndex: Int,
    /** 听音题需要朗读的文本（kind = AUDIO_WORD_JP_TO_CN 时非空）。 */
    val audioText: String? = null,
    val acceptedAnswers: List<String> = emptyList(),
    val inputPrompt: String? = null,
) {
    val isTypeAnswer: Boolean get() = kind == QuizKind.WORD_TYPE_KANA
    val answerText: String get() {
        check(!isTypeAnswer || acceptedAnswers.isNotEmpty()) { "WORD_TYPE_KANA requires acceptedAnswers" }
        return if (isTypeAnswer) acceptedAnswers.first() else options[answerIndex]
    }
}

data class QuizWord(
    val id: String,
    val ja: String,
    val kana: String,
    val zh: String,
    val pos: String = "",
    val cat: String = "",
    val romaji: String = "",
)
data class QuizKana(val id: String, val hiragana: String, val katakana: String, val romaji: String, val group: String = "seion")

/** 五十音分组（PRD v0.2：清音/浊音/拗音）。 */
enum class KanaGroup(val key: String, val label: String) {
    SEION("seion", "清音"),
    DAKUON("dakuon", "浊音"),
    YOUON("youon", "拗音");

    companion object {
        fun fromKey(key: String): KanaGroup = entries.first { it.key == key }
    }
}

enum class WordQuizDirection { JP_TO_CN, CN_TO_JP;

    companion object {
        fun random(random: Random): WordQuizDirection =
            if (random.nextBoolean()) JP_TO_CN else CN_TO_JP
    }
}

/** 听音题触发判定：日语→中文方向下以给定概率升级为听音变体。 */
object AudioQuizPolicy {
    const val DEFAULT_CHANCE = 0.3

    fun shouldUseAudio(direction: WordQuizDirection, roll: Double, chance: Double = DEFAULT_CHANCE): Boolean =
        direction == WordQuizDirection.JP_TO_CN && roll < chance
}

/** 汉字题触发判定（v0.3）：词带汉字写法（ja 与 kana 不同形）时以给定概率出「假名⇄汉字」变体。 */
object KanjiQuizPolicy {
    const val DEFAULT_CHANCE = 0.25

    fun hasKanjiForm(ja: String, kana: String): Boolean = ja != kana && ja.any { it in '\u4E00'..'\u9FFF' }

    fun shouldUseKanji(ja: String, kana: String, roll: Double, chance: Double = DEFAULT_CHANCE): Boolean =
        hasKanjiForm(ja, kana) && roll < chance

    fun shouldUseKanji(hasKanji: Boolean, roll: Double, chance: Double = DEFAULT_CHANCE): Boolean =
        hasKanji && roll < chance
}

/**
 * 选择题生成器：
 * - 固定 4 个选项，干扰项取自内容池，且不与正确答案重复
 * - 正确答案一定在选项中
 * - 选项顺序由注入的 [Random] 决定，测试可复现
 */
object QuizGenerator {

    const val OPTION_COUNT = 4

    fun wordQuiz(
        target: QuizWord,
        pool: List<QuizWord>,
        direction: WordQuizDirection,
        random: Random = Random.Default,
        audio: Boolean = false,
    ): Quiz {
        if (audio && direction == WordQuizDirection.JP_TO_CN) {
            val distractors = DistractorSelector.pick(
                target, pool, { it.zh }, OPTION_COUNT - 1, random,
                excludeSameKana = false,
            )
            val options = (distractors + target.zh).shuffled(random)
            return Quiz(
                kind = QuizKind.AUDIO_WORD_JP_TO_CN,
                question = "听发音，选出正确的意思",
                subQuestion = null,
                options = options,
                answerIndex = options.indexOf(target.zh),
                audioText = target.ja,
            )
        }
        val (question, subQuestion, answerText, distractorOf, excludeSameKana) = when (direction) {
            WordQuizDirection.JP_TO_CN -> QuizSpec(
                question = "「${target.ja}」是什么意思？",
                subQuestion = target.kana.takeIf { it != target.ja },
                answerText = target.zh,
                distractorOf = { it.zh },
                excludeSameKana = false,
            )
            WordQuizDirection.CN_TO_JP -> QuizSpec(
                question = "“${target.zh}”对应哪个词？",
                subQuestion = null,
                answerText = displayWord(target),
                distractorOf = { displayWord(it) },
                excludeSameKana = true,
            )
        }
        val distractors = DistractorSelector.pick(
            target, pool, distractorOf, OPTION_COUNT - 1, random, excludeSameKana,
        )
        val options = (distractors + answerText).shuffled(random)
        return Quiz(
            kind = if (direction == WordQuizDirection.JP_TO_CN) QuizKind.WORD_JP_TO_CN else QuizKind.WORD_CN_TO_JP,
            question = question,
            subQuestion = subQuestion,
            options = options,
            answerIndex = options.indexOf(answerText),
        )
    }

    fun typeKanaQuiz(target: QuizWord): Quiz {
        val accepted = listOf(target.kana, target.romaji.lowercase())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return Quiz(
            kind = QuizKind.WORD_TYPE_KANA,
            question = "“${target.zh}”的读音怎么写？",
            subQuestion = target.ja.takeIf { it != target.kana },
            options = emptyList(),
            answerIndex = -1,
            acceptedAnswers = accepted,
            inputPrompt = "用假名或罗马音作答",
        )
    }

    /**
     * 汉字题（v0.3）：
     * - toKanji = true：看假名选汉字写法，选项均为汉字词；干扰项不取与目标同读音的词（避免同音歧义）
     * - toKanji = false：看汉字选读音，选项均为假名
     * 目标词必须带汉字写法（hasKanjiForm），由调用方经 KanjiQuizPolicy 保证。
     */
    fun kanjiQuiz(target: QuizWord, pool: List<QuizWord>, toKanji: Boolean, random: Random = Random.Default): Quiz {
        require(target.ja != target.kana) { "kanjiQuiz 需要汉字形目标词" }
        return if (toKanji) {
            val answerText = target.ja
            val distractors = DistractorSelector.pick(
                target, pool, { it.ja }, OPTION_COUNT - 1, random,
                excludeSameKana = true,
                eligible = { KanjiQuizPolicy.hasKanjiForm(it.ja, it.kana) },
            )
            val options = (distractors + answerText).shuffled(random)
            Quiz(
                kind = QuizKind.KANA_TO_KANJI,
                question = "「${target.kana}」的汉字写法是？",
                subQuestion = "意思：${target.zh}",
                options = options,
                answerIndex = options.indexOf(answerText),
            )
        } else {
            val answerText = target.kana
            val distractors = DistractorSelector.pick(
                target, pool, { it.kana }, OPTION_COUNT - 1, random,
                excludeSameKana = true,
            )
            val options = (distractors + answerText).shuffled(random)
            Quiz(
                kind = QuizKind.KANJI_TO_KANA,
                question = "「${target.ja}」的读音是？",
                subQuestion = "意思：${target.zh}",
                options = options,
                answerIndex = options.indexOf(answerText),
            )
        }
    }

    fun kanaQuiz(target: QuizKana, pool: List<QuizKana>, random: Random = Random.Default): Quiz {
        val answerText = target.romaji
        val distractors = pool.asSequence()
            .filter { it.id != target.id }
            .map { it.romaji }
            .filter { it != answerText }
            .distinct()
            .shuffled(random)
            .take(OPTION_COUNT - 1)
            .toList()
        val options = (distractors + answerText).shuffled(random)
        return Quiz(
            kind = QuizKind.KANA_TO_ROMAJI,
            question = "「${target.hiragana}」怎么读？",
            subQuestion = "片假名：${target.katakana}",
            options = options,
            answerIndex = options.indexOf(answerText),
        )
    }

    /** 语法填空题直接来自内容数据，这里只负责打乱选项并定位答案。 */
    fun grammarQuiz(question: String, options: List<String>, answerIndex: Int, random: Random = Random.Default): Quiz {
        val answerText = options[answerIndex]
        val shuffled = options.shuffled(random)
        return Quiz(
            kind = QuizKind.GRAMMAR_FILL,
            question = question,
            subQuestion = null,
            options = shuffled,
            answerIndex = shuffled.indexOf(answerText),
        )
    }

    private fun displayWord(w: QuizWord): String =
        if (w.kana != w.ja) "${w.ja}（${w.kana}）" else w.ja

    private data class QuizSpec(
        val question: String,
        val subQuestion: String?,
        val answerText: String,
        val distractorOf: (QuizWord) -> String,
        val excludeSameKana: Boolean,
    )
}
