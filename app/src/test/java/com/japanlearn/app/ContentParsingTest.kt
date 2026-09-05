package com.japanlearn.app

import com.japanlearn.app.data.content.Breakdown
import com.japanlearn.app.data.content.ContentJson
import com.japanlearn.app.data.content.Example
import com.japanlearn.app.data.content.Exercise
import com.japanlearn.app.data.content.GrammarFile
import com.japanlearn.app.data.content.KanaFile
import com.japanlearn.app.data.content.SentencesFile
import com.japanlearn.app.data.content.WordItem
import com.japanlearn.app.data.content.WordsFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内容 JSON Schema 解析测试（PRD §17.8）。
 */
class ContentParsingTest {

    @Test
    fun `解析五十音数据`() {
        val json = """
            {"version":1,"kana":[
              {"id":"k01","h":"あ","k":"ア","r":"a","exJa":"あさ","exZh":"早晨"}
            ]}
        """.trimIndent()
        val file = ContentJson.decodeFromString<KanaFile>(json)
        assertEquals(1, file.version)
        assertEquals(1, file.kana.size)
        with(file.kana[0]) {
            assertEquals("あ", h)
            assertEquals("ア", k)
            assertEquals("a", r)
            assertEquals("あさ", exJa)
            assertEquals("早晨", exZh)
        }
    }

    @Test
    fun `解析单词数据`() {
        val json = """
            {"version":1,"words":[
              {"id":"w001","ja":"食べる","kana":"たべる","romaji":"taberu","zh":"吃",
               "pos":"动词","cat":"动作","example":"毎日ご飯を食べます。","exampleZh":"每天吃饭。"}
            ]}
        """.trimIndent()
        val file = ContentJson.decodeFromString<WordsFile>(json)
        val w: WordItem = file.words.single()
        assertEquals("食べる", w.ja)
        assertEquals("たべる", w.kana)
        assertEquals("吃", w.zh)
        assertEquals("动作", w.cat)
    }

    @Test
    fun `解析语法数据 含例句与练习`() {
        val json = """
            {"version":1,"grammar":[
              {"id":"g01","title":"～です","meaning":"是……","connection":"名词 + です","explanation":"判断句。",
               "examples":[{"ja":"私は学生です。","zh":"我是学生。"}],
               "exercises":[{"question":"私は学生＿＿。","options":["です","ます","でした","ません"],"answer":0,"explanation":"名词句。"}]}
            ]}
        """.trimIndent()
        val file = ContentJson.decodeFromString<GrammarFile>(json)
        val g = file.grammar.single()
        assertEquals("～です", g.title)
        val examples: List<Example> = g.examples
        assertEquals("私は学生です。", examples[0].ja)
        val exercises: List<Exercise> = g.exercises
        assertEquals(4, exercises[0].options.size)
        assertEquals(0, exercises[0].answer)
    }

    @Test
    fun `解析每日一句 含词汇拆解`() {
        val json = """
            {"version":1,"sentences":[
              {"id":"s01","scene":"日常聊天","ja":"ちょっと待ってください。","zh":"请稍等一下。",
               "breakdown":[{"t":"ちょっと","zh":"稍微"},{"t":"待って","zh":"等待"}]}
            ]}
        """.trimIndent()
        val file = ContentJson.decodeFromString<SentencesFile>(json)
        val s = file.sentences.single()
        val breakdown: List<Breakdown> = s.breakdown
        assertEquals(2, breakdown.size)
        assertEquals("ちょっと", breakdown[0].t)
    }

    @Test
    fun `容忍未知字段 保证内容向前兼容`() {
        val json = """
            {"version":1,"kana":[
              {"id":"k01","h":"あ","k":"ア","r":"a","exJa":"あさ","exZh":"早晨","futureField":"x"}
            ],"extra":123}
        """.trimIndent()
        val file = ContentJson.decodeFromString<KanaFile>(json)
        assertTrue(file.kana.isNotEmpty())
    }
}
