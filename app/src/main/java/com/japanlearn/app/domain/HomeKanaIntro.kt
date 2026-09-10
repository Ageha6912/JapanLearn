package com.japanlearn.app.domain

/** 首页零基础五十音横幅：只看用户是否点过「暂时跳过」，不推断假名学习数。 */
object HomeKanaIntro {
    fun shouldShow(dismissed: Boolean): Boolean = !dismissed
}
