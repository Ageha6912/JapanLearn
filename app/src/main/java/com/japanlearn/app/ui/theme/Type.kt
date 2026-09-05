@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.japanlearn.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.japanlearn.app.R

/**
 * Manrope 可变字体（OFL）：负责拉丁字母与数字的表现力，
 * 日文/中文字形自动回退到系统字体（Noto Sans CJK），保证离线与体积。
 */
val ManropeFamily = FontFamily(
    Font(
        resId = R.font.manrope_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.manrope_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.manrope_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.manrope_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        resId = R.font.manrope_variable,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)

private val Manrope = ManropeFamily

val JapanLearnTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Manrope, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold,
        lineHeight = 54.sp, letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Manrope, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
        lineHeight = 46.sp, letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Manrope, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
        lineHeight = 40.sp, letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Manrope, fontSize = 32.sp, fontWeight = FontWeight.Bold,
        lineHeight = 38.sp, letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope, fontSize = 26.sp, fontWeight = FontWeight.Bold,
        lineHeight = 32.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Manrope, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        lineHeight = 27.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp, letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.Normal,
        lineHeight = 25.sp, letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight.Normal,
        lineHeight = 21.sp, letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope, fontSize = 12.sp, fontWeight = FontWeight.Normal,
        lineHeight = 17.sp, letterSpacing = 0.15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        lineHeight = 14.sp, letterSpacing = 0.4.sp,
    ),
)
