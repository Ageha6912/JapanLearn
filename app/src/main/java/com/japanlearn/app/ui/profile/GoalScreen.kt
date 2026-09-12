package com.japanlearn.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.japanlearn.app.AppContainer
import com.japanlearn.app.LocalAppContainer
import com.japanlearn.app.data.SettingsRepository
import com.japanlearn.app.domain.StudyPlanner
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.motion.AnimatedCounterText
import com.japanlearn.app.ui.motion.AnimatedProgressBar
import com.japanlearn.app.ui.motion.StaggerIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalUiState(
    val goalLevel: String = StudyPlanner.LEVEL_NONE,
    val targetEpochDay: Long = 0L,
    val daysRemaining: Int? = null,
    val percent: Int = 0,
    val learnedWords: Int = 0,
    val totalWords: Int = 0,
    val learnedGrammar: Int = 0,
    val totalGrammar: Int = 0,
    val dailyNewWords: Int = SettingsRepository.DEFAULT_NEW_WORDS,
    val recommendedTier: Int? = null,
    val overdue: Boolean = false,
)

class GoalViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(GoalUiState())
    val uiState = _state.asStateFlow()

    private data class GoalDerived(
        val level: String,
        val targetEpochDay: Long,
        val plan: StudyPlanner.Plan,
        val percent: Int,
        val learnedWords: Int,
        val totalWords: Int,
        val learnedGrammar: Int,
        val totalGrammar: Int,
    )

    init {
        fun <T> collect(flow: kotlinx.coroutines.flow.Flow<T>, reducer: (GoalUiState, T) -> GoalUiState) {
            viewModelScope.launch { flow.collect { v -> _state.update { cur -> reducer(cur, v) } } }
        }
        collect(app.settings.dailyNewWords) { s, v -> s.copy(dailyNewWords = v) }
        collect(derivedFlow()) { s, d ->
            s.copy(
                goalLevel = d.level,
                targetEpochDay = d.targetEpochDay,
                daysRemaining = d.plan.daysRemaining,
                overdue = d.plan.overdue,
                recommendedTier = d.plan.recommendedTier,
                percent = d.percent,
                learnedWords = d.learnedWords,
                totalWords = d.totalWords,
                learnedGrammar = d.learnedGrammar,
                totalGrammar = d.totalGrammar,
            )
        }
    }

    /** 级别相关计数 + 目标日期合成派生状态；未设目标时发空值。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun derivedFlow(): Flow<GoalDerived> =
        app.settings.goalLevel.flatMapLatest { level ->
            if (level == StudyPlanner.LEVEL_NONE) {
                flowOf(
                    GoalDerived(
                        level = level, targetEpochDay = 0L,
                        plan = StudyPlanner.Plan(null, null, false),
                        percent = 0, learnedWords = 0, totalWords = 0,
                        learnedGrammar = 0, totalGrammar = 0,
                    ),
                )
            } else {
                combine(
                    app.content.wordCountByLevel(level),
                    app.content.learnedWordCountByLevel(level),
                    app.content.grammarCountByLevel(level),
                    app.content.learnedGrammarCountByLevel(level),
                    app.settings.goalTargetEpochDay,
                ) { totalW, learnedW, totalG, learnedG, target ->
                    GoalDerived(
                        level = level,
                        targetEpochDay = target,
                        plan = StudyPlanner.plan(
                            remainingWords = (totalW - learnedW).coerceAtLeast(0),
                            targetEpochDay = target,
                            todayEpochDay = app.dateProvider.today().toEpochDay(),
                        ),
                        percent = StudyPlanner.progressPercent(learnedW + learnedG, totalW + totalG),
                        learnedWords = learnedW,
                        totalWords = totalW,
                        learnedGrammar = learnedG,
                        totalGrammar = totalG,
                    )
                }
            }
        }

    fun setLevel(level: String) {
        val normalized = StudyPlanner.normalizeLevel(level)
        if (normalized == _state.value.goalLevel) return
        app.settings.setGoal(normalized, _state.value.targetEpochDay)
        recalibrateNow()
    }

    fun setTargetInDays(days: Int) {
        val today = app.dateProvider.today().toEpochDay()
        app.settings.setGoal(_state.value.goalLevel, today + days.coerceAtLeast(1))
        recalibrateNow()
    }

    fun clearTargetDate() {
        app.settings.setGoal(_state.value.goalLevel, 0L)
    }

    fun clearGoal() = app.settings.clearGoal()

    /** 手动采用当前显示的建议档位（不受每周校准门槛限制）。 */
    fun applyRecommended() {
        val tier = _state.value.recommendedTier ?: return
        app.settings.applyGoalTier(tier, app.dateProvider.today().toEpochDay())
    }

    /** 设置/更新目标后立即按剩余内容校准一次（setGoal 已重置校准时间戳）。 */
    private fun recalibrateNow() {
        viewModelScope.launch { app.recalibrateGoal() }
    }
}

