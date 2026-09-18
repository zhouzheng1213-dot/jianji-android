package com.jianji.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories", indices = [Index("type"), Index("hidden")])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** TxType.EXPENSE / TxType.INCOME */
    val type: Int,
    /** 图标注册表 key，见 ui/IconRegistry.kt */
    val icon: String,
    /** 统计图表配色，形如 "#C8503C" */
    val colorHex: String,
    val sortOrder: Int,
    val hidden: Boolean = false,
    /** 内置分类不可删除，只能隐藏；自定义分类可删。 */
    val builtIn: Boolean = true
)

@Entity(tableName = "accounts", indices = [Index("archived")])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: Int,
    /** 建账时的期初余额（分） */
    val initialBalanceCents: Long = 0L,
    val icon: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false
)

/**
 * 独立账本。每条流水都归属一个账本 —— 默认账本「日常」承载日常记账，
 * 用户可以为一次旅行、一个项目单独开一本，塞进专项资金并设预算。
 */
@Entity(tableName = "ledgers", indices = [Index("archived")])
data class LedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val icon: String,
    val colorHex: String,
    /** 预算（分）。<= 0 表示这本不设预算。 */
    val budgetCents: Long = 0L,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** 内置账本不可删除，只能改名或隐藏。 */
    val builtIn: Boolean = false
) {
    companion object {
        /** 默认账本的固定 id。v1 的历史流水迁移后全部落到这本。 */
        const val DEFAULT_ID: Long = 0L

        const val DEFAULT_NAME = "日常"

        const val DEFAULT_COLOR = "#2B2724"

        const val DEFAULT_ICON = "wallet"

        /** 账本图标可选的 key，取自 ui/IconRegistry.kt。 */
        val ICON_KEYS: List<String> = listOf(
            "wallet", "flight", "home", "gift", "star", "favorite",
            "work", "school", "savings", "card", "pet", "cake"
        )
    }
}

@Entity(
    tableName = "transactions",
    indices = [
        Index("ledgerId"),
        Index("dateEpochDay"),
        Index("categoryId"),
        Index("accountId"),
        Index("type")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** TxType.EXPENSE / INCOME / TRANSFER */
    val type: Int,
    /** 始终为正数，方向由 type 决定 */
    val amountCents: Long,
    /** 转账没有分类，为 null */
    val categoryId: Long?,
    /** 支出=付款账户；收入=收款账户；转账=转出账户 */
    val accountId: Long,
    /** 仅转账使用：转入账户 */
    val toAccountId: Long?,
    val dateEpochDay: Long,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * 所属账本。v1 升级上来的历史数据一律落到 [LedgerEntity.DEFAULT_ID]。
     * 声明 SQL 默认值是为了让 `ALTER TABLE ADD COLUMN` 能直接加上非空列。
     */
    @ColumnInfo(defaultValue = "0") val ledgerId: Long = LedgerEntity.DEFAULT_ID
)
