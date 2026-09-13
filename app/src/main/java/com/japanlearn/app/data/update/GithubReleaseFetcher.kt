package com.japanlearn.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 拉取 GitHub 最新 Release 信息（更新检查，PRD §19.12）。
 * 单向 GET、短超时、任何网络/HTTP 错误一律返回 null（静默跳过，不打扰用户）。
 */
class GithubReleaseFetcher(
    private val latestUrl: String = DEFAULT_URL,
) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatest(): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(
                Request.Builder()
                    .url(latestUrl)
                    .header("Accept", "application/vnd.github+json")
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
            }
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_URL = "https://api.github.com/repos/Ageha6912/JapanLearn/releases/latest"
        const val RELEASES_PAGE = "https://github.com/Ageha6912/JapanLearn/releases/latest"
    }
}
