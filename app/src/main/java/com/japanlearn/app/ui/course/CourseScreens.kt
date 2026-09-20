package com.japanlearn.app.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.japanlearn.app.data.local.GrammarEntity
import com.japanlearn.app.data.local.WordEntity
import com.japanlearn.app.domain.CheckpointBuilder
import com.japanlearn.app.domain.CourseCatalog
import com.japanlearn.app.domain.CoursePointer
import com.japanlearn.app.domain.CourseUnitProgress
import com.japanlearn.app.domain.Quiz
import com.japanlearn.app.domain.QuizWord
import com.japanlearn.app.domain.TypeAnswerNormalizer
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.QuizView
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.motion.AnimatedProgressBar
import com.japanlearn.app.ui.motion.ConfettiBurst
import com.japanlearn.app.ui.motion.FeedbackReveal
import com.japanlearn.app.ui.motion.StaggerIn
import com.japanlearn.app.ui.review.FeedbackText
import com.japanlearn.app.ui.review.LoadingPlaceholder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------- 单元列表 ----------------

data class CourseUiState(
    val level: String = "N5",
    val rows: List<CourseUnitProgress> = emptyList(),
    val overrideUnit: Int? = null,
    val currentUnit: Int = 1,
    val checkpointBest: Map<Int, Pair<Int, Int>> = emptyMap(),
)

class CourseViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CourseUiState())
    val uiState = _state.asStateFlow()

    init {
        fun <T> collect(flow: Flow<T>, reducer: (CourseUiState, T) -> CourseUiState) {
            viewModelScope.launch { flow.collect { v -> _state.update { cur -> reducer(cur, v) } } }
        }
        collect(app.settings.studyLevel) { s, v -> s.copy(level = v) }
        collect(courseFlow()) { s, (rows, current, override) ->
            s.copy(rows = rows, currentUnit = current, overrideUnit = override)
        }
        refreshCheckpointScores()
    }

    private fun courseFlow() = app.settings.studyLevel.flatMapLatest { level ->
        combine(
            app.content.unitProgressByLevelFlow(level),
            app.settings.courseUnitOverride,
        ) { rows, override ->
            val mapped = rows.map { CourseUnitProgress(it.unit, it.total, it.learned) }
            val overrideUnit = CoursePointer.parseOverride(override, level)
            Triple(mapped, overrideUnit ?: CoursePointer.currentUnit(mapped), overrideUnit)
        }
    }

    fun setLevel(level: String) = app.settings.setStudyLevel(level)

    fun setCurrentUnit(unit: Int) = app.settings.setCourseUnitOverride(_state.value.level, unit)

    fun clearOverride() = app.settings.setCourseUnitOverride(_state.value.level, null)

    fun refreshCheckpointScores() {
        val level = _state.value.level
        val best = (1..CourseCatalog.UNITS_PER_LEVEL).mapNotNull { u ->
            app.settings.checkpointBest(level, u)?.let { u to it }
        }.toMap()
        _state.update { it.copy(checkpointBest = best) }
    }
}

