package com.japanlearn.app.data

import android.content.Context
import com.japanlearn.app.data.local.AppDatabase
import com.japanlearn.app.data.local.DailyStudyEntity
import com.japanlearn.app.data.local.ReviewRecordEntity
import com.japanlearn.app.data.local.UserProgressEntity
import com.japanlearn.app.data.local.WordEntity
import com.japanlearn.app.data.local.WrongAnswerEntity
import com.japanlearn.app.domain.AiConfig
import com.japanlearn.app.domain.CoursePointer
import com.japanlearn.app.domain.Mastery
import com.japanlearn.app.domain.Scheduler
import com.japanlearn.app.domain.SrsScheduler
import com.japanlearn.app.domain.SrsState
import com.japanlearn.app.domain.StreakCalculator
import com.japanlearn.app.domain.StudyPlanner
import com.japanlearn.app.util.DateProvider
import com.japanlearn.app.util.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

// ---------------- 学习目标 / 设置 ----------------

/** 主题模式：跟随系统 / 浅色 / 深色。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        /** 非法或缺失值回退 SYSTEM（持久化解析的纯函数，便于测试）。 */
        fun fromRaw(raw: String?): ThemeMode = entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val dailyNewWords = MutableStateFlow(prefs.getInt(KEY_NEW_WORDS, DEFAULT_NEW_WORDS))
    val dailyNewGrammar = MutableStateFlow(prefs.getInt(KEY_NEW_GRAMMAR, DEFAULT_NEW_GRAMMAR))
    val dailyReviewCap = MutableStateFlow(prefs.getInt(KEY_REVIEW_CAP, DEFAULT_REVIEW_CAP))
    val reminderEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDER, false))
    val reminderHour = MutableStateFlow(
        ReminderScheduler.coerceHour(prefs.getInt(KEY_REMINDER_HOUR, ReminderScheduler.DEFAULT_HOUR)),
    )
    val reminderMinute = MutableStateFlow(
        ReminderScheduler.coerceMinute(prefs.getInt(KEY_REMINDER_MINUTE, ReminderScheduler.DEFAULT_MINUTE)),
    )
    val kanaIntroDismissed = MutableStateFlow(prefs.getBoolean(KEY_KANA_INTRO_DISMISSED, false))
    /** 首启引导已完成/跳过（PRD §19.6）：看过就永不打扰。 */
    val onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val studyLevel = MutableStateFlow(prefs.getString(KEY_STUDY_LEVEL, DEFAULT_STUDY_LEVEL) ?: DEFAULT_STUDY_LEVEL)
    val themeMode = MutableStateFlow(ThemeMode.fromRaw(prefs.getString(KEY_THEME, null)))
    /** 用户偏好的 Google TTS 日语 voice 名；空字符串 = 自动选最高质量。 */
    val ttsVoiceName = MutableStateFlow(prefs.getString(KEY_TTS_VOICE, "") ?: "")
    // 学习目标（PRD §19.6）：级别 + 可选目标日期 + 上次自动应用推荐档的日期
    val goalLevel = MutableStateFlow(StudyPlanner.normalizeLevel(prefs.getString(KEY_GOAL_LEVEL, null)))
    val goalTargetEpochDay = MutableStateFlow(prefs.getLong(KEY_GOAL_DATE, 0L))
    val goalTierAppliedEpochDay = MutableStateFlow(prefs.getLong(KEY_GOAL_TIER_APPLIED, 0L))

    fun setThemeMode(value: ThemeMode) {
        prefs.edit().putString(KEY_THEME, value.name).apply()
        themeMode.value = value
    }

    fun setStudyLevel(value: String) {
        prefs.edit().putString(KEY_STUDY_LEVEL, value).apply()
        studyLevel.value = value
    }

    fun setReminderEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER, value).apply()
        reminderEnabled.value = value
    }

    fun setReminderHour(value: Int) {
        val hour = ReminderScheduler.coerceHour(value)
        prefs.edit().putInt(KEY_REMINDER_HOUR, hour).apply()
        reminderHour.value = hour
    }

    fun setReminderMinute(value: Int) {
        val minute = ReminderScheduler.coerceMinute(value)
        prefs.edit().putInt(KEY_REMINDER_MINUTE, minute).apply()
        reminderMinute.value = minute
    }

    fun setKanaIntroDismissed(value: Boolean) {
        prefs.edit().putBoolean(KEY_KANA_INTRO_DISMISSED, value).apply()
        kanaIntroDismissed.value = value
    }

    fun setOnboardingDone(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()
        onboardingDone.value = value
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

    fun setTtsVoiceName(value: String) {
        prefs.edit().putString(KEY_TTS_VOICE, value).apply()
        ttsVoiceName.value = value
    }

    /** 设置或更新学习目标；同时重置档位校准时间戳，随后应通过 recalibrateGoal 应用推荐档。 */
    fun setGoal(level: String, targetEpochDay: Long) {
        val normalized = StudyPlanner.normalizeLevel(level)
        val day = targetEpochDay.coerceAtLeast(0L)
        prefs.edit().putString(KEY_GOAL_LEVEL, normalized).putLong(KEY_GOAL_DATE, day).apply()
        goalLevel.value = normalized
        goalTargetEpochDay.value = day
        goalTierAppliedEpochDay.value = 0L
    }

    fun clearGoal() = setGoal(StudyPlanner.LEVEL_NONE, 0L)

    /** 应用目标推荐档位并记录校准日期（目标设置与每周校准时调用）。 */
    fun applyGoalTier(tier: Int, appliedEpochDay: Long) {
        setDailyNewWords(tier)
        prefs.edit().putLong(KEY_GOAL_TIER_APPLIED, appliedEpochDay).apply()
        goalTierAppliedEpochDay.value = appliedEpochDay
    }

    // ---- 课程单元（PRD §19.8）----

    /** 手动覆盖的当前单元（格式 "N5:3"）；空 = 自动跟随第一个未完成单元。 */
    val courseUnitOverride = MutableStateFlow(prefs.getString(KEY_COURSE_UNIT, "") ?: "")

    fun setCourseUnitOverride(level: String, unit: Int?) {
        val value = CoursePointer.formatOverride(level, unit)
        prefs.edit().putString(KEY_COURSE_UNIT, value).apply()
        courseUnitOverride.value = value
    }

    /** 单元检查点最佳成绩（correct to total），无记录返回 null。 */
    fun checkpointBest(level: String, unit: Int): Pair<Int, Int>? {
        val raw = prefs.getString(checkpointKey(level, unit), null) ?: return null
        val parts = raw.split("/")
        val correct = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val total = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return correct to total
    }

    /** 记录检查点成绩，只保留最佳（按答对数）。 */
    fun setCheckpointBest(level: String, unit: Int, correct: Int, total: Int) {
        val best = checkpointBest(level, unit)
        if (best == null || correct > best.first) {
            prefs.edit().putString(checkpointKey(level, unit), "$correct/$total").apply()
        }
    }

    private fun checkpointKey(level: String, unit: Int) = "checkpoint_best_${level}_$unit"

    // ---- AI 助手（PRD §19.9，BYOK）----

    val aiBaseUrl = MutableStateFlow(prefs.getString(KEY_AI_BASE_URL, "") ?: "")
    val aiApiKey = MutableStateFlow(prefs.getString(KEY_AI_API_KEY, "") ?: "")
    val aiModel = MutableStateFlow(prefs.getString(KEY_AI_MODEL, "") ?: "")
    val aiDailyLimit = MutableStateFlow(prefs.getInt(KEY_AI_DAILY_LIMIT, 20))

    fun saveAiConfig(baseUrl: String, apiKey: String, model: String) {
        val url = baseUrl.trim()
        val key = apiKey.trim()
        val mdl = model.trim()
        prefs.edit()
            .putString(KEY_AI_BASE_URL, url)
            .putString(KEY_AI_API_KEY, key)
            .putString(KEY_AI_MODEL, mdl)
            .apply()
        aiBaseUrl.value = url
        aiApiKey.value = key
        aiModel.value = mdl
    }

    fun setAiDailyLimit(value: Int) {
        val v = if (value == AiConfig.UNLIMITED || value > 0) value else 20
        prefs.edit().putInt(KEY_AI_DAILY_LIMIT, v).apply()
        aiDailyLimit.value = v
    }

    /** 当日调用次数（date 为 ISO 日期，调用方从 DateProvider 取）。 */
    fun aiCallsToday(date: String): Int = prefs.getInt("ai_calls_$date", 0)

    fun incrementAiCalls(date: String) {
        prefs.edit().putInt("ai_calls_$date", aiCallsToday(date) + 1).apply()
    }

    companion object {
        const val DEFAULT_NEW_WORDS = 10
        const val DEFAULT_NEW_GRAMMAR = 3
        const val DEFAULT_REVIEW_CAP = 30
        private const val KEY_NEW_WORDS = "daily_new_words"
        private const val KEY_NEW_GRAMMAR = "daily_new_grammar"
        private const val KEY_REVIEW_CAP = "daily_review_cap"
        private const val KEY_REMINDER = "reminder_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val KEY_KANA_INTRO_DISMISSED = "kana_intro_dismissed"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_STUDY_LEVEL = "study_level"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_TTS_VOICE = "tts_voice_name"
        private const val KEY_GOAL_LEVEL = "goal_level"
        private const val KEY_GOAL_DATE = "goal_target_epoch_day"
        private const val KEY_GOAL_TIER_APPLIED = "goal_tier_applied_epoch_day"
        private const val KEY_COURSE_UNIT = "course_unit_override"
        private const val KEY_AI_BASE_URL = "ai_base_url"
        private const val KEY_AI_API_KEY = "ai_api_key"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_DAILY_LIMIT = "ai_daily_limit"
        const val DEFAULT_STUDY_LEVEL = "N5"
        val STUDY_LEVELS = listOf("N5", "N4")
    }
}

