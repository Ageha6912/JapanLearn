package com.japanlearn.app.ui.learn

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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.japanlearn.app.AppContainer
import com.japanlearn.app.LocalAppContainer
import com.japanlearn.app.Routes
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.motion.AnimatedProgressBar
import com.japanlearn.app.ui.motion.StaggerIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LearnUiState(
    val totalKana: Int = 0,
    val totalWords: Int = 0,
    val totalGrammar: Int = 0,
    val learnedWords: Int = 0,
    val learnedGrammar: Int = 0,
    val dailyNewWords: Int = 10,
)

class LearnViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LearnUiState())
    val uiState = _state.asStateFlow()

    init {
        fun <T> collect(flow: kotlinx.coroutines.flow.Flow<T>, reducer: (LearnUiState, T) -> LearnUiState) {
            viewModelScope.launch { flow.collect { v -> _state.update { cur -> reducer(cur, v) } } }
        }
        collect(app.content.kanaAll()) { s, v -> s.copy(totalKana = v.size) }
        collect(app.content.wordsAll()) { s, v -> s.copy(totalWords = v.size) }
        collect(app.content.grammarAll()) { s, v -> s.copy(totalGrammar = v.size) }
        collect(app.progress.learnedWordCount()) { s, v -> s.copy(learnedWords = v) }
        collect(app.progress.learnedGrammarCount()) { s, v -> s.copy(learnedGrammar = v) }
        collect(app.settings.dailyNewWords) { s, v -> s.copy(dailyNewWords = v) }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnTabScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: LearnViewModel = androidx.lifecycle.viewmodel.compose.viewModel { LearnViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StaggerIn(0) {
                Text("学习", style = MaterialTheme.typography.headlineMedium)
            }

            StaggerIn(1) {
                LearnEntry(
                    title = "五十音",
                    subtitle = "平假名 · 片假名 · 基础发音",
                    icon = Icons.Filled.Translate,
                    tint = MaterialTheme.colorScheme.secondary,
                    progress = null,
                    onClick = { nav.navigate(Routes.KANA) },
                )
            }
            StaggerIn(2) {
                LearnEntry(
                    title = "N5 单词",
                    subtitle = "共 ${state.totalWords} 个 · 已学 ${state.learnedWords}",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    tint = MaterialTheme.colorScheme.primary,
                    progress = if (state.totalWords > 0) state.learnedWords.toFloat() / state.totalWords else 0f,
                    onClick = { nav.navigate(Routes.WORD_LIST) },
                )
            }
            StaggerIn(3) {
                LearnEntry(
                    title = "N5 语法",
                    subtitle = "共 ${state.totalGrammar} 条 · 已学 ${state.learnedGrammar}",
                    icon = Icons.Outlined.EditNote,
                    tint = MaterialTheme.colorScheme.tertiary,
                    progress = if (state.totalGrammar > 0) state.learnedGrammar.toFloat() / state.totalGrammar else 0f,
                    onClick = { nav.navigate(Routes.GRAMMAR_LIST) },
                )
            }

            StaggerIn(4) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("快速开始", style = MaterialTheme.typography.titleMedium)
                    AppButton(
                        text = "学 ${state.dailyNewWords} 个新词",
                        onClick = { nav.navigate(Routes.wordSession(state.dailyNewWords)) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** 课程入口卡：彩色图标章 + 标题 + 动画进度条 + 圆形箭头（按压内缩） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearnEntry(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    progress: Float?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(
                        Icons.AutoMirrored.Outlined.NavigateNext,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (progress != null) {
                AnimatedProgressBar(
                    progress = progress,
                    height = 6.dp,
                    fillColor = tint,
                )
            }
        }
    }
}
