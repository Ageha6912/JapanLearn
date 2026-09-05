package com.japanlearn.app.ui.grammar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.japanlearn.app.data.content.Example
import com.japanlearn.app.data.content.Exercise
import com.japanlearn.app.data.examples
import com.japanlearn.app.data.exercises
import com.japanlearn.app.data.local.GrammarEntity
import com.japanlearn.app.domain.Mastery
import com.japanlearn.app.domain.Quiz
import com.japanlearn.app.domain.QuizGenerator
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.MasteryRow
import com.japanlearn.app.ui.components.QuizView
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.components.SessionPhase
import com.japanlearn.app.ui.components.TtsButton
import com.japanlearn.app.ui.motion.ConfettiBurst
import com.japanlearn.app.ui.motion.MotionTokens
import com.japanlearn.app.ui.motion.StaggerIn
import com.japanlearn.app.ui.motion.rememberReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------- 语法列表 ----------------

@Composable
fun GrammarListScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val grammar by app.content.grammarAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val learned by app.progress.learnedGrammarCount().collectAsStateWithLifecycle(initialValue = 0)
    val dailyTarget by app.settings.dailyNewGrammar.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar("N5 语法") { nav.popBackStack() } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            item {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "已学 $learned / ${grammar.size} 条",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppButton("学习语法（$dailyTarget 条）") {
                        nav.navigate(Routes.grammarSession(dailyTarget))
                    }
                }
            }
            items(grammar, key = { it.id }) { g ->
                Surface(
                    onClick = { nav.navigate(Routes.grammarDetail(g.id)) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(g.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            g.meaning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ---------------- 语法学习卡（学习会话与详情页共用） ----------------

@Composable
fun GrammarCardContent(g: GrammarEntity, onSpeak: (String) -> Unit) {
    val examples = g.examples()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        SectionCard {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(g.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    g.meaning,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TtsButton(examples.firstOrNull()?.ja ?: g.title, onSpeak)
            }
        }
        SectionCard(title = "接续方式") {
            Text(g.connection, style = MaterialTheme.typography.bodyLarge)
        }
        SectionCard(title = "说明") {
            Text(g.explanation, style = MaterialTheme.typography.bodyLarge)
        }
        SectionCard(title = "例句") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                examples.forEach { ex ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ex.ja, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                ex.zh,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TtsButton(ex.ja, onSpeak)
                    }
                }
            }
        }
    }
}

// ---------------- 语法详情（浏览 + 自由练习） ----------------

data class GrammarDetailUiState(
    val entity: GrammarEntity? = null,
    val exercise: Exercise? = null,
    val quiz: Quiz? = null,
    val selected: Int? = null,
    val finished: Boolean = false,
)

