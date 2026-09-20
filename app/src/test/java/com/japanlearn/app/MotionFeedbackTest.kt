package com.japanlearn.app

import com.japanlearn.app.ui.motion.MotionTokens
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 判题时刻新增动效的预算契约（Emil Kowalski「You Don't Need Animations」约束）：
 * 反馈块与判定图标是全 App 最高频的状态变化，参数必须保持快、轻、不过冲。
 */
class MotionFeedbackTest {

    @Test
    fun `反馈块入场不超 UI 标准 300ms 预算`() {
        assertTrue(MotionTokens.FEEDBACK_REVEAL_DURATION_MS <= 300)
    }

    @Test
    fun `判定图标入场不从零缩放且淡入不超时`() {
        assertTrue(MotionTokens.VERDICT_ICON_SCALE_FROM in 0.5f..0.95f)
        assertTrue(MotionTokens.VERDICT_ICON_FADE_MS <= 200)
    }

    @Test
    fun `TTS 呼吸脉冲幅度近乎不可察`() {
        assertTrue(MotionTokens.TTS_PULSE_MAX_SCALE <= 1.1f)
        assertTrue(MotionTokens.TTS_PULSE_HALF_CYCLE_MS in 300..1200L)
    }
}
