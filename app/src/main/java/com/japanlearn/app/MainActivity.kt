package com.japanlearn.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.japanlearn.app.ui.home.HomeScreen
import com.japanlearn.app.ui.kana.KanaQuizScreen
import com.japanlearn.app.ui.kana.KanaScreen
import com.japanlearn.app.ui.grammar.GrammarDetailScreen
import com.japanlearn.app.ui.grammar.GrammarListScreen
import com.japanlearn.app.ui.grammar.GrammarSessionScreen
import com.japanlearn.app.ui.learn.LearnTabScreen
import com.japanlearn.app.ui.motion.MotionTokens
import com.japanlearn.app.ui.motion.rememberReducedMotion
import com.japanlearn.app.ui.profile.ProfileScreen
import com.japanlearn.app.ui.review.ReviewHomeScreen
import com.japanlearn.app.ui.review.ReviewSessionScreen
import com.japanlearn.app.ui.review.WrongAnswersScreen
import com.japanlearn.app.ui.sentence.SentenceScreen
import com.japanlearn.app.ui.stats.StatsScreen
import com.japanlearn.app.ui.theme.JapanLearnTheme
import com.japanlearn.app.ui.words.WordListScreen
import com.japanlearn.app.ui.words.WordSessionScreen
import com.japanlearn.app.work.ReviewReminder

/** 全局依赖容器 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

object Routes {
    const val HOME = "home"
    const val LEARN = "learn"
    const val REVIEW = "review"
    const val PROFILE = "profile"

    const val KANA = "kana"
    const val KANA_QUIZ = "kanaQuiz"
    const val WORD_LIST = "wordList"
    const val WORD_SESSION = "wordSession/{count}"
    const val GRAMMAR_LIST = "grammarList"
    const val GRAMMAR_DETAIL = "grammarDetail/{id}"
    const val GRAMMAR_SESSION = "grammarSession/{count}"
    const val REVIEW_SESSION = "reviewSession"
    const val WRONG_ANSWERS = "wrongAnswers"
    const val STATS = "stats"
    const val SENTENCE = "sentence/{index}"

    fun wordSession(count: Int) = "wordSession/$count"
    fun grammarSession(count: Int) = "grammarSession/$count"
    fun grammarDetail(id: String) = "grammarDetail/$id"
    fun sentence(index: Int) = "sentence/$index"

    val TABS = listOf(HOME, LEARN, REVIEW, PROFILE)
}

class MainActivity : ComponentActivity() {

    private val navigateTo = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        navigateTo.value = intent?.getStringExtra(ReviewReminder.EXTRA_NAVIGATE_TO)
        setContent {
            val container = (application as JapanLearnApp).container
            CompositionLocalProvider(LocalAppContainer provides container) {
                JapanLearnTheme {
                    MainRoot(navTarget = navigateTo.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navigateTo.value = intent.getStringExtra(ReviewReminder.EXTRA_NAVIGATE_TO)
    }
}

@Composable
fun MainRoot(navTarget: String? = null) {
    val navController = rememberNavController()

    // 通知深链：跳转到复习 Tab
    LaunchedEffect(navTarget) {
        if (navTarget == com.japanlearn.app.work.ReviewReminder.NAVIGATE_REVIEW) {
            navController.navigate(Routes.REVIEW) { launchSingleTop = true }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTab = currentRoute in Routes.TABS
    val reduceMotion = rememberReducedMotion()

    // 转场语言：底部页签 = 淡入 + 轻缩放；二级页 = 自下而上滑入，返回时滑落。
    val enterTransition = if (reduceMotion) fadeIn(tween(150)) else fadeIn(tween(260)) + scaleIn(tween(320, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.97f)
    val exitTransition = fadeOut(tween(150))
    val detailEnter = if (reduceMotion) {
        fadeIn(tween(150))
    } else {
        slideInVertically(tween(380, easing = MotionTokens.EmphasizedDecelerate)) { it / 6 } + fadeIn(tween(260))
    }
    val detailExit = if (reduceMotion) fadeOut(tween(150)) else slideOutVertically(tween(280, easing = MotionTokens.Emphasized)) { it / 8 } + fadeOut(tween(200))

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isTab,
                enter = if (reduceMotion) fadeIn() else slideInVertically(tween(320, easing = MotionTokens.EmphasizedDecelerate)) { it } + fadeIn(),
                exit = if (reduceMotion) fadeOut() else slideOutVertically(tween(240)) { it } + fadeOut(tween(160)),
            ) {
                JapanBottomBar(navController, currentRoute)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = {
                if (targetState.destination.route in Routes.TABS) enterTransition else detailEnter
            },
            exitTransition = { exitTransition },
            popEnterTransition = { enterTransition },
            popExitTransition = {
                if (targetState.destination.route in Routes.TABS) exitTransition else detailExit
            },
        ) {
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.LEARN) { LearnTabScreen(navController) }
            composable(Routes.REVIEW) { ReviewHomeScreen(navController) }
            composable(Routes.PROFILE) { ProfileScreen(navController) }

            composable(Routes.KANA) { KanaScreen(navController) }
            composable(Routes.KANA_QUIZ) { KanaQuizScreen(navController) }
            composable(Routes.WORD_LIST) { WordListScreen(navController) }
            composable(Routes.WORD_SESSION) { entry ->
                val count = entry.arguments?.getString("count")?.toIntOrNull() ?: 10
                WordSessionScreen(navController, count)
            }
            composable(Routes.GRAMMAR_LIST) { GrammarListScreen(navController) }
            composable(Routes.GRAMMAR_DETAIL) { entry ->
                GrammarDetailScreen(navController, entry.arguments?.getString("id") ?: "")
            }
            composable(Routes.GRAMMAR_SESSION) { entry ->
                val count = entry.arguments?.getString("count")?.toIntOrNull() ?: 3
                GrammarSessionScreen(navController, count)
            }
            composable(Routes.REVIEW_SESSION) { ReviewSessionScreen(navController) }
            composable(Routes.WRONG_ANSWERS) { WrongAnswersScreen(navController) }
            composable(Routes.STATS) { StatsScreen(navController) }
            composable(Routes.SENTENCE) { entry ->
                SentenceScreen(navController, entry.arguments?.getString("index")?.toIntOrNull() ?: 0)
            }
        }
    }
}

private data class TabItem(
    val route: String,
    val label: String,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector,
)

/** 底部导航：胶囊指示器弹性展开 + 图标缩放，选中/未选中使用成对填充/描边图标。 */
@Composable
private fun JapanBottomBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(
        TabItem(Routes.HOME, "首页", Icons.Filled.Home, Icons.Outlined.Home),
        TabItem(Routes.LEARN, "学习", Icons.Filled.School, Icons.Outlined.School),
        TabItem(Routes.REVIEW, "复习", Icons.Filled.Refresh, Icons.Outlined.Refresh),
        TabItem(Routes.PROFILE, "我的", Icons.Filled.Person, Icons.Outlined.Person),
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val indicator by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = MotionTokens.springBouncy(),
                    label = "tabIndicator",
                )
                val iconScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (selected) 1.12f else 1f,
                    animationSpec = MotionTokens.springBouncy(),
                    label = "tabIconScale",
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .clickableNoRipple { 
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (indicator > 0.01f) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = indicator),
                                    shape = RoundedCornerShape(100),
                                    modifier = Modifier.size(width = 46.dp * indicator.coerceIn(0.3f, 1f), height = 30.dp),
                                ) {}
                            }
                            Icon(
                                if (selected) item.iconFilled else item.iconOutlined,
                                contentDescription = item.label,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp).graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            )
                        }
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    Modifier.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