class GrammarDetailViewModel(private val app: AppContainer, private val grammarId: String) : ViewModel() {
    private val _state = MutableStateFlow(GrammarDetailUiState())
    val uiState = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val g = app.content.grammarById(grammarId)
            _state.update { it.copy(entity = g) }
        }
    }

    fun beginPractice() {
        val g = _state.value.entity ?: return
        val exercise = g.exercises().firstOrNull() ?: return
        _state.update {
            it.copy(exercise = exercise, quiz = QuizGenerator.grammarQuiz(exercise.question, exercise.options, exercise.answer))
        }
    }

    fun onSelect(index: Int) {
        if (_state.value.selected != null) return
        _state.update { it.copy(selected = index) }
    }

    fun rate(mastery: Mastery) {
        viewModelScope.launch {
            app.progress.applyReview("grammar", grammarId, mastery)
            _state.update { it.copy(finished = true) }
        }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun GrammarDetailScreen(nav: NavHostController, grammarId: String) {
    val app = LocalAppContainer.current
    val vm: GrammarDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "grammar_$grammarId") {
        GrammarDetailViewModel(app, grammarId)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar("语法详情") { nav.popBackStack() } },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val g = state.entity
            if (g == null) {
                com.japanlearn.app.ui.review.LoadingPlaceholder()
            } else {
                StaggerIn(0) { GrammarCardContent(g) { vm.speak(it) } }
                when {
                    state.quiz == null -> StaggerIn(1) {
                        AppButton("练一练") { vm.beginPractice() }
                    }
                    !state.finished -> {
                        state.quiz?.let { quiz ->
                            QuizView(quiz, state.selected, onSelect = { vm.onSelect(it) })
                            if (state.selected != null) {
                                val correct = state.selected == quiz.answerIndex
                                com.japanlearn.app.ui.review.FeedbackText(correct = correct, answerText = quiz.answerText)
                                state.exercise?.let {
                                    Text(
                                        it.explanation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "这条语法你掌握了吗？",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MasteryRow { vm.rate(it) }
                            }
                        }
                    }
                    else -> {
                        val jc = com.japanlearn.app.ui.theme.japanColors()
                        Text(
                            "已记录，稍后会安排复习。",
                            style = MaterialTheme.typography.titleSmall,
                            color = jc.correct,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ---------------- 今日语法学习会话 ----------------

data class GrammarSessionUiState(
    val phase: SessionPhase = SessionPhase.LOADING,
    val queue: List<GrammarEntity> = emptyList(),
    val index: Int = 0,
    val quiz: Quiz? = null,
    val exercise: Exercise? = null,
    val selected: Int? = null,
    val distinctLearned: Int = 0,
    val canReview: Boolean = false,
)

class GrammarSessionViewModel(
    private val app: AppContainer,
    requestedCount: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(GrammarSessionUiState())
    val uiState = _state.asStateFlow()

    private val learnedIds = mutableSetOf<String>()
    private var startedAt = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            val target = app.settings.dailyNewGrammar.value
            val today = app.stats.todayFlow().first()
            val todayRemaining = (target - (today?.newGrammar ?: 0)).coerceAtLeast(0)
            val effective = if (requestedCount <= 0) todayRemaining else requestedCount.coerceAtMost(todayRemaining)
            if (effective <= 0) {
                _state.update { it.copy(phase = SessionPhase.DONE) }
                refreshNextSteps()
                return@launch
            }
            var items = app.content.nextNewGrammar(effective)
            var retries = 0
            while (items.isEmpty() && retries < 20) {
                kotlinx.coroutines.delay(300)
                retries++
                items = app.content.nextNewGrammar(effective)
            }
            startedAt = System.currentTimeMillis()
            _state.update { it.copy(phase = SessionPhase.CARD, queue = items, index = 0) }
        }
    }

    val current: GrammarEntity? get() = _state.value.queue.getOrNull(_state.value.index)

    fun beginQuiz() {
        val g = current ?: return
        val exercise = g.exercises().firstOrNull()
        if (exercise == null) {
            // 无练习题时直接自评
            _state.update { it.copy(phase = SessionPhase.QUIZ, exercise = null, quiz = null, selected = null) }
            return
        }
        _state.update {
            it.copy(
                phase = SessionPhase.QUIZ,
                exercise = exercise,
                quiz = QuizGenerator.grammarQuiz(exercise.question, exercise.options, exercise.answer),
                selected = null,
            )
        }
    }

    fun onSelect(index: Int) {
        if (_state.value.selected != null) return
        _state.update { it.copy(selected = index) }
    }

    fun rate(mastery: Mastery) {
        val g = current ?: return
        viewModelScope.launch {
            app.progress.applyReview("grammar", g.id, mastery)
            // 每题实时落库：首次评级计入当日新学语法
            val isFirst = learnedIds.add(g.id)
            if (isFirst) app.stats.addStudy(0, newGrammar = 1)
            _state.update { s ->
                val newQueue = if (mastery == Mastery.UNKNOWN) s.queue + g else s.queue
                val nextIndex = s.index + 1
                if (nextIndex >= newQueue.size) {
                    s.copy(
                        queue = newQueue,
                        index = nextIndex,
                        phase = SessionPhase.DONE,
                        distinctLearned = learnedIds.size,
                    )
                } else {
                    s.copy(queue = newQueue, index = nextIndex, phase = SessionPhase.CARD, quiz = null, selected = null)
                }
            }
            if (_state.value.phase == SessionPhase.DONE) {
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                app.stats.addStudy(seconds.coerceAtLeast(1))
                refreshNextSteps()
            }
        }
    }

    private suspend fun refreshNextSteps() {
        val due = app.progress.dueWordCount().first() + app.progress.dueGrammarCount().first()
        _state.update { it.copy(canReview = due > 0) }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun GrammarSessionScreen(nav: NavHostController, count: Int) {
    val app = LocalAppContainer.current
    val vm: GrammarSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "grammarSession_$count") {
        GrammarSessionViewModel(app, count)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val reduceMotion = rememberReducedMotion()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = if (state.phase == SessionPhase.DONE) "学习完成" else "学语法 ${state.index + 1}/${state.queue.size}",
                ) { nav.popBackStack() }
            },
        ) { padding ->
            AnimatedContent(
                targetState = state.phase,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                    } else {
                        (slideInVertically(tween(360, easing = MotionTokens.EmphasizedDecelerate)) { it / 8 } + fadeIn(tween(240))) togetherWith
                            (slideOutVertically(tween(240)) { -it / 10 } + fadeOut(tween(160)))
                    }
                },
                label = "grammarPhase",
                modifier = Modifier.padding(padding),
            ) { phase ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (phase) {
                        SessionPhase.LOADING -> com.japanlearn.app.ui.review.LoadingPlaceholder()

                        SessionPhase.CARD -> {
                            val g = vm.current ?: return@AnimatedContent
                            GrammarCardContent(g) { vm.speak(it) }
                            AppButton("开始练习") { vm.beginQuiz() }
                        }

                        SessionPhase.QUIZ -> {
                            val quiz = state.quiz
                            if (quiz == null) {
                                Text("读一读例句，然后自评掌握程度。", style = MaterialTheme.typography.bodyLarge)
                            } else {
                                QuizView(quiz, state.selected, onSelect = { vm.onSelect(it) })
                                if (state.selected != null) {
                                    val correct = state.selected == quiz.answerIndex
                                    com.japanlearn.app.ui.review.FeedbackText(correct = correct, answerText = quiz.answerText)
                                    state.exercise?.let {
                                        Text(
                                            it.explanation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Text(
                                "这条语法你掌握了吗？",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MasteryRow { vm.rate(it) }
                        }

                        SessionPhase.DONE -> {
                            StaggerIn(0) {
                                SectionCard {
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text("语法学习完成", style = MaterialTheme.typography.headlineSmall)
                                        Text("学会 ${state.distinctLearned} 条语法", style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                            if (state.canReview) {
                                StaggerIn(1) {
                                    AppButton("继续：开始复习") { nav.navigate(Routes.REVIEW_SESSION) }
                                }
                            }
                            StaggerIn(2) {
                                AppButton("返回") { nav.popBackStack() }
                            }
                        }
                    }
                }
            }
        }
        ConfettiBurst(trigger = if (state.phase == SessionPhase.DONE) 1 else 0)
    }
}
