package com.japanlearn.app

import com.japanlearn.app.util.SpeakingTracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TTS 发音状态跟踪：onStart → true，onDone/onError/onStop → false，
 * 引擎重建/关闭 reset 归零。供 TtsButton 的呼吸脉冲与主色提示消费。
 */
class SpeakingTrackerTest {

    @Test
    fun `初始为未发音`() = runTest {
        assertFalse(SpeakingTracker().speaking.first())
    }

    @Test
    fun `onStart 置真 onSettled 归假`() = runTest {
        val tracker = SpeakingTracker()
        tracker.onStart()
        assertTrue(tracker.speaking.first())
        tracker.onSettled()
        assertFalse(tracker.speaking.first())
    }

    @Test
    fun `连续两条语句 保持真直到落定`() = runTest {
        val tracker = SpeakingTracker()
        tracker.onStart()
        tracker.onStart()
        assertTrue(tracker.speaking.first())
        tracker.onSettled()
        assertFalse(tracker.speaking.first())
    }

    @Test
    fun `reset 直接归零`() = runTest {
        val tracker = SpeakingTracker()
        tracker.onStart()
        tracker.reset()
        assertFalse(tracker.speaking.first())
    }
}
