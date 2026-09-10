package com.japanlearn.app.domain

/**
 * 打字题答案规范化：罗马音按 Modified Hepburn 最长匹配转平假名，
 * 片假名折到平假名。用户键入词库 romaji 原文也算对。
 */
object TypeAnswerNormalizer {

    fun matches(raw: String, accepted: List<String>): Boolean {
        val n = normalize(raw)
        val rawFold = foldInput(raw)
        if (n.isEmpty() && rawFold.isEmpty()) return false
        return accepted.any { it == n || it == rawFold }
    }

    fun normalize(raw: String): String {
        val folded = foldInput(raw)
        val hira = romajiToHiragana(folded)
        return toHiragana(hira)
    }

    fun romajiToHiragana(romaji: String): String {
        val s = foldMacrons(romaji.lowercase())
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\'') {
                i++
                continue
            }
            if (c == '-') {
                out.append('〜')
                i++
                continue
            }
            if (c in '\u3040'..'\u30FF' || c == '〜' || c == 'ー') {
                out.append(c)
                i++
                continue
            }
            // 促音：双写辅音（n 除外）或 tch
            if (i + 2 < s.length && s.startsWith("tch", i)) {
                out.append('っ')
                i++
                continue
            }
            if (i + 1 < s.length) {
                val a = s[i]
                val b = s[i + 1]
                if (a == b && a in SOKUON_CONS) {
                    out.append('っ')
                    i++
                    continue
                }
            }
            val rest = s.substring(i)
            val hit = TABLE.firstOrNull { rest.startsWith(it.first) }
            if (hit != null) {
                out.append(hit.second)
                i += hit.first.length
                continue
            }
            if (s[i] == 'n') {
                out.append('ん')
                i++
                continue
            }
            i++
        }
        return out.toString()
    }

    fun toHiragana(text: String): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    in '\u30A1'..'\u30F6' -> c - 0x60
                    else -> c
                },
            )
        }
    }

    private fun foldInput(raw: String): String =
        raw.trim().lowercase().replace(" ", "").replace("　", "")

    private fun foldMacrons(s: String): String = buildString(s.length * 2) {
        for (c in s) {
            append(MACRON[c] ?: c.toString())
        }
    }

    private val MACRON = mapOf(
        'ā' to "aa", 'ī' to "ii", 'ū' to "uu", 'ē' to "ee", 'ō' to "oo",
        'â' to "aa", 'î' to "ii", 'û' to "uu", 'ê' to "ee", 'ô' to "oo",
    )

    private const val SOKUON_CONS = "kstphgzdjbcf"

    /** 最长优先：拗音 3 拍 → 长音/普通 2 拍 → 1 拍。n 单辅音在循环里收成 ん。 */
    private val TABLE: List<Pair<String, String>> = listOf(
        "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ",
        "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ",
        "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
        "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
        "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
        "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
        "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
        "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ",
        "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
        "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "sa" to "さ", "shi" to "し", "su" to "す", "se" to "せ", "so" to "そ",
        "ta" to "た", "chi" to "ち", "tsu" to "つ", "te" to "て", "to" to "と",
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        "ha" to "は", "hi" to "ひ", "fu" to "ふ", "he" to "へ", "ho" to "ほ",
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        "ya" to "や", "yu" to "ゆ", "yo" to "よ",
        "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        "wa" to "わ", "wo" to "を",
        "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
        "za" to "ざ", "ji" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
        "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
        "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
        "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
        "fa" to "ふぁ", "fi" to "ふぃ", "fe" to "ふぇ", "fo" to "ふぉ",
        "ou" to "おう", "uu" to "うう", "aa" to "ああ", "ii" to "いい", "ee" to "ええ", "oo" to "おお",
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
    ).sortedByDescending { it.first.length }
}
