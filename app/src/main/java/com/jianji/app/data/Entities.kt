package com.jianji.app.data

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

@Entity(
    tableName = "transactions",
    indices = [
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
    val updatedAt: Long
)
