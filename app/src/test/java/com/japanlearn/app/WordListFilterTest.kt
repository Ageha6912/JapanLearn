package com.japanlearn.app

import com.japanlearn.app.data.local.WordEntity
import com.japanlearn.app.domain.Mastery
import com.japanlearn.app.domain.WordListFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordListFilterTest {

    private fun word(
        id: String = "w001",
        ja: String = "私",
        kana: String = "わたし",
        romaji: String = "watashi",
        zh: String = "我",
        cat: String = "人物",
    ) = WordEntity(
        id = id, ja = ja, kana = kana, romaji = romaji, zh = zh,
        pos = "代词", cat = cat, example = "私は学生です。", exampleZh = "我是学生。",
        level = "N5", order = 0,
    )

    @Test
    fun `空白查询命中全部`() {
        assertTrue(WordListFilter.matches(word(), query = "  ", cat = null, mastery = null))
    }

    @Test
    fun `罗马音大小写不敏感`() {
        val w = word()
        assertTrue(WordListFilter.matches(w, "WATASHI", cat = null, mastery = null))
        assertTrue(WordListFilter.matches(w, " wata ", cat = null, mastery = null))
        assertFalse(WordListFilter.matches(w, "taberu", cat = null, mastery = null))
    }

    @Test
    fun `按分类筛选`() {
        val w = word(cat = "人物")
        assertTrue(WordListFilter.matches(w, "", WordListFilter.CAT_ALL, mastery = null))
        assertTrue(WordListFilter.matches(w, "", "人物", mastery = null))
        assertFalse(WordListFilter.matches(w, "", "食物", mastery = null))
    }

    @Test
    fun `未学筛选只留 mastery 为空的词`() {
        val w = word()
        assertTrue(
            WordListFilter.matches(w, "", null, mastery = null, masteryFilter = WordListFilter.MASTERY_UNLEARNED),
        )
        assertFalse(
            WordListFilter.matches(
                w, "", null, mastery = Mastery.FUZZY.level,
                masteryFilter = WordListFilter.MASTERY_UNLEARNED,
            ),
        )
    }

    @Test
    fun `熟悉筛选排除未学与其他档`() {
        val w = word()
        assertTrue(
            WordListFilter.matches(w, "", null, mastery = Mastery.KNOWN.level, masteryFilter = Mastery.KNOWN.level),
        )
        assertFalse(
            WordListFilter.matches(w, "", null, mastery = null, masteryFilter = Mastery.KNOWN.level),
        )
        assertFalse(
            WordListFilter.matches(w, "", null, mastery = Mastery.FUZZY.level, masteryFilter = Mastery.KNOWN.level),
        )
    }
}