// ---------------- 内容 ----------------

class ContentRepository(private val db: AppDatabase) {
    fun kanaAll() = db.kanaDao().all()
    fun wordsAll() = db.wordDao().all()
    fun grammarAll() = db.grammarDao().all()
    fun sentencesAll() = db.sentenceDao().all()

    fun wordCount() = db.wordDao().countFlow()
    fun grammarCount() = db.grammarDao().countFlow()
    fun kanaCount() = db.kanaDao().countFlow()
    fun wordCountByLevel(level: String) = db.wordDao().countByLevelFlow(level)
    fun grammarCountByLevel(level: String) = db.grammarDao().countByLevelFlow(level)
    fun learnedWordCountByLevel(level: String) = db.wordDao().learnedCountByLevelFlow(level)
    fun learnedGrammarCountByLevel(level: String) = db.grammarDao().learnedCountByLevelFlow(level)

    suspend fun wordById(id: String): WordEntity? = db.wordDao().byId(id)
    suspend fun grammarById(id: String) = db.grammarDao().byId(id)

    suspend fun nextNewWords(n: Int, level: String): List<WordEntity> = db.wordDao().newWords(n, level)
    suspend fun nextNewGrammar(n: Int, level: String) = db.grammarDao().newGrammar(n, level)

    // ---- 课程单元（PRD §19.8）----

