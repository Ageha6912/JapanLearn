package com.japanlearn.app.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp

/** 弹出卡片的触发角：卡片从哪个位置生长（关闭时原路缩回同一点）。 */
enum class PopupAnchor {
    /** 右上角触发（如设置按钮）。 */
    TopEnd,

    /** 底部居中触发（如今日一句横条）。 */
    BottomCenter,
}

private fun PopupAnchor.transformOrigin() = when (this) {
    PopupAnchor.TopEnd -> TransformOrigin(1f, 0f)
    PopupAnchor.BottomCenter -> TransformOrigin(0.5f, 1f)
}

private fun PopupAnchor.alignment() = when (this) {
    PopupAnchor.TopEnd -> Alignment.TopEnd
    PopupAnchor.BottomCenter -> Alignment.BottomCenter
}

/**
 * 容器变换弹出卡片：打开时从触发角一边放大一边到位，spring 带一次轻微过冲；
 * 关闭时原路缩回触发点。背景仅加遮罩变暗，尺寸保持不变。
 * 系统开启「减弱动画」时退化为快速淡入淡出。
 */
@Composable
fun TransformCardPopup(
    visible: Boolean,
    anchor: PopupAnchor,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduce = rememberReducedMotion()
    val origin = anchor.transformOrigin()

    // 半透明遮罩：点击空白处关闭
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )
    }

    // 卡片：贴着触发角对齐，从 0 放大到 1
    // 弹出节奏与视频一致：约 600ms 从容到位，带一次轻微过冲（冲过头一点点再收回）
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 52.dp),
        contentAlignment = anchor.alignment(),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                animationSpec = if (reduce) {
                    tween(150)
                } else {
                    spring(dampingRatio = 0.72f, stiffness = 260f)
                },
                initialScale = 0f,
                transformOrigin = origin,
            ) + fadeIn(tween(220)),
            exit = scaleOut(
                animationSpec = if (reduce) tween(150) else tween(240, easing = MotionTokens.Emphasized),
                targetScale = 0f,
                transformOrigin = origin,
            ) + fadeOut(tween(180)),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 16.dp,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}
