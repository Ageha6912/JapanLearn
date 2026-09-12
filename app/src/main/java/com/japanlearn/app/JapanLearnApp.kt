package com.japanlearn.app

import android.app.Application
import android.util.Log
import com.japanlearn.app.data.BackupManager
import com.japanlearn.app.data.ContentLoader
import com.japanlearn.app.data.ContentRepository
import com.japanlearn.app.data.ProgressRepository
import com.japanlearn.app.data.SettingsRepository
import com.japanlearn.app.data.StatsRepository
import com.japanlearn.app.data.local.AppDatabase
import com.japanlearn.app.domain.FsrsScheduler
import com.japanlearn.app.domain.StudyPlanner
import com.japanlearn.app.util.DateProvider
import com.japanlearn.app.util.JapaneseTts
import com.japanlearn.app.util.SystemDateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 手工依赖容器（MVP 不引入 Hilt，见 PRD §17.9） */
class AppContainer(context: Application) {
    val dateProvider: DateProvider = SystemDateProvider()
    val settings = SettingsRepository(context)
    val tts = JapaneseTts(context).also { engine ->
        engine.setPreferredVoice(settings.ttsVoiceName.value)
    }
    private val db: AppDatabase = AppDatabase.build(context)
    val content = ContentRepository(db)
    val progress = ProgressRepository(db, dateProvider, FsrsScheduler)
    val stats = StatsRepository(db, dateProvider)
    val backup = BackupManager(db)
    val aiClient: com.japanlearn.app.data.ai.AiClient = com.japanlearn.app.data.ai.OpenAiCompatibleClient()
    private val loader = ContentLoader(context, db)

    /** 装载课程内容（首次启动或内容版本升级时生效），IO 线程调用。 */
    suspend fun seedContent() {
        loader.seedIfNeeded()
    }

    /** 目标周校准（PRD §19.6）：距上次自动应用 ≥ 7 天时按剩余内容重算并应用推荐档位。 */
    suspend fun recalibrateGoal() {
        val level = StudyPlanner.normalizeLevel(settings.goalLevel.value)
        if (level == StudyPlanner.LEVEL_NONE) return
        val today = dateProvider.today().toEpochDay()
        if (!StudyPlanner.shouldRecalibrate(settings.goalTierAppliedEpochDay.value, today)) return
        val total = content.wordCountByLevel(level).first()
        val learned = content.learnedWordCountByLevel(level).first()
        val plan = StudyPlanner.plan(
            remainingWords = (total - learned).coerceAtLeast(0),
            targetEpochDay = settings.goalTargetEpochDay.value,
            todayEpochDay = today,
        )
        val tier = plan.recommendedTier ?: return
        settings.applyGoalTier(tier, today)
    }
}

class JapanLearnApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = SupervisorJob() + Dispatchers.IO

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 内容装载在 IO 线程进行；Room 的 Flow 查询会在数据就绪后自动刷新 UI
        kotlinx.coroutines.CoroutineScope(appScope).launch {
            try {
                container.seedContent()
            } catch (e: Exception) {
                Log.e(ContentLoader.TAG, "seed failed", e)
            }
            // 恢复复习提醒的调度状态（WorkManager 任务在系统重启后由 WorkManager 自行恢复，
            // 这里覆盖一次以保证开关状态与调度一致）
            com.japanlearn.app.work.ReviewReminder.schedule(
                this@JapanLearnApp,
                container.settings.reminderEnabled.value,
                container.settings.reminderHour.value,
                container.settings.reminderMinute.value,
            )
            // 目标周校准（PRD §19.6）：有目标时按剩余内容量重算推荐档位
            try {
                container.recalibrateGoal()
            } catch (e: Exception) {
                Log.e("JapanLearnApp", "goal recalibrate failed", e)
            }
        }
    }
}
