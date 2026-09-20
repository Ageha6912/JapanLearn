package com.japanlearn.app.ui.kanji

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.japanlearn.app.data.content.KanjiExample
import com.japanlearn.app.data.examples
import com.japanlearn.app.data.kunReadings
import com.japanlearn.app.data.local.KanjiEntity
import com.japanlearn.app.data.onReadings
import com.japanlearn.app.domain.KanjiQuizGenerator
import com.japanlearn.app.domain.KanjiQuizVariant
import com.japanlearn.app.domain.KanjiQuizVariantPicker
import com.japanlearn.app.domain.Mastery
import com.japanlearn.app.domain.Quiz
import com.japanlearn.app.domain.QuizKanji
import com.japanlearn.app.domain.StudyPlanner
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.MasteryRow
import com.japanlearn.app.ui.components.QuizView
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.components.SessionPhase
import com.japanlearn.app.ui.components.TtsButton
import com.japanlearn.app.ui.motion.ConfettiBurst
import com.japanlearn.app.ui.motion.MotionTokens
import com.japanlearn.app.ui.motion.rememberReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KanjiSessionUiState(
    val phase: SessionPhase = SessionPhase.LOADING,
    val queue: List<KanjiEntity> = emptyList(),
    val index: Int = 0,
    val quiz: Quiz? = null,
    val selected: Int? = null,
    val distinctLearned: Int = 0,
    val quizCorrect: Int = 0,
    val quizTotal: Int = 0,
    val canReview: Boolean = false,
    val dailyTarget: Int = StudyPlanner.KANJI_DAILY_DEFAULT,
    val todayLearned: Int = 0,
)

/**
 * 汉字专项会话（PRD §19.14）：卡片学 → 即时练 → 自评进 FSRS。
 * 新学配额按目标档位折算，当日已学计入后从剩余中扣。
 */
class KanjiSessionViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(KanjiSessionUiState())
    val uiState = _state.asStateFlow()

    private var pool: List<QuizKanji> = emptyList()
    private val learnedIds = mutableSetOf<String>()
    private var startedAt = System.currentTimeMillis()
    private var variantToggle = 0

    init {
        viewModelScope.launch {
            val hasGoal = app.settings.goalLevel.value != StudyPlanner.LEVEL_NONE
            val target = StudyPlanner.kanjiDailyCount(app.settings.dailyNewWords.value, hasGoal)
            val todayLearned = app.progress.newKanjiLearnedToday()
            val remaining = (target - todayLearned).coerceAtLeast(0)
            _state.update { it.copy(dailyTarget = target, todayLearned = todayLearned) }
            if (remaining <= 0) {
                _state.update { it.copy(phase = SessionPhase.DONE) }
                refreshCanReview()
                return@launch
            }
            val level = app.settings.studyLevel.value.ifEmpty { "N5" }
            var kanji = app.content.nextNewKanji(remaining, level)
            var retries = 0
            while (kanji.isEmpty() && retries < 20) {
                kotlinx.coroutines.delay(300)
                retries++
                kanji = app.content.nextNewKanji(remaining, level)
            }
            if (kanji.isEmpty()) {
                _state.update { it.copy(phase = SessionPhase.DONE) }
                refreshCanReview()
                return@launch
            }
            pool = app.content.kanjiAll().first().map { it.toQuizKanji() }
            startedAt = System.currentTimeMillis()
            _state.update { it.copy(phase = SessionPhase.CARD, queue = kanji, index = 0) }
        }
    }

    val current: KanjiEntity? get() = _state.value.queue.getOrNull(_state.value.index)

    fun beginQuiz() {
        val entity = current ?: return
        val target = entity.toQuizKanji()
        val canAudio = target.primaryReading().isNotEmpty()
        // 五种题型轮换，避免连续同型
        val rolls = listOf(0.05, 0.25, 0.45, 0.65, 0.85)
        var variant = KanjiQuizVariantPicker.pick(rolls[variantToggle % rolls.size], canAudio)
        if (!canAudio && variant == KanjiQuizVariant.AUDIO) variant = KanjiQuizVariant.TO_ZH
        variantToggle++
        val quiz = KanjiQuizGenerator.build(target, pool, variant)
        _state.update { it.copy(phase = SessionPhase.QUIZ, quiz = quiz, selected = null) }
    }

    fun onSelect(index: Int) {
        val s = _state.value
        val quiz = s.quiz ?: return
        if (s.selected != null) return
        val correct = quiz.answerIndex == index
        _state.update {
            it.copy(
                selected = index,
                quizTotal = it.quizTotal + 1,
                quizCorrect = it.quizCorrect + if (correct) 1 else 0,
            )
        }
    }

    fun rate(mastery: Mastery) {
        val entity = current ?: return
        viewModelScope.launch {
            // 首次评级不写 daily_study.newWords（会抢词额度）；汉字新学用 progress.learnedAt 统计
            app.progress.applyReview("kanji", entity.id, mastery)
            learnedIds.add(entity.id)
            _state.update { s ->
                val newQueue = if (mastery == Mastery.UNKNOWN) s.queue + entity else s.queue
                val nextIndex = s.index + 1
                if (nextIndex >= newQueue.size) {
                    s.copy(
                        queue = newQueue,
                        index = nextIndex,
                        phase = SessionPhase.DONE,
                        distinctLearned = learnedIds.size,
                    )
                } else {
                    s.copy(
                        queue = newQueue, index = nextIndex, phase = SessionPhase.CARD,
                        quiz = null, selected = null,
                    )
                }
            }
            if (_state.value.phase == SessionPhase.DONE) {
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                app.stats.addStudy(seconds.coerceAtLeast(1))
                refreshCanReview()
            }
        }
    }

    private suspend fun refreshCanReview() {
        val due = app.progress.dueKanjiCount().first()
        _state.update { it.copy(canReview = due > 0) }
    }

    fun speak(text: String) = app.tts.speak(text)
}

