package com.japanlearn.app.ui.words

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import com.japanlearn.app.data.local.WordEntity
import com.japanlearn.app.domain.AudioQuizPolicy
import com.japanlearn.app.domain.Mastery
import com.japanlearn.app.domain.Quiz
import com.japanlearn.app.domain.QuizGenerator
import com.japanlearn.app.domain.QuizWord
import com.japanlearn.app.domain.WordQuizDirection
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.LevelSwitchRow
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

// ---------------- 单词列表 ----------------

@Composable
fun WordListScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val wordsAll by app.content.wordsAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val masteryMap by app.progress.wordMasteryMap().collectAsStateWithLifecycle(initialValue = emptyMap())
    val level by app.settings.studyLevel.collectAsStateWithLifecycle()
    val dailyTarget by app.settings.dailyNewWords.collectAsStateWithLifecycle()
    val words = wordsAll.filter { it.level == level }
    val learned = words.count { it.id in masteryMap }

    Scaffold(
        topBar = { AppTopBar("单词") { nav.popBackStack() } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            item {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LevelSwitchRow(selected = level, onSelect = { app.settings.setStudyLevel(it) })
                    Text(
                        "$level 已学 $learned / ${words.size} 个",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppButton("开始学习新词（$dailyTarget 个）") {
                        nav.navigate(Routes.wordSession(dailyTarget))
                    }
                }
            }
            val grouped = words.groupBy { it.cat }
            grouped.forEach { (cat, list) ->
                item(key = "header_$cat") {
                    Text(
                        cat,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                    )
                }
                items(list, key = { it.id }) { w ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            MasteryDot(masteryMap[w.id])
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(w.ja, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    w.kana,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(w.zh, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    w.pos,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

// ---------------- 新词学习会话（PRD §7.4 学习流程） ----------------

data class WordSessionUiState(
    val phase: SessionPhase = SessionPhase.LOADING,
    val queue: List<WordEntity> = emptyList(),
    val index: Int = 0,
    val quiz: Quiz? = null,
    val selected: Int? = null,
    val distinctLearned: Int = 0,
    val quizCorrect: Int = 0,
    val quizTotal: Int = 0,
    val canLearnGrammar: Boolean = false,
    val canReview: Boolean = false,
)

class WordSessionViewModel(
    private val app: AppContainer,
    requestedCount: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(WordSessionUiState())
    val uiState = _state.asStateFlow()

    private var pool: List<QuizWord> = emptyList()
    private val learnedIds = mutableSetOf<String>()
    private var startedAt = System.currentTimeMillis()
    private var directionToggle = false

    init {
        viewModelScope.launch {
            val target = app.settings.dailyNewWords.value
            val today = app.stats.todayFlow().first()
            val todayRemaining = (target - (today?.newWords ?: 0)).coerceAtLeast(0)
            val effective = requestedCount.coerceAtMost(todayRemaining)
            if (effective <= 0) {
                _state.update { it.copy(phase = SessionPhase.DONE) }
                refreshNextSteps()
                return@launch
            }
            // 等待首次启动的内容装载完成（新词队列按当前学习级别取）
            val level = app.settings.studyLevel.value
            var words = app.content.nextNewWords(effective, level)
            var retries = 0
            while (words.isEmpty() && retries < 20) {
                kotlinx.coroutines.delay(300)
                retries++
                words = app.content.nextNewWords(effective, level)
            }
            pool = app.content.wordsAll().first().map { QuizWord(it.id, it.ja, it.kana, it.zh) }
            startedAt = System.currentTimeMillis()
            _state.update {
                it.copy(phase = SessionPhase.CARD, queue = words, index = 0)
            }
        }
    }

    val current: WordEntity? get() = _state.value.queue.getOrNull(_state.value.index)

    fun showCardTts(onSpeak: (String) -> Unit) {
        current?.let { onSpeak(it.ja) }
    }

    fun beginQuiz() {
        val word = current ?: return
        directionToggle = !directionToggle
        val direction = if (directionToggle) WordQuizDirection.JP_TO_CN else WordQuizDirection.CN_TO_JP
        val audio = AudioQuizPolicy.shouldUseAudio(direction, kotlin.random.Random.nextDouble())
        _state.update {
            it.copy(
                phase = SessionPhase.QUIZ,
                quiz = QuizGenerator.wordQuiz(QuizWord(word.id, word.ja, word.kana, word.zh), pool, direction, audio = audio),
                selected = null,
            )
        }
    }

    fun onSelect(index: Int) {
        val s = _state.value
        if (s.selected != null) return
        val correct = s.quiz?.answerIndex == index
        _state.update {
            it.copy(
                selected = index,
                quizTotal = it.quizTotal + 1,
                quizCorrect = it.quizCorrect + if (correct) 1 else 0,
            )
        }
    }

    fun rate(mastery: Mastery) {
        val word = current ?: return
        viewModelScope.launch {
            app.progress.applyReview("word", word.id, mastery)
            // 每题实时落库：首次评级计入当日新词，中断会话也能保留进度（PRD §15 原则 4）
            val isFirst = learnedIds.add(word.id)
            if (isFirst) app.stats.addStudy(0, newWords = 1)
            _state.update { s ->
                val newQueue = if (mastery == Mastery.UNKNOWN) s.queue + word else s.queue
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
        val grammarPool = app.content.nextNewGrammar(1, app.settings.studyLevel.value)
        val grammarTodayRemaining = (app.settings.dailyNewGrammar.value - (app.stats.todayFlow().first()?.newGrammar ?: 0))
        val due = app.progress.dueWordCount().first() + app.progress.dueGrammarCount().first()
        _state.update {
            it.copy(
                canLearnGrammar = grammarPool.isNotEmpty() && grammarTodayRemaining > 0,
                canReview = due > 0,
            )
        }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun WordSessionScreen(nav: NavHostController, count: Int) {
    val app = LocalAppContainer.current
    val vm: WordSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "wordSession_$count") {
        WordSessionViewModel(app, count)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val reduceMotion = rememberReducedMotion()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = if (state.phase == SessionPhase.DONE) "学习完成" else "学习新词 ${state.index + 1}/${state.queue.size}",
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
                label = "wordPhase",
                modifier = Modifier.padding(padding),
            ) { phase ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (phase) {
                        SessionPhase.LOADING -> com.japanlearn.app.ui.review.LoadingPlaceholder()

                        SessionPhase.CARD -> {
                            val word = vm.current ?: return@AnimatedContent
                            WordCard(word, onSpeak = { vm.speak(it) }, onPractice = { vm.beginQuiz() })
                        }

                        SessionPhase.QUIZ -> {
                            state.quiz?.let { quiz ->
                                QuizView(quiz, state.selected, onSelect = { vm.onSelect(it) }, onSpeak = { vm.speak(it) })
                                if (state.selected != null) {
                                    val correct = state.selected == quiz.answerIndex
                                    com.japanlearn.app.ui.review.FeedbackText(correct = correct, answerText = quiz.answerText)
                                    Text(
                                        "这个单词你掌握了吗？",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    MasteryRow { vm.rate(it) }
                                }
                            }
                        }

                        SessionPhase.DONE -> {
                            StaggerIn(0) {
                                SectionCard {
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text("本轮学习完成", style = MaterialTheme.typography.headlineSmall)
                                        Text("学会 ${state.distinctLearned} 个新词", style = MaterialTheme.typography.bodyLarge)
                                        if (state.quizTotal > 0) {
                                            Text(
                                                "练习正确率 ${state.quizCorrect}/${state.quizTotal}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.canLearnGrammar) {
                                StaggerIn(1) {
                                    AppButton("继续：学语法") { nav.navigate(Routes.grammarSession(0)) }
                                }
                            }
                            if (state.canReview) {
                                StaggerIn(2) {
                                    AppButton("继续：开始复习") { nav.navigate(Routes.REVIEW_SESSION) }
                                }
                            }
                            StaggerIn(3) {
                                AppButton("返回首页", enabled = true, onClick = { nav.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
        ConfettiBurst(trigger = if (state.phase == SessionPhase.DONE) 1 else 0)
    }
}

/** 单词学习卡：大字展示 + 例句；换词时横向滑动过渡。 */
@Composable
private fun WordCard(word: WordEntity, onSpeak: (String) -> Unit, onPractice: () -> Unit) {
    val reduceMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = word.id,
        transitionSpec = {
            if (reduceMotion) {
                fadeIn(tween(150)) togetherWith fadeOut(tween(100))
            } else {
                (slideInHorizontally(tween(360, easing = MotionTokens.EmphasizedDecelerate)) { it / 5 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(240)) { -it / 6 } + fadeOut(tween(160)))
            }
        },
        label = "wordCard",
    ) { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            SectionCard {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(word.ja, style = MaterialTheme.typography.displaySmall)
                    Text(
                        word.kana,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            word.pos,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Text(word.zh, style = MaterialTheme.typography.headlineSmall)
                    TtsButton(word.ja, onSpeak)
                }
            }
            SectionCard(title = "例句") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(word.example, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            word.exampleZh,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TtsButton(word.example, onSpeak)
                }
            }
            AppButton("开始练习", onClick = onPractice)
        }
    }
}

/** 单词掌握度色点：未学=空心，不认识=灰，模糊=黄，熟悉=抹茶，熟练=藍。 */
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
