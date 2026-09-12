package com.japanlearn.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.japanlearn.app.data.content.ContentJson
import com.japanlearn.app.data.content.Exercise
import com.japanlearn.app.data.local.KanaEntity
import com.japanlearn.app.data.local.WordEntity
import com.japanlearn.app.domain.DrillBuilder
import com.japanlearn.app.domain.DrillEntry
import com.japanlearn.app.domain.DrillGrammarExercise
import com.japanlearn.app.domain.Quiz
import com.japanlearn.app.domain.QuizKana
import com.japanlearn.app.domain.QuizWord
import com.japanlearn.app.domain.TypeAnswerNormalizer
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.QuizView
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.motion.ConfettiBurst
import com.japanlearn.app.ui.motion.StaggerIn
import com.japanlearn.app.ui.review.LoadingPlaceholder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

enum class DrillPhase { LOADING, QUIZ, DONE }

data class DrillUiState(
    val phase: DrillPhase = DrillPhase.LOADING,
    val questions: List<Quiz> = emptyList(),
    val contentTypes: List<String> = emptyList(),
    val contentIds: List<String> = emptyList(),
    val index: Int = 0,
    val selected: Int? = null,
    val typedDraft: String = "",
    val typedResult: Boolean? = null,
    val correct: Int = 0,
    val total: Int = 0,
    val lastCorrect: Boolean? = null,
    val remainingWrong: Int = 0,
)

/** 错题突击会话（PRD §19.10）：答对移出错题本，答错留下 +1，不推进 SRS。 */
class WrongAnswerDrillViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DrillUiState())
    val uiState = _state.asStateFlow()

    private var startedAt = 0L

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            val entries = app.progress.wrongAnswers().first()
                .map { DrillEntry(it.contentType, it.contentId) }
            val words = app.content.wordsAll().first().map { it.toQuizWord() }
            val kana = app.content.kanaAll().first().map { it.toQuizKana() }
            val wrongIds = entries.filter { it.contentType == "grammar" }.map { it.contentId }.toSet()
            val grammarExercises = if (wrongIds.isEmpty()) {
                emptyMap()
            } else {
                app.content.grammarAll().first()
                    .filter { it.id in wrongIds }
                    .associate { g -> g.id to parseExercises(g.exercisesJson) }
            }
            val questions = DrillBuilder.build(entries, words, kana, grammarExercises, count = 10)
            if (questions.isEmpty()) {
                _state.update { it.copy(phase = DrillPhase.DONE, remainingWrong = 0) }
                return@launch
            }
            startedAt = System.currentTimeMillis()
            _state.update {
                it.copy(
                    phase = DrillPhase.QUIZ,
                    questions = questions.map { q -> q.quiz },
                    contentTypes = questions.map { q -> q.contentType },
                    contentIds = questions.map { q -> q.contentId },
                    index = 0,
                    selected = null,
                    typedDraft = "",
                    typedResult = null,
                    correct = 0,
                    total = 0,
                    lastCorrect = null,
                )
            }
        }
    }

    private fun parseExercises(json: String): List<DrillGrammarExercise> =
        runCatching {
            ContentJson.decodeFromString(ListSerializer(Exercise.serializer()), json)
                .map { DrillGrammarExercise(it.question, it.options, it.answer) }
        }.getOrDefault(emptyList())

    private fun WordEntity.toQuizWord() = QuizWord(id, ja, kana, zh, pos, cat, romaji)

    private fun KanaEntity.toQuizKana() = QuizKana(id, hiragana, katakana, romaji, groupName)

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
        val type = s.contentTypes.getOrNull(index) ?: return
        val contentId = s.contentIds.getOrNull(index) ?: return
        viewModelScope.launch {
            // 突击对错只同步错题本，不推进 SRS（PRD §19.10）
            if (s.lastCorrect != null) {
                app.progress.recordAuxAnswer(type, contentId, s.lastCorrect)
            }
            val nextIndex = index + 1
            if (nextIndex >= s.questions.size) {
                val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                app.stats.addStudy(seconds.coerceAtLeast(1))
                val remaining = app.progress.wrongAnswerCount().first()
                _state.update { it.copy(index = nextIndex, phase = DrillPhase.DONE, remainingWrong = remaining) }
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
fun WrongAnswerDrillScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: WrongAnswerDrillViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        WrongAnswerDrillViewModel(app)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AppTopBar(
                title = when (state.phase) {
                    DrillPhase.DONE -> "突击完成"
                    DrillPhase.QUIZ -> "错题突击 ${state.index + 1}/${state.questions.size}"
                    else -> "错题突击"
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
                    DrillPhase.LOADING -> LoadingPlaceholder()

                    DrillPhase.QUIZ -> {
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
                                FeedbackText(correct = correct, answerText = quiz.answerText)
                                AppButton(
                                    text = if (state.index + 1 >= state.questions.size) "看结果" else "下一题",
                                    onClick = { vm.next() },
                                )
                            }
                        }
                    }

                    DrillPhase.DONE -> {
                        StaggerIn(0) {
                            SectionCard {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text("本轮突击完成", style = MaterialTheme.typography.headlineSmall)
                                    if (state.total > 0) {
                                        Text("答对 ${state.correct}/${state.total}", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            if (state.remainingWrong > 0) "还有 ${state.remainingWrong} 道错题待清，再来一轮吧"
                                            else "错题本已全部清空！",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Text(
                                            "错题本里没有可练习的错题",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.total > 0 && state.remainingWrong > 0) {
                            StaggerIn(1) {
                                AppButton("再来一轮", onClick = { vm.refresh() })
                            }
                        }
                        StaggerIn(2) {
                            AppButton("返回", onClick = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
        ConfettiBurst(trigger = if (state.phase == DrillPhase.DONE && state.remainingWrong == 0 && state.total > 0) 1 else 0)
    }
}
