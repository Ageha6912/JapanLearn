package com.japanlearn.app.data

import android.content.Context
import com.japanlearn.app.data.content.Breakdown
import com.japanlearn.app.data.content.ContentJson
import com.japanlearn.app.data.content.Example
import com.japanlearn.app.data.content.Exercise
import com.japanlearn.app.data.content.GrammarFile
import com.japanlearn.app.data.content.KanaFile
import com.japanlearn.app.data.content.SentencesFile
import com.japanlearn.app.data.content.WordsFile
import com.japanlearn.app.data.local.AppDatabase
import com.japanlearn.app.data.local.GrammarEntity
import com.japanlearn.app.data.local.KanaEntity
import com.japanlearn.app.data.local.MetaEntity
import com.japanlearn.app.data.local.SentenceEntity
import com.japanlearn.app.data.local.WordEntity
import kotlinx.serialization.encodeToString

/**
 * 内容装载（PRD §17.6）：首次启动或内容版本升级时，把 assets 的 content 目录下四个 JSON 装入 Room。
 * 学习进度表不受内容重装影响。
 */
class ContentLoader(private val context: Context, private val db: AppDatabase) {

    suspend fun seedIfNeeded() {
        val kana = ContentJson.decodeFromString<KanaFile>(readAsset("kana.json"))
        val words = ContentJson.decodeFromString<WordsFile>(readAsset("words_n5.json"))
        val grammar = ContentJson.decodeFromString<GrammarFile>(readAsset("grammar_n5.json"))
        val sentences = ContentJson.decodeFromString<SentencesFile>(readAsset("sentences.json"))
        val totalVersion = kana.version + words.version + grammar.version + sentences.version

        val installed = db.metaDao().get(KEY_CONTENT_VERSION)
        if (installed == totalVersion.toString()) return

        db.kanaDao().insertAll(kana.kana.mapIndexed { i, k ->
            KanaEntity(id = k.id, hiragana = k.h, katakana = k.k, romaji = k.r, groupName = k.group, exampleJa = k.exJa, exampleZh = k.exZh, order = i)
        })
        db.wordDao().insertAll(words.words.mapIndexed { i, w ->
            WordEntity(
                id = w.id, ja = w.ja, kana = w.kana, romaji = w.romaji, zh = w.zh,
                pos = w.pos, cat = w.cat, example = w.example, exampleZh = w.exampleZh, order = i,
            )
        })
        db.grammarDao().insertAll(grammar.grammar.mapIndexed { i, g ->
            GrammarEntity(
                id = g.id, title = g.title, meaning = g.meaning, connection = g.connection,
                explanation = g.explanation,
                examplesJson = ContentJson.encodeToString(g.examples),
                exercisesJson = ContentJson.encodeToString(g.exercises),
                order = i,
            )
        })
        db.sentenceDao().insertAll(sentences.sentences.mapIndexed { i, s ->
            SentenceEntity(
                id = s.id, scene = s.scene, ja = s.ja, zh = s.zh,
                breakdownJson = ContentJson.encodeToString(s.breakdown), order = i,
            )
        })
        db.metaDao().upsert(MetaEntity(KEY_CONTENT_VERSION, totalVersion.toString()))
    }

    private fun readAsset(name: String): String =
        context.assets.open("content/$name").bufferedReader().use { it.readText() }

    companion object {
        const val KEY_CONTENT_VERSION = "content_version"
    }
}

/** 语法实体的 JSON 字段解码助手 */
fun GrammarEntity.examples(): List<Example> = ContentJson.decodeFromString(examplesJson)
fun GrammarEntity.exercises(): List<Exercise> = ContentJson.decodeFromString(exercisesJson)
fun SentenceEntity.breakdown(): List<Breakdown> = ContentJson.decodeFromString(breakdownJson)
