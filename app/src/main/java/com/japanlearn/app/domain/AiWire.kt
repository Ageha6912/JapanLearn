package com.japanlearn.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

    fun requestBody(model: String, systemPrompt: String, userPrompt: String): String =
        buildJsonObject {
            put("model", model)
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

    /** 取 choices[0].message.content；结构不符返回 null。 */
    fun parseContent(responseJson: String): String? = runCatching {
        val root = json.parseToJsonElement(responseJson).jsonObject
        val choices = root.getValue("choices").jsonArray
        val message = (choices[0] as JsonObject).getValue("message").jsonObject
        message.getValue("content").jsonPrimitive.content
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
}