@Composable
fun GoalScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: GoalViewModel = androidx.lifecycle.viewmodel.compose.viewModel { GoalViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val hasGoal = state.goalLevel != StudyPlanner.LEVEL_NONE

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 固定头部：标题不随内容滚动
            Column(Modifier.padding(horizontal = 20.dp)) {
                StaggerIn(0) {
                    Text("学习目标", style = MaterialTheme.typography.headlineMedium)
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

                StaggerIn(1) {
                    SectionCard(title = if (hasGoal) "目标级别" else "设定学习目标") {
                        if (!hasGoal) {
                            Text(
                                "选定级别后，JapanLearn 会按剩余内容量推荐每天学多少新词，让进度贴着目标走。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf("N5", "N4").forEach { level ->
                                FilterChip(
                                    selected = state.goalLevel == level,
                                    onClick = { vm.setLevel(level) },
                                    label = { Text(level) },
                                )
                            }
                        }
                    }
                }

                if (hasGoal) {
                    StaggerIn(2) {
                        SectionCard(title = "${state.goalLevel} 目标进度") {
                            AnimatedCounterText(
                                value = state.percent,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                suffix = "%",
                            )
                            AnimatedProgressBar(progress = state.percent / 100f)
                            GoalLine("单词", state.learnedWords, state.totalWords)
                            GoalLine(
                                "语法", state.learnedGrammar, state.totalGrammar,
                                fillColor = MaterialTheme.colorScheme.tertiary,
                            )
                            if (state.percent >= 100) {
                                Text(
                                    "该级别内容已全部学完，可以把目标升到下一级别。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }

                    StaggerIn(3) {
                        SectionCard(title = "目标日期") {
                            if (state.targetEpochDay > 0) {
                                val date = LocalDate.ofEpochDay(state.targetEpochDay)
                                Text(
                                    "达成日：${date.year}年${date.monthValue}月${date.dayOfMonth}日",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                when (val days = state.daysRemaining) {
                                    null -> {}
                                    else -> if (days > 0) {
                                        Text(
                                            "还有 $days 天",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Text(
                                            "目标日已到",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    "未设定日期，将按当前档位的默认节奏学习",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("把达成日设为", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30 to "1 个月", 90 to "3 个月", 180 to "6 个月").forEach { (days, label) ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { vm.setTargetInDays(days) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            if (state.targetEpochDay > 0) {
                                TextButton(onClick = { vm.clearTargetDate() }) { Text("清除日期") }
                            }
                        }
                    }

                    StaggerIn(4) {
                        SectionCard(title = "每日计划") {
                            Text(
                                "当前：每天 ${state.dailyNewWords} 个新词",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val tier = state.recommendedTier
                            if (tier == null) {
                                Text(
                                    "设定目标日期后，这里会按剩余内容给出建议的每日新词量。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    "建议：每天 $tier 个新词" + if (state.overdue) "（按此节奏难以在目标日完成，建议延后日期）" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (tier != state.dailyNewWords) {
                                    AppButton("采用建议档位", onClick = { vm.applyRecommended() })
                                } else {
                                    Text(
                                        "已在建议档位",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                "每周自动校准一次；也可在「我的 → 每日任务量」手动调整。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    StaggerIn(5) {
                        SectionCard {
                            AppButton("清除学习目标", onClick = { vm.clearGoal() })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun GoalLine(
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
