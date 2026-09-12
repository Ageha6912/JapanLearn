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
import androidx.compose.material.icons.filled.VolumeUp
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
import com.japanlearn.app.domain.CourseCatalog
import com.japanlearn.app.domain.CoursePointer
import com.japanlearn.app.domain.CourseUnitProgress
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.LevelSwitchRow
import com.japanlearn.app.ui.motion.AnimatedProgressBar
import com.japanlearn.app.ui.motion.StaggerIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LearnUiState(
    val totalKana: Int = 0,
    val studyLevel: String = "N5",
    val unitRows: List<CourseUnitProgress> = emptyList(),
    val currentUnit: Int = 1,
    val dailyNewWords: Int = 10,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LearnViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LearnUiState())
    val uiState = _state.asStateFlow()

    init {
        fun <T> collect(flow: kotlinx.coroutines.flow.Flow<T>, reducer: (LearnUiState, T) -> LearnUiState) {
            viewModelScope.launch { flow.collect { v -> _state.update { cur -> reducer(cur, v) } } }
        }
        collect(app.content.kanaCount()) { s, v -> s.copy(totalKana = v) }
        collect(app.settings.studyLevel) { s, v -> s.copy(studyLevel = v) }
        collect(app.settings.dailyNewWords) { s, v -> s.copy(dailyNewWords = v) }
        collect(courseFlow()) { s, (rows, current) -> s.copy(unitRows = rows, currentUnit = current) }
    }

    /** 当前级别 → 单元进度 + 当前单元（手动覆盖优先，PRD §19.8）。 */
    private fun courseFlow() = app.settings.studyLevel.flatMapLatest { level ->
        combine(
            app.content.unitProgressByLevelFlow(level),
            app.settings.courseUnitOverride,
        ) { rows, override ->
            val mapped = rows.map { CourseUnitProgress(it.unit, it.total, it.learned) }
            mapped to (CoursePointer.parseOverride(override, level) ?: CoursePointer.currentUnit(mapped))
        }
    }

    fun setLevel(level: String) = app.settings.setStudyLevel(level)

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
                .fillMaxSize(),
        ) {
            // 固定头部：标题不随内容滚动
            Column(Modifier.padding(horizontal = 20.dp)) {
                StaggerIn(0) {
                    Text("学习", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(12.dp))
                LevelSwitchRow(
                    selected = state.studyLevel,
                    onSelect = { vm.setLevel(it) },
                )
                Spacer(Modifier.height(16.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
                val row = state.unitRows.find { it.unit == state.currentUnit }
                LearnEntry(
                    title = "课程",
                    subtitle = "${state.studyLevel} 第 ${state.currentUnit} 单元 · " +
                        "${CourseCatalog.unitTitle(state.currentUnit)} · 已学 ${row?.learned ?: 0}/${row?.total ?: 0}",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    tint = MaterialTheme.colorScheme.primary,
                    progress = row?.progress ?: 0f,
                    onClick = { nav.navigate(Routes.COURSE) },
                )
            }

            StaggerIn(4) {
                LearnEntry(
                    title = "听力训练",
                    subtitle = "听音辨词 · 听写假名 · 听句选义",
                    icon = Icons.Filled.VolumeUp,
                    tint = MaterialTheme.colorScheme.secondary,
                    progress = null,
                    onClick = { nav.navigate(Routes.LISTENING) },
                )
            }

            StaggerIn(5) {
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
