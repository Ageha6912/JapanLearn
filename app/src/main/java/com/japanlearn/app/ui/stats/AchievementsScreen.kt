package com.japanlearn.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.japanlearn.app.AppContainer
import com.japanlearn.app.LocalAppContainer
import com.japanlearn.app.domain.CourseCatalog
import com.japanlearn.app.domain.StreakCalculator
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.components.StatTile
import com.japanlearn.app.ui.motion.StaggerIn
import com.japanlearn.app.util.formatStudyDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AchievementsUiState(
    val loading: Boolean = true,
    val totalSeconds: Int = 0,
    val learnedWords: Int = 0,
    val totalWords: Int = 0,
    val masteredWords: Int = 0,
    val learnedGrammar: Int = 0,
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val completedUnits: Int = 0,
    val reviewCorrect: Int? = null,
    val reviewTotal: Int? = null,
)

/** 学习成果页（PRD §19.8）：全部来自现有表的纯聚合，不新增存储。 */
class AchievementsViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsUiState())
    val uiState = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val weekly = app.stats.weekly().first()
            val accuracy = app.stats.reviewAccuracy()
            _state.update {
                it.copy(
                    loading = false,
                    totalSeconds = app.stats.totalSeconds().first(),
                    learnedWords = app.progress.learnedWordCount().first(),
                    totalWords = app.content.wordCount().first(),
                    masteredWords = app.progress.masteredWordCount().first(),
                    learnedGrammar = app.progress.learnedGrammarCount().first(),
                    streak = weekly.streak,
                    longestStreak = StreakCalculator.longestStreak(app.stats.allStudyDates()),
                    completedUnits = app.stats.completedUnitCount(),
                    reviewCorrect = accuracy?.first,
                    reviewTotal = accuracy?.second,
                )
            }
        }
    }
}

@Composable
fun AchievementsScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: AchievementsViewModel = androidx.lifecycle.viewmodel.compose.viewModel { AchievementsViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AppTopBar(title = "学习成果") { nav.popBackStack() }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StaggerIn(0) {
                    SectionCard(title = "学习总量") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                value = formatStudyDuration(state.totalSeconds),
                                label = "累计学习时长",
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                value = "${state.learnedWords}",
                                label = "已学单词 / ${state.totalWords}",
                                modifier = Modifier.weight(1f),
                                numericValue = state.learnedWords,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                value = "${state.masteredWords}",
                                label = "已掌握单词",
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.tertiary,
                                numericValue = state.masteredWords,
                            )
                            StatTile(
                                value = "${state.learnedGrammar}",
                                label = "已学语法",
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.tertiary,
                                numericValue = state.learnedGrammar,
                            )
                        }
                    }
                }

                StaggerIn(1) {
                    SectionCard(title = "坚持") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                value = "${state.streak} 天",
                                label = "当前连击",
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.secondary,
                                numericValue = state.streak,
                            )
                            StatTile(
                                value = "${state.longestStreak} 天",
                                label = "历史最长连击",
                                modifier = Modifier.weight(1f),
                                numericValue = state.longestStreak,
                            )
                        }
                    }
                }

                StaggerIn(2) {
                    SectionCard(title = "课程进度") {
                        Text(
                            "已完成课程单元 ${state.completedUnits} / ${CourseCatalog.UNITS_PER_LEVEL * 2}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "N5 与 N4 各 ${CourseCatalog.UNITS_PER_LEVEL} 个单元，单元词全部学过即完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                StaggerIn(3) {
                    SectionCard(title = "复习正确率") {
                        val c = state.reviewCorrect
                        val t = state.reviewTotal
                        if (c == null || t == null || t == 0) {
                            Text(
                                "完成一次复习后这里会出现正确率",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "$c / $t（${c * 100 / t}%）",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "统计所有 SRS 复习的自评结果（模糊及以上记为答对）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
