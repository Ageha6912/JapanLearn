package com.japanlearn.app.ui.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.motion.AnimatedCounterText
import com.japanlearn.app.ui.motion.StaggerIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val dailyNewWords: Int = 10,
    val dailyNewGrammar: Int = 3,
    val dailyReviewCap: Int = 30,
    val streak: Int = 0,
    val learnedWords: Int = 0,
    val masteredWords: Int = 0,
    val totalWords: Int = 0,
)

class ProfileViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val uiState = _state.asStateFlow()

    init {
        fun <T> collect(flow: kotlinx.coroutines.flow.Flow<T>, reducer: (ProfileUiState, T) -> ProfileUiState) {
            viewModelScope.launch { flow.collect { v -> _state.update { cur -> reducer(cur, v) } } }
        }
        collect(app.settings.dailyNewWords) { s, v -> s.copy(dailyNewWords = v) }
        collect(app.settings.dailyNewGrammar) { s, v -> s.copy(dailyNewGrammar = v) }
        collect(app.settings.dailyReviewCap) { s, v -> s.copy(dailyReviewCap = v) }
        collect(app.stats.weekly()) { s, v -> s.copy(streak = v.streak) }
        collect(app.progress.learnedWordCount()) { s, v -> s.copy(learnedWords = v) }
        collect(app.progress.masteredWordCount()) { s, v -> s.copy(masteredWords = v) }
        collect(app.content.wordsAll()) { s, v -> s.copy(totalWords = v.size) }
    }

    fun setDailyNewWords(v: Int) = app.settings.setDailyNewWords(v)
    fun setDailyNewGrammar(v: Int) = app.settings.setDailyNewGrammar(v)
    fun setDailyReviewCap(v: Int) = app.settings.setDailyReviewCap(v)

    fun resetAll() {
        viewModelScope.launch { app.progress.resetAll() }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun ProfileScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel { ProfileViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("清空学习记录？") },
            text = { Text("将删除全部学习进度、错题和统计记录，内容不受影响。此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAll()
                    showResetDialog = false
                }) { Text("确认清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("取消") } },
        )
    }

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
                Text("我的", style = MaterialTheme.typography.headlineMedium)
            }

            // 学习者卡片
            StaggerIn(1) {
                SectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                "日",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("游客模式", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "本地学习，数据保存在本机",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(
                                    Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                AnimatedCounterText(
                                    value = state.streak,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            Text(
                                "已学 ${state.learnedWords}/${state.totalWords}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    AppButton("查看学习统计", onClick = { nav.navigate(Routes.STATS) })
                }
            }

            StaggerIn(2) {
                SectionCard(title = "学习目标") {
                    Text("每天新词数", style = MaterialTheme.typography.titleSmall)
                    ChipRow(
                        options = listOf(5, 10, 15, 20),
                        selected = state.dailyNewWords,
                        onSelect = { vm.setDailyNewWords(it) },
                    )
                    Text("每天语法数", style = MaterialTheme.typography.titleSmall)
                    ChipRow(
                        options = listOf(2, 3, 5),
                        selected = state.dailyNewGrammar,
                        onSelect = { vm.setDailyNewGrammar(it) },
                    )
                    Text("每天复习上限", style = MaterialTheme.typography.titleSmall)
                    ChipRow(
                        options = listOf(10, 20, 30, 50),
                        selected = state.dailyReviewCap,
                        onSelect = { vm.setDailyReviewCap(it) },
                    )
                }
            }

            StaggerIn(3) {
                SectionCard(title = "数据") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text("清空学习记录", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "删除全部进度、错题与统计，内容保留",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    AppButton("清空学习记录", onClick = { showResetDialog = true })
                }
            }

            StaggerIn(4) {
                SectionCard(title = "关于") {
                    Text("JapanLearn v0.1.0 (MVP)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "发音使用系统 TTS（日语语音包）。若设备未安装日语语音，发音可能不可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChipRow(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text("$option") },
            )
        }
    }
}
