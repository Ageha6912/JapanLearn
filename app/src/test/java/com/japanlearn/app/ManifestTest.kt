package com.japanlearn.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * manifest 关键声明守护（v1.2.1 热修复教训：AI 助手上线时漏声明 INTERNET，
 * 所有请求在 socket 层被拦，错误文案「网络不可用」掩盖了真实原因）。
 */
class ManifestTest {

    private fun readManifest(): String {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found; cwd=${File(".").canonicalPath}")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun `已声明 INTERNET 权限`() {
        assertTrue(readManifest().contains("android.permission.INTERNET"))
    }

    @Test
    fun `已声明通知权限`() {
        assertTrue(readManifest().contains("android.permission.POST_NOTIFICATIONS"))
    }
}
