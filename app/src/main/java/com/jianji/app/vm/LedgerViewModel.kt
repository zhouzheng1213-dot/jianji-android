package com.jianji.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jianji.app.data.AccountEntity
import com.jianji.app.data.AccountWithBalance
import com.jianji.app.data.CategoryEntity
import com.jianji.app.data.CategoryStatRow
import com.jianji.app.data.DeleteOutcome
import com.jianji.app.data.LedgerEntity
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val ledger: LedgerEntity? = null,
    val ledgers: List<LedgerEntity> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val archivedAccounts: List<AccountEntity> = emptyList(),
    val balances: List<AccountWithBalance> = emptyList(),
    val dayGroups: List<DayGroup> = emptyList(),
    val monthExpenseCents: Long = 0L,
    val monthIncomeCents: Long = 0L,
    val lifetimeExpenseCents: Long = 0L,
    val lifetimeIncomeCents: Long = 0L,
    val transactionCount: Int = 0
) {
    val monthBalanceCents: Long get() = monthIncomeCents - monthExpenseCents

    val totalAssetsCents: Long get() = balances.sumOf { it.balanceCents }

    val isCurrentMonth: Boolean get() = month == YearMonth.now()

    val ledgerId: Long get() = ledger?.id ?: LedgerEntity.DEFAULT_ID

    /** 该账本是否设了预算。预算为 0 表示「不设」。 */
    val hasBudget: Boolean get() = (ledger?.budgetCents ?: 0L) > 0L

    /** 预算消耗比例，未设预算时为 0。 */
    val budgetFraction: Float
        get() {
            val budget = ledger?.budgetCents ?: 0L
            if (budget <= 0L) return 0f
            return (lifetimeExpenseCents.toDouble() / budget.toDouble()).toFloat()
        }

    /** 预算剩余（分），可为负 —— 超支时正是要让它负着显示。 */
    val budgetRemainingCents: Long
        get() = (ledger?.budgetCents ?: 0L) - lifetimeExpenseCents

    val isOverBudget: Boolean get() = hasBudget && lifetimeExpenseCents > (ledger?.budgetCents ?: 0L)
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
    private val ledgerId = MutableStateFlow(settings.lastLedgerId)
    private val statsMode = MutableStateFlow(TxType.EXPENSE)
    private val filter = MutableStateFlow(SearchFilter())

    val currentMonth: StateFlow<YearMonth> = month.asStateFlow()
    val searchFilter: StateFlow<SearchFilter> = filter.asStateFlow()
    val currentLedgerId: StateFlow<Long> = ledgerId.asStateFlow()

    init {
        // 账本被归档或被删掉之后，把当前选中项挪回第一本可用的，
        // 否则整个明细页会静默变成空白，用户只会以为数据丢了。
        viewModelScope.launch {
            repo.observeLedgers()
                .distinctUntilChanged()
                .collect { list ->
                    if (list.isNotEmpty() && list.none { it.id == ledgerId.value }) {
                        selectLedger(list.first().id)
                    }
                }
        }
    }

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

    val allLedgers: StateFlow<List<LedgerEntity>> = repo.observeAllLedgers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = repo.observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<LedgerUiState> = combine(month, ledgerId, repo.observeLedgers()) {
            m, id, ledgers -> Triple(m, id, ledgers)
    }.flatMapLatest { (m, id, ledgers) ->
        val from = Dates.monthStart(m)
        val to = Dates.monthEnd(m)
        combine(
            repo.observeRange(id, from, to),
            repo.observeTypeTotals(id, from, to),
            repo.observeTypeTotals(id, EPOCH_MIN, EPOCH_MAX),
            repo.observeCountIn(id),
            combine(categoriesFlow, accountsFlow) { c, a -> c to a }
        ) { rows, monthTotals, lifetimeTotals, count, stable ->
            val (categories, accounts) = stable
            LedgerUiState(
                month = m,
                ledger = ledgers.firstOrNull { it.id == id },
                ledgers = ledgers,
                expenseCategories = categories.first,
                incomeCategories = categories.second,
                accounts = accounts.first,
                balances = accounts.second,
                archivedAccounts = accounts.third.filter { it.archived },
                dayGroups = rows.groupByDay(),
                monthExpenseCents = monthTotals.expense(),
                monthIncomeCents = monthTotals.income(),
                lifetimeExpenseCents = lifetimeTotals.expense(),
                lifetimeIncomeCents = lifetimeTotals.income(),
                transactionCount = count
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(month = Dates.currentMonth())
    )

    val statsState: StateFlow<StatsUiState> = combine(month, statsMode, ledgerId) { m, mode, id ->
        Triple(m, mode, id)
    }.flatMapLatest { (m, mode, id) ->
        val from = Dates.monthStart(m)
        val to = Dates.monthEnd(m)
        val trendFrom = Dates.monthStart(m.minusMonths(5))
        combine(
            repo.observeCategoryStats(id, mode, from, to),
            repo.observeDayTotals(id, from, to),
            repo.observeDayTotals(id, trendFrom, to)
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

    val searchState: StateFlow<SearchUiState> = combine(filter, ledgerId) { f, id -> f to id }
        .flatMapLatest { (f, id) ->
            val (from, to) = f.rangeBounds()
            repo.search(
                ledgerId = id,
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

    // ---------- 账本 ----------

    fun selectLedger(id: Long) {
        ledgerId.value = id
        settings.lastLedgerId = id
    }

    /** 新建账本后立即切过去，省掉一次「再点一下」。 */
    fun addLedger(name: String, icon: String, colorHex: String, budgetCents: Long) {
        viewModelScope.launch {
            selectLedger(repo.addLedger(name, icon, colorHex, budgetCents))
        }
    }

    fun updateLedger(item: LedgerEntity) {
        viewModelScope.launch { repo.updateLedger(item) }
    }

    fun setLedgerArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { repo.setLedgerArchived(id, archived) }
    }

    fun deleteLedger(id: Long, onResult: (DeleteOutcome) -> Unit) {
        viewModelScope.launch { onResult(repo.deleteLedger(id)) }
    }

    suspend fun ledgerUsage(id: Long): Int = repo.transactionCountIn(id)

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
            // 新增的流水一律落进「当前账本」。放在这里统一兜底，
            // EntryScreen 就不需要知道账本的存在，也就不会漏传。
            val target = if (editing) entity else entity.copy(ledgerId = ledgerId.value)
            val id = if (editing) {
                repo.updateTransaction(target)
                target.id
            } else {
                repo.insertTransaction(target)
            }
            if (target.type == TxType.EXPENSE) {
                settings.lastExpenseCategoryId = target.categoryId ?: -1L
            } else if (target.type == TxType.INCOME) {
                settings.lastIncomeCategoryId = target.categoryId ?: -1L
            }
            settings.lastAccountId = target.accountId
            onDone(id)
        }
    }

    fun deleteTransaction(id: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteTransaction(id)
            onDone()
        }
    }

    /** 清空范围限定在当前账本，并且是**本账本全部时间**的流水。 */
    fun clearCurrentLedger(onDone: () -> Unit = {}) {
        val id = ledgerId.value
        viewModelScope.launch {
            repo.clearLedger(id)
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

private fun List<com.jianji.app.data.TypeTotal>.expense(): Long =
    firstOrNull { it.type == TxType.EXPENSE }?.totalCents ?: 0L

private fun List<com.jianji.app.data.TypeTotal>.income(): Long =
    firstOrNull { it.type == TxType.INCOME }?.totalCents ?: 0L

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
