package com.japanlearn.app.data.content

enum class ContentKind { KANA, WORDS, GRAMMAR, SENTENCES }

data class ContentVersions(
    val kana: Int,
    val words: Int,
    val grammar: Int,
    val sentences: Int,
) {
    fun total(): Int = kana + words + grammar + sentences

    companion object {
        val ZERO = ContentVersions(0, 0, 0, 0)

        const val KEY_KANA = "content_version_kana"
        const val KEY_WORDS = "content_version_words"
        const val KEY_GRAMMAR = "content_version_grammar"
        const val KEY_SENTENCES = "content_version_sentences"
        /** 0.4.x 加总 key。0.5.0 双写、不删除，便于回滚旧 APK。 */
        const val LEGACY_TOTAL = "content_version"

        fun fromMeta(get: (String) -> String?): ContentVersions = ContentVersions(
            kana = get(KEY_KANA)?.toIntOrNull() ?: 0,
            words = get(KEY_WORDS)?.toIntOrNull() ?: 0,
            grammar = get(KEY_GRAMMAR)?.toIntOrNull() ?: 0,
            sentences = get(KEY_SENTENCES)?.toIntOrNull() ?: 0,
        )
    }
}

/**
 * 内容装载规划器（纯函数）：决定重装哪些表、删除哪些过期 id。
 * 写入与 Room 事务在 [com.japanlearn.app.data.ContentLoader]。
 */
object ContentSeedPlanner {

    /**
     * 仅当「有加总 key 且四把新 key 全缺」时视为 0.4.x 安装，强制四文件重装。
     * 双写安装（新旧 key 并存）不得走这条。
     */
    fun hasLegacyTotalOnly(legacyTotal: String?, perFileKana: String?): Boolean =
        legacyTotal != null && perFileKana == null

    fun kindsToReload(
        installed: ContentVersions,
        incoming: ContentVersions,
        hasLegacyTotalOnly: Boolean,
    ): Set<ContentKind> {
        if (hasLegacyTotalOnly) return ContentKind.entries.toSet()
        return buildSet {
            if (installed.kana != incoming.kana) add(ContentKind.KANA)
            if (installed.words != incoming.words) add(ContentKind.WORDS)
            if (installed.grammar != incoming.grammar) add(ContentKind.GRAMMAR)
            if (installed.sentences != incoming.sentences) add(ContentKind.SENTENCES)
        }
    }

    /**
     * 计算应删除的旧 id。incoming 为空则拒绝（防止误把半份列表当全量，差集过大）。
     * 调用方必须传入该文件的完整 id 列表；空差集时不要调用 DAO。
     */
    fun idsToDelete(existing: Set<String>, incoming: Set<String>): List<String> {
        require(incoming.isNotEmpty()) { "incoming ids must not be empty" }
        return (existing - incoming).toList()
    }
}
