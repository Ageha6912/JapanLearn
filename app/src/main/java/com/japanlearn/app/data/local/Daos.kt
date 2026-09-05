package com.japanlearn.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY `order`")
    fun all(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words ORDER BY `order`")
    suspend fun allOnce(): List<WordEntity>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun byId(id: String): WordEntity?

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WordEntity>)

    /** 尚未开始学习的单词（新词队列） */
    @Query(
        "SELECT * FROM words WHERE id NOT IN " +
            "(SELECT contentId FROM user_progress WHERE contentType = 'word') " +
            "ORDER BY `order` LIMIT :n"
    )
    suspend fun newWords(n: Int): List<WordEntity>

    @Query(
        "SELECT w.* FROM words w JOIN user_progress p ON p.contentId = w.id AND p.contentType = 'word' " +
            "WHERE p.dueAt <= :now ORDER BY p.dueAt LIMIT :limit"
    )
    suspend fun dueWords(now: Long, limit: Int): List<WordEntity>
}

@Dao
interface GrammarDao {
    @Query("SELECT * FROM grammar ORDER BY `order`")
    fun all(): Flow<List<GrammarEntity>>

    @Query("SELECT * FROM grammar ORDER BY `order`")
    suspend fun allOnce(): List<GrammarEntity>

    @Query("SELECT * FROM grammar WHERE id = :id")
    suspend fun byId(id: String): GrammarEntity?

    @Query("SELECT COUNT(*) FROM grammar")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GrammarEntity>)

    @Query(
        "SELECT * FROM grammar WHERE id NOT IN " +
            "(SELECT contentId FROM user_progress WHERE contentType = 'grammar') " +
            "ORDER BY `order` LIMIT :n"
    )
    suspend fun newGrammar(n: Int): List<GrammarEntity>

    @Query(
        "SELECT g.* FROM grammar g JOIN user_progress p ON p.contentId = g.id AND p.contentType = 'grammar' " +
            "WHERE p.dueAt <= :now ORDER BY p.dueAt LIMIT :limit"
    )
    suspend fun dueGrammar(now: Long, limit: Int): List<GrammarEntity>
}

@Dao
interface KanaDao {
    @Query("SELECT * FROM kana ORDER BY `order`")
    fun all(): Flow<List<KanaEntity>>

    @Query("SELECT * FROM kana ORDER BY `order`")
    suspend fun allOnce(): List<KanaEntity>

    @Query("SELECT COUNT(*) FROM kana")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<KanaEntity>)
}

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences ORDER BY `order`")
    fun all(): Flow<List<SentenceEntity>>

    @Query("SELECT * FROM sentences ORDER BY `order`")
    suspend fun allOnce(): List<SentenceEntity>

    @Query("SELECT COUNT(*) FROM sentences")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SentenceEntity>)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM user_progress WHERE contentType = :type AND contentId = :contentId")
    suspend fun get(type: String, contentId: String): UserProgressEntity?

    @Upsert
    suspend fun upsert(item: UserProgressEntity)

    @Query("DELETE FROM user_progress")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM user_progress WHERE contentType = :type")
    suspend fun countByType(type: String): Int

    @Query("SELECT COUNT(*) FROM user_progress WHERE contentType = 'word' AND dueAt <= :now")
    fun dueWordCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_progress WHERE contentType = 'grammar' AND dueAt <= :now")
    fun dueGrammarCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_progress WHERE contentType = 'word' AND intervalDays >= :threshold")
    fun masteredWordCount(threshold: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_progress WHERE contentType = 'word'")
    fun countWordFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM user_progress WHERE contentType = 'grammar'")
    fun countGrammarFlow(): Flow<Int>
}

@Dao
interface ReviewRecordDao {
    @Insert
    suspend fun insert(item: ReviewRecordEntity)

    @Query("SELECT COUNT(*) FROM review_records WHERE reviewedAt BETWEEN :from AND :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM review_records WHERE reviewedAt >= :from")
    fun countSinceFlow(from: Long): Flow<Int>

    @Query("DELETE FROM review_records")
    suspend fun clear()
}

@Dao
interface DailyStudyDao {
    @Query("SELECT * FROM daily_study WHERE date = :date")
    suspend fun get(date: String): DailyStudyEntity?

    @Upsert
    suspend fun upsert(item: DailyStudyEntity)

    @Query("SELECT * FROM daily_study WHERE date >= :fromDate ORDER BY date")
    fun since(fromDate: String): Flow<List<DailyStudyEntity>>

    @Query("SELECT date FROM daily_study")
    suspend fun allDates(): List<String>

    @Query("SELECT * FROM daily_study WHERE date = :date")
    suspend fun getOnce(date: String): DailyStudyEntity?

    @Query("SELECT COALESCE(SUM(studySeconds), 0) FROM daily_study")
    fun totalSeconds(): Flow<Int>

    @Query("SELECT COALESCE(SUM(newWords), 0) FROM daily_study")
    fun totalNewWords(): Flow<Int>

    @Query("SELECT COALESCE(SUM(reviewsDone), 0) FROM daily_study")
    fun totalReviews(): Flow<Int>

    @Query("DELETE FROM daily_study")
    suspend fun clear()
}

@Dao
interface WrongAnswerDao {
    @Query("SELECT * FROM wrong_answers ORDER BY lastWrongAt DESC")
    fun all(): Flow<List<WrongAnswerEntity>>

    @Query("SELECT COUNT(*) FROM wrong_answers")
    fun count(): Flow<Int>

    @Query("SELECT * FROM wrong_answers WHERE contentType = :type AND contentId = :contentId")
    suspend fun get(type: String, contentId: String): WrongAnswerEntity?

    @Upsert
    suspend fun upsert(item: WrongAnswerEntity)

    @Query("DELETE FROM wrong_answers WHERE contentType = :type AND contentId = :contentId")
    suspend fun delete(type: String, contentId: String)

    @Query("DELETE FROM wrong_answers")
    suspend fun clear()
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun upsert(item: MetaEntity)
}
