package com.japanlearn.app.data.ai

import com.japanlearn.app.domain.AiConfig
import com.japanlearn.app.domain.AiWire
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** AI 请求异常：message 为可直接展示的中文文案。 */
class AiException(message: String) : Exception(message)

data class AiCompletionRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
)

/** AI 服务抽象；单测用 fake 实现，不打真网。 */
interface AiClient {
    suspend fun complete(request: AiCompletionRequest): String
}

/** OpenAI /chat/completions 兼容实现（DeepSeek / 智谱 GLM / OpenAI / Ollama 均适用）。 */
class OpenAiCompatibleClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) : AiClient {

    private val jsonMedia = "application/json".toMediaType()

    override suspend fun complete(request: AiCompletionRequest): String = withContext(Dispatchers.IO) {
        val body = AiWire.requestBody(request.model, request.systemPrompt, request.userPrompt)
            .toRequestBody(jsonMedia)
        val call = Request.Builder()
            .url(AiConfig.normalizeBaseUrl(request.baseUrl))
            .header("Authorization", "Bearer ${request.apiKey}")
            .post(body)
            .build()
        try {
            http.newCall(call).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AiException(AiWire.parseErrorMessage(text, response.code))
                }
                AiWire.parseContent(text) ?: throw AiException("响应格式异常，请检查模型名是否正确")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException("网络不可用或接口无法连接")
        }
    }
}
