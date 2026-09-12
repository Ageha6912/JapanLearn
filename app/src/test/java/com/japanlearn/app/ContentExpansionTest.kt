package com.japanlearn.app

import com.japanlearn.app.data.content.ContentJson
import com.japanlearn.app.data.content.GrammarFile
import com.japanlearn.app.data.content.SentencesFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentExpansionTest {

    @Test
    fun `N4 第二批 title 与现网不相交 且每条至少两例两题`() {
        val batch = Json.parseToJsonElement(readTools("new_grammar_n4_b2.json")).jsonObject
        val items = batch.getValue("items").jsonArray
        assertEquals(7, items.size)
        val batchTitles = items.map { it.jsonObject.getValue("title").jsonPrimitive.content }.toSet()
        val existing = ContentJson.decodeFromString<GrammarFile>(readContent("grammar.json"))
        val priorTitles = existing.grammar.filter { !it.id.matches(Regex("g8[1-7]")) }.map { it.title }.toSet()
        assertTrue(
            "batch collided with prior titles: ${batchTitles.intersect(priorTitles)}",
            batchTitles.intersect(priorTitles).isEmpty(),
        )
        items.forEach { el ->
            val obj = el.jsonObject
            val title = obj.getValue("title").jsonPrimitive.content
            val examples = obj.getValue("examples").jsonArray
            val exercises = obj.getValue("exercises").jsonArray
            assertTrue("$title examples", examples.size >= 2)
            assertTrue("$title exercises", exercises.size >= 2)
        }
    }

    @Test
    fun `合并后语法 120 且含点名两条`() {
        val file = ContentJson.decodeFromString<GrammarFile>(readContent("grammar.json"))
        assertEquals(120, file.grammar.size)
        val titles = file.grammar.map { it.title }.toSet()
        assertTrue(titles.contains("～んです / ～のです"))
        assertTrue(titles.contains("～について"))
        assertEquals(7, file.version)
        assertTrue(file.grammar.all { it.unit in 1..11 })
    }

    @Test
    fun `每日一句 180`() {
        val file = ContentJson.decodeFromString<SentencesFile>(readContent("sentences.json"))
        assertEquals(180, file.sentences.size)
        assertEquals(5, file.version)
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

    private fun readTools(name: String): String {
        val candidates = listOf(
            File("tools", name),
            File("../tools", name),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("tools/$name not found; cwd=${File(".").canonicalPath}")
        return file.readText(Charsets.UTF_8)
    }
}
