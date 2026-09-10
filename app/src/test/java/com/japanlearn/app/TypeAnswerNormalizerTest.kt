package com.japanlearn.app

import com.japanlearn.app.data.content.ContentJson
import com.japanlearn.app.data.content.WordsFile
import com.japanlearn.app.domain.TypeAnswerNormalizer
import com.japanlearn.app.domain.TypeAnswerScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TypeAnswerNormalizerTest {

    @Test
    fun `taberu 大小写假名片假名都匹配`() {
        val accepted = listOf("たべる", "taberu")
        assertTrue(TypeAnswerNormalizer.matches("taberu", accepted))
        assertTrue(TypeAnswerNormalizer.matches("TABERU", accepted))
        assertTrue(TypeAnswerNormalizer.matches("たべる", accepted))
        assertTrue(TypeAnswerNormalizer.matches("タベル", accepted))
        assertFalse(TypeAnswerNormalizer.matches("nomu", accepted))
        assertFalse(TypeAnswerNormalizer.matches("  ", accepted))
    }

    @Test
    fun `拨音 sensei 促音 nippon`() {
        assertEquals("せんせい", TypeAnswerNormalizer.romajiToHiragana("sensei"))
        assertEquals("にっぽん", TypeAnswerNormalizer.romajiToHiragana("nippon"))
    }

    @Test
    fun `submitTyped 计分 对错都加总 只有对加正确`() {
        assertEquals(TypeAnswerScoring.Score(1, 1), TypeAnswerScoring.afterSubmit(true, 0, 0))
        assertEquals(TypeAnswerScoring.Score(2, 1), TypeAnswerScoring.afterSubmit(false, 1, 1))
    }

    @Test
    fun `词库罗马音转写金标 失败项须在 allowlist`() {
        val file = ContentJson.decodeFromString<WordsFile>(readContent("words.json"))
        val failed = file.words.mapNotNull { w ->
            val got = TypeAnswerNormalizer.romajiToHiragana(w.romaji)
            val expect = TypeAnswerNormalizer.toHiragana(w.kana)
            if (got == expect) null else "${w.id} ${w.ja} romaji=${w.romaji} kana=${w.kana} got=$got"
        }
        val unexpected = failed.filter { it.substringBefore(' ') !in ROMAJI_ALLOWLIST }
        assertTrue(
            "unexpected romaji mismatches (${unexpected.size}):\n${unexpected.joinToString("\n")}",
            unexpected.isEmpty(),
        )
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

    companion object {
        /** 助词读音、长音习惯等与整词 romaji 不完全对应的条目。 */
        val ROMAJI_ALLOWLIST = setOf(
            "w057", "w058", "w098", "w115", "w208", "w209", "w235",
            "w261", "w263", "w267", "w268", "w269", "w270", "w287", "w288",
            "w297", "w298", "w306", "w325", "w363", "w364", "w368", "w377",
            "w380", "w391", "w454", "w491", "w496", "w519", "w531",
            "w559", "w560", "w567", "w568", "w569", "w573", "w574", "w575",
            "w576", "w577", "w584", "w586", "w587", "w598", "w600", "w604",
            "w606", "w609", "w616", "w658", "w670", "w692",
        )
    }
}
