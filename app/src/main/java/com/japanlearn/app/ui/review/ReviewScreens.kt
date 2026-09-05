package com.japanlearn.app.ui.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import com.japanlearn.app.data.examples
import com.japanlearn.app.data.exercises
import com.japanlearn.app.data.local.GrammarEntity
import com.japanlearn.app.data.local.WordEntity
import com.japanlearn.app.domain.Mastery
import com.japanlearn.app.domain.Quiz
import com.japanlearn.app.domain.QuizGenerator
import com.japanlearn.app.domain.QuizWord
import com.japanlearn.app.domain.ReviewPlanner
import com.japanlearn.app.domain.WordQuizDirection
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.EmptyState
import com.japanlearn.app.ui.components.MasteryRow
import com.japanlearn.app.ui.components.QuizView
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.components.SessionPhase
import com.japanlearn.app.ui.components.TaskRow
import com.japanlearn.app.ui.motion.AnimatedCounterText
import com.japanlearn.app.ui.motion.ConfettiBurst
import com.japanlearn.app.ui.motion.MotionTokens
import com.japanlearn.app.ui.motion.StaggerIn
import com.japanlearn.app.ui.motion.rememberReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------- 复习首页（Tab） ----------------

data class ReviewHomeUiState(
    val dueWords: Int = 0,
    val dueGrammar: Int = 0,
    val reviewsDoneToday: Int = 0,
    val dailyCap: Int = 30,
    val wrongCount: Int = 0,
)

class ReviewHomeViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ReviewHomeUiState())
    val uiState = _state.asStateFlow()

    init {
        fun <T> collect(flow: kotlinx.coroutines.flow.Flow<T>, reducer: (ReviewHomeUiState, T) -> ReviewHomeUiState) {
            viewModelScope.launch { flow.collect { v -> _state.update { cur -> reducer(cur, v) } } }
        }
        collect(app.progress.dueWordCount()) { s, v -> s.copy(dueWords = v) }
        collect(app.progress.dueGrammarCount()) { s, v -> s.copy(dueGrammar = v) }
        collect(app.progress.wrongAnswerCount()) { s, v -> s.copy(wrongCount = v) }
        collect(app.settings.dailyReviewCap) { s, v -> s.copy(dailyCap = v) }
        collect(app.progress.reviewsDoneTodayFlow()) { s, v -> s.copy(reviewsDoneToday = v) }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun ReviewHomeScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: ReviewHomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel { ReviewHomeViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val remaining = ReviewPlanner.remainingToday(state.reviewsDoneToday, state.dailyCap)
    val dueTotal = state.dueWords + state.dueGrammar

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            StaggerIn(0) {
                Text("复习", style = MaterialTheme.typography.headlineMedium)
            }

            StaggerIn(1) {
                SectionCard(title = "今日复习") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AnimatedCounterText(
                            value = dueTotal,
                            style = MaterialTheme.typography.displaySmall,
                            color = if (dueTotal > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            "条待复习",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    TaskRow("单词", state.dueWords, "条", Icons.AutoMirrored.Filled.MenuBook)
                    TaskRow("语法", state.dueGrammar, "条", Icons.Outlined.EditNote, tint = MaterialTheme.colorScheme.tertiary)
                    TaskRow("今日已复习", state.reviewsDoneToday, "条", Icons.Filled.Refresh, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppButton(
                        text = when {
                            dueTotal == 0 -> "今日复习已完成"
                            remaining == 0 -> "今日额度已用完，明天继续"
                            else -> "开始复习（$remaining 条）"
                        },
                        enabled = dueTotal > 0 && remaining > 0,
                        onClick = { nav.navigate(Routes.REVIEW_SESSION) },
                    )
                }
            }

            StaggerIn(2) {
                SectionCard(title = "错题本", onClick = { nav.navigate(Routes.WRONG_ANSWERS) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                            Box(Modifier.padding(8.dp)) {
                                Icon(
                                    Icons.Filled.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${state.wrongCount} 条待攻克", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "复习中答对后自动移除",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { nav.navigate(Routes.WRONG_ANSWERS) }) {
                            Text("查看")
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---------------- 复习会话（SRS 到期项，混合单词与语法） ----------------

sealed interface ReviewItem {
    data class WordItem(val word: WordEntity) : ReviewItem
    data class GrammarItem(val grammar: GrammarEntity) : ReviewItem
}

data class ReviewSessionUiState(
    val phase: SessionPhase = SessionPhase.LOADING,
    val items: List<ReviewItem> = emptyList(),
    val index: Int = 0,
    val quiz: Quiz? = null,
    val selected: Int? = null,
    val ratedCount: Int = 0,
    val correctCount: Int = 0,
)

class ReviewSessionViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ReviewSessionUiState())
    val uiState = _state.asStateFlow()

    private var startedAt = System.currentTimeMillis()
    private var directionToggle = false

    init {
        viewModelScope.launch {
            val cap = app.settings.dailyReviewCap.value
            val done = app.progress.reviewsDoneToday()
            val remaining = ReviewPlanner.remainingToday(done, cap)
            listPool = app.content.wordsAll().first().map { QuizWord(it.id, it.ja, it.kana, it.zh) }
            if (remaining == 0) {
                _state.update { it.copy(phase = SessionPhase.DONE) }
                return@launch
            }
            val words = app.progress.dueWords(remaining)
            val grammar = app.progress.dueGrammar(remaining - words.size)
            startedAt = System.currentTimeMillis()
            val items = words.map { ReviewItem.WordItem(it) } + grammar.map { ReviewItem.GrammarItem(it) }
            _state.update {
                it.copy(
                    phase = if (items.isEmpty()) SessionPhase.DONE else SessionPhase.QUIZ,
                    items = items,
                    quiz = items.firstOrNull()?.let { item -> makeQuiz(item) },
                )
            }
        }
    }

    private fun makeQuiz(item: ReviewItem): Quiz = when (item) {
        is ReviewItem.WordItem -> {
            directionToggle = !directionToggle
            val direction = if (directionToggle) WordQuizDirection.JP_TO_CN else WordQuizDirection.CN_TO_JP
            QuizGenerator.wordQuiz(
                QuizWord(item.word.id, item.word.ja, item.word.kana, item.word.zh),
                poolOf(item.word.id),
                direction,
            )
        }
        is ReviewItem.GrammarItem -> {
            val ex = item.grammar.exercises().first()
            QuizGenerator.grammarQuiz(ex.question, ex.options, ex.answer)
        }
    }

    private fun poolOf(excludeId: String): List<QuizWord> {
        val s = _state.value
        return s.items.mapNotNull { item ->
            when (item) {
                is ReviewItem.WordItem -> QuizWord(item.word.id, item.word.ja, item.word.kana, item.word.zh)
                is ReviewItem.GrammarItem -> null
            }
        } + listPool.filter { it.id != excludeId }
    }

    private var listPool: List<QuizWord> = emptyList()

    val current: ReviewItem? get() = _state.value.items.getOrNull(_state.value.index)

    fun onSelect(index: Int) {
        val s = _state.value
        if (s.selected != null) return
        val correct = s.quiz?.answerIndex == index
        _state.update {
            it.copy(
                selected = index,
                ratedCount = it.ratedCount + 1,
                correctCount = it.correctCount + if (correct) 1 else 0,
            )
        }
    }

    fun rate(mastery: Mastery) {
        val item = current ?: return
        viewModelScope.launch {
            when (item) {
                is ReviewItem.WordItem -> app.progress.applyReview("word", item.word.id, mastery)
                is ReviewItem.GrammarItem -> app.progress.applyReview("grammar", item.grammar.id, mastery)
            }
            app.stats.addStudy(0, reviewsDone = 1)
            _state.update { s ->
                val newItems = if (mastery == Mastery.UNKNOWN) s.items + item else s.items
                val nextIndex = s.index + 1
                if (nextIndex >= newItems.size) {
                    s.copy(items = newItems, index = nextIndex, phase = SessionPhase.DONE)
                } else {
                    s.copy(items = newItems, index = nextIndex, quiz = makeQuiz(newItems[nextIndex]), selected = null)
                }
            }
            if (_state.value.phase == SessionPhase.DONE) {
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                app.stats.addStudy(seconds.coerceAtLeast(1))
            }
        }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun ReviewSessionScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: ReviewSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel { ReviewSessionViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val reduceMotion = rememberReducedMotion()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = if (state.phase == SessionPhase.DONE) "复习完成" else "复习 ${state.index + 1}/${state.items.size}",
                ) { nav.popBackStack() }
            },
        ) { padding ->
            AnimatedContent(
                targetState = state.phase,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(androidx.compose.animation.core.tween(150)) togetherWith fadeOut(androidx.compose.animation.core.tween(100))
                    } else {
                        (slideInVertically(androidx.compose.animation.core.tween(360, easing = MotionTokens.EmphasizedDecelerate)) { it / 8 } + fadeIn(androidx.compose.animation.core.tween(240))) togetherWith
                            (slideOutVertically(androidx.compose.animation.core.tween(240)) { -it / 10 } + fadeOut(androidx.compose.animation.core.tween(160)))
                    }
                },
                label = "reviewPhase",
                modifier = Modifier.padding(padding),
            ) { phase ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (phase) {
                        SessionPhase.LOADING -> LoadingPlaceholder()

                        SessionPhase.CARD -> LoadingPlaceholder()

                        SessionPhase.QUIZ -> {
                            val quiz = state.quiz
                            if (quiz == null) {
                                LoadingPlaceholder()
                            } else {
                                QuizView(quiz, state.selected, onSelect = { vm.onSelect(it) })
                                if (state.selected != null) {
                                    val correct = state.selected == quiz.answerIndex
                                    FeedbackText(correct = correct, answerText = quiz.answerText)
                                    Text(
                                        "现在感觉掌握了吗？",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    MasteryRow { vm.rate(it) }
                                }
                            }
                        }

                        SessionPhase.DONE -> {
                            SectionCard {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text("复习完成", style = MaterialTheme.typography.headlineSmall)
                                    if (state.ratedCount > 0) {
                                        Text(
                                            "共复习 ${state.ratedCount} 项 · 正确率 ${state.correctCount}/${state.ratedCount}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Text("今天没有待复习的内容", style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                            AppButton("返回") { nav.popBackStack() }
                        }
                    }
                }
            }
        }
        ConfettiBurst(trigger = if (state.phase == SessionPhase.DONE) 1 else 0)
    }
}

@Composable
internal fun FeedbackText(correct: Boolean, answerText: String) {
    val jc = com.japanlearn.app.ui.theme.japanColors()
    Text(
        if (correct) "答对了" else "正确答案：$answerText",
        style = MaterialTheme.typography.titleMedium,
        color = if (correct) jc.correct else jc.wrong,
    )
}

@Composable
internal fun LoadingPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        com.japanlearn.app.ui.components.SkeletonBlock(Modifier.fillMaxWidth().height(28.dp))
        repeat(4) {
            com.japanlearn.app.ui.components.SkeletonBlock(
                Modifier.fillMaxWidth().height(52.dp),
                radius = 14,
            )
        }
    }
}

// ---------------- 错题本 ----------------

data class WrongDisplay(val type: String, val id: String, val primary: String, val secondary: String, val count: Int)

class WrongAnswersViewModel(private val app: AppContainer) : ViewModel() {
    val items = combine(
        app.progress.wrongAnswers(),
        app.content.wordsAll(),
        app.content.grammarAll(),
        app.content.kanaAll(),
    ) { wrong, words, grammar, kana ->
        wrong.mapNotNull { w ->
            when (w.contentType) {
                "word" -> words.find { it.id == w.contentId }?.let {
                    WrongDisplay("word", it.id, it.ja, it.zh, w.wrongCount)
                }
                "grammar" -> grammar.find { it.id == w.contentId }?.let {
                    WrongDisplay("grammar", it.id, it.title, it.meaning, w.wrongCount)
                }
                "kana" -> kana.find { it.id == w.contentId }?.let {
                    WrongDisplay("kana", it.id, it.hiragana, "读作 ${it.romaji}", w.wrongCount)
                }
                else -> null
            }
        }
    }
}

@Composable
fun WrongAnswersScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: WrongAnswersViewModel = androidx.lifecycle.viewmodel.compose.viewModel { WrongAnswersViewModel(app) }
    val items by vm.items.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = { AppTopBar("错题本") { nav.popBackStack() } },
    ) { padding ->
        if (items.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "错题本是空的",
                    body = "练习或复习中答错的题目会出现在这里，答对后自动移除。",
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    Text(
                        "在复习或练习中答对后，对应条目会自动移除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                items(items, key = { it.type + it.id }) { w ->
                    StaggerIn(0) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(w.primary, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        w.secondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Text(
                                        "错 ${w.count} 次",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}
