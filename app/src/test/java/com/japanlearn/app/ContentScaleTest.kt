package com.japanlearn.app

import com.japanlearn.app.data.content.ContentJson
import com.japanlearn.app.data.content.GrammarFile
import com.japanlearn.app.data.content.KanaFile
import com.japanlearn.app.data.content.SentencesFile
import com.japanlearn.app.data.content.WordsFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 锁住首页/统计数字应对齐的内容规模（OPTIMIZATION PR-02）。
 * Gradle 工作目录可能是模块根或仓库根，两条候选路径都试。
 */
class ContentScaleTest {

    @Test
    fun `words json 804`() {
        val file = ContentJson.decodeFromString<WordsFile>(readContent("words.json"))
        assertEquals(804, file.words.size)
    }

    @Test
    fun `grammar json 87`() {
        val file = ContentJson.decodeFromString<GrammarFile>(readContent("grammar.json"))
        assertEquals(87, file.grammar.size)
    }

    @Test
    fun `kana json 101`() {
        val file = ContentJson.decodeFromString<KanaFile>(readContent("kana.json"))
        assertEquals(101, file.kana.size)
    }

    @Test
    fun `sentences json 120`() {
        val file = ContentJson.decodeFromString<SentencesFile>(readContent("sentences.json"))
        assertEquals(120, file.sentences.size)
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
