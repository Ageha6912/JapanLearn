package com.japanlearn.app

import android.app.Application
import com.japanlearn.app.data.BackupManager
import com.japanlearn.app.data.ContentLoader
import com.japanlearn.app.data.ContentRepository
import com.japanlearn.app.data.ProgressRepository
import com.japanlearn.app.data.SettingsRepository
import com.japanlearn.app.data.StatsRepository
import com.japanlearn.app.data.local.AppDatabase
import com.japanlearn.app.util.DateProvider
import com.japanlearn.app.util.JapaneseTts
import com.japanlearn.app.util.SystemDateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 手工依赖容器（MVP 不引入 Hilt，见 PRD §17.9） */
class AppContainer(context: Application) {
    val dateProvider: DateProvider = SystemDateProvider()
    val tts = JapaneseTts(context)
    val settings = SettingsRepository(context)
    private val db: AppDatabase = AppDatabase.build(context)
    val content = ContentRepository(db)
    val progress = ProgressRepository(db, dateProvider)
    val stats = StatsRepository(db, dateProvider)
    val backup = BackupManager(db)
    private val loader = ContentLoader(context, db)

    /** 装载课程内容（首次启动或内容版本升级时生效），IO 线程调用。 */
    suspend fun seedContent() {
        loader.seedIfNeeded()
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
            container.seedContent()
            // 恢复复习提醒的调度状态（WorkManager 任务在系统重启后由 WorkManager 自行恢复，
            // 这里覆盖一次以保证开关状态与调度一致）
            com.japanlearn.app.work.ReviewReminder.schedule(
                this@JapanLearnApp,
                container.settings.reminderEnabled.value,
            )
        }
    }
}
