package com.japanlearn.app

import com.japanlearn.app.data.content.ContentKind
import com.japanlearn.app.data.content.ContentSeedPlanner
import com.japanlearn.app.data.content.ContentVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSeedPlannerTest {

    private val incoming = ContentVersions(kana = 2, words = 6, grammar = 4, sentences = 2, kanji = 1)

    @Test
    fun `五文件版本全相等 不重装`() {
        val kinds = ContentSeedPlanner.kindsToReload(incoming, incoming, hasLegacyTotalOnly = false)
        assertTrue(kinds.isEmpty())
    }

    @Test
    fun `加总相同但单文件不同 只重装变化文件`() {
        val installed = ContentVersions(kana = 2, words = 6, grammar = 4, sentences = 2, kanji = 1)
        val bumped = ContentVersions(kana = 2, words = 7, grammar = 3, sentences = 2, kanji = 1)
        val kinds = ContentSeedPlanner.kindsToReload(installed, bumped, hasLegacyTotalOnly = false)
        assertEquals(setOf(ContentKind.WORDS, ContentKind.GRAMMAR), kinds)
    }

    @Test
    fun `汉字版本变化只重装汉字`() {
        val installed = ContentVersions(kana = 2, words = 6, grammar = 4, sentences = 2, kanji = 1)
        val bumped = installed.copy(kanji = 2)
        val kinds = ContentSeedPlanner.kindsToReload(installed, bumped, hasLegacyTotalOnly = false)
        assertEquals(setOf(ContentKind.KANJI), kinds)
    }

    @Test
    fun `首次安装 全 0 无 legacy 全文件重装`() {
        val kinds = ContentSeedPlanner.kindsToReload(
            ContentVersions.ZERO,
            incoming,
            hasLegacyTotalOnly = false,
        )
        assertEquals(ContentKind.entries.toSet(), kinds)
    }

    @Test
    fun `仅有加总 key 无分文件 key 视为旧安装 全文件重装`() {
        assertTrue(ContentSeedPlanner.hasLegacyTotalOnly("14", perFileKana = null))
        val kinds = ContentSeedPlanner.kindsToReload(incoming, incoming, hasLegacyTotalOnly = true)
        assertEquals(ContentKind.entries.toSet(), kinds)
    }

    @Test
    fun `双写安装 不走 legacy 全量重装 按每文件比较`() {
        assertFalse(ContentSeedPlanner.hasLegacyTotalOnly("14", perFileKana = "2"))
        val installed = incoming
        val bumpedWords = incoming.copy(words = 7)
        val kinds = ContentSeedPlanner.kindsToReload(installed, bumpedWords, hasLegacyTotalOnly = false)
        assertEquals(setOf(ContentKind.WORDS), kinds)
    }

    @Test
    fun `无任何 meta 不是 legacy-only`() {
        assertFalse(ContentSeedPlanner.hasLegacyTotalOnly(null, null))
    }

    @Test
    fun `fromMeta 缺 key 视为 0`() {
        val versions = ContentVersions.fromMeta { null }
        assertEquals(ContentVersions.ZERO, versions)
        assertEquals(0, versions.total())
    }

    @Test
    fun `fromMeta 读五把新 key`() {
        val meta = mapOf(
            ContentVersions.KEY_KANA to "2",
            ContentVersions.KEY_WORDS to "6",
            ContentVersions.KEY_GRAMMAR to "4",
            ContentVersions.KEY_SENTENCES to "2",
            ContentVersions.KEY_KANJI to "1",
        )
        assertEquals(incoming, ContentVersions.fromMeta { meta[it] })
        assertEquals(15, incoming.total())
    }

    @Test
    fun `idsToDelete 返回差集`() {
        val existing = setOf("w001", "w002", "w003")
        val incomingIds = setOf("w001", "w003", "w004")
        assertEquals(listOf("w002"), ContentSeedPlanner.idsToDelete(existing, incomingIds))
    }

    @Test
    fun `idsToDelete incoming 为空则抛 以免误清空`() {
        try {
            ContentSeedPlanner.idsToDelete(setOf("w001"), emptySet())
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("incoming ids must not be empty"))
        }
    }

    @Test
    fun `idsToDelete incoming 是完整新表时只返回被去掉的 id`() {
        val existing = setOf("w001", "w002", "w003")
        val incomingIds = setOf("w001", "w002")
        assertEquals(listOf("w003"), ContentSeedPlanner.idsToDelete(existing, incomingIds))
    }

    @Test
    fun `idsToDelete 完全覆盖时差集为空 调用方不得打 DAO`() {
        val ids = setOf("k01", "k02")
        assertTrue(ContentSeedPlanner.idsToDelete(ids, ids).isEmpty())
    }
}
