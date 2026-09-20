package com.japanlearn.app

import com.japanlearn.app.data.content.ContentJson
import com.japanlearn.app.data.content.GrammarFile
import com.japanlearn.app.data.content.KanaFile
import com.japanlearn.app.data.content.KanjiFile
import com.japanlearn.app.data.content.SentencesFile
import com.japanlearn.app.data.content.WordsFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 锁住首页/统计数字应对齐的内容规模（OPTIMIZATION PR-02）。
 * Gradle 工作目录可能是模块根或仓库根，两条候选路径都试。
 */
class ContentScaleTest {

    @Test
    fun `words json 1104`() {
        val file = ContentJson.decodeFromString<WordsFile>(readContent("words.json"))
        assertEquals(1104, file.words.size)
    }

    @Test
    fun `grammar json 148`() {
        val file = ContentJson.decodeFromString<GrammarFile>(readContent("grammar.json"))
        assertEquals(148, file.grammar.size)
    }

    @Test
    fun `kana json 101`() {
        val file = ContentJson.decodeFromString<KanaFile>(readContent("kana.json"))
        assertEquals(101, file.kana.size)
    }

    @Test
    fun `sentences json 300`() {
        val file = ContentJson.decodeFromString<SentencesFile>(readContent("sentences.json"))
        assertEquals(300, file.sentences.size)
    }

    @Test
    fun `kanji json 597 字 N5 397 + N4 200`() {
        val file = ContentJson.decodeFromString<KanjiFile>(readContent("kanji.json"))
        assertEquals(597, file.kanji.size)
        assertEquals(397, file.kanji.count { it.level == "N5" })
        assertEquals(200, file.kanji.count { it.level == "N4" })
        assertTrue(file.kanji.all { it.examples.isNotEmpty() })
    }

    @Test
    fun `words 覆盖两个级别各 11 个课程单元`() {
        val file = ContentJson.decodeFromString<WordsFile>(readContent("words.json"))
        val pairs = file.words.map { it.level to it.unit }.toSet()
        assertEquals(22, pairs.size)
        assertTrue(file.words.all { it.unit in 1..11 })
    }

    @Test
    fun `grammar 单元都在 1 到 11 之间`() {
        val file = ContentJson.decodeFromString<GrammarFile>(readContent("grammar.json"))
        assertTrue(file.grammar.all { it.unit in 1..11 })
        assertEquals(11, file.grammar.map { it.unit }.distinct().size)
    }

    private fun readContent(name: String): String {
        val candidates = listOf(
            File("src/main/assets/content", name),
            File("app/src/main/assets/content", name),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("content/$name not found; cwd=${File(".").canonicalPath}")
        return file.readText(Charsets.UTF_8)
    }
}
