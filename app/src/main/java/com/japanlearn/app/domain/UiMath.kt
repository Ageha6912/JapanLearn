package com.japanlearn.app.domain

/** UI 层共用的纯计算，便于单元测试。 */
object UiMath {

    /** 今日完成进度（0..1）；total <= 0 时返回 0，超出部分截断。 */
    fun dailyProgress(done: Int, total: Int): Float =
        if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    /** 柱状图单柱高度占比（0..1）；max <= 0 时返回 0，超出部分截断。 */
    fun barFraction(value: Int, max: Int): Float =
        if (max <= 0) 0f else (value.toFloat() / max).coerceIn(0f, 1f)

    /** 入场级联延迟毫秒：按序号递增并封顶，避免长列表尾部等待。 */
    fun staggerDelayMs(index: Int, stepMs: Long, maxSteps: Int): Long =
        index.coerceIn(0, maxSteps) * stepMs
}
