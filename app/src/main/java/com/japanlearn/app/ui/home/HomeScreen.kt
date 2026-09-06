package com.japanlearn.app.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.japanlearn.app.AppContainer
import com.japanlearn.app.LocalAppContainer
import com.japanlearn.app.Routes
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

    Box(Modifier.fillMaxSize()) {
        Scaffold { padding ->
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

                // 今日一句
                val sentence = state.sentence
                if (sentence != null) {
                    StaggerIn(4) {
                        val interaction = remember { MutableInteractionSource() }
                        SectionCard(title = "今日一句 · ${sentence.scene}", onClick = { nav.navigate(Routes.sentence(state.sentenceIndex)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        sentence.ja,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        sentence.zh,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TtsButton(sentence.ja, onSpeak = { vm.speak(it) })
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
