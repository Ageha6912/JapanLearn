package com.japanlearn.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.japanlearn.app.domain.Mastery
import com.japanlearn.app.domain.Quiz
import com.japanlearn.app.ui.motion.MotionTokens
import com.japanlearn.app.ui.motion.pressScale
import com.japanlearn.app.ui.motion.rememberReducedMotion
import com.japanlearn.app.ui.motion.shake
import com.japanlearn.app.ui.motion.shimmerBrush
import com.japanlearn.app.ui.theme.japanColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 学习会话的阶段，单词/语法/复习会话共用。 */
enum class SessionPhase { LOADING, CARD, QUIZ, DONE }

/** 学习级别切换（N5 / N4），选项与设置页共享同一常量。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSwitchRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.japanlearn.app.data.SettingsRepository.STUDY_LEVELS.forEach { level ->
            FilterChip(selected = selected == level, onClick = { onSelect(level) }, label = { Text(level) })
        }
    }
}

@Composable
fun AppButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interaction, pressedScale = 0.98f),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(),
        // 收紧水平内边距，避免并排按钮里中文长文案被挤到换行
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 16.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/** 内容面板：和纸面 + 1px 发丝边，点击时带按压缩放。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressModifier = if (onClick != null) {
        Modifier.pressScale(interaction)
    } else {
        Modifier
    }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        interactionSource = if (onClick != null) interaction else remember { MutableInteractionSource() },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .then(pressModifier),
    ) {
        Column(
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    numericValue: Int? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (numericValue != null) {
                val reduce = rememberReducedMotion()
                val animated by androidx.compose.animation.core.animateIntAsState(
                    targetValue = numericValue,
                    animationSpec = if (reduce) tween(0) else tween(700, easing = MotionTokens.EmphasizedDecelerate),
                    label = "statValue",
                )
                Text(
                    animated.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                )
            } else {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TtsButton(text: String, onSpeak: (String) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val app = com.japanlearn.app.LocalAppContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var guideKind by remember { mutableStateOf<com.japanlearn.app.util.JapaneseTts.Action?>(null) }
    var waitingForTtsCheck by remember { mutableStateOf(false) }
    LaunchedEffect(waitingForTtsCheck) {
        if (!waitingForTtsCheck) return@LaunchedEffect
        delay(com.japanlearn.app.util.JapaneseTts.INIT_TIMEOUT_MS + 100L)
        val latestState = app.tts.currentState()
        if (latestState == com.japanlearn.app.util.JapaneseTts.State.READY) {
            app.tts.refreshJapaneseStatus()
        }
        val latestAction = com.japanlearn.app.util.JapaneseTts.decideAction(
            latestState,
            app.tts.japaneseUsable(),
            app.tts.japaneseMissingData(),
        )
        if (latestAction != com.japanlearn.app.util.JapaneseTts.Action.SPEAK) {
            guideKind = latestAction
        }
        waitingForTtsCheck = false
    }
    guideKind?.let { kind ->
        VoiceGuideDialog(kind = kind, onDismiss = { guideKind = null })
    }
    // 发音中的状态提示：喇叭轻微呼吸 + 主色。TTS 有初始化延迟，让「点了、在响」可被看见。
    val speaking by app.tts.speaking.collectAsStateWithLifecycle()
    val reduceMotion = rememberReducedMotion()
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(speaking, reduceMotion) {
        if (speaking && !reduceMotion) {
            while (true) {
                pulseScale.animateTo(
                    MotionTokens.TTS_PULSE_MAX_SCALE,
                    tween(MotionTokens.TTS_PULSE_HALF_CYCLE_MS, easing = MotionTokens.Emphasized),
                )
                pulseScale.animateTo(
                    1f,
                    tween(MotionTokens.TTS_PULSE_HALF_CYCLE_MS, easing = MotionTokens.Emphasized),
                )
            }
        } else {
            pulseScale.snapTo(1f)
        }
    }
    FilledTonalIconButton(
        onClick = {
            val state = app.tts.currentState()
            if (state == com.japanlearn.app.util.JapaneseTts.State.READY) app.tts.refreshJapaneseStatus()
            val action = com.japanlearn.app.util.JapaneseTts.decideAction(
                state,
                app.tts.japaneseUsable(),
                app.tts.japaneseMissingData(),
            )
            android.util.Log.i("JapaneseTts", "speak tapped: state=$state action=$action")
            when (action) {
                com.japanlearn.app.util.JapaneseTts.Action.SPEAK -> {
                    onSpeak(text)
                    if (state == com.japanlearn.app.util.JapaneseTts.State.WAITING) {
                        waitingForTtsCheck = true
                    } else if (!app.tts.japaneseUsable()) {
                        val after = com.japanlearn.app.util.JapaneseTts.decideAction(
                            app.tts.currentState(),
                            app.tts.japaneseUsable(),
                            app.tts.japaneseMissingData(),
                        )
                        if (after != com.japanlearn.app.util.JapaneseTts.Action.SPEAK) {
                            guideKind = after
                        }
                    }
                }
                com.japanlearn.app.util.JapaneseTts.Action.GUIDE_VOICE_DATA ->
                    guideKind = com.japanlearn.app.util.JapaneseTts.Action.GUIDE_VOICE_DATA
                com.japanlearn.app.util.JapaneseTts.Action.GUIDE_ENGINE -> {
                    // 引导安装引擎的同时重试一次初始化：刚装好引擎的设备关掉对话框再点即可发音
                    app.tts.retryInit()
                    guideKind = com.japanlearn.app.util.JapaneseTts.Action.GUIDE_ENGINE
                }
            }
        },
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction, 0.9f),
        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(
            Icons.Filled.VolumeUp,
            contentDescription = "播放发音",
            modifier = Modifier.graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            },
        )
    }
}

/** 发音占位骨架 */
/** 发音不可用时的引导：缺语音数据 → 系统语音数据安装页；无语音引擎 → 引导安装 Google TTS。 */
@Composable
internal fun VoiceGuideDialog(
    kind: com.japanlearn.app.util.JapaneseTts.Action,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val newTask = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    val openTtsSettings: () -> Unit = {
        try {
            context.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(newTask))
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                context,
                "当前设备没有可用的语音引擎设置项",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (kind == com.japanlearn.app.util.JapaneseTts.Action.GUIDE_ENGINE) "缺少语音引擎" else "缺少日语语音包",
            )
        },
        text = {
            Text(
                if (kind == com.japanlearn.app.util.JapaneseTts.Action.GUIDE_ENGINE) {
                    "设备上没有可用的文字转语音引擎。安装「Google 文字转语音」并下载日语语音后，即可离线发音。"
                } else {
                    "设备还没有安装日语发音数据，下载后即可离线发音（通常只需几 MB）。下载完成后回到 App 再点一次喇叭即可。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if (kind == com.japanlearn.app.util.JapaneseTts.Action.GUIDE_ENGINE) {
                    // 先尝试应用商店的 Google TTS 详情页，失败退回 TTS 设置页
                    try {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.tts")).addFlags(newTask),
                        )
                    } catch (_: android.content.ActivityNotFoundException) {
                        try {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts")).addFlags(newTask),
                            )
                        } catch (_: Exception) {
                            openTtsSettings()
                        }
                    }
                } else {
                    try {
                        context.startActivity(
                            android.content.Intent("android.speech.tts.engine.INSTALL_TTS_DATA").addFlags(newTask),
                        )
                    } catch (_: android.content.ActivityNotFoundException) {
                        openTtsSettings()
                    }
                }
            }) {
                Text(
                    if (kind == com.japanlearn.app.util.JapaneseTts.Action.GUIDE_ENGINE) "安装语音引擎" else "去下载语音数据",
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, radius: Int = 12) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(shimmerBrush()),
    )
}

