package com.japanlearn.app.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 统一动效语汇：强调曲线 + 弹簧物理。全 App 只从这里的令牌取值。 */
object MotionTokens {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 轻盈回弹：入场、选中态 */
    fun springBouncy(): androidx.compose.animation.core.SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    /** 干脆利落：按压、反馈 */
    fun springSnappy(): androidx.compose.animation.core.SpringSpec<Float> =
        spring(dampingRatio = 0.85f, stiffness = 550f)

    const val ENTER_DURATION = 480
    const val STAGGER_STEP_MS = 55L
    const val MAX_STAGGER_STEPS = 8
}

/** 跟随系统的「减弱动态效果」无障碍设置（动画时长缩放为 0）。 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * 级联入场：内容上浮 + 淡入，按 index 依次错峰。
 * 用于区块/列表项的首次出现，营造内容逐层就位的节奏感。
 */
@Composable
fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduce = rememberReducedMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (progress.value < 1f) {
            kotlinx.coroutines.delay(
                com.japanlearn.app.domain.UiMath.staggerDelayMs(
                    index,
                    MotionTokens.STAGGER_STEP_MS,
                    MotionTokens.MAX_STAGGER_STEPS,
                ),
            )
            progress.animateTo(1f, tween(MotionTokens.ENTER_DURATION, easing = MotionTokens.EmphasizedDecelerate))
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * 24.dp.toPx()
        },
    ) { content() }
}

/** 按压缩放：物理按压感。与 clickable(shared interactionSource) 搭配使用。 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed = interactionSource.collectIsPressedAsState()
    val reduce = rememberReducedMotion()
    val scale = animateFloatAsState(
        targetValue = if (pressed.value && !reduce) pressedScale else 1f,
        animationSpec = MotionTokens.springSnappy(),
        label = "pressScale",
    )
    return graphicsLayer {
        val s = scale.value
        scaleX = s
        scaleY = s
    }
}

/** 数字滚动：数值变化时平滑计数到目标值。 */
@Composable
fun AnimatedCounterText(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = Color.Unspecified,
    suffix: String = "",
) {
    val reduce = rememberReducedMotion()
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = if (reduce) snap() else tween(700, easing = MotionTokens.EmphasizedDecelerate),
        label = "counter",
    )
    Text(text = "$animated$suffix", modifier = modifier, style = style, color = color)
}

/** 进度条：绘制层动画（不触发布局），圆角端帽。 */
@Composable
fun AnimatedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    fillColor: Color = MaterialTheme.colorScheme.primary,
) {
    val reduce = rememberReducedMotion()
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reduce) snap() else tween(800, easing = MotionTokens.EmphasizedDecelerate),
        label = "progressBar",
    )
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(this.size.height / 2)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val fillWidth = this.size.width * animated
        if (fillWidth > this.size.height / 2) {
            drawRoundRect(
                color = fillColor,
                size = Size(fillWidth, this.size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** 进度环：圆环百分比，中心可放内容。 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit = {},
) {
    val reduce = rememberReducedMotion()
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reduce) snap() else tween(900, easing = MotionTokens.EmphasizedDecelerate),
        label = "progressRing",
    )
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            if (animated > 0.005f) {
                drawArc(
                    color = fillColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** 水平摇晃：答错时的物理反馈。trigger 变化时播放一次。 */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    val reduce = rememberReducedMotion()
    val x = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && !reduce) {
            listOf(-12f, 12f, -8f, 8f, -4f, 0f).forEach { v ->
                x.animateTo(v, spring(stiffness = 1300f, dampingRatio = 0.75f))
            }
        }
    }
    return graphicsLayer { translationX = x.value }
}

/** 微光扫过骨架屏。减弱动态时退化为静态占位色。 */
@Composable
fun shimmerBrush(shape: Shape = MaterialTheme.shapes.medium): Brush {
    val reduce = rememberReducedMotion()
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    if (reduce) return Brush.horizontalGradient(listOf(base, base))
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerOffset",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(offset * 900f, 0f),
        end = Offset((offset + 1f) * 900f, 220f),
    )
}

private data class ConfettiParticle(
    val angleRad: Float,
    val speed: Float,
    val color: Color,
    val sizePx: Float,
    val spin: Float,
    val isRect: Boolean,
)

/**
 * 完成时刻的彩带迸发。trigger 递增时从顶部中央迸发一次，
 * 全部走 transform/alpha 绘制层；减弱动态时不显示。
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val reduce = rememberReducedMotion()
    if (reduce || trigger <= 0) return
    val palette = com.japanlearn.app.ui.theme.LocalJapanColors.current.confetti
    val particles = remember(trigger) {
        val rnd = Random(trigger)
        List(42) {
            ConfettiParticle(
                angleRad = (-90f + rnd.nextFloat() * 100f - 50f) * (Math.PI.toFloat() / 180f),
                speed = 0.55f + rnd.nextFloat() * 0.6f,
                color = palette[rnd.nextInt(palette.size)],
                sizePx = 8f + rnd.nextFloat() * 8f,
                spin = (rnd.nextFloat() - 0.5f) * 720f,
                isRect = rnd.nextBoolean(),
            )
        }
    }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1600, easing = LinearEasing))
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val originX = size.width / 2f
        val originY = size.height * 0.18f
        particles.forEach { p ->
            val vx = cos(p.angleRad) * p.speed * size.width
            val vy = sin(p.angleRad) * p.speed * size.height
            val px = originX + vx * t
            val py = originY + vy * t + 1.1f * t * t * size.height
            val alpha = (1f - ((t - 0.55f) / 0.45f).coerceIn(0f, 1f))
            rotate(degrees = p.spin * t, pivot = Offset(px, py)) {
                if (p.isRect) {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(px - p.sizePx / 2, py - p.sizePx / 4),
                        size = Size(p.sizePx, p.sizePx / 2),
                    )
                } else {
                    drawCircle(color = p.color.copy(alpha = alpha), radius = p.sizePx / 2, center = Offset(px, py))
                }
            }
        }
    }
}
