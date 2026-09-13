package com.japanlearn.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** GitHub Release 最新版信息（更新检查，PRD §19.12）。 */
data class ReleaseInfo(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String?,
)

/**
 * 应用内更新检查判定（PRD §19.12）：全部为纯函数，便于单测。
 * 策略：每 24h（按日历日）静默查一次 GitHub releases/latest，有新版才提示，
 * 用户可跳过当前版本；请求单向拉取，不携带任何用户数据。
 */
object UpdateChecker {

    /** 距上次检查跨了一个日历日才再查；从未查过（null/0）立即查。 */
    fun shouldCheck(lastCheckEpochDay: Long?, todayEpochDay: Long): Boolean {
        if (lastCheckEpochDay == null || lastCheckEpochDay <= 0L) return true
        return todayEpochDay > lastCheckEpochDay
    }

    /** "v1.3.2" → "1.3.2"；无法解析出数字段时返回 null。 */
    fun parseTag(tag: String): String? {
        val cleaned = tag.trim().removePrefix("v").removePrefix("V")
        if (cleaned.isEmpty()) return null
        return cleaned.split('.').all { it.toIntOrNull() != null }.let { ok ->
            if (ok) cleaned else null
        }
    }

    /** 语义版本数值比较（缺失段补 0），latest 严格大于 current 才算新。 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: return false }
        val b = current.split('.').map { it.toIntOrNull() ?: return false }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 有更新且用户没有跳过这个版本时才提示。 */
    fun shouldPrompt(latestVersion: String?, skippedVersion: String?, currentVersion: String): Boolean {
        val latest = latestVersion ?: return false
        if (!isNewer(latest, currentVersion)) return false
        return skippedVersion == null || skippedVersion != latest
    }

    /**
     * 解析 GitHub API `releases/latest` 响应。
     * 取 tag_name / html_url / 第一个 .apk 资产直链；结构不符或 tag 脏返回 null。
     * API 本身不返回 draft 与 prerelease，无需再过滤。
     */
    fun parseReleaseResponse(body: String): ReleaseInfo? = runCatching {
        val root: JsonObject = json.parseToJsonElement(body).jsonObject
        val version = parseTag(root.getValue("tag_name").jsonPrimitive.content) ?: return null
        val releaseUrl = runCatching { root.getValue("html_url").jsonPrimitive.content }.getOrNull().orEmpty()
        val apkUrl = runCatching {
            root.getValue("assets").jsonArray
                .mapNotNull { (it as? JsonObject)?.getValue("browser_download_url")?.jsonPrimitive?.content }
                .firstOrNull { it.endsWith(".apk") }
        }.getOrNull()
        ReleaseInfo(version = version, releaseUrl = releaseUrl, apkUrl = apkUrl)
    }.getOrNull()

    private val json = Json { ignoreUnknownKeys = true }
}
