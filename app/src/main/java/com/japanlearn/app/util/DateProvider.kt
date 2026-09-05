package com.japanlearn.app.util

import java.time.LocalDate
import java.time.ZoneId

/** 可注入的时钟，单元测试使用固定实现。 */
interface DateProvider {
    fun nowMillis(): Long
    fun today(): LocalDate
    fun todayString(): String = today().toString()
}

class SystemDateProvider(private val zone: ZoneId = ZoneId.systemDefault()) : DateProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now(zone)
}
