package com.japanlearn.app.data.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------- assets 的 content 目录下 JSON 对应的 DTO（PRD §17.6） ----------

@Serializable
data class KanaFile(val version: Int, val kana: List<KanaItem>)

@Serializable
data class KanaItem(val id: String, val h: String, val k: String, val r: String, val exJa: String, val exZh: String)

@Serializable
data class WordsFile(val version: Int, val words: List<WordItem>)

@Serializable
data class WordItem(
    val id: String,
    val ja: String,
    val kana: String,
    val romaji: String,
    val zh: String,
    val pos: String,
    val cat: String,
    val example: String,
    val exampleZh: String,
)

@Serializable
data class GrammarFile(val version: Int, val grammar: List<GrammarItem>)

@Serializable
data class GrammarItem(
    val id: String,
    val title: String,
    val meaning: String,
    val connection: String,
    val explanation: String,
    val examples: List<Example>,
    val exercises: List<Exercise>,
)

@Serializable
data class Example(val ja: String, val zh: String)

@Serializable
data class Exercise(val question: String, val options: List<String>, val answer: Int, val explanation: String)

@Serializable
data class SentencesFile(val version: Int, val sentences: List<SentenceItem>)

@Serializable
data class SentenceItem(val id: String, val scene: String, val ja: String, val zh: String, val breakdown: List<Breakdown>)

@Serializable
data class Breakdown(val t: String, val zh: String)

/** 解析容错：忽略未知字段，防止内容新增字段导致旧版本崩溃。 */
val ContentJson = Json { ignoreUnknownKeys = true }
