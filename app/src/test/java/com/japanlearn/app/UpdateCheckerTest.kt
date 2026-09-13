package com.japanlearn.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 应用内更新检查（PRD §19.12）：版本比较、提示判定、GitHub 响应解析。 */
class UpdateCheckerTest {

    // ---- parseTag ----

    @Test
    fun `解析 v 前缀的 tag`() {
        assertEquals("1.3.2", UpdateChecker.parseTag("v1.3.2"))
        assertEquals("1.3.2", UpdateChecker.parseTag("1.3.2"))
    }

    @Test
    fun `脏 tag 返回 null`() {
        assertNull(UpdateChecker.parseTag("abc"))
        assertNull(UpdateChecker.parseTag("v"))
        assertNull(UpdateChecker.parseTag(""))
        assertNull(UpdateChecker.parseTag("v1.x.2"))
    }

    // ---- isNewer（数值段比较）----

    @Test
    fun `更高版本为真`() {
        assertTrue(UpdateChecker.isNewer("1.3.2", "1.3.1"))
        assertTrue(UpdateChecker.isNewer("1.4.0", "1.3.9"))
        // 两位数段必须按数值比较，不能按字符串（"10" < "9" 是字符串序）
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
    }

    @Test
    fun `相同或更旧为假`() {
        assertFalse(UpdateChecker.isNewer("1.3.2", "1.3.2"))
        assertFalse(UpdateChecker.isNewer("1.3.1", "1.3.2"))
    }

    @Test
    fun `段数不同缺失段补零`() {
        assertTrue(UpdateChecker.isNewer("1.4", "1.3.9"))
        assertFalse(UpdateChecker.isNewer("1.3", "1.3.1"))
    }

    @Test
    fun `无法解析为版本号时为假`() {
        assertFalse(UpdateChecker.isNewer("abc", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("1.x.0", "1.0.0"))
    }

    // ---- shouldPrompt ----

    @Test
    fun `有新版本且未跳过时提示`() {
        assertTrue(UpdateChecker.shouldPrompt("1.4.0", null, "1.3.2"))
        assertTrue(UpdateChecker.shouldPrompt("1.4.0", "", "1.3.2"))
    }

    @Test
    fun `用户跳过该版本则不再提示`() {
        assertFalse(UpdateChecker.shouldPrompt("1.4.0", "1.4.0", "1.3.2"))
        // 跳过的是别的版本（更旧的），出新版仍提示
        assertTrue(UpdateChecker.shouldPrompt("1.4.0", "1.3.9", "1.3.2"))
    }

    @Test
    fun `无新版本不提示`() {
        assertFalse(UpdateChecker.shouldPrompt(null, null, "1.3.2"))
        assertFalse(UpdateChecker.shouldPrompt("1.3.2", null, "1.3.2"))
        assertFalse(UpdateChecker.shouldPrompt("1.3.1", null, "1.3.2"))
    }

    // ---- parseReleaseResponse ----

    @Test
    fun `解析完整响应`() {
        val body = """
            {"tag_name":"v1.4.0","html_url":"https://github.com/Ageha6912/JapanLearn/releases/tag/v1.4.0",
             "assets":[{"name":"JapanLearn-v1.4.0.apk","browser_download_url":"https://github.com/Ageha6912/JapanLearn/releases/download/v1.4.0/JapanLearn-v1.4.0.apk"}],
             "prerelease":false,"draft":false}
        """.trimIndent()
        val info = UpdateChecker.parseReleaseResponse(body)
        assertEquals("1.4.0", info?.version)
        assertEquals("https://github.com/Ageha6912/JapanLearn/releases/tag/v1.4.0", info?.releaseUrl)
        assertEquals(
            "https://github.com/Ageha6912/JapanLearn/releases/download/v1.4.0/JapanLearn-v1.4.0.apk",
            info?.apkUrl,
        )
    }

    @Test
    fun `无 apk 资产时 apkUrl 为 null 但仍有 Release 页`() {
        val body = """
            {"tag_name":"v1.4.1","html_url":"https://github.com/Ageha6912/JapanLearn/releases/tag/v1.4.1","assets":[]}
        """.trimIndent()
        val info = UpdateChecker.parseReleaseResponse(body)
        assertEquals("1.4.1", info?.version)
        assertNull(info?.apkUrl)
    }

    @Test
    fun `跳过非 apk 资产取第一个 apk`() {
        val body = """
            {"tag_name":"v1.4.2","html_url":"u","assets":[
              {"name":"checksums.txt","browser_download_url":"https://x/checksums.txt"},
              {"name":"app.apk","browser_download_url":"https://x/app.apk"}]}
        """.trimIndent()
        assertEquals("https://x/app.apk", UpdateChecker.parseReleaseResponse(body)?.apkUrl)
    }

    @Test
    fun `坏 JSON 或脏 tag 返回 null`() {
        assertNull(UpdateChecker.parseReleaseResponse("not json"))
        assertNull(UpdateChecker.parseReleaseResponse("""{"tag_name":"release-2026"}"""))
        assertNull(UpdateChecker.parseReleaseResponse("""{"html_url":"u"}"""))
    }
}