@Composable
fun CourseScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: CourseViewModel = androidx.lifecycle.viewmodel.compose.viewModel { CourseViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    // 从检查点/详情返回时刷新最佳成绩
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshCheckpointScores() }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                AppTopBar(title = "课程") { nav.popBackStack() }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("N5", "N4").forEach { level ->
                        FilterChip(
                            selected = state.level == level,
                            onClick = { vm.setLevel(level) },
                            label = { Text(level) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { nav.navigate(Routes.WORD_LIST) }) { Text("全部单词") }
                    TextButton(onClick = { nav.navigate(Routes.GRAMMAR_LIST) }) { Text("全部语法") }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.rows.sortedBy { it.unit }.forEachIndexed { i, row ->
                    StaggerIn(i % 6) {
                        UnitCard(
                            level = state.level,
                            row = row,
                            isCurrent = row.unit == state.currentUnit,
                            best = state.checkpointBest[row.unit],
                            onClick = { nav.navigate(Routes.courseUnit(state.level, row.unit)) },
                        )
                    }
                }
                if (state.overrideUnit != null) {
                    TextButton(onClick = { vm.clearOverride() }) { Text("恢复自动跟随当前单元") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun UnitCard(
    level: String,
    row: CourseUnitProgress,
    isCurrent: Boolean,
    best: Pair<Int, Int>?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (row.isComplete) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        if (row.isComplete) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "已完成",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text("${row.unit}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "第 ${row.unit} 单元 · ${CourseCatalog.unitTitle(row.unit)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "已学 ${row.learned}/${row.total}" + if (best != null) " · 检查点最佳 ${best.first}/${best.second}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isCurrent) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "当前",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            AnimatedProgressBar(progress = row.progress, height = 6.dp)
        }
    }
}

// ---------------- 单元详情 ----------------

data class CourseUnitUiState(
    val words: List<WordEntity> = emptyList(),
    val grammar: List<GrammarEntity> = emptyList(),
    val mastery: Map<String, Int> = emptyMap(),
    val learned: Int = 0,
    val total: Int = 0,
    val isCurrent: Boolean = false,
    val isOverridden: Boolean = false,
)

class CourseUnitViewModel(
    private val app: AppContainer,
    private val level: String,
    private val unit: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(CourseUnitUiState())
    val uiState = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val words = app.content.wordsByUnit(level, unit)
            val grammar = app.content.grammarByUnit(level, unit)
            _state.update { it.copy(words = words, grammar = grammar, total = words.size) }
        }
        viewModelScope.launch {
            app.progress.wordMasteryMap().collect { map ->
                _state.update { s ->
                    val learned = s.words.count { (map[it.id] ?: 0) > 0 || map.containsKey(it.id) }
                    s.copy(mastery = map, learned = learned)
                }
            }
        }
        viewModelScope.launch {
            app.content.unitProgressByLevelFlow(level).collect { rows ->
                val row = rows.firstOrNull { it.unit == unit }
                _state.update { s ->
                    val mapped = rows.map { CourseUnitProgress(it.unit, it.total, it.learned) }
                    val overrideUnit = CoursePointer.parseOverride(
                        app.settings.courseUnitOverride.value, level,
                    )
                    s.copy(
                        learned = row?.learned ?: s.learned,
                        total = row?.total ?: s.total,
                        isCurrent = (overrideUnit ?: CoursePointer.currentUnit(mapped)) == unit,
                        isOverridden = overrideUnit != null,
                    )
                }
            }
        }
    }

    fun setCurrentHere() = app.settings.setCourseUnitOverride(level, unit)
    fun clearOverride() = app.settings.setCourseUnitOverride(level, null)
    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun CourseUnitScreen(nav: NavHostController, level: String, unit: Int) {
    val app = LocalAppContainer.current
    val vm: CourseUnitViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "courseUnit_${level}_$unit",
    ) { CourseUnitViewModel(app, level, unit) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AppTopBar(title = "第 $unit 单元 · ${CourseCatalog.unitTitle(unit)}") { nav.popBackStack() }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StaggerIn(0) {
                    SectionCard {
                        Text(
                            "$level · 已学 ${state.learned}/${state.total}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        AnimatedProgressBar(
                            progress = if (state.total > 0) state.learned.toFloat() / state.total else 0f,
                        )
                        if (state.isCurrent) {
                            Text(
                                "这是当前的课程单元，今日新词从这里顺序取",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.isOverridden) {
                                TextButton(onClick = { vm.clearOverride() }) { Text("恢复自动跟随") }
                            }
                        } else {
                            TextButton(onClick = { vm.setCurrentHere() }) { Text("设为当前单元") }
                        }
                    }
                }

                StaggerIn(1) {
                    SectionCard(title = "单元单词") {
                        if (state.words.isEmpty()) {
                            Text(
                                "暂无内容",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.words.forEach { w ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MasteryDot(state.mastery[w.id])
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (w.kana != w.ja) "${w.ja}（${w.kana}）" else w.ja,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        w.zh,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                StaggerIn(2) {
                    SectionCard(title = "单元语法") {
                        if (state.grammar.isEmpty()) {
                            Text(
                                "本单元没有语法条目",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.grammar.forEach { g ->
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(g.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    g.meaning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                StaggerIn(3) {
                    AppButton("单元测试（10 题）", onClick = { nav.navigate(Routes.courseCheckpoint(level, unit)) })
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** 单词掌握度色点（与单词列表一致）：灰=不认识 黄=模糊 绿=熟悉 蓝=熟练。 */
@Composable
private fun MasteryDot(mastery: Int?) {
    val color = when (mastery) {
        null -> androidx.compose.ui.graphics.Color.Transparent
        0 -> androidx.compose.ui.graphics.Color(0xFFB9B2A6)
        1 -> androidx.compose.ui.graphics.Color(0xFFE7C86D)
        2 -> androidx.compose.ui.graphics.Color(0xFF4E7D5B)
        else -> androidx.compose.ui.graphics.Color(0xFF1B3A5C)
    }
    Box(
        Modifier
            .size(12.dp)
            .background(
                color = color,
                shape = CircleShape,
            )
            .then(
                if (mastery == null) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = androidx.compose.ui.graphics.Color(0xFFD8D2C5),
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            ),
    )
}

// ---------------- 单元测试（检查点） ----------------

private enum class CheckpointPhase { LOADING, QUIZ, DONE }

private data class CheckpointUiState(
    val phase: CheckpointPhase = CheckpointPhase.LOADING,
    val questions: List<Quiz> = emptyList(),
    val contentIds: List<String> = emptyList(),
    val index: Int = 0,
    val selected: Int? = null,
    val typedDraft: String = "",
    val typedResult: Boolean? = null,
    val correct: Int = 0,
    val total: Int = 0,
    val lastCorrect: Boolean? = null,
)

private class CheckpointViewModel(
    private val app: AppContainer,
    private val level: String,
    private val unit: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckpointUiState())
    val uiState = _state.asStateFlow()

    private var startedAt = 0L

    init {
        viewModelScope.launch {
            val words = app.content.wordsByUnit(level, unit)
            val quizzes = CheckpointBuilder.build(
                words.map { QuizWord(it.id, it.ja, it.kana, it.zh, it.pos, it.cat, it.romaji) },
                count = 10,
            )
            if (quizzes.isEmpty()) {
                _state.update { it.copy(phase = CheckpointPhase.DONE) }
                return@launch
            }
            startedAt = System.currentTimeMillis()
            _state.update {
                it.copy(
                    phase = CheckpointPhase.QUIZ,
                    questions = quizzes,
                    contentIds = words.map { w -> w.id },
                )
            }
        }
    }

    val current: Quiz? get() = _state.value.questions.getOrNull(_state.value.index)

    fun onSelect(index: Int) {
        val s = _state.value
        val quiz = current ?: return
        if (quiz.isTypeAnswer || s.selected != null) return
        val ok = quiz.answerIndex == index
        _state.update {
            it.copy(selected = index, lastCorrect = ok, total = it.total + 1, correct = it.correct + if (ok) 1 else 0)
        }
    }

    fun onTypedDraftChange(value: String) {
        if (_state.value.typedResult == null) _state.update { it.copy(typedDraft = value) }
    }

    fun submitTyped() {
        val s = _state.value
        val quiz = current ?: return
        if (!quiz.isTypeAnswer || s.typedResult != null) return
        val ok = TypeAnswerNormalizer.matches(s.typedDraft, quiz.acceptedAnswers)
        _state.update {
            it.copy(typedResult = ok, lastCorrect = ok, total = it.total + 1, correct = it.correct + if (ok) 1 else 0)
        }
    }

    fun next() {
        val s = _state.value
        val index = s.index
        val quiz = s.questions.getOrNull(index) ?: return
        val contentId = s.contentIds.getOrNull(index % s.contentIds.size) ?: return
        viewModelScope.launch {
            // 检查点对错只同步错题本，不推进 SRS（PRD §19.8）
            if (s.lastCorrect != null) {
                app.progress.recordAuxAnswer("word", contentId, s.lastCorrect)
            }
            val nextIndex = index + 1
            if (nextIndex >= s.questions.size) {
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                app.stats.addStudy(seconds.coerceAtLeast(1))
                app.settings.setCheckpointBest(level, unit, s.correct, s.total)
                _state.update { it.copy(index = nextIndex, phase = CheckpointPhase.DONE) }
            } else {
                _state.update {
                    it.copy(index = nextIndex, selected = null, typedDraft = "", typedResult = null, lastCorrect = null)
                }
            }
        }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun CourseCheckpointScreen(nav: NavHostController, level: String, unit: Int) {
    val app = LocalAppContainer.current
    val vm: CheckpointViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "checkpoint_${level}_$unit",
    ) { CheckpointViewModel(app, level, unit) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AppTopBar(
                title = when (state.phase) {
                    CheckpointPhase.DONE -> "检查点完成"
                    CheckpointPhase.QUIZ -> "单元测试 ${state.index + 1}/${state.questions.size}"
                    else -> "单元测试"
                },
            ) { nav.popBackStack() }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state.phase) {
                    CheckpointPhase.LOADING -> LoadingPlaceholder()

                    CheckpointPhase.QUIZ -> {
                        vm.current?.let { quiz ->
                            QuizView(
                                quiz,
                                state.selected,
                                onSelect = { vm.onSelect(it) },
                                typedDraft = state.typedDraft,
                                typedResult = state.typedResult,
                                onSpeak = { vm.speak(it) },
                                onTypedDraftChange = { vm.onTypedDraftChange(it) },
                                onTypeSubmit = { vm.submitTyped() },
                            )
                            val answered =
                                if (quiz.isTypeAnswer) state.typedResult != null else state.selected != null
                            if (answered) {
                                val correct =
                                    if (quiz.isTypeAnswer) state.typedResult == true else state.selected == quiz.answerIndex
                                FeedbackReveal {
                                    FeedbackText(correct = correct, answerText = quiz.answerText)
                                    AppButton(
                                        text = if (state.index + 1 >= state.questions.size) "看结果" else "下一题",
                                        onClick = { vm.next() },
                                    )
                                }
                            }
                        }
                    }

                    CheckpointPhase.DONE -> {
                        StaggerIn(0) {
                            SectionCard {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text("检查点完成", style = MaterialTheme.typography.headlineSmall)
                                    if (state.total > 0) {
                                        Text("答对 ${state.correct}/${state.total}", style = MaterialTheme.typography.bodyLarge)
                                    } else {
                                        Text(
                                            "本单元暂无单词可测",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        StaggerIn(1) {
                            AppButton("返回单元", onClick = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
        ConfettiBurst(trigger = if (state.phase == CheckpointPhase.DONE && state.total > 0) 1 else 0)
    }
}
