package com.jianji.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jianji.app.data.AccountEntity
import com.jianji.app.data.AccountWithBalance
import com.jianji.app.data.CategoryEntity
import com.jianji.app.data.CategoryStatRow
import com.jianji.app.data.DeleteOutcome
import com.jianji.app.data.LedgerRepository
import com.jianji.app.data.SettingsStore
import com.jianji.app.data.TransactionEntity
import com.jianji.app.data.TxRow
import com.jianji.app.domain.Dates
import com.jianji.app.domain.TxType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** 全库最早的日期边界，用作「不限时间」的哨兵值。 */
private const val EPOCH_MIN = 0L
private const val EPOCH_MAX = 100_000L

data class LedgerUiState(
    val month: YearMonth,
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val archivedAccounts: List<AccountEntity> = emptyList(),
    val balances: List<AccountWithBalance> = emptyList(),
    val dayGroups: List<DayGroup> = emptyList(),
    val monthExpenseCents: Long = 0L,
    val monthIncomeCents: Long = 0L,
    val transactionCount: Int = 0
) {
    val monthBalanceCents: Long get() = monthIncomeCents - monthExpenseCents
    val totalAssetsCents: Long get() = balances.sumOf { it.balanceCents }
    val isCurrentMonth: Boolean get() = month == YearMonth.now()
}

data class StatsUiState(
    val month: YearMonth,
    val mode: Int,
    val categoryStats: List<CategoryStatRow> = emptyList(),
    val totalCents: Long = 0L,
    val dailyExpense: List<Pair<Long, Long>> = emptyList(),
    val trend: List<TrendPoint> = emptyList()
)

data class SearchUiState(
    val filter: SearchFilter,
    val results: List<TxRow> = emptyList()
) {
    val totalCents: Long get() = results.sumOf { it.amountCents }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repo: LedgerRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val month = MutableStateFlow(Dates.currentMonth())
    private val statsMode = MutableStateFlow(TxType.EXPENSE)
    private val filter = MutableStateFlow(SearchFilter())

    val currentMonth: StateFlow<YearMonth> = month.asStateFlow()
    val searchFilter: StateFlow<SearchFilter> = filter.asStateFlow()

    // 账户与分类属于「跨月常驻」数据，单独合成一次，避免每次切月都重新订阅。
    private val categoriesFlow = combine(
        repo.observeCategories(TxType.EXPENSE),
        repo.observeCategories(TxType.INCOME)
    ) { expense, income -> expense to income }

    private val accountsFlow = combine(
        repo.observeActiveAccounts(),
        repo.observeAccounts(),
        repo.observeAllAccounts()
    ) { active, balances, all -> Triple(active, balances, all) }

    private val stableFlow = combine(
        categoriesFlow,
        accountsFlow,
        repo.observeTransactionCount()
    ) { categories, accounts, count -> Triple(categories, accounts, count) }

    val uiState: StateFlow<LedgerUiState> = month.flatMapLatest { ym ->
        val from = Dates.monthStart(ym)
        val to = Dates.monthEnd(ym)
        combine(
            repo.observeRange(from, to),
            repo.observeTypeTotals(from, to),
            stableFlow
        ) { rows, totals, stable ->
            val (categories, accounts, count) = stable
            LedgerUiState(
                month = ym,
                expenseCategories = categories.first,
                incomeCategories = categories.second,
                accounts = accounts.first,
                balances = accounts.second,
                archivedAccounts = accounts.third.filter { it.archived },
                dayGroups = rows.groupByDay(),
                monthExpenseCents = totals.firstOrNull { it.type == TxType.EXPENSE }?.totalCents ?: 0L,
                monthIncomeCents = totals.firstOrNull { it.type == TxType.INCOME }?.totalCents ?: 0L,
                transactionCount = count
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(month = Dates.currentMonth())
    )

    val statsState: StateFlow<StatsUiState> = combine(month, statsMode) { m, mode -> m to mode }
        .flatMapLatest { (m, mode) ->
            val from = Dates.monthStart(m)
            val to = Dates.monthEnd(m)
            val trendFrom = Dates.monthStart(m.minusMonths(5))
            combine(
                repo.observeCategoryStats(mode, from, to),
                repo.observeDayTotals(from, to),
                repo.observeDayTotals(trendFrom, to)
            ) { stats, days, trendDays ->
                StatsUiState(
                    month = m,
                    mode = mode,
                    categoryStats = stats,
                    totalCents = stats.sumOf { it.totalCents },
                    dailyExpense = days
                        .filter { it.type == TxType.EXPENSE }
                        .map { it.dateEpochDay to it.totalCents },
                    trend = buildTrend(m, trendDays)
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(Dates.currentMonth(), TxType.EXPENSE)
        )

    val allCategories: StateFlow<List<CategoryEntity>> = repo.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchState: StateFlow<SearchUiState> = filter.flatMapLatest { f ->
        val (from, to) = f.rangeBounds()
        repo.search(
            from = from,
            to = to,
            type = f.type,
            categoryId = f.categoryId,
            keyword = f.keyword.trim(),
            minCents = f.minCents,
            maxCents = f.maxCents
        ).map { rows -> SearchUiState(f, rows) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(SearchFilter())
    )

    // ---------- 月份导航 ----------

    fun stepMonth(delta: Long) {
        month.value = Dates.shiftMonth(month.value, delta)
    }

    fun setMonth(target: YearMonth) {
        month.value = target
    }

    fun setStatsMode(mode: Int) {
        statsMode.value = mode
    }

    // ---------- 筛选 ----------

    fun updateFilter(transform: (SearchFilter) -> SearchFilter) {
        filter.value = transform(filter.value)
    }

    fun resetFilter() {
        filter.value = SearchFilter()
    }

    // ---------- 流水 ----------

    suspend fun findTransaction(id: Long): TransactionEntity? = repo.findTransaction(id)

    fun saveTransaction(entity: TransactionEntity, editing: Boolean, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (editing) {
                repo.updateTransaction(entity)
                entity.id
            } else {
                repo.insertTransaction(entity)
            }
            if (entity.type == TxType.EXPENSE) {
                settings.lastExpenseCategoryId = entity.categoryId ?: -1L
            } else if (entity.type == TxType.INCOME) {
                settings.lastIncomeCategoryId = entity.categoryId ?: -1L
            }
            settings.lastAccountId = entity.accountId
            onDone(id)
        }
    }

    fun deleteTransaction(id: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteTransaction(id)
            onDone()
        }
    }

    fun clearAllTransactions(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.clearAllTransactions()
            onDone()
        }
    }

    // ---------- 分类 ----------

    fun addCategory(name: String, type: Int, icon: String, colorHex: String) {
        viewModelScope.launch { repo.addCategory(name, type, icon, colorHex) }
    }

    fun updateCategory(item: CategoryEntity) {
        viewModelScope.launch { repo.updateCategory(item) }
    }

    fun setCategoryHidden(id: Long, hidden: Boolean) {
        viewModelScope.launch { repo.setCategoryHidden(id, hidden) }
    }

    fun deleteCategory(id: Long, onResult: (DeleteOutcome) -> Unit) {
        viewModelScope.launch { onResult(repo.deleteCategory(id)) }
    }

    // ---------- 账户 ----------

    fun addAccount(name: String, type: Int, icon: String, initialBalanceCents: Long) {
        viewModelScope.launch { repo.addAccount(name, type, icon, initialBalanceCents) }
    }

    fun updateAccount(item: AccountEntity) {
        viewModelScope.launch { repo.updateAccount(item) }
    }

    fun setAccountArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            if (archived) repo.archiveAccount(id) else repo.restoreAccount(id)
        }
    }

    suspend fun accountUsage(id: Long): Int = repo.accountUsage(id)

    suspend fun firstAccount(): AccountEntity? = repo.firstAccount()

    // ---------- 首次运行种子 ----------

    fun seedIfNeeded() {
        viewModelScope.launch { repo.ensureSeeded() }
    }

    // ---------- 记忆项（上次用的账户/分类） ----------

    fun lastAccountIdHint(): Long = settings.lastAccountId

    fun lastExpenseCategoryIdHint(): Long = settings.lastExpenseCategoryId

    fun lastIncomeCategoryIdHint(): Long = settings.lastIncomeCategoryId

    class Factory(
        private val repo: LedgerRepository,
        private val settings: SettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LedgerViewModel(repo, settings) as T
        }
    }
}

