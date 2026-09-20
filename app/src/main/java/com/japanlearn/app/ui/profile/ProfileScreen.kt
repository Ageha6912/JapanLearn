package com.japanlearn.app.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.japanlearn.app.AppContainer
import com.japanlearn.app.LocalAppContainer
import com.japanlearn.app.Routes
import com.japanlearn.app.domain.AiConfig
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
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val streak: Int = 0,
    val learnedWords: Int = 0,
    val masteredWords: Int = 0,
    val totalWords: Int = 0,
    val ttsVoiceName: String = "",
    val ttsVoices: List<com.japanlearn.app.util.JapaneseTts.VoiceOption> = emptyList(),
    val aiBaseUrl: String = "",
    val aiApiKey: String = "",
    val aiModel: String = "",
    val aiDailyLimit: Int = 20,
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
        collect(app.settings.reminderEnabled) { s, v -> s.copy(reminderEnabled = v) }
        collect(app.settings.reminderHour) { s, v -> s.copy(reminderHour = v) }
        collect(app.settings.ttsVoiceName) { s, v -> s.copy(ttsVoiceName = v) }
        collect(app.settings.aiBaseUrl) { s, v -> s.copy(aiBaseUrl = v) }
        collect(app.settings.aiApiKey) { s, v -> s.copy(aiApiKey = v) }
        collect(app.settings.aiModel) { s, v -> s.copy(aiModel = v) }
        collect(app.settings.aiDailyLimit) { s, v -> s.copy(aiDailyLimit = v) }
        collect(app.stats.weekly()) { s, v -> s.copy(streak = v.streak) }
        collect(app.progress.learnedWordCount()) { s, v -> s.copy(learnedWords = v) }
        collect(app.progress.masteredWordCount()) { s, v -> s.copy(masteredWords = v) }
        collect(app.content.wordCount()) { s, v -> s.copy(totalWords = v) }
    }

    fun refreshTtsVoices() {
        _state.update { it.copy(ttsVoices = app.tts.listInstalledJapaneseVoices()) }
    }

    fun setTtsVoice(name: String) {
        app.settings.setTtsVoiceName(name)
        app.tts.setPreferredVoice(name)
    }

    fun setDailyNewWords(v: Int) = app.settings.setDailyNewWords(v)
    fun setDailyNewGrammar(v: Int) = app.settings.setDailyNewGrammar(v)
    fun setDailyReviewCap(v: Int) = app.settings.setDailyReviewCap(v)

    fun saveAiConfig(baseUrl: String, apiKey: String, model: String) =
        app.settings.saveAiConfig(baseUrl, apiKey, model)

    fun setAiDailyLimit(v: Int) = app.settings.setAiDailyLimit(v)

    fun setReminderEnabled(context: android.content.Context, v: Boolean) {
        app.settings.setReminderEnabled(v)
        com.japanlearn.app.work.ReviewReminder.schedule(
            context, v, app.settings.reminderHour.value, app.settings.reminderMinute.value,
        )
    }

    fun setReminderHour(context: android.content.Context, hour: Int) {
        app.settings.setReminderHour(hour)
        if (app.settings.reminderEnabled.value) {
            com.japanlearn.app.work.ReviewReminder.schedule(
                context, true, hour, app.settings.reminderMinute.value,
            )
        }
    }

    fun resetAll() {
        viewModelScope.launch { app.progress.resetAll() }
    }

    /** 导出学习数据到用户选择的 URI（SAF），完成后回调反馈文案。 */
    fun exportData(context: android.content.Context, uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.backup.exportJson() }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("无法写入所选位置")
                }
                onDone("备份已导出")
            } catch (e: Exception) {
                onDone("导出失败：${e.message}")
            }
        }
    }

    /** 从用户选择的 JSON 备份恢复（合并写入，不清空现有数据）。 */
    fun importData(context: android.content.Context, uri: android.net.Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("无法读取所选文件")
                }
                val file = com.japanlearn.app.data.BackupFileSchema.parse(text)
                if (file == null) {
                    onDone("导入失败：不是有效的 JapanLearn 备份文件")
                } else {
                    val s = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.backup.import(file) }
                    onDone("恢复完成：进度 ${s.progress}、错题 ${s.wrongAnswers}、统计 ${s.dailyStudy}、复习记录 ${s.reviewRecords}")
                }
            } catch (e: Exception) {
                onDone("导入失败：${e.message}")
            }
        }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun ProfileScreen(nav: NavHostController) {
    val app = LocalAppContainer.current
    val vm: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel { ProfileViewModel(app) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            vm.exportData(context, uri) { msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.importData(context, uri) { msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        // TTS init 异步，稍等再拉一次列表；用户也可点「试听」触发刷新
        kotlinx.coroutines.delay(com.japanlearn.app.util.JapaneseTts.INIT_TIMEOUT_MS + 400L)
        vm.refreshTtsVoices()
    }

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
                .fillMaxSize(),
        ) {
            // 固定头部：标题不随内容滚动
            Column(Modifier.padding(horizontal = 20.dp)) {
                StaggerIn(0) {
                    Text("我的", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(16.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            "查看学习统计",
                            modifier = Modifier.weight(1f),
                            onClick = { nav.navigate(Routes.STATS) },
                        )
                        AppButton(
                            "学习成果",
                            modifier = Modifier.weight(1f),
                            onClick = { nav.navigate(Routes.ACHIEVEMENTS) },
                        )
                    }
                }
            }

            StaggerIn(2) {
                SectionCard(title = "学习目标", onClick = { nav.navigate(Routes.GOAL) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text("设定 N5 / N4 目标与达成日期", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "按剩余内容量自动推荐每日新词量",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            StaggerIn(3) {
                SectionCard(title = "每日任务量") {
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

            StaggerIn(4) {
                SectionCard(title = "提醒") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("每日复习提醒", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "大约在所选整点检查一次，有到期内容时提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.reminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                vm.setReminderEnabled(context, enabled)
                            },
                        )
                    }
                    ChipRow(
                        options = com.japanlearn.app.util.ReminderScheduler.HOUR_PRESETS,
                        selected = state.reminderHour,
                        onSelect = { vm.setReminderHour(context, it) },
                        label = { "%02d:00".format(it) },
                    )
                }
            }

            StaggerIn(5) {
                SectionCard(title = "AI 助手（可选，需联网）") {
                    val configured = AiConfig.isConfigured(state.aiBaseUrl, state.aiApiKey, state.aiModel)
                    var aiUrl by remember { mutableStateOf(app.settings.aiBaseUrl.value) }
                    var aiKey by remember { mutableStateOf(app.settings.aiApiKey.value) }
                    var aiModelText by remember { mutableStateOf(app.settings.aiModel.value) }
                    val privacyNote = "Key 仅保存在本机，请求只包含你主动输入的内容，不发送任何学习数据。"
                    if (!configured) {
                        Text(
                            "填入你自己的大模型 API Key 启用，不填则完全不出现相关功能。费用由你的 Key 承担。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 2.dp),
                        ) {
                            AiConfig.PRESETS.forEach { preset ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        aiUrl = preset.baseUrl
                                        aiModelText = preset.model
                                    },
                                    label = { Text(preset.name) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = aiUrl,
                            onValueChange = { aiUrl = it },
                            label = { Text("接口地址") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = aiKey,
                            onValueChange = { aiKey = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = aiModelText,
                            onValueChange = { aiModelText = it },
                            label = { Text("模型名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        AppButton(
                            "保存并启用",
                            enabled = aiUrl.isNotBlank() && aiKey.isNotBlank() && aiModelText.isNotBlank(),
                            onClick = { vm.saveAiConfig(aiUrl, aiKey, aiModelText) },
                        )
                        Text(
                            privacyNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("已启用 · ${state.aiModel}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            state.aiBaseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        AppButton("开始对话", onClick = { nav.navigate(Routes.aiAssistant()) })
                        Text("每日调用上限", style = MaterialTheme.typography.titleSmall)
                        ChipRow(
                            options = AiConfig.DAILY_LIMIT_CHOICES,
                            selected = state.aiDailyLimit,
                            onSelect = { vm.setAiDailyLimit(it) },
                            label = { if (it == AiConfig.UNLIMITED) "不限" else "$it 次" },
                        )
                        Text(
                            privacyNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { vm.saveAiConfig("", "", "") }) { Text("清除 AI 配置") }
                    }
                }
            }

            StaggerIn(6) {
                SectionCard(title = "发音") {
                    Text("日语音色", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "仅列出已下载的本地日语语音。更多音色可在「Google 文字转语音」里下载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.ttsVoices.isEmpty()) {
                        Text(
                            "暂无可选音色（需已安装 Google TTS 并下载日语语音）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val selectedName = state.ttsVoiceName.ifEmpty {
                            state.ttsVoices.maxByOrNull { it.quality }?.name ?: ""
                        }
                        val selectedVoice = state.ttsVoices.firstOrNull { it.name == selectedName }
                            ?: state.ttsVoices.maxByOrNull { it.quality }
                        var showAllVoices by remember { mutableStateOf(false) }
                        val voicesToShow = if (showAllVoices) {
                            state.ttsVoices
                        } else {
                            listOfNotNull(selectedVoice)
                        }
                        voicesToShow.forEach { voice ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = voice.name == selectedName,
                                    onClick = { vm.setTtsVoice(voice.name) },
                                )
                                Text(
                                    voice.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (state.ttsVoices.size > 1) {
                            TextButton(onClick = { showAllVoices = !showAllVoices }) {
                                Text(if (showAllVoices) "收起音色列表" else "显示全部音色（${state.ttsVoices.size}）")
                            }
                        }
                    }
                    AppButton("试听当前音色") {
                        vm.refreshTtsVoices()
                        vm.speak("こんにちは。日本語の音声をテストします。")
                    }
                }
            }

            StaggerIn(6) {
                SectionCard(title = "数据") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text("备份与恢复", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "进度、错题、统计导出为 JSON；导入按记录合并恢复",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            "导出数据",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                exportLauncher.launch("japanlearn-backup-" + java.time.LocalDate.now() + ".json")
                            },
                        )
                        AppButton(
                            "导入数据",
                            modifier = Modifier.weight(1f),
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
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

            StaggerIn(6) {
                SectionCard(title = "关于") {
                    Text("JapanLearn v${com.japanlearn.app.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "发音使用系统 TTS（日语语音包）。若缺少语音包，点发音按钮时会引导你下载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ChipRow(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: (Int) -> String = { "$it" },
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}
