package com.jianji.app.domain

object TxType {
    const val EXPENSE = 0
    const val INCOME = 1
    const val TRANSFER = 2

    /** 搜索筛选里表示「不限类型」。 */
    const val ANY = -1
}

object AccountType {
    const val CASH = 0
    const val DEBIT = 1
    const val CREDIT = 2
    const val VIRTUAL = 3

    fun label(type: Int): String = when (type) {
        CASH -> "现金"
        DEBIT -> "储蓄卡"
        CREDIT -> "信用卡"
        VIRTUAL -> "虚拟账户"
        else -> "账户"
    }
}
