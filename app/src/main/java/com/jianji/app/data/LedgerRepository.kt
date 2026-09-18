package com.jianji.app.data

import com.jianji.app.domain.TxType

enum class DeleteOutcome { OK, BUILT_IN, IN_USE }

/**
 * 唯一的数据库出入口。UI 与 ViewModel 只依赖它，不直接接触 DAO。
 *
 * 关于「作用域」的约定：
 *  - **流水**（明细、统计、搜索）永远按账本过滤；
 *  - **分类**与**账户**是全局资源，跨账本共享 —— 一次旅行账本里记的「餐饮」，
 *    和日常账本里的「餐饮」是同一个分类，统计口径才可比；
 *  - **账户余额**也只按账户本身算，不按账本切分，否则「我有多少钱」会随当前账本跳变。
 */
class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val txDao: TransactionDao
) {

    // ---------- 首次运行种子化 ----------

    suspend fun ensureSeeded() {
        if (ledgerDao.count() == 0) {
            val seed = Seed.defaultLedger()
            ledgerDao.insertBuiltIn(
                id = seed.id,
                name = seed.name,
                icon = seed.icon,
                colorHex = seed.colorHex,
                budgetCents = seed.budgetCents,
                sortOrder = seed.sortOrder
            )
        }
        if (categoryDao.count() == 0) categoryDao.insertAll(Seed.categories())
        if (accountDao.count() == 0) accountDao.insertAll(Seed.accounts())
    }

    // ---------- 账本 ----------

    fun observeLedgers() = ledgerDao.observeActive()

    fun observeAllLedgers() = ledgerDao.observeAll()

    suspend fun findLedger(id: Long): LedgerEntity? = ledgerDao.find(id)

    suspend fun ledgerCount(): Int = ledgerDao.count()

    suspend fun addLedger(name: String, icon: String, colorHex: String, budgetCents: Long): Long {
        val order = ledgerDao.nextSortOrder()
        return ledgerDao.insert(
            LedgerEntity(
                name = name,
                icon = icon,
                colorHex = colorHex,
                budgetCents = budgetCents,
                sortOrder = order,
                builtIn = false
            )
        )
    }

    suspend fun updateLedger(item: LedgerEntity) = ledgerDao.update(item)

    suspend fun setLedgerArchived(id: Long, archived: Boolean) =
        ledgerDao.setArchived(id, archived)

    /** 账本里还有流水就不让删，避免账目凭空蒸发。 */
    suspend fun deleteLedger(id: Long): DeleteOutcome {
        val item = ledgerDao.find(id) ?: return DeleteOutcome.OK
        if (item.builtIn) return DeleteOutcome.BUILT_IN
        if (txDao.countIn(id) > 0) return DeleteOutcome.IN_USE
        ledgerDao.deleteCustom(id)
        return DeleteOutcome.OK
    }

    // ---------- 观测（按账本作用域） ----------

    fun observeCategories(type: Int) = categoryDao.observeVisible(type)

    fun observeAllCategories() = categoryDao.observeAll()

    fun observeAccounts() = accountDao.observeWithBalance()

    fun observeActiveAccounts() = accountDao.observeActive()

    fun observeAllAccounts() = accountDao.observeAll()

    fun observeCountIn(ledgerId: Long) = txDao.observeCountIn(ledgerId)

    fun observeTotalCount() = txDao.observeTotalCount()

    fun observeRange(ledgerId: Long, from: Long, to: Long) = txDao.observeRange(ledgerId, from, to)

    fun observeTypeTotals(ledgerId: Long, from: Long, to: Long) =
        txDao.observeTypeTotals(ledgerId, from, to)

    fun observeDayTotals(ledgerId: Long, from: Long, to: Long) =
        txDao.observeDayTotals(ledgerId, from, to)

    fun observeCategoryStats(ledgerId: Long, type: Int, from: Long, to: Long) =
        txDao.observeCategoryStats(ledgerId, type, from, to)

    fun search(
        ledgerId: Long,
        from: Long,
        to: Long,
        type: Int,
        categoryId: Long,
        keyword: String,
        minCents: Long,
        maxCents: Long
    ) = txDao.search(ledgerId, from, to, type, categoryId, keyword, minCents, maxCents)

    // ---------- 流水读写 ----------

    suspend fun findTransaction(id: Long): TransactionEntity? = txDao.find(id)

    suspend fun insertTransaction(item: TransactionEntity): Long = txDao.insert(item)

    suspend fun updateTransaction(item: TransactionEntity) = txDao.update(item)

    suspend fun deleteTransaction(id: Long) = txDao.deleteById(id)

    /** 只清空当前账本 —— 有了独立账本之后，「清空全部」的语义必须收窄。 */
    suspend fun clearLedger(ledgerId: Long) = txDao.clearIn(ledgerId)

    suspend fun transactionCountIn(ledgerId: Long): Int = txDao.countIn(ledgerId)

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

    companion object {
        fun isTransfer(type: Int) = type == TxType.TRANSFER
    }
}
