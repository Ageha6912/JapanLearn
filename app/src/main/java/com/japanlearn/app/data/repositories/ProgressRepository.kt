package com.japanlearn.app.data

import android.content.Context
import com.japanlearn.app.data.local.AppDatabase
import com.japanlearn.app.data.local.DailyStudyEntity
import com.japanlearn.app.data.local.ReviewRecordEntity
import com.japanlearn.app.data.local.UserProgressEntity
import com.japanlearn.app.data.local.WordEntity
import com.japanlearn.app.data.local.WrongAnswerEntity
import com.japanlearn.app.domain.Mastery
import com.japanlearn.app.domain.SrsScheduler
import com.japanlearn.app.domain.SrsState
import com.japanlearn.app.domain.StreakCalculator
import com.japanlearn.app.util.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

// ---------------- 学习目标 / 设置 ----------------

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val dailyNewWords = MutableStateFlow(prefs.getInt(KEY_NEW_WORDS, DEFAULT_NEW_WORDS))
    val dailyNewGrammar = MutableStateFlow(prefs.getInt(KEY_NEW_GRAMMAR, DEFAULT_NEW_GRAMMAR))
    val dailyReviewCap = MutableStateFlow(prefs.getInt(KEY_REVIEW_CAP, DEFAULT_REVIEW_CAP))
    val reminderEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDER, false))

    fun setReminderEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER, value).apply()
        reminderEnabled.value = value
    }

    fun setDailyNewWords(value: Int) {
        prefs.edit().putInt(KEY_NEW_WORDS, value).apply()
        dailyNewWords.value = value
    }

    fun setDailyNewGrammar(value: Int) {
        prefs.edit().putInt(KEY_NEW_GRAMMAR, value).apply()
        dailyNewGrammar.value = value
    }

    fun setDailyReviewCap(value: Int) {
        prefs.edit().putInt(KEY_REVIEW_CAP, value).apply()
        dailyReviewCap.value = value
    }

    companion object {
        const val DEFAULT_NEW_WORDS = 10
        const val DEFAULT_NEW_GRAMMAR = 3
        const val DEFAULT_REVIEW_CAP = 30
        private const val KEY_NEW_WORDS = "daily_new_words"
        private const val KEY_NEW_GRAMMAR = "daily_new_grammar"
        private const val KEY_REVIEW_CAP = "daily_review_cap"
        private const val KEY_REMINDER = "reminder_enabled"
    }
}

// ---------------- 内容 ----------------

class ContentRepository(private val db: AppDatabase) {
    fun kanaAll() = db.kanaDao().all()
    fun wordsAll() = db.wordDao().all()
    fun grammarAll() = db.grammarDao().all()
    fun sentencesAll() = db.sentenceDao().all()

    suspend fun wordById(id: String): WordEntity? = db.wordDao().byId(id)
    suspend fun grammarById(id: String) = db.grammarDao().byId(id)

    suspend fun nextNewWords(n: Int): List<WordEntity> = db.wordDao().newWords(n)
    suspend fun nextNewGrammar(n: Int) = db.grammarDao().newGrammar(n)
}

// ---------------- SRS 进度 / 错题 ----------------