// ---------- 纯函数工具 ----------

private fun List<TxRow>.groupByDay(): List<DayGroup> =
    groupBy { it.dateEpochDay }
        .map { (day, rows) -> DayGroup(day, rows) }
        .sortedByDescending { it.dateEpochDay }

private fun buildTrend(anchor: YearMonth, days: List<com.jianji.app.data.DayTotal>): List<TrendPoint> {
    val buckets = HashMap<YearMonth, Pair<Long, Long>>()
    for (day in days) {
        val ym = Dates.monthOf(day.dateEpochDay)
        val current = buckets[ym] ?: (0L to 0L)
        buckets[ym] = when (day.type) {
            TxType.EXPENSE -> (current.first + day.totalCents) to current.second
            TxType.INCOME -> current.first to (current.second + day.totalCents)
            else -> current
        }
    }
    return (0L until 6L).map { offset ->
        val ym = anchor.minusMonths(5 - offset)
        val pair = buckets[ym] ?: (0L to 0L)
        TrendPoint(ym, pair.first, pair.second)
    }
}

private fun SearchFilter.rangeBounds(): Pair<Long, Long> {
    val now = Dates.currentMonth()
    return when (range) {
        RangeMode.ALL -> EPOCH_MIN to EPOCH_MAX
        RangeMode.THIS_MONTH -> Dates.monthStart(now) to Dates.monthEnd(now)
        RangeMode.LAST_MONTH -> {
            val last = now.minusMonths(1)
            Dates.monthStart(last) to Dates.monthEnd(last)
        }
        RangeMode.RECENT_3M -> Dates.monthStart(now.minusMonths(2)) to Dates.monthEnd(now)
    }
}
