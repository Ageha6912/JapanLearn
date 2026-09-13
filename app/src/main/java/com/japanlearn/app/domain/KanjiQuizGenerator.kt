package com.japanlearn.app.domain

import kotlin.random.Random

/** 汉字练习用的纯视图（与 Room 解耦，PRD §19.14）。 */
data class QuizKanji(
    val id: String,
    val char: String,
    val zh: String,
    val on: List<String> = emptyList(),
    val kun: List<String> = emptyList(),
) {
    /** 音训合并展示：音：…／訓：…；只有一类时不写前缀分隔。 */
    fun readingDisplay(): String {
        val parts = buildList {
            if (on.isNotEmpty()) add("音：${on.joinToString("・")}")
            if (kun.isNotEmpty()) add("訓：${kun.joinToString("・")}")
        }
        return parts.joinToString("／").ifEmpty { "—" }
    }

    /** TTS/主读音：优先训读，其次音读。 */
    fun primaryReading(): String = kun.firstOrNull() ?: on.firstOrNull() ?: ""
}

/** 汉字专项题型（PRD §19.14）：五种选择题轮换。 */
enum class KanjiQuizVariant { TO_ZH, TO_CHAR, TO_READING, FROM_READING, AUDIO }

object KanjiQuizVariantPicker {
    fun pick(
        roll: Double,
        canAudio: Boolean = true,
    ): KanjiQuizVariant {
        // 均匀切分 [0,1)；无读音可播时听音题回退看字选义
        val variant = when {
            roll < 0.2 -> KanjiQuizVariant.TO_ZH
            roll < 0.4 -> KanjiQuizVariant.TO_CHAR
            roll < 0.6 -> KanjiQuizVariant.TO_READING
            roll < 0.8 -> KanjiQuizVariant.FROM_READING
            else -> if (canAudio) KanjiQuizVariant.AUDIO else KanjiQuizVariant.TO_ZH
        }
        return variant
    }
}

/**
 * 汉字专项出题（PRD §19.14）：
 * - 看字选义 / 看义选字 / 看字选读音 / 看读音选字 / 听音选字
 * - 干扰项：字形题排除同字；读音题排除与正确读音相同的字（避免同读音歧义）
 * - 听音题 audioText 优先训读，其次音读；读音为空时由调用方避开 AUDIO
 */
object KanjiQuizGenerator {

    const val OPTION_COUNT = 4

    fun build(
        target: QuizKanji,
        pool: List<QuizKanji>,
        variant: KanjiQuizVariant,
        random: Random = Random.Default,
    ): Quiz = when (variant) {
        KanjiQuizVariant.TO_ZH -> toZh(target, pool, random)
        KanjiQuizVariant.TO_CHAR -> toChar(target, pool, random)
        KanjiQuizVariant.TO_READING -> toReading(target, pool, random)
        KanjiQuizVariant.FROM_READING -> fromReading(target, pool, random)
        KanjiQuizVariant.AUDIO -> audio(target, pool, random)
    }

    fun toZh(target: QuizKanji, pool: List<QuizKanji>, random: Random = Random.Default): Quiz {
        val answer = target.zh
        val distractors = pickDistractors(target, pool, { it.zh }, random) { it.id != target.id }
        val options = (distractors + answer).shuffled(random)
        return Quiz(
            kind = QuizKind.KANJI_TO_ZH,
            question = "「${target.char}」是什么意思？",
            subQuestion = target.readingDisplay(),
            options = options,
            answerIndex = options.indexOf(answer),
        )
    }

    fun toChar(target: QuizKanji, pool: List<QuizKanji>, random: Random = Random.Default): Quiz {
        val answer = target.char
        val distractors = pickDistractors(target, pool, { it.char }, random) { it.id != target.id }
        val options = (distractors + answer).shuffled(random)
        return Quiz(
            kind = QuizKind.ZH_TO_KANJI,
            question = "“${target.zh}”对应哪个汉字？",
            subQuestion = target.readingDisplay(),
            options = options,
            answerIndex = options.indexOf(answer),
        )
    }

    fun toReading(target: QuizKanji, pool: List<QuizKanji>, random: Random = Random.Default): Quiz {
        val answer = target.readingDisplay()
        // 干扰项用其它字的读音展示串，并排除与目标读音集合重叠的字
        val targetReadings = (target.on + target.kun).toSet()
        val distractors = pickDistractors(target, pool, { it.readingDisplay() }, random) {
            it.id != target.id && (it.on + it.kun).none { r -> r in targetReadings }
        }
        val options = (distractors + answer).shuffled(random)
        return Quiz(
            kind = QuizKind.KANJI_TO_READING,
            question = "「${target.char}」的读音是？",
            subQuestion = "意思：${target.zh}",
            options = options,
            answerIndex = options.indexOf(answer),
        )
    }

    fun fromReading(target: QuizKanji, pool: List<QuizKanji>, random: Random = Random.Default): Quiz {
        val answer = target.char
        val targetReadings = (target.on + target.kun).toSet()
        val distractors = pickDistractors(target, pool, { it.char }, random) {
            it.id != target.id && (it.on + it.kun).none { r -> r in targetReadings }
        }
        val options = (distractors + answer).shuffled(random)
        return Quiz(
            kind = QuizKind.READING_TO_KANJI,
            question = "读音「${target.readingDisplay()}」对应哪个汉字？",
            subQuestion = "意思：${target.zh}",
            options = options,
            answerIndex = options.indexOf(answer),
        )
    }

    fun audio(target: QuizKanji, pool: List<QuizKanji>, random: Random = Random.Default): Quiz {
        val speak = target.primaryReading()
        require(speak.isNotEmpty()) { "audio 题需要至少一个读音" }
        val answer = target.char
        val targetReadings = (target.on + target.kun).toSet()
        val distractors = pickDistractors(target, pool, { it.char }, random) {
            it.id != target.id && (it.on + it.kun).none { r -> r in targetReadings }
        }
        val options = (distractors + answer).shuffled(random)
        return Quiz(
            kind = QuizKind.AUDIO_KANJI,
            question = "听读音，选出对应的汉字",
            subQuestion = "意思：${target.zh}",
            options = options,
            answerIndex = options.indexOf(answer),
            audioText = speak,
        )
    }

    private fun pickDistractors(
        target: QuizKanji,
        pool: List<QuizKanji>,
        textOf: (QuizKanji) -> String,
        random: Random,
        eligible: (QuizKanji) -> Boolean,
    ): List<String> {
        val answer = textOf(target)
        return pool.asSequence()
            .filter(eligible)
            .map(textOf)
            .filter { it.isNotBlank() && it != answer }
            .distinct()
            .shuffled(random)
            .take(OPTION_COUNT - 1)
            .toList()
    }
}
