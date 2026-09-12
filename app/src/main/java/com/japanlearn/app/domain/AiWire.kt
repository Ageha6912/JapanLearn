package com.japanlearn.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OpenAI /chat/completions 线格式（PRD §19.9）：请求体构建、响应解析、错误信息提取。
 * 纯字符串函数，便于单元测试；网络层只负责 HTTP。
 */
object AiWire {

    private val json = Json { ignoreUnknownKeys = true }

    fun requestBody(model: String, systemPrompt: String, userPrompt: String, stream: Boolean = false): String =
        buildJsonObject {
            put("model", model)
            if (stream) put("stream", true)
            put(
                "messages",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        },
                        buildJsonObject {
                            put("role", "user")
                            put("content", userPrompt)
                        },
                    ),
                ),
            )
        }.toString()

    /** 取 choices[0].message.content；结构不符或 content 为 JSON null 返回 null。 */
    fun parseContent(responseJson: String): String? = runCatching {
        val root = json.parseToJsonElement(responseJson).jsonObject
        val choices = root.getValue("choices").jsonArray
        val message = (choices[0] as JsonObject).getValue("message").jsonObject
        textOrNull(message.getValue("content"))
    }.getOrNull()

    /** 错误响应里尽量提取可读信息；取不到时按 HTTP 码给兜底文案。 */
    fun parseErrorMessage(responseJson: String, httpCode: Int): String {
        val detail = runCatching {
            val root = json.parseToJsonElement(responseJson).jsonObject
            val error = root.getValue("error")
            (error as? JsonObject)?.getValue("message")?.jsonPrimitive?.content
                ?: error.jsonPrimitive.content
        }.getOrNull()
        return detail ?: when (httpCode) {
            401, 403 -> "API Key 无效或无权限"
            404 -> "接口地址不正确（检查 Base URL 与模型名）"
            429 -> "调用太频繁或额度不足"
            in 500..599 -> "服务端暂时不可用，请稍后再试"
            else -> "请求失败（HTTP $httpCode）"
        }
    }

    /**
     * 解析一行 SSE（流式，PRD §19.11），返回该行的增量文本；非数据行返回 null。
     * 覆盖：`data: {...}` 增量块、`data: [DONE]` 终止行、`: keep-alive` 注释行、残缺 JSON。
     * 思考模型（如 deepseek-reasoner）思考阶段的 chunk 是 `delta.content: null` +
     * `reasoning_content`，必须丢弃，否则 JsonNull 的字面量 "null" 会被拼进回答。
     */
    fun parseStreamDelta(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        return runCatching {
            val root = json.parseToJsonElement(payload).jsonObject
            val choices = root.getValue("choices").jsonArray
            if (choices.isEmpty()) return null
            val delta = (choices[0] as JsonObject).getValue("delta").jsonObject
            textOrNull(delta["content"] ?: return null)
        }.getOrNull()
    }

    /** 只接受 JSON 字符串字面量；JsonNull / 数字 / 布尔 / 对象 / 数组一律 null。 */
    private fun textOrNull(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString && it !is JsonNull }?.content
}
