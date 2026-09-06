package com.japanlearn.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val id: String,
    val ja: String,
    val kana: String,
    val romaji: String,
    val zh: String,
    val pos: String,
    val cat: String,
    val example: String,
    val exampleZh: String,
    val order: Int,
)

@Entity(tableName = "grammar")
data class GrammarEntity(
    @PrimaryKey val id: String,
    val title: String,
    val meaning: String,
    val connection: String,
    val explanation: String,
    val examplesJson: String,
    val exercisesJson: String,
    val order: Int,
)

@Entity(tableName = "kana")
data class KanaEntity(
    @PrimaryKey val id: String,
    val hiragana: String,
    val katakana: String,
    val romaji: String,
    val groupName: String,
    val exampleJa: String,
    val exampleZh: String,
    val order: Int,
)

@Entity(tableName = "sentences")
data class SentenceEntity(
    @PrimaryKey val id: String,
    val scene: String,
    val ja: String,
    val zh: String,
    val breakdownJson: String,
    val order: Int,
)

/** 用户学习进度（SRS 状态），contentType: word / grammar / kana */
@Entity(
    tableName = "user_progress",
    indices = [Index(value = ["contentType", "contentId"], unique = true)],
)
data class UserProgressEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val contentType: String,
    val contentId: String,
    val mastery: Int,
    val intervalDays: Int,
    val reviewCount: Int,
    val dueAt: Long,
    val status: String,
    val learnedAt: Long,
    val lastReviewedAt: Long? = null,
)

@Entity(tableName = "review_records")
data class ReviewRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentType: String,
    val contentId: String,
    val correct: Boolean,
    val masteryAfter: Int,
    val reviewedAt: Long,
    val nextDueAt: Long,
)

/** 每日学习记录，date 格式 yyyy-MM-dd */
@Entity(tableName = "daily_study")
data class DailyStudyEntity(
    @PrimaryKey val date: String,
    val studySeconds: Int,
    val newWords: Int,
    val newGrammar: Int,
    val reviewsDone: Int,
)

/** 错题本 */
@Entity(tableName = "wrong_answers", primaryKeys = ["contentType", "contentId"])
data class WrongAnswerEntity(
    val contentType: String,
    val contentId: String,
    val wrongCount: Int,
    val lastWrongAt: Long,
)

/** 元信息表：内容版本等 */
@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)