    fun unitProgressByLevelFlow(level: String) = db.wordDao().unitProgressByLevelFlow(level)
    suspend fun unitProgressByLevel(level: String) = db.wordDao().unitProgressByLevel(level)
    suspend fun wordsByUnit(level: String, unit: Int) = db.wordDao().byLevelAndUnit(level, unit)
    suspend fun grammarByUnit(level: String, unit: Int) = db.grammarDao().byLevelAndUnit(level, unit)
    suspend fun nextNewWordsByUnit(n: Int, level: String, unit: Int) = db.wordDao().newWordsByUnit(n, level, unit)
}

// ---------------- SRS 进度 / 错题 ----------------

class ProgressRepository(
    private val db: AppDatabase,
    private val dates: DateProvider,
    private val scheduler: Scheduler = SrsScheduler,
) {
    /**
     * 学习/复习一项内容后推进 SRS；“不认识”同时记入错题本，答对（模糊及以上）则从错题本移除。
     */
    suspend fun applyReview(contentType: String, contentId: String, mastery: Mastery) {
        val now = dates.nowMillis()
        val existing = db.progressDao().get(contentType, contentId)
        val previous = existing?.let {
            SrsState(
                mastery = it.mastery,
                intervalDays = it.intervalDays,
                reviewCount = it.reviewCount,
                dueAt = it.dueAt,
                stability = it.stability,
                difficulty = it.difficulty,
                lapses = it.lapses,
                fsrsState = it.fsrsState,
                lastReviewedAt = it.lastReviewedAt,
            )
        } ?: SrsState.INITIAL
        val next = scheduler.next(previous, mastery, now)
        db.progressDao().upsert(
            UserProgressEntity(
                rowId = existing?.rowId ?: 0,
                contentType = contentType,
                contentId = contentId,
                mastery = next.mastery,
                intervalDays = next.intervalDays,
                reviewCount = next.reviewCount,
                dueAt = next.dueAt,
                status = if (scheduler.isMastered(next)) "mastered" else "learning",
                learnedAt = existing?.learnedAt ?: now,
                lastReviewedAt = now,
                stability = next.stability,
                difficulty = next.difficulty,
                lapses = next.lapses,
                fsrsState = next.fsrsState,
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

    /** 听力等辅练的对错只同步错题本，不推进 SRS（PRD §19.7）。 */
    suspend fun recordAuxAnswer(contentType: String, contentId: String, correct: Boolean) {
        if (correct) {
            db.wrongAnswerDao().delete(contentType, contentId)
            return
        }
        val now = dates.nowMillis()
        val wrong = db.wrongAnswerDao().get(contentType, contentId)
        db.wrongAnswerDao().upsert(
            WrongAnswerEntity(
                contentType = contentType,
                contentId = contentId,
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

    /** 某类内容已学过的 id 集合（用于按级别统计已学数）。 */
    fun learnedIds(type: String): Flow<Set<String>> =
        db.progressDao().allByType(type).map { list -> list.map { it.contentId }.toSet() }

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

    // ---- 学习成果页（PRD §19.8）：全部来自现有表的聚合 ----

    /** 全量学习日期（ISO 字符串），用于最长连击计算。 */
    suspend fun allStudyDates(): Set<String> = db.dailyStudyDao().allDates().toSet()

    /** 复习自评正确率（correct to 总数）；无记录返回 null。 */
    suspend fun reviewAccuracy(): Pair<Int, Int>? {
        val total = db.reviewRecordDao().countAll()
        if (total == 0) return null
        return db.reviewRecordDao().countAllCorrect() to total
    }

    /** 两个级别合计的已完成课程单元数（完成 = 该单元词全部学过）。 */
    suspend fun completedUnitCount(): Int {
        val levels = listOf("N5", "N4")
        return levels.sumOf { level ->
            db.wordDao().unitProgressByLevel(level).count { it.total > 0 && it.learned >= it.total }
        }
    }
}
