package com.japanlearn.app.ui.listening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.japanlearn.app.AppContainer
import com.japanlearn.app.LocalAppContainer
import com.japanlearn.app.domain.ListeningQuestion
import com.japanlearn.app.domain.ListeningQuizGenerator
import com.japanlearn.app.domain.ListeningSentence
import com.japanlearn.app.domain.QuizWord
import com.japanlearn.app.domain.TypeAnswerNormalizer
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.QuizView
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.VoiceGuideDialog
import com.japanlearn.app.ui.motion.ConfettiBurst
import com.japanlearn.app.ui.motion.FeedbackReveal
import com.japanlearn.app.ui.motion.StaggerIn
import com.japanlearn.app.ui.review.FeedbackText
import com.japanlearn.app.ui.review.LoadingPlaceholder
import com.japanlearn.app.util.JapaneseTts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ListeningPhase { LOADING, PICK, QUIZ, DONE }

data class ListeningSessionUiState(
    val phase: ListeningPhase = ListeningPhase.LOADING,
    val questions: List<ListeningQuestion> = emptyList(),
    val index: Int = 0,
    val selected: Int? = null,
    val typedDraft: String = "",
    val typedResult: Boolean? = null,
    val quizCorrect: Int = 0,
    val quizTotal: Int = 0,
    /** 本题对错，下一题时落错题本。 */
    val lastCorrect: Boolean? = null,
    val requestedCount: Int = 10,
)

class ListeningSessionViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ListeningSessionUiState())
    val uiState = _state.asStateFlow()

    private var startedAt = 0L

    init {
        viewModelScope.launch {
            // 等待首次启动的内容装载完成（与单词会话相同的等待模式）
            var words: List<QuizWord>? = null
            var retries = 0
            while (words == null && retries < 20) {
                val all = app.content.wordsAll().first()
                if (all.isNotEmpty()) {
                    words = all.map { it.toQuizWord() }
                } else {
                    delay(300)
                    retries++
                }
            }
            _state.update { it.copy(phase = ListeningPhase.PICK) }
        }
    }

    val current: ListeningQuestion? get() = _state.value.questions.getOrNull(_state.value.index)

    fun start(count: Int) {
        viewModelScope.launch {
            val level = app.settings.studyLevel.value
            val wordPool = app.content.wordsAll().first()
                .filter { it.level == level }
                .map { it.toQuizWord() }
            val learnedIds = app.progress.learnedIds("word").first()
            val sentences = app.content.sentencesAll().first()
                .map { ListeningSentence(it.id, it.ja, it.zh, it.scene) }
            val questions = ListeningQuizGenerator.buildSession(wordPool, learnedIds, sentences, count)
            if (questions.isEmpty()) {
                _state.update { it.copy(phase = ListeningPhase.DONE, requestedCount = count) }
                return@launch
            }
            startedAt = System.currentTimeMillis()
            _state.update {
                it.copy(
                    phase = ListeningPhase.QUIZ,
                    questions = questions,
                    requestedCount = count,
                    index = 0,
                    selected = null,
                    typedDraft = "",
                    typedResult = null,
                    quizCorrect = 0,
                    quizTotal = 0,
                    lastCorrect = null,
                )
            }
        }
    }

    fun onSelect(index: Int) {
        val s = _state.value
        val quiz = current?.quiz ?: return
        if (quiz.isTypeAnswer || s.selected != null) return
        val correct = quiz.answerIndex == index
        _state.update {
            it.copy(
                selected = index,
                lastCorrect = correct,
                quizTotal = it.quizTotal + 1,
                quizCorrect = it.quizCorrect + if (correct) 1 else 0,
            )
        }
    }

    fun onTypedDraftChange(value: String) {
        if (_state.value.typedResult != null) return
        _state.update { it.copy(typedDraft = value) }
    }

    fun submitTyped() {
        val s = _state.value
        val quiz = current?.quiz ?: return
        if (!quiz.isTypeAnswer || s.typedResult != null) return
        val ok = TypeAnswerNormalizer.matches(s.typedDraft, quiz.acceptedAnswers)
        _state.update {
            it.copy(
                typedResult = ok,
                lastCorrect = ok,
                quizTotal = it.quizTotal + 1,
                quizCorrect = it.quizCorrect + if (ok) 1 else 0,
            )
        }
    }

    fun next() {
        val s = _state.value
        val question = current ?: return
        viewModelScope.launch {
            // 听力对错只同步错题本，不推进 SRS（PRD §19.7）
            if (question.contentType == "word" && s.lastCorrect != null) {
                app.progress.recordAuxAnswer(question.contentType, question.contentId, s.lastCorrect)
            }
            val nextIndex = s.index + 1
            if (nextIndex >= s.questions.size) {
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                app.stats.addStudy(seconds.coerceAtLeast(1))
                _state.update { it.copy(index = nextIndex, phase = ListeningPhase.DONE) }
            } else {
                _state.update {
                    it.copy(index = nextIndex, selected = null, typedDraft = "", typedResult = null, lastCorrect = null)
                }
            }
        }
    }

    fun speak(text: String) = app.tts.speak(text)
}

