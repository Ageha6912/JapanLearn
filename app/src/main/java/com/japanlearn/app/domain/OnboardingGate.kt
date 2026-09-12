package com.japanlearn.app.domain

/**
 * 首启引导门槛（PRD §19.6）：只打扰「没看过引导且还没有任何学习进度」的用户。
 * 已有进度的老用户与跳过/完成过的用户永不打扰。
 */
object OnboardingGate {
    fun shouldShow(onboardingDone: Boolean, learnedWords: Int, learnedGrammar: Int): Boolean =
        !onboardingDone && learnedWords == 0 && learnedGrammar == 0
}
