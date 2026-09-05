package com.japanlearn.app.util

/** 学习时长展示：>= 1 小时显示 h/m，否则只显示分钟。 */
fun formatStudyDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val h = safeSeconds / 3600
    val m = (safeSeconds % 3600) / 60
    return if (h > 0) "${h}h${m}m" else "${m}m"
}
