package com.japanlearn.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.japanlearn.app.data.ai.AiCompletionRequest
import com.japanlearn.app.data.ai.AiException
import com.japanlearn.app.domain.AiMode
import com.japanlearn.app.domain.AiPrompts
import com.japanlearn.app.domain.AiQuota
import com.japanlearn.app.navigateToTab
import com.japanlearn.app.Routes
import com.japanlearn.app.ui.components.AppButton
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.motion.StaggerIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiAssistantUiState(
    val configured: Boolean = false,
    val mode: AiMode = AiMode.GRAMMAR,
    val input: String = "",
    val context: String? = null,
    /** 首个增量到达前为 true（显示「思考中」）；打字机阶段为 false。 */
    val loading: Boolean = false,
    /** 流式进行中（含等待与打字），期间禁用发送。 */
    val streaming: Boolean = false,
    val result: String? = null,
    val error: String? = null,
    val callsToday: Int = 0,
    val dailyLimit: Int = 20,
    // 配置镜像（发送时使用，不展示）
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

class AiAssistantViewModel(
    private val app: AppContainer,
    initialMode: AiMode,
    initialInput: String,
    initialContext: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AiAssistantUiState(
            mode = initialMode,
            input = initialInput,
            context = initialContext.takeIf { it.isNotBlank() },
        ),
    )
    val uiState = _state.asStateFlow()

    init {
        fun <T> collect(flow: Flow<T>, reducer: (AiAssistantUiState, T) -> AiAssistantUiState) {
            viewModelScope.launch { flow.collect { v -> _state.update { cur -> reducer(cur, v) } } }
        }
        collect(app.settings.aiConfigured) { s, v -> s.copy(configured = v) }
        collect(app.settings.aiBaseUrl) { s, v -> s.copy(baseUrl = v) }
        collect(app.settings.aiApiKey) { s, v -> s.copy(apiKey = v) }
        collect(app.settings.aiModel) { s, v -> s.copy(model = v) }
        collect(app.settings.aiDailyLimit) { s, v -> s.copy(dailyLimit = v) }
        refreshCalls(_state.value)
    }

    private fun refreshCalls(s: AiAssistantUiState) {
        _state.update { it.copy(callsToday = app.settings.aiCallsToday(app.dateProvider.today().toString())) }
    }

    fun setMode(mode: AiMode) = _state.update { it.copy(mode = mode) }

    fun setInput(value: String) = _state.update { it.copy(input = value) }

    fun send() {
        val s = _state.value
        if (!s.configured || s.streaming || s.input.isBlank()) return
        val today = app.dateProvider.today().toString()
        val used = app.settings.aiCallsToday(today)
        if (!AiQuota.canCall(used, s.dailyLimit)) {
            _state.update { it.copy(error = "今日 ${s.dailyLimit} 次额度已用完，明天再来，或在「我的 → AI 助手」调高上限。") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, streaming = true, error = null, result = null) }
            var counted = false
            try {
                val text = app.aiClient.stream(
                    AiCompletionRequest(
                        baseUrl = s.baseUrl,
                        apiKey = s.apiKey,
                        model = s.model,
                        systemPrompt = AiPrompts.systemPrompt(s.mode),
                        userPrompt = AiPrompts.userPrompt(s.mode, s.input, s.context),
                    ),
                    onDelta = { delta ->
                        // 首个增量到达 = 200 已收到、token 已在服务端消耗，此时计数（PRD §19.11）
                        if (!counted) {
                            counted = true
                            app.settings.incrementAiCalls(today)
                        }
                        _state.update { it.copy(loading = false, result = (it.result ?: "") + delta) }
                    },
                )
                _state.update { it.copy(loading = false, streaming = false, result = text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                // 流中断：保留已收到的部分文本，附错误说明
                _state.update { it.copy(loading = false, streaming = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, streaming = false, error = "出错了：${e.message}") }
            }
        }
    }

    fun speak(text: String) = app.tts.speak(text)
}

@Composable
fun AiAssistantScreen(
    nav: NavHostController,
    initialMode: AiMode,
    initialInput: String,
    initialContext: String,
) {
    val app = LocalAppContainer.current
    val vm: AiAssistantViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        AiAssistantViewModel(app, initialMode, initialInput, initialContext)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AppTopBar(title = "AI 助手") { nav.popBackStack() }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                AiAssistantPanel(
                    state = state,
                    onMode = { vm.setMode(it) },
                    onInput = { vm.setInput(it) },
                    onSend = { vm.send() },
                    onOpenConfig = { nav.navigateToTab(Routes.PROFILE) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * AI 助手正文：全屏页与首页弹窗共用。
 * 自身不滚动，滚动约束由宿主通过 modifier 传入。
 */
@Composable
fun AiAssistantPanel(
    state: AiAssistantUiState,
    onMode: (AiMode) -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!state.configured) {
            StaggerIn(0) {
                SectionCard(title = "尚未启用") {
                    Text(
                        "AI 助手需要填入你自己的大模型 API Key。不配置则完全不使用，核心学习功能不受影响。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppButton("去设置 AI 助手", onClick = onOpenConfig)
                }
            }
        } else {
            StaggerIn(0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.mode == mode,
                                onClick = { onMode(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    Text(
                        "需联网 · 调用费用由你的 API Key 承担" +
                            (AiQuota.remaining(state.callsToday, state.dailyLimit)?.let { " · 今日剩余 $it 次" }
                                ?: " · 今日不限次数"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.context?.let { ctx ->
                StaggerIn(1) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "已带入教材上下文：" + ctx,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }

            StaggerIn(2) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = { onInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = {
                            Text(
                                when (state.mode) {
                                    AiMode.GRAMMAR -> "输入想弄懂的语法点或句子，如：〜てしまう"
                                    AiMode.CORRECT -> "粘贴你想检查的日语句子"
                                    AiMode.TRANSLATE -> "输入日语或中文，自动互译"
                                }
                            )
                        },
                    )
                    AppButton(
                        text = when {
                            state.loading -> "思考中…"
                            state.streaming -> "回答中…"
                            else -> "发送"
                        },
                        enabled = !state.streaming && state.input.isNotBlank(),
                        onClick = onSend,
                    )
                }
            }

            state.error?.let { msg ->
                StaggerIn(3) {
                    SectionCard {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.result?.let { text ->
                StaggerIn(4) {
                    SectionCard(title = when (state.mode) {
                        AiMode.GRAMMAR -> "语法讲解"
                        AiMode.CORRECT -> "批改结果"
                        AiMode.TRANSLATE -> "译文"
                    }) {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
