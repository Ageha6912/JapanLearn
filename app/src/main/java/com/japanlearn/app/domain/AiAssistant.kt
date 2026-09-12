package com.japanlearn.app.domain

/**
 * AI 助手（PRD §19.9，BYOK）：模式、Prompt 构建、配置归一化、每日限额。
 * 全部纯函数，便于单元测试；网络收发见 data 层 AiClient。
 */

/** 助手三模式：语法解释 / 句子纠错 / 翻译。 */
enum class AiMode(val label: String) {
    GRAMMAR("语法解释"),
    CORRECT("句子纠错"),
    TRANSLATE("翻译"),
}

object AiConfig {

    const val UNLIMITED = -1
    val DAILY_LIMIT_CHOICES = listOf(10, 20, 50, UNLIMITED)

    data class Preset(val name: String, val baseUrl: String, val model: String)

    val PRESETS = listOf(
        Preset("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
        Preset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    )

    /** 三字段齐备才算已启用；未启用时所有 AI 入口隐藏。 */
    fun isConfigured(baseUrl: String, apiKey: String, model: String): Boolean =
        baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /**
     * 端点归一化：去尾斜杠；未以 /chat/completions 结尾则补上。
     * "https://api.deepseek.com" 与 "https://api.openai.com/v1/chat/completions" 都能填。
     */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }
}

object AiPrompts {

    fun systemPrompt(mode: AiMode): String = when (mode) {
        AiMode.GRAMMAR ->
            "你是面向日语初学者（JLPT N5/N4）的语法老师。用简体中文讲解用户给出的语法点或句子中的语法，" +
                "先一句话说明意思，再讲接续和用法要点，最后给一个新例句（日语例句用括号标注假名读音）。" +
                "全文控制在 300 字以内，不使用 Markdown 标题符号。"
        AiMode.CORRECT ->
            "你是日语老师，负责检查用户给出的日语句子。若无错误，回答「这个句子是正确的」，再简要说明句子含义；" +
                "若有错误，先给出修正后的完整句子（括号标注假名读音），再用简体中文逐条说明错在哪里。全文控制在 300 字以内。"
        AiMode.TRANSLATE ->
            "你是日语翻译。用户给日语就翻译成简体中文，给中文就翻译成日语（译出的日语在括号里标注假名读音）。" +
                "只输出译文本身，不要解释。"
    }

    fun userPrompt(mode: AiMode, input: String, context: String? = null): String {
        val body = when (mode) {
            AiMode.GRAMMAR -> "请讲解这里的日语语法：${input.trim()}"
            AiMode.CORRECT -> "请检查这个日语句子：${input.trim()}"
            AiMode.TRANSLATE -> "请翻译：${input.trim()}"
        }
        return if (context.isNullOrBlank()) body else "$body\n\n（教材上下文供参考：${context.trim()}）"
    }
}

object AiQuota {

    /** 今日是否还可调用；limit = [AiConfig.UNLIMITED] 表示不限。 */
    fun canCall(todayCount: Int, dailyLimit: Int): Boolean =
        dailyLimit == AiConfig.UNLIMITED || todayCount < dailyLimit

    /** 今日剩余次数；不限时返回 null。 */
    fun remaining(todayCount: Int, dailyLimit: Int): Int? =
        if (dailyLimit == AiConfig.UNLIMITED) null else (dailyLimit - todayCount).coerceAtLeast(0)
}
