package com.japanlearn.app.ui.kana

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.japanlearn.app.data.local.KanaEntity
import com.japanlearn.app.domain.Quiz
import com.japanlearn.app.domain.QuizGenerator
import com.japanlearn.app.domain.QuizKana
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.QuizView
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.components.TtsButton
import com.japanlearn.app.ui.motion.ConfettiBurst
import com.japanlearn.app.ui.motion.MotionTokens
import com.japanlearn.app.ui.motion.StaggerIn
import com.japanlearn.app.ui.motion.pressScale
import com.japanlearn.app.ui.motion.rememberReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---------------- 五十音列表 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanaScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val kanaList by app.content.kanaAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by remember { mutableStateOf(0) } // 0 = 平假名, 1 = 片假名
    var selected by remember { mutableStateOf<KanaEntity?>(null) }

    Scaffold(
        topBar = { AppTopBar("五十音") { nav.popBackStack() } },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("平假名") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("片假名") })
                Spacer(Modifier.weight(1f))
                FilterChip(selected = false, onClick = { nav.navigate(Routes.KANA_QUIZ) }, label = { Text("开始测验") })
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(androidx.compose.animation.core.tween(320)) togetherWith
                        fadeOut(androidx.compose.animation.core.tween(120))
                },
                label = "kanaTab",
            ) { currentTab ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(kanaList) { index, k ->
                        val char = if (currentTab == 0) k.hiragana else k.katakana
                        StaggerIn(index % MotionTokens.MAX_STAGGER_STEPS) {
                            KanaCell(char = char, onClick = { selected = k })
                        }
                    }
                }
            }
        }
    }

    selected?.let { k ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            KanaDetail(k, onSpeak = { app.tts.speak(it) })
        }
    }
}

@Composable
private fun KanaCell(char: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interaction, 0.92f),
        onClick = onClick,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(char, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun KanaDetail(kana: KanaEntity, onSpeak: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            Text(kana.hiragana, style = MaterialTheme.typography.displayMedium)
            Text(
                kana.katakana,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                kana.romaji,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
        TtsButton(kana.hiragana, onSpeak)
        Spacer(Modifier.height(8.dp))
        SectionCard(title = "示例单词") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(kana.exampleJa, style = MaterialTheme.typography.titleLarge)
                    Text(
                        kana.exampleZh,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TtsButton(kana.exampleJa, onSpeak)
            }
        }
    }
}

// ---------------- 五十音测验 ----------------

data class KanaQuizUiState(
    val questions: List<QuizKana> = emptyList(),
    val pool: List<QuizKana> = emptyList(),
    val index: Int = 0,
    val quiz: Quiz? = null,
    val selected: Int? = null,
    val correctCount: Int = 0,
    val finished: Boolean = false,
)

class KanaQuizViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(KanaQuizUiState())
    val uiState = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val all = app.content.kanaAll().map { list -> list.map { QuizKana(it.id, it.hiragana, it.katakana, it.romaji) } }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
            all.collect { pool ->
                if (pool.isNotEmpty() && _state.value.questions.isEmpty()) {
                    start(pool)
                }
            }
        }
    }

    private fun start(pool: List<QuizKana>) {
        val questions = pool.shuffled(Random.Default).take(10)
        _state.update {
            KanaQuizUiState(
                questions = questions,
                pool = pool,
                index = 0,
                quiz = questions.firstOrNull()?.let { QuizGenerator.kanaQuiz(it, pool) },
            )
        }
    }

    fun onSelect(index: Int) {
        val s = _state.value
        val quiz = s.quiz ?: return
        if (s.selected != null) return
        val correct = index == quiz.answerIndex
        if (!correct) {
            val target = s.questions[s.index]
            viewModelScope.launch { app.progress.recordKanaWrong(target.id) }
        }
        _state.update {
            it.copy(
                selected = index,
                correctCount = if (correct) it.correctCount + 1 else it.correctCount,
            )
        }
    }

    fun next() {
        val s = _state.value
        val nextIndex = s.index + 1
        if (nextIndex >= s.questions.size) {
            _state.update { it.copy(finished = true) }
        } else {
            _state.update {
                it.copy(
                    index = nextIndex,
                    quiz = QuizGenerator.kanaQuiz(it.questions[nextIndex], it.pool),
                    selected = null,
                )
            }
        }
    }

    fun restart() {
        val s = _state.value
        if (s.pool.isNotEmpty()) start(s.pool)
    }
}

@Composable
fun KanaQuizScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: KanaQuizViewModel = androidx.lifecycle.viewmodel.compose.viewModel { KanaQuizViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val reduceMotion = rememberReducedMotion()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { AppTopBar("五十音测验") { nav.popBackStack() } },
        ) { padding ->
            AnimatedContent(
                targetState = state.finished,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(androidx.compose.animation.core.tween(150)) togetherWith fadeOut(androidx.compose.animation.core.tween(100))
                    } else {
                        fadeIn(androidx.compose.animation.core.tween(360)) togetherWith
                            fadeOut(androidx.compose.animation.core.tween(160))
                    }
                },
                label = "kanaQuizState",
                modifier = Modifier.padding(padding),
            ) { finished ->
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (finished) {
                        SectionCard {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("测验完成", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "${state.correctCount} / ${state.questions.size} 正确",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AppButton("再来一轮") { vm.restart() }
                                AppButton("返回") { nav.popBackStack() }
                            }
                        }
                    } else {
                        Text(
                            "第 ${state.index + 1} / ${state.questions.size} 题",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AnimatedContent(
                            targetState = state.index,
                            transitionSpec = {
                                if (reduceMotion) {
                                    fadeIn(androidx.compose.animation.core.tween(150)) togetherWith fadeOut(androidx.compose.animation.core.tween(100))
                                } else {
                                    val forward = targetState > initialState
                                    val enter = androidx.compose.animation.slideInHorizontally(
                                        androidx.compose.animation.core.tween(320, easing = MotionTokens.EmphasizedDecelerate),
                                    ) { if (forward) it / 5 else -it / 5 } + fadeIn(androidx.compose.animation.core.tween(240))
                                    val exit = androidx.compose.animation.slideOutHorizontally(
                                        androidx.compose.animation.core.tween(240),
                                    ) { if (forward) -it / 6 else it / 6 } + fadeOut(androidx.compose.animation.core.tween(160))
                                    enter togetherWith exit
                                }
                            },
                            label = "kanaQuestion",
                        ) { _ ->
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                state.quiz?.let { quiz ->
                                    QuizView(quiz, state.selected, onSelect = { vm.onSelect(it) })
                                    if (state.selected != null) {
                                        AppButton(if (state.index + 1 >= state.questions.size) "查看成绩" else "下一题") { vm.next() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ConfettiBurst(trigger = if (state.finished) 1 else 0)
    }
}