class ProgressRepository(
    private val db: AppDatabase,
    private val dates: DateProvider,
) {
    /**
     * 学习/复习一项内容后推进 SRS；“不认识”同时记入错题本，答对（模糊及以上）则从错题本移除。
     */
    suspend fun applyReview(contentType: String, contentId: String, mastery: Mastery) {
        val now = dates.nowMillis()
        val existing = db.progressDao().get(contentType, contentId)
        val previous = existing?.let {
            SrsState(it.mastery, it.intervalDays, it.reviewCount, it.dueAt)
        } ?: SrsState.INITIAL
        val next = SrsScheduler.next(previous, mastery, now)
        db.progressDao().upsert(
            UserProgressEntity(
                rowId = existing?.rowId ?: 0,
                contentType = contentType,
                contentId = contentId,
                mastery = next.mastery,
                intervalDays = next.intervalDays,
                reviewCount = next.reviewCount,
                dueAt = next.dueAt,
                status = if (SrsScheduler.isMastered(next)) "mastered" else "learning",
                learnedAt = existing?.learnedAt ?: now,
                lastReviewedAt = now,
            )
        )
        db.reviewRecordDao().insert(
            ReviewRecordEntity(
                contentType = contentType,
                contentId = contentId,
                correct = mastery != Mastery.UNKNOWN,
                masteryAfter = next.mastery,
                reviewedAt = now,
                nextDueAt = next.dueAt,
            )
        )
        if (mastery == Mastery.UNKNOWN) {
            val wrong = db.wrongAnswerDao().get(contentType, contentId)
            db.wrongAnswerDao().upsert(
                WrongAnswerEntity(
                    contentType = contentType,
                    contentId = contentId,
                    wrongCount = (wrong?.wrongCount ?: 0) + 1,
                    lastWrongAt = now,
                )
            )
        } else {
            db.wrongAnswerDao().delete(contentType, contentId)
        }
    }

    /** 五十音不参与 SRS，答错只进错题本。 */
    suspend fun recordKanaWrong(kanaId: String) {
        val now = dates.nowMillis()
        val wrong = db.wrongAnswerDao().get("kana", kanaId)
        db.wrongAnswerDao().upsert(
            WrongAnswerEntity(
                contentType = "kana",
                contentId = kanaId,
                wrongCount = (wrong?.wrongCount ?: 0) + 1,
                lastWrongAt = now,
            )
        )
    }

    fun dueWordCount(): Flow<Int> = db.progressDao().dueWordCount(dates.nowMillis())
    fun dueGrammarCount(): Flow<Int> = db.progressDao().dueGrammarCount(dates.nowMillis())

    suspend fun dueWords(limit: Int): List<WordEntity> = db.wordDao().dueWords(dates.nowMillis(), limit)
    suspend fun dueGrammar(limit: Int) = db.grammarDao().dueGrammar(dates.nowMillis(), limit)

    suspend fun reviewsDoneToday(): Int {
        val dayStart = dates.today().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return db.reviewRecordDao().countBetween(dayStart, Long.MAX_VALUE)
    }

    /** 实时流：今日已复习条数（跨会话落库后 UI 自动刷新）。 */
    fun reviewsDoneTodayFlow(): Flow<Int> {
        val dayStart = dates.today().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return db.reviewRecordDao().countSinceFlow(dayStart)
    }

    fun learnedWordCount(): Flow<Int> = db.progressDao().countWordFlow()
    fun learnedGrammarCount(): Flow<Int> = db.progressDao().countGrammarFlow()

    /** 单词 id → 当前掌握度（用于列表色点）。 */
    fun wordMasteryMap(): Flow<Map<String, Int>> =
        db.progressDao().allByType("word").map { rows -> rows.associate { it.contentId to it.mastery } }
    fun masteredWordCount(): Flow<Int> = db.progressDao().masteredWordCount(SrsScheduler.MASTERED_INTERVAL_DAYS)

    fun wrongAnswers(): Flow<List<WrongAnswerEntity>> = db.wrongAnswerDao().all()
    fun wrongAnswerCount(): Flow<Int> = db.wrongAnswerDao().count()

    suspend fun resetAll() {
        db.progressDao().clear()
        db.reviewRecordDao().clear()
        db.wrongAnswerDao().clear()
        db.dailyStudyDao().clear()
    }
}

// ---------------- 统计 ----------------

data class WeeklyStats(
    val days: List<DailyStudyEntity>,
    val streak: Int,
)

class StatsRepository(
    private val db: AppDatabase,
    private val dates: DateProvider,
) {
    suspend fun addStudy(seconds: Int, newWords: Int = 0, newGrammar: Int = 0, reviewsDone: Int = 0) {
        val date = dates.todayString()
        val current = db.dailyStudyDao().get(date) ?: DailyStudyEntity(date, 0, 0, 0, 0)
        db.dailyStudyDao().upsert(
            current.copy(
                studySeconds = current.studySeconds + seconds,
                newWords = current.newWords + newWords,
                newGrammar = current.newGrammar + newGrammar,
                reviewsDone = current.reviewsDone + reviewsDone,
            )
        )
    }

    fun weekly(): Flow<WeeklyStats> {
        val today = dates.today()
        val fromDate = today.minusDays(6).toString()
        return db.dailyStudyDao().since(fromDate).map { rows ->
            WeeklyStats(
                days = rows,
                streak = StreakCalculator.streak(db.dailyStudyDao().allDates().toSet(), today),
            )
        }
    }

    fun todayFlow(): Flow<DailyStudyEntity?> =
        db.dailyStudyDao().since(dates.todayString()).map { rows -> rows.lastOrNull { it.date == dates.todayString() } }

    fun totalSeconds(): Flow<Int> = db.dailyStudyDao().totalSeconds()
    fun totalNewWords(): Flow<Int> = db.dailyStudyDao().totalNewWords()
    fun totalReviews(): Flow<Int> = db.dailyStudyDao().totalReviews()

    /** 每日一句：按日期轮换（daysSinceEpoch % size）。 */
    suspend fun sentenceIndexForToday(size: Int): Int {
        if (size == 0) return 0
        val day = dates.today().toEpochDay()
        return (day % size).toInt()
    }
}
