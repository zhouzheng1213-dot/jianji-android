package com.jianji.app.data

import com.jianji.app.domain.AccountType
import com.jianji.app.domain.TxType

/**
 * 首次启动写入的默认分类与账户。
 * 配色是一套低饱和的暖色系，保证在暖白底上彼此可区分、又不刺眼。
 */
object Seed {

    /** 支出分类色板 */
    private const val C_RED = "#C8503C"
    private const val C_ORANGE = "#D9863F"
    private const val C_YELLOW = "#C9A227"
    private const val C_OLIVE = "#7E9A55"
    private const val C_GREEN = "#3F8F6B"
    private const val C_TEAL = "#4C8C93"
    private const val C_BLUE = "#4E7C9B"
    private const val C_INDIGO = "#6E6BA8"
    private const val C_PURPLE = "#9B6BA0"
    private const val C_ROSE = "#B5677F"
    private const val C_BROWN = "#A0725A"
    private const val C_GRAY = "#8A8578"

    fun categories(): List<CategoryEntity> {
        var order = 0
        fun next() = order++

        return listOf(
            CategoryEntity(name = "餐饮", type = TxType.EXPENSE, icon = "restaurant", colorHex = C_RED, sortOrder = next()),
            CategoryEntity(name = "交通", type = TxType.EXPENSE, icon = "bus", colorHex = C_BLUE, sortOrder = next()),
            CategoryEntity(name = "购物", type = TxType.EXPENSE, icon = "shopping", colorHex = C_ROSE, sortOrder = next()),
            CategoryEntity(name = "居住", type = TxType.EXPENSE, icon = "home", colorHex = C_BROWN, sortOrder = next()),
            CategoryEntity(name = "日用", type = TxType.EXPENSE, icon = "cart", colorHex = C_OLIVE, sortOrder = next()),
            CategoryEntity(name = "娱乐", type = TxType.EXPENSE, icon = "game", colorHex = C_PURPLE, sortOrder = next()),
            CategoryEntity(name = "医疗", type = TxType.EXPENSE, icon = "hospital", colorHex = C_GREEN, sortOrder = next()),
            CategoryEntity(name = "学习", type = TxType.EXPENSE, icon = "school", colorHex = C_INDIGO, sortOrder = next()),
            CategoryEntity(name = "人情", type = TxType.EXPENSE, icon = "gift", colorHex = C_ORANGE, sortOrder = next()),
            CategoryEntity(name = "通讯", type = TxType.EXPENSE, icon = "phone", colorHex = C_TEAL, sortOrder = next()),
            CategoryEntity(name = "宠物", type = TxType.EXPENSE, icon = "pets", colorHex = C_YELLOW, sortOrder = next()),
            CategoryEntity(name = "其他", type = TxType.EXPENSE, icon = "other", colorHex = C_GRAY, sortOrder = next()),

            CategoryEntity(name = "工资", type = TxType.INCOME, icon = "salary", colorHex = C_GREEN, sortOrder = next()),
            CategoryEntity(name = "奖金", type = TxType.INCOME, icon = "bonus", colorHex = C_YELLOW, sortOrder = next()),
            CategoryEntity(name = "理财", type = TxType.INCOME, icon = "invest", colorHex = C_BLUE, sortOrder = next()),
            CategoryEntity(name = "兼职", type = TxType.INCOME, icon = "parttime", colorHex = C_INDIGO, sortOrder = next()),
            CategoryEntity(name = "红包", type = TxType.INCOME, icon = "redpack", colorHex = C_RED, sortOrder = next()),
            CategoryEntity(name = "其他收入", type = TxType.INCOME, icon = "other_income", colorHex = C_GRAY, sortOrder = next())
        )
    }

    fun accounts(): List<AccountEntity> = listOf(
        AccountEntity(name = "现金", type = AccountType.CASH, icon = "wallet", sortOrder = 0),
        AccountEntity(name = "微信钱包", type = AccountType.VIRTUAL, icon = "virtual", sortOrder = 1),
        AccountEntity(name = "支付宝", type = AccountType.VIRTUAL, icon = "bank", sortOrder = 2),
        AccountEntity(name = "银行卡", type = AccountType.DEBIT, icon = "card", sortOrder = 3)
    )

    /**
     * 内置账本「日常」。必须走 [LedgerDao.insertBuiltIn] 写，
     * 保证 id 就是 [LedgerEntity.DEFAULT_ID]，与 v1 迁移过来的历史流水对齐。
     */
    fun defaultLedger(): LedgerEntity = LedgerEntity(
        id = LedgerEntity.DEFAULT_ID,
        name = LedgerEntity.DEFAULT_NAME,
        icon = LedgerEntity.DEFAULT_ICON,
        colorHex = LedgerEntity.DEFAULT_COLOR,
        budgetCents = 0L,
        sortOrder = 0,
        archived = false,
        builtIn = true
    )

    /** 新建账本时可挑的配色，比分类色板更收敛一些。 */
    val ledgerPalette: List<String> = listOf(
        "#2B2724", "#3F8F6B", "#4E7C9B", "#C8503C",
        "#9B6BA0", "#C9853C", "#4C8C93", "#A0725A"
    )

    /** 自定义分类可选的新增配色 */
    val palette: List<String> = listOf(
        C_RED, C_ORANGE, C_YELLOW, C_OLIVE, C_GREEN, C_TEAL,
        C_BLUE, C_INDIGO, C_PURPLE, C_ROSE, C_BROWN, C_GRAY
    )
}
