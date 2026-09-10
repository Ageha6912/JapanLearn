package com.japanlearn.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.japanlearn.app.data.content.Breakdown
import com.japanlearn.app.data.content.ContentJson
import com.japanlearn.app.data.content.ContentKind
import com.japanlearn.app.data.content.ContentSeedPlanner
import com.japanlearn.app.data.content.ContentVersions
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
 * 内容装载（PRD §17.6 / OPTIMIZATION D2/D11）：
 * 按文件独立版本重装 assets JSON；一次启动包在一个事务里。
 * 学习进度表不受内容重装影响（不级联删除）。
 * 0.5.0 双写旧加总 key，不删除，便于回滚 0.4.4 APK。
 */
class ContentLoader(
    private val readAsset: (String) -> String,
    private val db: AppDatabase,
) {
    constructor(context: Context, db: AppDatabase) : this(
        readAsset = { name ->
            context.assets.open("content/$name").bufferedReader().use { it.readText() }
        },
        db = db,
    )

    suspend fun seedIfNeeded() {
        val kana = ContentJson.decodeFromString<KanaFile>(readAsset("kana.json"))
        val words = ContentJson.decodeFromString<WordsFile>(readAsset("words.json"))
        val grammar = ContentJson.decodeFromString<GrammarFile>(readAsset("grammar.json"))
        val sentences = ContentJson.decodeFromString<SentencesFile>(readAsset("sentences.json"))
        val incoming = ContentVersions(kana.version, words.version, grammar.version, sentences.version)

        db.withTransaction {
            val legacy = db.metaDao().get(ContentVersions.LEGACY_TOTAL)
            val perFileKana = db.metaDao().get(ContentVersions.KEY_KANA)
            val hasLegacyTotalOnly = ContentSeedPlanner.hasLegacyTotalOnly(legacy, perFileKana)
            val installed = ContentVersions(
                kana = perFileKana?.toIntOrNull() ?: 0,
                words = db.metaDao().get(ContentVersions.KEY_WORDS)?.toIntOrNull() ?: 0,
                grammar = db.metaDao().get(ContentVersions.KEY_GRAMMAR)?.toIntOrNull() ?: 0,
                sentences = db.metaDao().get(ContentVersions.KEY_SENTENCES)?.toIntOrNull() ?: 0,
            )
            val kinds = ContentSeedPlanner.kindsToReload(installed, incoming, hasLegacyTotalOnly)
            if (kinds.isEmpty()) return@withTransaction
            Log.i(TAG, "reload kinds=$kinds incoming=$incoming")

            if (ContentKind.WORDS in kinds) reloadWords(words)
            if (ContentKind.KANA in kinds) reloadKana(kana)
            if (ContentKind.GRAMMAR in kinds) reloadGrammar(grammar)
            if (ContentKind.SENTENCES in kinds) reloadSentences(sentences)

            db.metaDao().upsert(MetaEntity(ContentVersions.KEY_KANA, incoming.kana.toString()))
            db.metaDao().upsert(MetaEntity(ContentVersions.KEY_WORDS, incoming.words.toString()))
            db.metaDao().upsert(MetaEntity(ContentVersions.KEY_GRAMMAR, incoming.grammar.toString()))
            db.metaDao().upsert(MetaEntity(ContentVersions.KEY_SENTENCES, incoming.sentences.toString()))
            db.metaDao().upsert(MetaEntity(ContentVersions.LEGACY_TOTAL, incoming.total().toString()))
        }
    }

    private suspend fun reloadWords(file: WordsFile) {
        val entities = file.words.mapIndexed { i, w ->
            WordEntity(
                id = w.id, ja = w.ja, kana = w.kana, romaji = w.romaji, zh = w.zh,
                pos = w.pos, cat = w.cat, example = w.example, exampleZh = w.exampleZh,
                level = w.level, order = i,
            )
        }
        require(entities.isNotEmpty()) { "words.json has no items" }
        db.wordDao().insertAll(entities)
        val existing = db.wordDao().allOnce().map { it.id }.toSet()
        val incomingIds = entities.map { it.id }.toSet()
        require(incomingIds.size == entities.size) { "words.json has duplicate ids" }
        val toDrop = ContentSeedPlanner.idsToDelete(existing, incomingIds)
        if (toDrop.isNotEmpty()) db.wordDao().deleteByIds(toDrop)
    }

    private suspend fun reloadKana(file: KanaFile) {
        val entities = file.kana.mapIndexed { i, k ->
            KanaEntity(
                id = k.id, hiragana = k.h, katakana = k.k, romaji = k.r,
                groupName = k.group, exampleJa = k.exJa, exampleZh = k.exZh, order = i,
            )
        }
        require(entities.isNotEmpty()) { "kana.json has no items" }
        db.kanaDao().insertAll(entities)
        val existing = db.kanaDao().allOnce().map { it.id }.toSet()
        val incomingIds = entities.map { it.id }.toSet()
        require(incomingIds.size == entities.size) { "kana.json has duplicate ids" }
        val toDrop = ContentSeedPlanner.idsToDelete(existing, incomingIds)
        if (toDrop.isNotEmpty()) db.kanaDao().deleteByIds(toDrop)
    }

    private suspend fun reloadGrammar(file: GrammarFile) {
        val entities = file.grammar.mapIndexed { i, g ->
            GrammarEntity(
                id = g.id, title = g.title, meaning = g.meaning, connection = g.connection,
                explanation = g.explanation,
                examplesJson = ContentJson.encodeToString(g.examples),
                exercisesJson = ContentJson.encodeToString(g.exercises),
                level = g.level, order = i,
            )
        }
        require(entities.isNotEmpty()) { "grammar.json has no items" }
        db.grammarDao().insertAll(entities)
        val existing = db.grammarDao().allOnce().map { it.id }.toSet()
        val incomingIds = entities.map { it.id }.toSet()
        require(incomingIds.size == entities.size) { "grammar.json has duplicate ids" }
        val toDrop = ContentSeedPlanner.idsToDelete(existing, incomingIds)
        if (toDrop.isNotEmpty()) db.grammarDao().deleteByIds(toDrop)
    }

    private suspend fun reloadSentences(file: SentencesFile) {
        val entities = file.sentences.mapIndexed { i, s ->
            SentenceEntity(
                id = s.id, scene = s.scene, ja = s.ja, zh = s.zh,
                breakdownJson = ContentJson.encodeToString(s.breakdown), order = i,
            )
        }
        require(entities.isNotEmpty()) { "sentences.json has no items" }
        db.sentenceDao().insertAll(entities)
        val existing = db.sentenceDao().allOnce().map { it.id }.toSet()
        val incomingIds = entities.map { it.id }.toSet()
        require(incomingIds.size == entities.size) { "sentences.json has duplicate ids" }
        val toDrop = ContentSeedPlanner.idsToDelete(existing, incomingIds)
        if (toDrop.isNotEmpty()) db.sentenceDao().deleteByIds(toDrop)
    }

    companion object {
        const val TAG = "ContentLoader"
        const val KEY_CONTENT_VERSION = ContentVersions.LEGACY_TOTAL
    }
}

/** 语法实体的 JSON 字段解码助手 */
fun GrammarEntity.examples(): List<Example> = ContentJson.decodeFromString(examplesJson)
fun GrammarEntity.exercises(): List<Exercise> = ContentJson.decodeFromString(exercisesJson)
fun SentenceEntity.breakdown(): List<Breakdown> = ContentJson.decodeFromString(breakdownJson)
