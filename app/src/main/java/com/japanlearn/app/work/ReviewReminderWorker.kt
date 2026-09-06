package com.japanlearn.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.japanlearn.app.AppContainer
import com.japanlearn.app.JapanLearnApp
import com.japanlearn.app.MainActivity
import com.japanlearn.app.R
import com.japanlearn.app.util.ReminderScheduler
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** 通知渠道与调度入口。 */
object ReviewReminder {
    const val CHANNEL_ID = "review_reminder"
    const val NOTIFICATION_ID = 1001
    const val WORK_NAME = "review_reminder"
    const val EXTRA_NAVIGATE_TO = "navigate_to"
    const val NAVIGATE_REVIEW = "review"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "复习提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "每天提醒你完成当天的复习" }
        manager.createNotificationChannel(channel)
    }

    /** 按开关调度或取消每日 20:00 的周期任务。 */
    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = ReminderScheduler.nextTriggerDelayMillis(System.currentTimeMillis())
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

/** 每日检查到期复习量，有内容才发通知。 */
class ReviewReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? JapanLearnApp ?: return Result.success()
        tryAttempt(app.container)
        return Result.success()
    }

    private suspend fun tryAttempt(container: AppContainer) {
        val due = container.progress.dueWordCount().first() + container.progress.dueGrammarCount().first()
        if (due <= 0) return
        postNotification(applicationContext, due)
    }

    private fun postNotification(context: Context, due: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // 用户未授权通知，静默跳过
        }
        ReviewReminder.ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReviewReminder.EXTRA_NAVIGATE_TO, ReviewReminder.NAVIGATE_REVIEW)
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ReviewReminder.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("今天有 $due 条内容等待复习")
            .setContentText("花几分钟完成今天的复习，保持连击不断")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ReviewReminder.NOTIFICATION_ID, notification)
    }
}