internal fun KanjiEntity.toQuizKanji() = QuizKanji(
    id = id,
    char = char,
    zh = zh,
    on = onReadings(),
    kun = kunReadings(),
)

@Composable
fun KanjiSessionScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: KanjiSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "kanjiSession") {
        KanjiSessionViewModel(app)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val reduceMotion = rememberReducedMotion()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = if (state.phase == SessionPhase.DONE) "汉字学习完成"
                    else "学习汉字 ${state.index + 1}/${state.queue.size}",
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
                label = "kanjiPhase",
                modifier = Modifier.padding(padding),
            ) { phase ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (phase) {
                        SessionPhase.LOADING -> com.japanlearn.app.ui.review.LoadingPlaceholder()

                        SessionPhase.CARD -> {
                            val entity = vm.current ?: return@AnimatedContent
                            KanjiCard(entity, onSpeak = { vm.speak(it) }, onPractice = { vm.beginQuiz() })
                        }

                        SessionPhase.QUIZ -> {
                            state.quiz?.let { quiz ->
                                QuizView(
                                    quiz, state.selected, onSelect = { vm.onSelect(it) },
                                    onSpeak = { vm.speak(it) },
                                )
                                if (state.selected != null) {
                                    val correct = state.selected == quiz.answerIndex
                                    com.japanlearn.app.ui.review.FeedbackText(
                                        correct = correct,
                                        answerText = quiz.answerText,
                                    )
                                    Text(
                                        "这个汉字你掌握了吗？",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    MasteryRow { vm.rate(it) }
                                }
                            }
                        }

                        SessionPhase.DONE -> {
                            ConfettiBurst(trigger = if (state.distinctLearned > 0) 1 else 0)
                            SectionCard(title = "本轮完成") {
                                Text(
                                    "新学 ${state.distinctLearned} 字 · 练习正确 ${state.quizCorrect}/${state.quizTotal}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "今日配额 ${state.dailyTarget} · 会话前已学 ${state.todayLearned}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                AppButton("返回学习") { nav.popBackStack() }
                                if (state.canReview) {
                                    AppButton("去复习到期汉字") {
                                        nav.navigate(Routes.REVIEW_SESSION)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KanjiCard(entity: KanjiEntity, onSpeak: (String) -> Unit, onPractice: () -> Unit) {
    val reduceMotion = rememberReducedMotion()
    val on = entity.onReadings()
    val kun = entity.kunReadings()
    val examples: List<KanjiExample> = entity.examples()
    AnimatedContent(
        targetState = entity.id,
        transitionSpec = {
            if (reduceMotion) {
                fadeIn(tween(150)) togetherWith fadeOut(tween(100))
            } else {
                (slideInHorizontally(tween(360, easing = MotionTokens.EmphasizedDecelerate)) { it / 5 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(240)) { -it / 6 } + fadeOut(tween(160)))
            }
        },
        label = "kanjiCard",
    ) { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            SectionCard {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(entity.char, style = MaterialTheme.typography.displayLarge)
                    if (on.isNotEmpty()) {
                        Text(
                            "音读 ${on.joinToString("・")}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (kun.isNotEmpty()) {
                        Text(
                            "训读 ${kun.joinToString("・")}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(entity.zh, style = MaterialTheme.typography.headlineSmall)
                    val speakText = kun.firstOrNull() ?: on.firstOrNull()
                    if (!speakText.isNullOrBlank()) {
                        TtsButton(speakText, onSpeak)
                    }
                }
            }
            if (examples.isNotEmpty()) {
                SectionCard(title = "词例") {
                    examples.forEach { ex ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text("${ex.ja}（${ex.kana}）", style = MaterialTheme.typography.titleMedium)
                            Text(
                                ex.zh,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val exSpeak = examples.firstOrNull()?.ja
                    if (exSpeak != null) {
                        Spacer(Modifier.height(4.dp))
                        TtsButton(exSpeak, onSpeak)
                    }
                }
            }
            AppButton("开始练习", onClick = onPractice)
        }
    }
}
