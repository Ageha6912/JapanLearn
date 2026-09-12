package com.japanlearn.app.ui.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.japanlearn.app.LocalAppContainer
import com.japanlearn.app.domain.StudyPlanner
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.motion.StaggerIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 首启三步引导（PRD §19.6）：① 学习闭环说明 → ② 水平 + 可选目标 → ③ 直达学习。
 * 全屏覆盖层；任意一步可跳过；选择即时写入设置，完成/跳过由调用方写 onboardingDone。
 */
@Composable
fun OnboardingOverlay(
    onDismiss: () -> Unit,
    onStartKana: () -> Unit,
) {
    val app = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val studyLevel by app.settings.studyLevel.collectAsStateWithLifecycle()
    val goalLevel by app.settings.goalLevel.collectAsStateWithLifecycle()
    val goalTargetEpochDay by app.settings.goalTargetEpochDay.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(0) }

    BackHandler { onDismiss() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .blockClicks(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // 步骤指示点
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .size(if (index == step) 22.dp else 8.dp, 8.dp)
                            .background(
                                if (index <= step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
            }

            when (step) {
                0 -> StaggerIn(0) {
                    SectionCard {
                        Text("欢迎来到 JapanLearn", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "每天 10 分钟，三步看到进步：",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LoopRow(Icons.AutoMirrored.Filled.MenuBook, "学习", "每天学几个新词、一条语法")
                        LoopRow(Icons.Outlined.EditNote, "练习", "选择、听音、汉字、打字题即时检验")
                        LoopRow(Icons.Filled.Refresh, "复习", "到期的内容自动排队，不容易忘")
                    }
                }

                1 -> StaggerIn(1) {
                    SectionCard {
                        Text("从哪里开始？", style = MaterialTheme.typography.headlineSmall)
                        Text("当前水平", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("N5", "N4").forEach { level ->
                                FilterChip(
                                    selected = studyLevel == level,
                                    onClick = { app.settings.setStudyLevel(level) },
                                    label = { Text(level) },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Filled.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text("学习目标（可选）", style = MaterialTheme.typography.titleSmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("N5", "N4").forEach { level ->
                                FilterChip(
                                    selected = goalLevel == level,
                                    onClick = {
                                        app.settings.setGoal(level, app.settings.goalTargetEpochDay.value)
                                        scope.launch { app.recalibrateGoal() }
                                    },
                                    label = { Text(level) },
                                )
                            }
                        }
                        if (goalLevel != StudyPlanner.LEVEL_NONE) {
                            Text("目标日期", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30 to "1 个月", 90 to "3 个月", 180 to "6 个月").forEach { (days, label) ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val today = app.dateProvider.today().toEpochDay()
                                            app.settings.setGoal(goalLevel, today + days)
                                            scope.launch { app.recalibrateGoal() }
                                        },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            if (goalTargetEpochDay > 0L) {
                                val date = LocalDate.ofEpochDay(goalTargetEpochDay)
                                Text(
                                    "达成日：${date.year}年${date.monthValue}月${date.dayOfMonth}日，可随时在「我的 → 学习目标」修改",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                else -> StaggerIn(2) {
                    SectionCard {
                        Text("准备好了", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "选一条路出发，之后随时可以在底部导航里切换：",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AppButton(
                            "先学五十音",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onStartKana,
                        )
                        AppButton(
                            "直接开始学单词",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onDismiss,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // 底部操作：跳过 + 下一步
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("跳过") }
                if (step < 2) {
                    AppButton("下一步", onClick = { step += 1 })
                } else {
                    Text(
                        "JapanLearn",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LoopRow(icon: ImageVector, title: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 覆盖层空白区域吞掉点击，防止透传到下层首页。 */
private fun Modifier.blockClicks(): Modifier = composed {
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
    )
}
