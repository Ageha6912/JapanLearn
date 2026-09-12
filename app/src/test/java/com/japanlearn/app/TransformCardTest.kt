package com.japanlearn.app.domain

import com.japanlearn.app.ui.motion.PopupAnchor
import com.japanlearn.app.ui.motion.alignment
import com.japanlearn.app.ui.motion.transformOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 弹出卡片锚点映射：变换原点决定卡片「从哪个角生长」，对齐方式决定卡片贴哪条边。
 * BottomEnd 为 AI 助手悬浮按钮（首页右下角）新增。
 */
class TransformCardTest {

    @Test
    fun `右上角锚点从右上生长并贴右上对齐`() {
        assertEquals(1f, PopupAnchor.TopEnd.transformOrigin().pivotFractionX, 0f)
        assertEquals(0f, PopupAnchor.TopEnd.transformOrigin().pivotFractionY, 0f)
        assertEquals(
            androidx.compose.ui.Alignment.TopEnd,
            PopupAnchor.TopEnd.alignment(),
        )
    }

    @Test
    fun `底部居中锚点从底部中央生长并贴底对齐`() {
        assertEquals(0.5f, PopupAnchor.BottomCenter.transformOrigin().pivotFractionX, 0f)
        assertEquals(1f, PopupAnchor.BottomCenter.transformOrigin().pivotFractionY, 0f)
        assertEquals(
            androidx.compose.ui.Alignment.BottomCenter,
            PopupAnchor.BottomCenter.alignment(),
        )
    }

    @Test
    fun `右下角锚点从右下生长并贴右下对齐`() {
        assertEquals(1f, PopupAnchor.BottomEnd.transformOrigin().pivotFractionX, 0f)
        assertEquals(1f, PopupAnchor.BottomEnd.transformOrigin().pivotFractionY, 0f)
        assertEquals(
            androidx.compose.ui.Alignment.BottomEnd,
            PopupAnchor.BottomEnd.alignment(),
        )
    }
}
