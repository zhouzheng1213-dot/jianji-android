package com.jianji.app.data

import android.content.Context

/**
 * 轻量偏好存储：记住上次用的账本、账户与分类，让「记一笔」尽量少点几下。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("jianji_settings", Context.MODE_PRIVATE)

    /** 上次停留的账本。回落到内置的「日常」。 */
    var lastLedgerId: Long
        get() = prefs.getLong(KEY_LEDGER, LedgerEntity.DEFAULT_ID)
        set(value) = prefs.edit().putLong(KEY_LEDGER, value).apply()

    var lastAccountId: Long
        get() = prefs.getLong(KEY_ACCOUNT, -1L)
        set(value) = prefs.edit().putLong(KEY_ACCOUNT, value).apply()

    var lastExpenseCategoryId: Long
        get() = prefs.getLong(KEY_EXPENSE_CATEGORY, -1L)
        set(value) = prefs.edit().putLong(KEY_EXPENSE_CATEGORY, value).apply()

    var lastIncomeCategoryId: Long
        get() = prefs.getLong(KEY_INCOME_CATEGORY, -1L)
        set(value) = prefs.edit().putLong(KEY_INCOME_CATEGORY, value).apply()

    private companion object {
        const val KEY_LEDGER = "last_ledger_id"
        const val KEY_ACCOUNT = "last_account_id"
        const val KEY_EXPENSE_CATEGORY = "last_expense_category_id"
        const val KEY_INCOME_CATEGORY = "last_income_category_id"
    }
}
