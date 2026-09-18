package com.jianji.app.data

/** 流水行 + 关联的分类/账户名称，避免 UI 层再查一次数据库。 */
data class TxRow(
    val id: Long,
    val type: Int,
    val amountCents: Long,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long?,
    val dateEpochDay: Long,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val ledgerId: Long,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: String?,
    val accountName: String?,
    val toAccountName: String?
) {
    fun toEntity(): TransactionEntity = TransactionEntity(
        id = id,
        type = type,
        amountCents = amountCents,
        categoryId = categoryId,
        accountId = accountId,
        toAccountId = toAccountId,
        dateEpochDay = dateEpochDay,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        ledgerId = ledgerId
    )
}

/** 账户 + 实时余额（期初 + 收入 - 支出 - 转出 + 转入）。 */
data class AccountWithBalance(
    val id: Long,
    val name: String,
    val type: Int,
    val icon: String,
    val initialBalanceCents: Long,
    val balanceCents: Long
)

/** 分类维度聚合，用于统计页的环形图与排行。 */
data class CategoryStatRow(
    val categoryId: Long?,
    val name: String?,
    val icon: String?,
    val colorHex: String?,
    val totalCents: Long,
    val txCount: Int
)

/** 按类型聚合的收支合计，用于汇总卡。 */
data class TypeTotal(
    val type: Int,
    val totalCents: Long
)

/** 按天 + 类型聚合，用于趋势柱状图。 */
data class DayTotal(
    val dateEpochDay: Long,
    val type: Int,
    val totalCents: Long
)
