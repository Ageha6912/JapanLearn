package com.japanlearn.app

import com.japanlearn.app.domain.WrongPortrait
import com.japanlearn.app.domain.WrongPortrait.CategoryCount
import com.japanlearn.app.domain.WrongPortrait.Entry
import com.japanlearn.app.domain.WrongPortrait.TopItem
import com.japanlearn.app.domain.WrongPortrait.WordMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class WrongPortraitTest {

    private fun e(
        type: String,
        id: String,
        count: Int,
        at: Long = 0L,
        primary: String = id,
    ) = Entry(type, id, count, at, primary)

    @Test
    fun `空错题返回空画像`() {
        val p = WrongPortrait.build(emptyList())
        assertEquals(0, p.total)
        assertEquals(emptyMap<String, Int>(), p.byType)
        assertEquals(emptyList<TopItem>(), p.topRepeat)
        assertEquals(emptyList<CategoryCount>(), p.topCategories)
    }

    @Test
    fun `按类型计数`() {
        val p = WrongPortrait.build(
            listOf(e("word", "w1", 1), e("word", "w2", 2), e("kana", "k1", 1), e("grammar", "g1", 3)),
        )
        assertEquals(4, p.total)
        assertEquals(2, p.byType["word"])
        assertEquals(1, p.byType["kana"])
        assertEquals(1, p.byType["grammar"])
    }

    @Test
    fun `反复错按次数降序并列看最近时间`() {
        val p = WrongPortrait.build(
            listOf(
                e("word", "wA", 2, at = 100, primary = "A"),
                e("word", "wB", 3, at = 50, primary = "B"),
                e("word", "wC", 2, at = 200, primary = "C"),
            ),
            topN = 3,
        )
        assertEquals(listOf("B", "C", "A"), p.topRepeat.map { it.primary })
    }

    @Test
    fun `topN 截断`() {
        val entries = (1..10).map { e("word", "w$it", it, primary = "n$it") }
        val p = WrongPortrait.build(entries, topN = 3)
        assertEquals(3, p.topRepeat.size)
        assertEquals("n10", p.topRepeat[0].primary)
    }

    @Test
    fun `分类热区只统计单词且按条目数`() {
        val p = WrongPortrait.build(
            listOf(
                e("word", "w1", 5),
                e("word", "w2", 1),
                e("word", "w3", 1),
                e("grammar", "g1", 9),
            ),
            wordCategories = listOf(
                WordMeta("w1", "食物"),
                WordMeta("w2", "食物"),
                WordMeta("w3", "时间"),
            ),
            topN = 5,
        )
        assertEquals(listOf(CategoryCount("食物", 2), CategoryCount("时间", 1)), p.topCategories)
    }

    @Test
    fun `分类热区并列按名称排序保证稳定`() {
        val p = WrongPortrait.build(
            listOf(e("word", "w1", 1), e("word", "w2", 1)),
            wordCategories = listOf(WordMeta("w2", "时间"), WordMeta("w1", "食物")),
            topN = 5,
        )
        assertEquals(listOf("时间", "食物"), p.topCategories.map { it.category }.sorted())
        // 构建结果应已按 count desc + name asc：两个 count=1 时名称升序
        assertEquals(listOf("时间", "食物"), p.topCategories.map { it.category })
    }
}
