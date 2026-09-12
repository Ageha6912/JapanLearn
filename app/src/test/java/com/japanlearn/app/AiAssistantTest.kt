package com.japanlearn.app

import com.japanlearn.app.domain.AiConfig
import com.japanlearn.app.domain.AiMode
import com.japanlearn.app.domain.AiPrompts
import com.japanlearn.app.domain.AiQuota
import com.japanlearn.app.domain.AiWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantTest {

    // ---- AiConfig ----

    @Test
    fun `三字段齐备才算启用`() {
        assertTrue(AiConfig.isConfigured("https://api.deepseek.com", "sk-x", "deepseek-chat"))
        assertFalse(AiConfig.isConfigured("", "sk-x", "deepseek-chat"))
        assertFalse(AiConfig.isConfigured("https://api.deepseek.com", " ", "deepseek-chat"))
        assertFalse(AiConfig.isConfigured("https://api.deepseek.com", "sk-x", ""))
    }

    @Test
    fun `端点归一化补全 chat completions 路径`() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            AiConfig.normalizeBaseUrl("https://api.deepseek.com"),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            AiConfig.normalizeBaseUrl("https://open.bigmodel.cn/api/paas/v4/"),
        )
    }

    @Test
    fun `已含完整路径的端点不再追加`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            AiConfig.normalizeBaseUrl("https://api.openai.com/v1/chat/completions"),
        )
        assertEquals("", AiConfig.normalizeBaseUrl("  "))
    }

    @Test
    fun `预设模板三字段齐备`() {
        assertEquals(3, AiConfig.PRESETS.size)
        AiConfig.PRESETS.forEach { preset ->
            assertTrue(preset.baseUrl.startsWith("https://"))
            assertTrue(preset.model.isNotBlank())
        }
    }

    // ---- AiPrompts ----

    @Test
    fun `三种模式系统提示词各不相同且为中文`() {
        val prompts = AiMode.entries.map { AiPrompts.systemPrompt(it) }
        assertEquals(prompts.size, prompts.toSet().size)
        prompts.forEach { assertTrue(it.contains("日语")) }
    }

    @Test
    fun `用户提示词包含输入内容`() {
        val prompt = AiPrompts.userPrompt(AiMode.GRAMMAR, "～てしまう")
        assertTrue(prompt.contains("～てしまう"))
        assertFalse(prompt.contains("教材上下文"))
    }

    @Test
    fun `带上下文时附加教材参考`() {
        val prompt = AiPrompts.userPrompt(AiMode.GRAMMAR, "～です", context = "教材说明：是……")
        assertTrue(prompt.contains("教材上下文"))
        assertTrue(prompt.contains("是……"))
    }

    // ---- AiQuota ----

    @Test
    fun `未达上限可调用`() {
        assertTrue(AiQuota.canCall(todayCount = 5, dailyLimit = 20))
        assertFalse(AiQuota.canCall(todayCount = 20, dailyLimit = 20))
        assertFalse(AiQuota.canCall(todayCount = 25, dailyLimit = 20))
    }

    @Test
    fun `不限额永远可调用`() {
        assertTrue(AiQuota.canCall(todayCount = 999, dailyLimit = AiConfig.UNLIMITED))
        assertEquals(null, AiQuota.remaining(todayCount = 999, dailyLimit = AiConfig.UNLIMITED))
    }

    @Test
    fun `剩余次数不为负`() {
        assertEquals(15, AiQuota.remaining(todayCount = 5, dailyLimit = 20))
        assertEquals(0, AiQuota.remaining(todayCount = 30, dailyLimit = 20))
    }
}

class AiWireTest {

    @Test
    fun `请求体包含模型与两条消息`() {
        val body = AiWire.requestBody("deepseek-chat", "系统提示", "用户输入")
        assertTrue(body.contains("\"model\":\"deepseek-chat\""))
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("系统提示"))
        assertTrue(body.contains("用户输入"))
    }

    @Test
    fun `解析标准响应的 content`() {
        val response = """
            {"choices":[{"message":{"role":"assistant","content":"修正后的句子"}}]}
        """.trimIndent()
        assertEquals("修正后的句子", AiWire.parseContent(response))
    }

    @Test
    fun `解析多余字段的响应不受影响`() {
        val response = """
            {"id":"x","usage":{},"choices":[{"finish_reason":"stop","message":{"content":"OK"}}]}
        """.trimIndent()
        assertEquals("OK", AiWire.parseContent(response))
    }

    @Test
    fun `结构异常返回 null`() {
        assertNull(AiWire.parseContent("{\"error\":{\"message\":\"bad\"}}"))
        assertNull(AiWire.parseContent("not json"))
    }

    @Test
    fun `错误响应优先取服务端信息`() {
        val response = """{"error":{"message":"Invalid API key"}}"""
        assertEquals("Invalid API key", AiWire.parseErrorMessage(response, 401))
    }

    @Test
    fun `错误响应无信息时按状态码兜底`() {
        assertEquals("API Key 无效或无权限", AiWire.parseErrorMessage("{}", 401))
        assertEquals("调用太频繁或额度不足", AiWire.parseErrorMessage("", 429))
        assertEquals("接口地址不正确（检查 Base URL 与模型名）", AiWire.parseErrorMessage("{}", 404))
        assertEquals("服务端暂时不可用，请稍后再试", AiWire.parseErrorMessage("{}", 503))
        assertEquals("请求失败（HTTP 418）", AiWire.parseErrorMessage("{}", 418))
    }
}
