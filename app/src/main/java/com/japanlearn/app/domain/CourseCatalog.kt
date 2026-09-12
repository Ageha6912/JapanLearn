package com.japanlearn.app.domain

/**
 * 课程单元目录（PRD §19.8）：N5/N4 各 11 个单元，单元号全级别统一，
 * 与内容 JSON 的 `unit` 字段及 tools/assign_units.py 的映射一致。
 */
object CourseCatalog {
    const val UNITS_PER_LEVEL = 11

    private val TITLES = listOf(
        "人物与称呼", "数字与量", "时间与日期", "食物与饮食", "地点与场所",
        "物品与工具", "动作与行为", "形容词与描述", "副词与表达", "自然与天气", "身体与健康",
    )

    fun isValidUnit(unit: Int): Boolean = unit in 1..UNITS_PER_LEVEL

    fun unitTitle(unit: Int): String = TITLES.getOrElse(unit - 1) { "" }
}
