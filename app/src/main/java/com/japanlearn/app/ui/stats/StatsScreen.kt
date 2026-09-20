package com.japanlearn.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
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
import com.japanlearn.app.Routes
import com.japanlearn.app.data.local.DailyStudyEntity
import com.japanlearn.app.domain.MasteryDistribution
import com.japanlearn.app.domain.WrongPortrait
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.MasterySegmentBar
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.components.StatTile
import com.japanlearn.app.ui.components.WeeklyBarChart
import com.japanlearn.app.ui.motion.AnimatedCounterText
import com.japanlearn.app.ui.motion.StaggerIn
import com.japanlearn.app.util.formatStudyDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StatsUiState(
    val streak: Int = 0,
    val totalSeconds: Int = 0,
    val totalNewWords: Int = 0,
    val totalReviews: Int = 0,
    val learnedWords: Int = 0,
    val masteredWords: Int = 0,
    val totalWords: Int = 0,
    val learnedGrammar: Int = 0,
    val totalGrammar: Int = 0,
    val learnedKanji: Int = 0,
    val totalKanji: Int = 0,
    val learnedKanjiN5: Int = 0,
    val totalKanjiN5: Int = 0,
    val learnedKanjiN4: Int = 0,
    val totalKanjiN4: Int = 0,
    val today: LocalDate = LocalDate.now(),
    val week: List<Pair<LocalDate, DailyStudyEntity?>> = emptyList(),
    val mastery: MasteryDistribution.Snapshot = MasteryDistribution.Snapshot(),
    val wrongPortrait: WrongPortrait.Portrait = WrongPortrait.Portrait(),
)

class StatsViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val uiState = _state.asStateFlow()

    init {
        fun <T> collect(flow: kotlinx.coroutines.flow.Flow<T>, reducer: (StatsUiState, T) -> StatsUiState) {
            viewModelScope.launch { flow.collect { v -> _state.update { cur -> reducer(cur, v) } } }
        }
        val today = app.dateProvider.today()
        val weekDates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        _state.update { it.copy(today = today) }

        collect(app.stats.weekly()) { s, v ->
            s.copy(streak = v.streak, week = weekDates.map { d -> d to v.days.find { it.date == d.toString() } })
        }
        collect(app.stats.totalSeconds()) { s, v -> s.copy(totalSeconds = v) }
        collect(app.stats.totalNewWords()) { s, v -> s.copy(totalNewWords = v) }
        collect(app.stats.totalReviews()) { s, v -> s.copy(totalReviews = v) }
        collect(app.progress.learnedWordCount()) { s, v -> s.copy(learnedWords = v) }
        collect(app.progress.masteredWordCount()) { s, v -> s.copy(masteredWords = v) }
        collect(app.progress.learnedGrammarCount()) { s, v -> s.copy(learnedGrammar = v) }
        collect(app.content.wordCount()) { s, v -> s.copy(totalWords = v) }
        collect(app.content.grammarCount()) { s, v -> s.copy(totalGrammar = v) }
        collect(app.progress.learnedKanjiCount()) { s, v -> s.copy(learnedKanji = v) }
        collect(app.content.kanjiCount()) { s, v -> s.copy(totalKanji = v) }
        collect(app.content.kanjiCountByLevel("N5")) { s, v -> s.copy(totalKanjiN5 = v) }
        collect(app.content.kanjiCountByLevel("N4")) { s, v -> s.copy(totalKanjiN4 = v) }
        collect(app.content.learnedKanjiCountByLevel("N5")) { s, v -> s.copy(learnedKanjiN5 = v) }
        collect(app.content.learnedKanjiCountByLevel("N4")) { s, v -> s.copy(learnedKanjiN4 = v) }

        collect(
            combine(app.progress.wordStabilities(), app.progress.kanjiStabilities()) { words, kanji ->
                words + kanji
            },
        ) { s, v ->
            s.copy(mastery = MasteryDistribution.snapshot(v))
        }

        collect(
            combine(
                app.progress.wrongAnswers(),
                app.content.wordsAll(),
                app.content.grammarAll(),
                app.content.kanaAll(),
                app.content.kanjiAll(),
            ) { wrong, words, grammar, kana, kanji ->
                val wordById = words.associateBy { it.id }
                val grammarById = grammar.associateBy { it.id }
                val kanaById = kana.associateBy { it.id }
                val kanjiById = kanji.associateBy { it.id }
                val entries = wrong.mapNotNull { w ->
                    val primary = when (w.contentType) {
                        "word" -> wordById[w.contentId]?.ja
                        "grammar" -> grammarById[w.contentId]?.title
                        "kana" -> kanaById[w.contentId]?.hiragana
                        "kanji" -> kanjiById[w.contentId]?.char
                        else -> null
                    } ?: return@mapNotNull null
                    WrongPortrait.Entry(
                        contentType = w.contentType,
                        contentId = w.contentId,
                        wrongCount = w.wrongCount,
                        lastWrongAt = w.lastWrongAt,
                        primary = primary,
                    )
                }
                val cats = words.map { WrongPortrait.WordMeta(it.id, it.cat) }
                WrongPortrait.build(entries, cats, topN = 5)
            },
        ) { s, v -> s.copy(wrongPortrait = v) }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun StatsScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: StatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel { StatsViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val chartData = state.week.map { (date, record) ->
        weekLabels[(date.dayOfWeek.value - 1).coerceIn(0, 6)] to (record?.studySeconds ?: 0) / 60
    }
    val todayIndex = state.week.indexOfFirst { (date, _) -> date == state.today }.takeIf { it >= 0 }
    val typeLabel = mapOf("word" to "单词", "grammar" to "语法", "kana" to "五十音", "kanji" to "汉字")

    Scaffold(
        topBar = { AppTopBar("学习统计") { nav.popBackStack() } },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(6.dp))

            // 连击主视觉：火焰 + 大数字
            StaggerIn(0) {
                SectionCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(40.dp),
                        )
                        AnimatedCounterText(
                            value = state.streak,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        "连续学习天数",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }

            StaggerIn(1) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = formatStudyDuration(state.totalSeconds),
                        label = "累计学习",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = "${state.totalNewWords}",
                        label = "累计新词",
                        modifier = Modifier.weight(1f),
                        numericValue = state.totalNewWords,
                    )
                }
            }
            StaggerIn(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = "${state.totalReviews}",
                        label = "累计复习",
                        modifier = Modifier.weight(1f),
                        numericValue = state.totalReviews,
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                    StatTile(
                        value = "${state.masteredWords}",
                        label = "已掌握单词",
                        modifier = Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary,
                        numericValue = state.masteredWords,
                    )
                }
            }

            StaggerIn(3) {
                SectionCard(title = "每日学习（分钟）") {
                    WeeklyBarChart(chartData, highlightIndex = todayIndex)
                }
            }

            StaggerIn(4) {
                SectionCard(title = "掌握度分布") {
                    if (state.mastery.total == 0) {
                        Text(
                            "还没有已学单词或汉字。开始今日学习后，这里会按 FSRS 稳定度显示新学 / 巩固中 / 已掌握。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MasterySegmentBar(
                            fractions = state.mastery.fractions(),
                            counts = listOf(state.mastery.fresh, state.mastery.consolidating, state.mastery.mastered),
                        )
                    }
                }
            }

            StaggerIn(5) {
                val portrait = state.wrongPortrait
                SectionCard(
                    title = "错题画像",
                    onClick = if (portrait.total > 0) {
                        { nav.navigate(Routes.WRONG_ANSWERS) }
                    } else {
                        null
                    },
                ) {
                    if (portrait.total == 0) {
                        Text(
                            "暂无待攻克错题。练习或复习中答错的题目会汇总到这里。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "待攻克 ${portrait.total} 条",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        val typeBits = listOf("word", "grammar", "kana", "kanji").mapNotNull { t ->
                            val n = portrait.byType[t] ?: return@mapNotNull null
                            "${typeLabel[t] ?: t} $n"
                        }
                        if (typeBits.isNotEmpty()) {
                            Text(
                                typeBits.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (portrait.topRepeat.isNotEmpty()) {
                            Text("反复错 Top ${portrait.topRepeat.size}", style = MaterialTheme.typography.titleSmall)
                            portrait.topRepeat.forEach { item ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        item.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Text(
                                        "×${item.wrongCount}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                        if (portrait.topCategories.isNotEmpty()) {
                            Text(
                                "分类热区：" + portrait.topCategories.joinToString(" · ") { "${it.category} ${it.count}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { nav.navigate(Routes.WRONG_ANSWERS) }) {
                            Text("打开错题本")
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }

            StaggerIn(6) {
                SectionCard(title = "内容进度") {
                    Text(
                        "单词：已学 ${state.learnedWords} / ${state.totalWords}，已掌握 ${state.masteredWords}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "语法：已学 ${state.learnedGrammar} / ${state.totalGrammar}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "汉字 N5：已学 ${state.learnedKanjiN5} / ${state.totalKanjiN5}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "汉字 N4：已学 ${state.learnedKanjiN4} / ${state.totalKanjiN4}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
