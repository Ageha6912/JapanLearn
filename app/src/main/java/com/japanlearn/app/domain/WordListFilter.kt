package com.japanlearn.app.domain

import com.japanlearn.app.data.local.WordEntity

/** 单词列表本地搜索 / 分类 / 掌握度筛选（纯函数）。 */
object WordListFilter {
    const val CAT_ALL = "全部"
    /** 掌握度筛选：未学（masteryMap 无记录）。 */
    const val MASTERY_UNLEARNED = -1

    /**
     * @param cat null 或 [CAT_ALL] 表示不限分类
     * @param mastery 该词当前掌握度，null = 未学
     * @param masteryFilter null = 不限；[MASTERY_UNLEARNED] = 未学；0..3 = [Mastery.level]
     */
    fun matches(
        word: WordEntity,
        query: String,
        cat: String?,
        mastery: Int?,
        masteryFilter: Int? = null,
    ): Boolean {
        if (!textMatches(word, query)) return false
        if (!cat.isNullOrEmpty() && cat != CAT_ALL && word.cat != cat) return false
        return masteryMatches(mastery, masteryFilter)
    }

    fun textMatches(word: WordEntity, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        val q = needle.lowercase()
        return word.ja.lowercase().contains(q) ||
            word.kana.lowercase().contains(q) ||
            word.romaji.lowercase().contains(q) ||
            word.zh.lowercase().contains(q)
    }

    fun masteryMatches(current: Int?, filter: Int?): Boolean = when (filter) {
        null -> true
        MASTERY_UNLEARNED -> current == null
        else -> current == filter
    }
}
