package com.japanlearn.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.japanlearn.app.JapanLearnApp
import com.japanlearn.app.domain.WidgetMath
import com.japanlearn.app.work.ReviewReminder
import kotlinx.coroutines.flow.first

/** 桌面小组件：今日任务（新词 / 语法 / 待复习）+ 连击，点击直达复习 Tab（v0.3）。 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as JapanLearnApp).container
        val dueWords = container.progress.dueWordCount().first()
        val dueGrammar = container.progress.dueGrammarCount().first()
        val today = container.stats.todayFlow().first()
        val streak = container.stats.weekly().first().streak
        val state = WidgetMath.compute(
            newTarget = container.settings.dailyNewWords.value,
            newDone = today?.newWords ?: 0,
            grammarTarget = container.settings.dailyNewGrammar.value,
            grammarDone = today?.newGrammar ?: 0,
            dueReviews = dueWords + dueGrammar,
            streak = streak,
        )
        provideContent { TodayWidgetContent(state) }
    }
}

/** 整卡点击动作：打开 App 并跳到复习 Tab（复用通知深链的 extra 约定）。 */
private fun openAppAction() = actionStartActivity(
    Intent().setClassName("com.japanlearn.app", "com.japanlearn.app.MainActivity").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(ReviewReminder.EXTRA_NAVIGATE_TO, ReviewReminder.NAVIGATE_REVIEW)
    },
)

private val DarkAi = ColorProvider(Color(0xFF1B3A5C))
private val Washi = Color(0xFFF7F5F0)

@Composable
private fun TodayWidgetContent(state: WidgetMath.TodayState) {
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(4.dp)
                .cornerRadius(16.dp)
                .background(DarkAi)
                .clickable(openAppAction()),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "今日任务",
                        style = TextStyle(color = ColorProvider(Washi), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        if (state.allDone) "全部完成 ✓" else "连击 ${state.streak} 天",
                        style = TextStyle(color = ColorProvider(Color(0xFFE7A0A0)), fontSize = 12.sp),
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                Metric("新词", state.newWordsLeft)
                Spacer(modifier = GlanceModifier.width(12.dp))
                Metric("语法", state.grammarLeft)
                Spacer(modifier = GlanceModifier.width(12.dp))
                Metric("复习", state.dueReviews)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = TextStyle(color = ColorProvider(Washi), fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
        Text(label, style = TextStyle(color = ColorProvider(Washi.copy(alpha = 0.7f)), fontSize = 11.sp))
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
