package com.japanlearn.app.domain

enum class WordQuizVariant { KANJI, AUDIO, TYPE_KANA, MCQ }

object TypeAnswerPolicy {
    const val DEFAULT_CHANCE = 0.20

    fun shouldUse(
        direction: WordQuizDirection,
        roll: Double,
        chance: Double = DEFAULT_CHANCE,
    ): Boolean = direction == WordQuizDirection.CN_TO_JP && roll < chance
}

object QuizVariantPicker {
    fun pick(
        direction: WordQuizDirection,
        hasKanji: Boolean,
        kanjiRoll: Double,
        audioRoll: Double,
        typeRoll: Double,
    ): WordQuizVariant = when {
        KanjiQuizPolicy.shouldUseKanji(hasKanji, kanjiRoll) -> WordQuizVariant.KANJI
        AudioQuizPolicy.shouldUseAudio(direction, audioRoll) -> WordQuizVariant.AUDIO
        TypeAnswerPolicy.shouldUse(direction, typeRoll) -> WordQuizVariant.TYPE_KANA
        else -> WordQuizVariant.MCQ
    }
}

object TypeAnswerScoring {
    data class Score(val total: Int, val correct: Int)

    fun afterSubmit(ok: Boolean, total: Int, correct: Int): Score =
        Score(total + 1, correct + if (ok) 1 else 0)
}
