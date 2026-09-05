package com.japanlearn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 藍（あい）× 朱（しゅ）× 抹茶 · 和纸浅色 / 墨夜深色
private val LightColors = lightColorScheme(
    primary = AiIndigo,
    onPrimary = Color.White,
    primaryContainer = AiIndigoContainer,
    onPrimaryContainer = AiIndigoDark,
    inversePrimary = Color(0xFFA9C6E8),
    secondary = ShuVermilion,
    onSecondary = Color.White,
    secondaryContainer = ShuContainer,
    onSecondaryContainer = ShuDeep,
    tertiary = MatchaGreen,
    onTertiary = Color.White,
    tertiaryContainer = MatchaContainer,
    onTertiaryContainer = MatchaDeep,
    background = WashiBg,
    onBackground = OnWashi,
    surface = WashiSurface,
    onSurface = OnWashi,
    surfaceVariant = WashiVariant,
    onSurfaceVariant = OnWashiMuted,
    surfaceContainerLowest = Color(0xFFF2EFE7),
    surfaceContainerLow = WashiSurfaceLow,
    surfaceContainer = WashiSurfaceContainer,
    surfaceContainerHigh = WashiSurfaceHigh,
    surfaceContainerHighest = WashiSurfaceHighest,
    surfaceTint = WashiSurface,
    inverseSurface = Color(0xFF302F2B),
    inverseOnSurface = Color(0xFFF4F1E8),
    outline = OutlineWashi,
    outlineVariant = OutlineVariantWashi,
    error = AkaneRed,
    onError = Color.White,
    errorContainer = AkaneContainer,
    onErrorContainer = Color(0xFF571A1C),
    scrim = Color(0x661B1B18),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C6E8),
    onPrimary = Color(0xFF123152),
    primaryContainer = AiIndigo,
    onPrimaryContainer = Color(0xFFD6E4F5),
    inversePrimary = AiIndigo,
    secondary = Color(0xFFE8A18D),
    onSecondary = Color(0xFF4E1F0F),
    secondaryContainer = Color(0xFF74331F),
    onSecondaryContainer = ShuContainer,
    tertiary = Color(0xFFB3CCB0),
    onTertiary = Color(0xFF1F3A22),
    tertiaryContainer = Color(0xFF3E5A3E),
    onTertiaryContainer = MatchaContainer,
    background = SumiBg,
    onBackground = OnSumi,
    surface = SumiSurface,
    onSurface = OnSumi,
    surfaceVariant = SumiVariant,
    onSurfaceVariant = OnSumiMuted,
    surfaceContainerLowest = Color(0xFF101216),
    surfaceContainerLow = SumiSurfaceLow,
    surfaceContainer = SumiSurfaceContainer,
    surfaceContainerHigh = SumiSurfaceHigh,
    surfaceContainerHighest = SumiSurfaceHighest,
    surfaceTint = SumiSurface,
    inverseSurface = OnSumi,
    inverseOnSurface = Color(0xFF302F2B),
    outline = OutlineSumi,
    outlineVariant = OutlineVariantSumi,
    error = Color(0xFFEF9A9C),
    onError = Color(0xFF571A1C),
    errorContainer = Color(0xFF6E2A2D),
    onErrorContainer = AkaneContainer,
    scrim = Color(0x88000000),
)

/** 语义扩展色：掌握度四档、对错反馈等，跟随明暗主题成对提供。 */
data class JapanColors(
    val correct: Color,
    val correctContainer: Color,
    val wrong: Color,
    val wrongContainer: Color,
    val masteryUnknown: Color,
    val masteryUnknownContainer: Color,
    val masteryFuzzy: Color,
    val masteryFuzzyContainer: Color,
    val masteryKnown: Color,
    val masteryKnownContainer: Color,
    val masteryMastered: Color,
    val masteryMasteredContainer: Color,
    val confetti: List<Color>,
)

private val LightJapanColors = JapanColors(
    correct = MatchaGreen,
    correctContainer = MatchaContainer,
    wrong = AkaneRed,
    wrongContainer = AkaneContainer,
    masteryUnknown = AkaneRed,
    masteryUnknownContainer = AkaneContainer,
    masteryFuzzy = YamabukiAmber,
    masteryFuzzyContainer = YamabukiContainer,
    masteryKnown = MatchaGreen,
    masteryKnownContainer = MatchaContainer,
    masteryMastered = SeihekiTeal,
    masteryMasteredContainer = SeihekiContainer,
    confetti = listOf(AiIndigo, ShuVermilion, MatchaGreen, YamabukiAmber, SeihekiTeal),
)

private val DarkJapanColors = JapanColors(
    correct = Color(0xFFB3CCB0),
    correctContainer = Color(0xFF3E5A3E),
    wrong = Color(0xFFEF9A9C),
    wrongContainer = Color(0xFF6E2A2D),
    masteryUnknown = Color(0xFFEF9A9C),
    masteryUnknownContainer = Color(0xFF6E2A2D),
    masteryFuzzy = Color(0xFFE3C383),
    masteryFuzzyContainer = Color(0xFF5C4515),
    masteryKnown = Color(0xFFB3CCB0),
    masteryKnownContainer = Color(0xFF3E5A3E),
    masteryMastered = Color(0xFF9CC7C5),
    masteryMasteredContainer = Color(0xFF2C5654),
    confetti = listOf(Color(0xFFA9C6E8), Color(0xFFE8A18D), Color(0xFFB3CCB0), Color(0xFFE3C383), Color(0xFF9CC7C5)),
)

val LocalJapanColors = staticCompositionLocalOf { LightJapanColors }

@Composable
fun japanColors(): JapanColors = LocalJapanColors.current

@Composable
fun JapanLearnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = JapanLearnTypography,
        shapes = JapanLearnShapes,
        content = {
            CompositionLocalProvider(
                LocalJapanColors provides if (darkTheme) DarkJapanColors else LightJapanColors,
                content = content,
            )
        },
    )
}
