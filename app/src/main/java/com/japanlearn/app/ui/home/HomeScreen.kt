package com.japanlearn.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.japanlearn.app.AppContainer
import com.japanlearn.app.LocalAppContainer
import com.japanlearn.app.Routes
import com.japanlearn.app.data.ThemeMode
import com.japanlearn.app.data.breakdown
import com.japanlearn.app.data.local.SentenceEntity
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.components.StatTile
import com.japanlearn.app.ui.components.TaskRow
import com.japanlearn.app.ui.components.TtsButton
import com.japanlearn.app.ui.motion.AnimatedCounterText
import com.japanlearn.app.ui.motion.AnimatedProgressBar
import com.japanlearn.app.ui.motion.ConfettiBurst
import com.japanlearn.app.ui.motion.ProgressRing
import com.japanlearn.app.ui.motion.StaggerIn
import com.japanlearn.app.ui.motion.PopupAnchor
import com.japanlearn.app.ui.motion.TransformCardPopup
import com.japanlearn.app.ui.motion.popupBackgroundScale
import com.japanlearn.app.ui.motion.pressScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val totalWords: Int = 0,
    val totalGrammar: Int = 0,
    val learnedWords: Int = 0,
    val learnedGrammar: Int = 0,
    val masteredWords: Int = 0,
    val dueWords: Int = 0,
    val dueGrammar: Int = 0,
    val streak: Int = 0,
    val targetNewWords: Int = 10,
    val targetNewGrammar: Int = 3,
    val todayNewWords: Int = 0,
    val todayNewGrammar: Int = 0,
    val todayReviews: Int = 0,
    val sentence: SentenceEntity? = null,
    val sentenceIndex: Int = 0,
)

class HomeViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val uiState = _state.asStateFlow()

    init {
        fun <T> collect(flow: kotlinx.coroutines.flow.Flow<T>, update: (HomeUiState, T) -> HomeUiState) {
            viewModelScope.launch { flow.collect { _state.update { s -> update(s, it) } } }
        }
        collect(app.content.wordsAll()) { s, v -> s.copy(totalWords = v.size) }
        collect(app.content.grammarAll()) { s, v -> s.copy(totalGrammar = v.size) }
        collect(app.progress.learnedWordCount()) { s, v -> s.copy(learnedWords = v) }
        collect(app.progress.learnedGrammarCount()) { s, v -> s.copy(learnedGrammar = v) }
        collect(app.progress.masteredWordCount()) { s, v -> s.copy(masteredWords = v) }
        collect(app.progress.dueWordCount()) { s, v -> s.copy(dueWords = v) }
        collect(app.progress.dueGrammarCount()) { s, v -> s.copy(dueGrammar = v) }
        collect(app.stats.weekly()) { s, v -> s.copy(streak = v.streak) }
        collect(app.stats.todayFlow()) { s, v ->
            s.copy(
                todayNewWords = v?.newWords ?: 0,
                todayNewGrammar = v?.newGrammar ?: 0,
                todayReviews = v?.reviewsDone ?: 0,
            )
        }
        collect(app.settings.dailyNewWords) { s, v -> s.copy(targetNewWords = v) }
        collect(app.settings.dailyNewGrammar) { s, v -> s.copy(targetNewGrammar = v) }
        viewModelScope.launch {
            val list = app.content.sentencesAll().first()
            val idx = app.stats.sentenceIndexForToday(list.size)
            val sentence = list.getOrNull(idx)
            _state.update { it.copy(sentence = sentence, sentenceIndex = idx) }
        }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel { HomeViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    val poolRemaining = (state.totalWords - state.learnedWords).coerceAtLeast(0)
    val wordsRemaining = poolRemaining.coerceAtMost((state.targetNewWords - state.todayNewWords).coerceAtLeast(0))
    val grammarPoolRemaining = (state.totalGrammar - state.learnedGrammar).coerceAtLeast(0)
    val grammarRemaining = grammarPoolRemaining.coerceAtMost((state.targetNewGrammar - state.todayNewGrammar).coerceAtLeast(0))
    val dueToday = state.dueWords + state.dueGrammar
    val todayDone = state.todayNewWords + state.todayNewGrammar + state.todayReviews
    val todayTotal = (state.targetNewWords + state.targetNewGrammar + dueToday).coerceAtLeast(1)
    val progress = com.japanlearn.app.domain.UiMath.dailyProgress(todayDone, todayTotal)
    val allDone = !(wordsRemaining > 0 || dueToday > 0)

    val canStartWords = wordsRemaining > 0
    val canStartReview = dueToday > 0

    var showSettings by remember { mutableStateOf(false) }
    var showSentence by remember { mutableStateOf(false) }
    val popupVisible = showSettings || showSentence
    val bgScale = popupBackgroundScale(popupVisible)
    val bgOrigin = if (showSettings) TransformOrigin(1f, 0f) else TransformOrigin(0.5f, 1f)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.graphicsLayer {
                scaleX = bgScale
                scaleY = bgScale
                transformOrigin = bgOrigin
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                // 固定头部：问候 + 连击徽章，不随内容滚动
                Column(Modifier.padding(horizontal = 20.dp)) {

                    // 问候 + 连击徽章
                    StaggerIn(0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("こんにちは", style = MaterialTheme.typography.headlineMedium)
                                Text(
                                    "今天想学点什么？",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.streak > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Row(
                                        Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.LocalFireDepartment,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            "${state.streak} 天",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                onClick = { showSettings = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "设置",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp).size(20.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                // 今日学习主卡：进度环 + 任务清单
                StaggerIn(1) {
                    SectionCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            ProgressRing(
                                progress = progress,
                                modifier = Modifier.size(84.dp),
                                stroke = 9.dp,
                                fillColor = if (allDone) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            ) {
                                AnimatedCounterText(
                                    value = (progress * 100).toInt(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (allDone) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    suffix = "%",
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TaskRow("新词", wordsRemaining, "个", Icons.AutoMirrored.Filled.MenuBook)
                                TaskRow("语法", grammarRemaining, "条", Icons.Outlined.EditNote, tint = MaterialTheme.colorScheme.tertiary)
                                TaskRow("待复习", dueToday, "条", Icons.Filled.Refresh, tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        AnimatedProgressBar(progress = progress)
                        Text(
                            "今日已完成 $todayDone / $todayTotal 项",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AppButton(
                            text = when {
                                canStartWords -> "开始今日学习"
                                canStartReview -> "开始今日复习"
                                else -> "今日任务已完成"
                            },
                            enabled = canStartWords || canStartReview,
                            onClick = {
                                when {
                                    canStartWords -> nav.navigate(Routes.wordSession(wordsRemaining))
                                    else -> nav.navigate(Routes.REVIEW_SESSION)
                                }
                            },
                        )
                    }
                }

                // 进度总览
                StaggerIn(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            value = "${state.learnedWords}",
                            label = "已学单词 / ${state.totalWords}",
                            modifier = Modifier.weight(1f),
                            numericValue = state.learnedWords,
                        )
                        StatTile(
                            value = "${state.masteredWords}",
                            label = "已掌握 / ${state.totalWords}",
                            modifier = Modifier.weight(1f),
                            accent = MaterialTheme.colorScheme.tertiary,
                            numericValue = state.masteredWords,
                        )
                    }
                }

                // 内容进度双条
                StaggerIn(3) {
                    SectionCard(title = "学习进度") {
                        ProgressLine(
                            label = "单词",
                            done = state.learnedWords,
                            total = state.totalWords,
                        )
                        ProgressLine(
                            label = "语法",
                            done = state.learnedGrammar,
                            total = state.totalGrammar,
                            fillColor = MaterialTheme.colorScheme.tertiary,
                        )
                        AppButton(
                            text = "查看学习统计",
                            onClick = { nav.navigate(Routes.STATS) },
                        )
                    }
                }

                // 今日一句：底部横条按钮，点击弹出完整卡片（容器变换）
                val sentence = state.sentence
                if (sentence != null) {
                    StaggerIn(4) {
                        Surface(
                            onClick = { showSentence = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text("今日一句", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    sentence.ja,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1.4f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.NavigateNext,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                }
            }
        }

        // 今日任务全部完成时撒彩带
        ConfettiBurst(trigger = if (allDone && todayDone > 0) 1 else 0)

        // 设置卡片：从右上角按钮生长弹出
        TransformCardPopup(visible = showSettings, anchor = PopupAnchor.TopEnd, onDismiss = { showSettings = false }) {
            val context = LocalContext.current
            val themeMode by app.settings.themeMode.collectAsStateWithLifecycle()
            val reminderEnabled by app.settings.reminderEnabled.collectAsStateWithLifecycle()
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { }
            Text("设置", style = MaterialTheme.typography.titleLarge)
            Text("外观", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "跟随系统",
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { app.settings.setThemeMode(mode) },
                        label = { Text(label) },
                    )
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("每日复习提醒", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "每天 20:00 检查到期内容",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        app.settings.setReminderEnabled(enabled)
                        com.japanlearn.app.work.ReviewReminder.schedule(context, enabled)
                    },
                )
            }
            HorizontalDivider()
            TextButton(onClick = {
                showSettings = false
                nav.navigate(Routes.PROFILE)
            }) { Text("全部设置") }
            Text(
                "JapanLearn v" + com.japanlearn.app.BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 今日一句卡片：从底部横条生长弹出
        TransformCardPopup(visible = showSentence, anchor = PopupAnchor.BottomCenter, onDismiss = { showSentence = false }) {
            state.sentence?.let { s2 ->
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "今日一句 · " + s2.scene,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s2.ja, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TtsButton(s2.ja, onSpeak = { vm.speak(it) })
                }
                Text(s2.zh, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("词汇拆解", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s2.breakdown().forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(b.t, style = MaterialTheme.typography.titleSmall)
                            Text(
                                b.zh,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(
    label: String,
    done: Int,
    total: Int,
    fillColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                "$done / $total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedProgressBar(
            progress = if (total > 0) done.toFloat() / total else 0f,
            height = 6.dp,
            fillColor = fillColor,
        )
    }
}
