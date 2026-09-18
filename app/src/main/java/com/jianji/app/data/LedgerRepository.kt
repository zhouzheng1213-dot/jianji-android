package com.jianji.app.data

import com.jianji.app.domain.TxType

enum class DeleteOutcome { OK, BUILT_IN, IN_USE }

/**
 * 唯一的数据库出入口。UI 与 ViewModel 只依赖它，不直接接触 DAO。
 */
class LedgerRepository(
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val txDao: TransactionDao
) {

    // ---------- 首次运行种子化 ----------

    suspend fun ensureSeeded() {
        if (categoryDao.count() == 0) categoryDao.insertAll(Seed.categories())
        if (accountDao.count() == 0) accountDao.insertAll(Seed.accounts())
    }

    // ---------- 观测 ----------

    fun observeCategories(type: Int) = categoryDao.observeVisible(type)

    fun observeAllCategories() = categoryDao.observeAll()

    fun observeAccounts() = accountDao.observeWithBalance()

    fun observeActiveAccounts() = accountDao.observeActive()

    fun observeAllAccounts() = accountDao.observeAll()

    fun observeTransactionCount() = txDao.observeCount()

    fun observeRange(from: Long, to: Long) = txDao.observeRange(from, to)

    fun observeTypeTotals(from: Long, to: Long) = txDao.observeTypeTotals(from, to)

    fun observeDayTotals(from: Long, to: Long) = txDao.observeDayTotals(from, to)

    fun observeCategoryStats(type: Int, from: Long, to: Long) =
        txDao.observeCategoryStats(type, from, to)

    fun search(
        from: Long,
        to: Long,
        type: Int,
        categoryId: Long,
        keyword: String,
        minCents: Long,
        maxCents: Long
    ) = txDao.search(from, to, type, categoryId, keyword, minCents, maxCents)

    // ---------- 流水读写 ----------

    suspend fun findTransaction(id: Long): TransactionEntity? = txDao.find(id)

    suspend fun insertTransaction(item: TransactionEntity): Long = txDao.insert(item)

    suspend fun updateTransaction(item: TransactionEntity) = txDao.update(item)

    suspend fun deleteTransaction(id: Long) = txDao.deleteById(id)

    suspend fun clearAllTransactions() = txDao.clearAll()

    // ---------- 分类维护 ----------

    suspend fun addCategory(name: String, type: Int, icon: String, colorHex: String): Long {
        val order = categoryDao.maxSortOrder(type) + 1
        return categoryDao.insert(
            CategoryEntity(
                name = name,
                type = type,
                icon = icon,
                colorHex = colorHex,
                sortOrder = order,
                builtIn = false
            )
        )
    }

    suspend fun updateCategory(item: CategoryEntity) = categoryDao.update(item)

    suspend fun setCategoryHidden(id: Long, hidden: Boolean) = categoryDao.setHidden(id, hidden)

    suspend fun categoryUsage(id: Long): Int = txDao.countByCategory(id)

    suspend fun deleteCategory(id: Long): DeleteOutcome {
        val item = categoryDao.find(id) ?: return DeleteOutcome.OK
        if (item.builtIn) return DeleteOutcome.BUILT_IN
        if (txDao.countByCategory(id) > 0) return DeleteOutcome.IN_USE
        categoryDao.deleteCustom(id)
        return DeleteOutcome.OK
    }

    // ---------- 账户维护 ----------

    suspend fun addAccount(
        name: String,
        type: Int,
        icon: String,
        initialBalanceCents: Long
    ): Long {
        val order = accountDao.maxSortOrder() + 1
        return accountDao.insert(
            AccountEntity(
                name = name,
                type = type,
                icon = icon,
                initialBalanceCents = initialBalanceCents,
                sortOrder = order
            )
        )
    }

    suspend fun updateAccount(item: AccountEntity) = accountDao.update(item)

    suspend fun findAccount(id: Long): AccountEntity? = accountDao.find(id)

    suspend fun firstAccount(): AccountEntity? = accountDao.first()

    suspend fun accountUsage(id: Long): Int = txDao.countByAccount(id)

    suspend fun archiveAccount(id: Long) = accountDao.setArchived(id, true)

    suspend fun restoreAccount(id: Long) = accountDao.setArchived(id, false)

    suspend fun accountCount(): Int = accountDao.count()

    suspend fun hasTransferCategoryNeed(): Boolean = false

    companion object {
        fun isTransfer(type: Int) = type == TxType.TRANSFER
    }
}
