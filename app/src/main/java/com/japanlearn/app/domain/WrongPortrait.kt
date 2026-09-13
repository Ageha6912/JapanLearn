package com.japanlearn.app.domain

/**
 * 错题画像（PRD §19.13）：待攻克总量、按类型分布、反复错 Top N、单词分类热区。
 * 与 Room 解耦，调用方先映射成 [Entry] / [WordMeta]。
 */
object WrongPortrait {

    data class Entry(
        val contentType: String,
        val contentId: String,
        val wrongCount: Int,
        val lastWrongAt: Long,
        /** 展示名（词形 / 语法标题 / 假名）。 */
        val primary: String,
    )

    data class WordMeta(val id: String, val category: String)

    data class TopItem(
        val contentType: String,
        val contentId: String,
        val primary: String,
        val wrongCount: Int,
    )

    data class CategoryCount(val category: String, val count: Int)

    data class Portrait(
        val total: Int = 0,
        /** word / grammar / kana → 条数。 */
        val byType: Map<String, Int> = emptyMap(),
        /** wrongCount 降序；并列按 lastWrongAt 降序。 */
        val topRepeat: List<TopItem> = emptyList(),
        /** 仅单词，按条目数（非 wrongCount 累计）降序。 */
        val topCategories: List<CategoryCount> = emptyList(),
    )

    fun build(
        entries: List<Entry>,
        wordCategories: List<WordMeta> = emptyList(),
        topN: Int = 5,
    ): Portrait {
        if (entries.isEmpty()) return Portrait()
        val byType = entries.groupingBy { it.contentType }.eachCount()
        val topRepeat = entries
            .sortedWith(compareByDescending<Entry> { it.wrongCount }.thenByDescending { it.lastWrongAt })
            .take(topN.coerceAtLeast(0))
            .map { TopItem(it.contentType, it.contentId, it.primary, it.wrongCount) }
        val catById = wordCategories.associate { it.id to it.category }
        val topCategories = entries
            .filter { it.contentType == "word" }
            .mapNotNull { e -> catById[e.contentId] }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(topN.coerceAtLeast(0))
            .map { CategoryCount(it.first, it.second) }
        return Portrait(
            total = entries.size,
            byType = byType,
            topRepeat = topRepeat,
            topCategories = topCategories,
        )
    }
}