/** 统一空状态：图标 + 标题 + 说明 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(20.dp).size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 学习任务行：图标 + 名称 + 动画计数 */
@Composable
fun TaskRow(
    label: String,
    count: Int,
    unit: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f)) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp).size(16.dp),
                    tint = tint,
                )
            }
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$count",
                style = MaterialTheme.typography.titleLarge,
                color = tint,
            )
            Text(
                " $unit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 选择题视图：点击选项立即判定。
 * 答对：正确项弹性放大 + 对勾；答错：错误项水平摇晃 + 叉号，正确项同时标出。
 */
@Composable
fun QuizView(
    quiz: Quiz,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    typedDraft: String = "",
    typedResult: Boolean? = null,
    onSpeak: ((String) -> Unit)? = null,
    onTypedDraftChange: (String) -> Unit = {},
    onTypeSubmit: () -> Unit = {},
) {
    val jc = japanColors()
    var shakeTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(selected) {
        if (!quiz.isTypeAnswer && selected != null && selected != quiz.answerIndex) shakeTrigger++
    }
    // 听音题：出现时自动朗读一次
    LaunchedEffect(quiz) {
        if (quiz.audioText != null) onSpeak?.invoke(quiz.audioText)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (quiz.audioText != null && onSpeak != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TtsButton(quiz.audioText, onSpeak)
                Text(
                    "点击喇叭可以重新播放",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(quiz.question, style = MaterialTheme.typography.titleLarge)
        if (quiz.subQuestion != null) {
            Text(
                quiz.subQuestion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (quiz.isTypeAnswer) {
            OutlinedTextField(
                value = typedDraft,
                onValueChange = { if (typedResult == null) onTypedDraftChange(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = typedResult == null,
                singleLine = true,
                placeholder = { Text(quiz.inputPrompt ?: "用假名或罗马音作答") },
            )
            AppButton(
                text = "确认",
                enabled = typedResult == null && typedDraft.isNotBlank(),
                onClick = onTypeSubmit,
            )
            return@Column
        }
        quiz.options.forEachIndexed { index, option ->
            val answered = selected != null
            val isAnswer = index == quiz.answerIndex
            val isSelected = index == selected

            val bg by animateColorAsState(
                targetValue = when {
                    answered && isAnswer -> jc.correctContainer
                    answered && isSelected -> jc.wrongContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = tween(300),
                label = "quizBg",
            )
            val fg by animateColorAsState(
                targetValue = when {
                    answered && isAnswer -> jc.correct
                    answered && isSelected -> jc.wrong
                    else -> MaterialTheme.colorScheme.onSurface
                },
                animationSpec = tween(300),
                label = "quizFg",
            )
            val popScale by animateFloatAsState(
                targetValue = if (answered && isAnswer) 1.02f else 1f,
                animationSpec = MotionTokens.springBouncy(),
                label = "quizPop",
            )

            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shake(if (isSelected && answered && selected != quiz.answerIndex) shakeTrigger else 0)
                    .graphicsLayer {
                        scaleX = popScale
                        scaleY = popScale
                    }
                    .pressScale(interaction, 0.98f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(bg)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = !answered,
                    ) { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (answered && (isAnswer || isSelected)) fg.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (answered && (isAnswer || isSelected)) fg
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(6.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = option,
                    color = fg,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (answered && isAnswer) {
                    VerdictIcon(Icons.Filled.Check, "正确", fg)
                } else if (answered && isSelected) {
                    VerdictIcon(Icons.Filled.Close, "错误", fg)
                }
            }
        }
    }
}

/** 判定图标入场：0.6→1 springSnappy + 120ms 淡入，只走绘制层，不打扰仍在过渡的底色；减弱动态时直接显示。 */
@Composable
private fun VerdictIcon(icon: ImageVector, description: String, tint: Color) {
    val reduce = rememberReducedMotion()
    val scale = remember { Animatable(if (reduce) 1f else MotionTokens.VERDICT_ICON_SCALE_FROM) }
    val fade = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        launch { scale.animateTo(1f, MotionTokens.springSnappy()) }
        launch { fade.animateTo(1f, tween(MotionTokens.VERDICT_ICON_FADE_MS)) }
    }
    Icon(
        icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            alpha = fade.value
        },
    )
}

/** 掌握程度四档自评（PRD §7.7）：语义色 + 表情图标 + 按压反馈 */
@Composable
fun MasteryRow(modifier: Modifier = Modifier, onRate: (Mastery) -> Unit) {
    val jc = japanColors()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mastery.entries.forEach { m ->
            val (color, container, icon) = when (m) {
                Mastery.UNKNOWN -> Triple(jc.masteryUnknown, jc.masteryUnknownContainer, Icons.Filled.SentimentDissatisfied)
                Mastery.FUZZY -> Triple(jc.masteryFuzzy, jc.masteryFuzzyContainer, Icons.Filled.SentimentNeutral)
                Mastery.KNOWN -> Triple(jc.masteryKnown, jc.masteryKnownContainer, Icons.Filled.SentimentSatisfied)
                Mastery.MASTERED -> Triple(jc.masteryMastered, jc.masteryMasteredContainer, Icons.Filled.SentimentVerySatisfied)
            }
            val interaction = remember { MutableInteractionSource() }
            Surface(
                onClick = { onRate(m) },
                interactionSource = interaction,
                shape = MaterialTheme.shapes.medium,
                color = container,
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction, 0.94f),
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(icon, contentDescription = m.label, tint = color, modifier = Modifier.size(20.dp))
                    Text(
                        m.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 每周学习柱状图：柱高按序错峰生长（绘制层动画），当日高亮朱色。 */
@Composable
fun WeeklyBarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
) {
    val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(
        modifier.fillMaxWidth().height(148.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEachIndexed { index, (label, minutes) ->
            val reduce = rememberReducedMotion()
            var grown by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index * 70L)
                grown = 1
            }
            val fraction by animateFloatAsState(
                targetValue = if (grown == 1 || reduce) com.japanlearn.app.domain.UiMath.barFraction(minutes, max) else 0f,
                animationSpec = if (reduce) tween(0) else tween(650, easing = MotionTokens.EmphasizedDecelerate),
                label = "bar$index",
            )
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (minutes > 0) {
                    Text(
                        "$minutes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val barColor = if (index == highlightIndex) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .padding(top = if (minutes > 0) 4.dp else 0.dp),
                ) {
                    val barHeight = size.height * fraction
                    if (barHeight > 1f) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(0f, size.height - barHeight),
                            size = Size(size.width, barHeight),
                            cornerRadius = CornerRadius(7f, 7f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == highlightIndex) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (index == highlightIndex) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 掌握度分段条（PRD §19.13）：新学 / 巩固中 / 已掌握 三档水平堆叠。
 * [fractions] 顺序固定为新学、巩固中、已掌握，各自 0..1。
 */
@Composable
fun MasterySegmentBar(
    fractions: List<Float>,
    counts: List<Int>,
    modifier: Modifier = Modifier,
) {
    val jc = japanColors()
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        jc.masteryFuzzy,
        jc.masteryMastered,
    )
    val labels = listOf("新学", "巩固中", "已掌握")
    val total = counts.sum()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(14.dp)
                .clip(RoundedCornerShape(7.dp)),
        ) {
            fractions.forEachIndexed { index, f ->
                if (f > 0f) {
                    Box(
                        Modifier
                            .weight(f.coerceAtLeast(0.001f))
                            .fillMaxHeight()
                            .background(colors[index]),
                    )
                }
            }
            if (total == 0) {
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.size(8.dp)
                            .background(colors[index], CircleShape),
                    )
                    Text(
                        "$label ${counts.getOrElse(index) { 0 }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