private fun com.japanlearn.app.data.local.WordEntity.toQuizWord() =
    QuizWord(id, ja, kana, zh, pos, cat, romaji)

@Composable
fun ListeningSessionScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: ListeningSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        ListeningSessionViewModel(app)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()

    // 会话开始即检查 TTS：不可用直接走 v0.7.x 引导链，不让用户面对哑巴题（PRD §19.7）
    var guideKind by remember { mutableStateOf<JapaneseTts.Action?>(null) }
    LaunchedEffect(Unit) {
        var ttsState = app.tts.currentState()
        if (ttsState == JapaneseTts.State.WAITING) {
            delay(JapaneseTts.INIT_TIMEOUT_MS)
            ttsState = app.tts.currentState()
        }
        if (ttsState == JapaneseTts.State.READY) app.tts.refreshJapaneseStatus()
        val action = JapaneseTts.decideAction(
            ttsState,
            app.tts.japaneseUsable(),
            app.tts.japaneseMissingData(),
        )
        if (action != JapaneseTts.Action.SPEAK) guideKind = action
    }
    guideKind?.let { kind ->
        VoiceGuideDialog(kind = kind, onDismiss = { guideKind = null })
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = when (state.phase) {
                        ListeningPhase.DONE -> "听力完成"
                        ListeningPhase.QUIZ -> "听力训练 ${state.index + 1}/${state.questions.size}"
                        else -> "听力训练"
                    },
                ) { nav.popBackStack() }
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state.phase) {
                    ListeningPhase.LOADING -> LoadingPlaceholder()

                    ListeningPhase.PICK -> {
                        StaggerIn(0) {
                            SectionCard(title = "选择题量") {
                                Text(
                                    "听音辨词 · 听写假名 · 听句选义，混合出题",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(5, 10, 20).forEach { n ->
                                        AppButton(
                                            "$n 题",
                                            modifier = Modifier.weight(1f),
                                            onClick = { vm.start(n) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ListeningPhase.QUIZ -> {
                        vm.current?.let { question ->
                            QuizView(
                                question.quiz,
                                state.selected,
                                onSelect = { vm.onSelect(it) },
                                typedDraft = state.typedDraft,
                                typedResult = state.typedResult,
                                onSpeak = { vm.speak(it) },
                                onTypedDraftChange = { vm.onTypedDraftChange(it) },
                                onTypeSubmit = { vm.submitTyped() },
                            )
                            val answered =
                                if (question.quiz.isTypeAnswer) state.typedResult != null else state.selected != null
                            if (answered) {
                                val correct =
                                    if (question.quiz.isTypeAnswer) state.typedResult == true else state.selected == question.quiz.answerIndex
                                FeedbackReveal {
                                    FeedbackText(correct = correct, answerText = question.quiz.answerText)
                                    AppButton(
                                        text = if (state.index + 1 >= state.questions.size) "看结果" else "下一题",
                                        onClick = { vm.next() },
                                    )
                                }
                            }
                        }
                    }

                    ListeningPhase.DONE -> {
                        StaggerIn(0) {
                            SectionCard {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text("本轮听力完成", style = MaterialTheme.typography.headlineSmall)
                                    if (state.quizTotal > 0) {
                                        Text(
                                            "答对 ${state.quizCorrect}/${state.quizTotal}",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    } else {
                                        Text(
                                            "没有可用的听力内容",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.quizTotal > 0) {
                            StaggerIn(1) {
                                AppButton("再来一轮", onClick = { vm.start(state.requestedCount) })
                            }
                        }
                        StaggerIn(2) {
                            AppButton("返回", onClick = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
        ConfettiBurst(trigger = if (state.phase == ListeningPhase.DONE && state.quizTotal > 0) 1 else 0)
    }
}
