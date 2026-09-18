package com.jianji.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jianji.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 独立账本的作用域边界。
 *
 * 这一组用例只回答一个问题：**「一次旅行的专项资金」会不会和日常记账互相污染**。
 * 覆盖四条边界：流水互不串台、清空只清自己、删除资格、分类与账户跨账本共享。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerScopeTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(
            db.ledgerDao(),
            db.categoryDao(),
            db.accountDao(),
            db.transactionDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun day(month: Int, dayOfMonth: Int): Long =
        LocalDate.of(2026, month, dayOfMonth).toEpochDay()

    private suspend fun spend(ledgerId: Long, amount: Long, categoryId: Long, accountId: Long, epochDay: Long) =
        repo.insertTransaction(
            TransactionEntity(
                type = TxType.EXPENSE, amountCents = amount, categoryId = categoryId,
                accountId = accountId, toAccountId = null, dateEpochDay = epochDay,
                note = "", createdAt = epochDay, updatedAt = epochDay, ledgerId = ledgerId
            )
        )

    private suspend fun earn(ledgerId: Long, amount: Long, categoryId: Long, accountId: Long, epochDay: Long) =
        repo.insertTransaction(
            TransactionEntity(
                type = TxType.INCOME, amountCents = amount, categoryId = categoryId,
                accountId = accountId, toAccountId = null, dateEpochDay = epochDay,
                note = "", createdAt = epochDay, updatedAt = epochDay, ledgerId = ledgerId
            )
        )

    private suspend fun wire(ledgerId: Long, amount: Long, from: Long, to: Long, epochDay: Long) =
        repo.insertTransaction(
            TransactionEntity(
                type = TxType.TRANSFER, amountCents = amount, categoryId = null,
                accountId = from, toAccountId = to, dateEpochDay = epochDay,
                note = "", createdAt = epochDay, updatedAt = epochDay, ledgerId = ledgerId
            )
        )

    // ---------- 内置账本 ----------

    @Test
    fun `first run seeds a built-in ledger pinned to id zero`() = runTest {
        repo.ensureSeeded()
        repo.ensureSeeded()

        val ledgers = repo.observeAllLedgers().first()
        assertEquals(1, ledgers.size)

        val daily = ledgers.single()
        // 固定 id 是迁移契约的一部分：v1 历史流水全部落在 0 上。
        assertEquals(LedgerEntity.DEFAULT_ID, daily.id)
        assertEquals(LedgerEntity.DEFAULT_NAME, daily.name)
        assertTrue(daily.builtIn)
        assertFalse(daily.archived)
        assertEquals(0L, daily.budgetCents)
    }

    @Test
    fun `a new ledger gets a fresh sort order and is not built-in`() = runTest {
        repo.ensureSeeded()

        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)
        val project = repo.addLedger("婚礼", "gift", "#C8503C", 0L)

        val all = repo.observeAllLedgers().first().associateBy { it.id }
        assertFalse(all.getValue(trip).builtIn)
        assertFalse(all.getValue(project).builtIn)
        // 排序值必须递增，否则新建的账本会插到「日常」前面。
        assertTrue(all.getValue(project).sortOrder > all.getValue(trip).sortOrder)
        assertEquals(500_000L, all.getValue(trip).budgetCents)
    }

    // ---------- 流水隔离 ----------

    @Test
    fun `transactions never leak across ledgers`() = runTest {
        repo.ensureSeeded()
        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)

        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()

        spend(LedgerEntity.DEFAULT_ID, 1_000L, category.id, account.id, day(10, 1))
        spend(LedgerEntity.DEFAULT_ID, 2_000L, category.id, account.id, day(10, 2))
        spend(trip, 30_000L, category.id, account.id, day(10, 3))

        val dailyRows = repo.observeRange(LedgerEntity.DEFAULT_ID, day(10, 1), day(10, 31)).first()
        val tripRows = repo.observeRange(trip, day(10, 1), day(10, 31)).first()

        assertEquals(2, dailyRows.size)
        assertEquals(1, tripRows.size)
        assertEquals(30_000L, tripRows.single().amountCents)
        // 每一行都要带上自己的归属，编辑保存时才不会串本。
        assertTrue(dailyRows.all { it.ledgerId == LedgerEntity.DEFAULT_ID })

        assertEquals(2, repo.observeCountIn(LedgerEntity.DEFAULT_ID).first())
        assertEquals(1, repo.observeCountIn(trip).first())
        // 跨账本总数仍然算得对
        assertEquals(3, repo.observeTotalCount().first())
    }

    @Test
    fun `stats search and day totals are all ledger scoped`() = runTest {
        repo.ensureSeeded()
        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)

        val account = repo.observeActiveAccounts().first().first()
        val food = repo.observeCategories(TxType.EXPENSE).first().first { it.name == "餐饮" }
        val income = repo.observeCategories(TxType.INCOME).first().first()

        // 日常：餐饮 10 元、收入 50 元；旅行：餐饮 300 元
        spend(LedgerEntity.DEFAULT_ID, 1_000L, food.id, account.id, day(10, 5))
        earn(LedgerEntity.DEFAULT_ID, 5_000L, income.id, account.id, day(10, 6))
        spend(trip, 30_000L, food.id, account.id, day(10, 7))

        val from = day(10, 1)
        val to = day(10, 31)

        // 分类统计
        val dailyStats = repo.observeCategoryStats(LedgerEntity.DEFAULT_ID, TxType.EXPENSE, from, to).first()
        val tripStats = repo.observeCategoryStats(trip, TxType.EXPENSE, from, to).first()
        assertEquals(1_000L, dailyStats.single().totalCents)
        assertEquals(30_000L, tripStats.single().totalCents)

        // 按天聚合是按「天 + 类型」分组的，所以同一天的收入会另占一行；
        // 每日柱状图只取支出那一行（ViewModel 里 filter EXPENSE），这里把这条契约钉住。
        val dailyDays = repo.observeDayTotals(LedgerEntity.DEFAULT_ID, from, to).first()
        assertEquals(2, dailyDays.size)
        val expenseDay = dailyDays.single { it.type == TxType.EXPENSE }
        assertEquals(day(10, 5), expenseDay.dateEpochDay)
        assertEquals(1_000L, expenseDay.totalCents)

        // 旅行账本只有一笔，且不掺进日常的那两行。
        val tripDays = repo.observeDayTotals(trip, from, to).first()
        val tripDay = tripDays.single()
        assertEquals(day(10, 7), tripDay.dateEpochDay)
        assertEquals(30_000L, tripDay.totalCents)

        // 收支合计：旅行的 300 元不能进日常的「本月支出」
        val dailyTotals = repo.observeTypeTotals(LedgerEntity.DEFAULT_ID, from, to).first()
        assertEquals(1_000L, dailyTotals.first { it.type == TxType.EXPENSE }.totalCents)
        assertEquals(5_000L, dailyTotals.first { it.type == TxType.INCOME }.totalCents)
        val tripTotals = repo.observeTypeTotals(trip, from, to).first()
        assertEquals(1, tripTotals.size)
        assertEquals(30_000L, tripTotals.single().totalCents)

        // 搜索
        val dailySearch = repo.search(LedgerEntity.DEFAULT_ID, 0L, 100_000L, -1, -1L, "", -1L, -1L).first()
        val tripSearch = repo.search(trip, 0L, 100_000L, -1, -1L, "", -1L, -1L).first()
        assertEquals(2, dailySearch.size)
        assertEquals(1, tripSearch.size)
    }

    // ---------- 清空 ----------

    @Test
    fun `clearing one ledger leaves every other ledger untouched`() = runTest {
        repo.ensureSeeded()
        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)

        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()

        spend(LedgerEntity.DEFAULT_ID, 1_000L, category.id, account.id, day(10, 1))
        spend(trip, 30_000L, category.id, account.id, day(10, 2))

        repo.clearLedger(LedgerEntity.DEFAULT_ID)

        assertEquals(0, repo.observeCountIn(LedgerEntity.DEFAULT_ID).first())
        assertEquals(1, repo.observeCountIn(trip).first())
        // 账本本身不能被清空连带删掉
        assertEquals(2, repo.observeAllLedgers().first().size)
    }

    // ---------- 删除与归档 ----------

    @Test
    fun `the built-in ledger refuses deletion`() = runTest {
        repo.ensureSeeded()
        // 先塞一笔，证明「拒绝」是因为内置而不是因为占用。
        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()
        spend(LedgerEntity.DEFAULT_ID, 100L, category.id, account.id, day(10, 1))

        assertEquals(DeleteOutcome.BUILT_IN, repo.deleteLedger(LedgerEntity.DEFAULT_ID))
        assertNotNull(repo.findLedger(LedgerEntity.DEFAULT_ID))
    }

    @Test
    fun `a ledger holding transactions refuses deletion but an empty one is removed`() = runTest {
        repo.ensureSeeded()
        val used = repo.addLedger("国庆北疆", "flight", "#176B66", 0L)
        val empty = repo.addLedger("废弃账本", "star", "#C9A227", 0L)

        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()
        spend(used, 500L, category.id, account.id, day(10, 1))

        assertEquals(DeleteOutcome.IN_USE, repo.deleteLedger(used))
        assertNotNull(repo.findLedger(used))

        assertEquals(DeleteOutcome.OK, repo.deleteLedger(empty))
        assertNull(repo.findLedger(empty))
    }

    @Test
    fun `archiving a ledger hides it from the switcher but keeps it in the full list`() = runTest {
        repo.ensureSeeded()
        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)

        repo.setLedgerArchived(trip, true)

        assertTrue(repo.observeLedgers().first().none { it.id == trip })
        assertTrue(repo.observeAllLedgers().first().any { it.id == trip && it.archived })

        repo.setLedgerArchived(trip, false)
        assertTrue(repo.observeLedgers().first().any { it.id == trip })
    }

    @Test
    fun `renaming and re-budgeting a ledger persists`() = runTest {
        repo.ensureSeeded()
        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 0L)

        val loaded = repo.findLedger(trip)!!
        repo.updateLedger(loaded.copy(name = "北疆一圈", budgetCents = 800_000L))

        val after = repo.findLedger(trip)!!
        assertEquals("北疆一圈", after.name)
        assertEquals(800_000L, after.budgetCents)
        // 改名不能顺手把 builtIn / id 弄丢
        assertEquals(trip, after.id)
        assertFalse(after.builtIn)
    }

    // ---------- 共享资源 ----------

    @Test
    fun `categories and accounts are shared across ledgers`() = runTest {
        repo.ensureSeeded()
        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)

        val account = repo.observeActiveAccounts().first().first()
        val customId = repo.addCategory("门票", TxType.EXPENSE, "star", "#176B66")

        // 在旅行账本里用掉这个分类，日常账本也应当能选到它 —— 统计口径才可比。
        spend(trip, 12_000L, customId, account.id, day(10, 4))

        assertTrue(repo.observeCategories(TxType.EXPENSE).first().any { it.id == customId })
        // 账户也是全局的：新建账本不会复制一份账户出来。
        assertEquals(4, repo.observeActiveAccounts().first().size)

        // 分类的「是否被占用」必须跨账本统计，否则日常账本会允许删掉旅行在用的分类。
        assertEquals(DeleteOutcome.IN_USE, repo.deleteCategory(customId))
    }

    @Test
    fun `account balances fold transactions from every ledger`() = runTest {
        repo.ensureSeeded()
        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)

        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()

        spend(LedgerEntity.DEFAULT_ID, 1_000L, category.id, account.id, day(10, 1))
        spend(trip, 30_000L, category.id, account.id, day(10, 2))

        val balance = repo.observeAccounts().first().first { it.id == account.id }.balanceCents
        // 余额是「我到底有多少钱」，不该随当前选中的账本跳变。
        assertEquals(-31_000L, balance)
    }

    // ---------- 预算口径 ----------

    @Test
    fun `budget consumption counts expenses only and ignores transfers`() = runTest {
        repo.ensureSeeded()
        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)

        val accounts = repo.observeActiveAccounts().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()
        val income = repo.observeCategories(TxType.INCOME).first().first()

        spend(trip, 120_000L, category.id, accounts[0].id, day(10, 2))
        earn(trip, 50_000L, income.id, accounts[0].id, day(10, 3))
        // 转账只是钱在账户间搬家，绝不能把预算顶掉。
        wire(trip, 200_000L, accounts[0].id, accounts[1].id, day(10, 4))

        val totals = repo.observeTypeTotals(trip, 0L, 100_000L).first()
        val expense = totals.firstOrNull { it.type == TxType.EXPENSE }?.totalCents ?: 0L
        val gained = totals.firstOrNull { it.type == TxType.INCOME }?.totalCents ?: 0L

        assertEquals(120_000L, expense)
        assertEquals(50_000L, gained)
        assertNull(totals.firstOrNull { it.type == TxType.TRANSFER })

        // 预算 5000.00，已花 1200.00 → 还剩 3800.00
        val budget = repo.findLedger(trip)!!.budgetCents
        assertEquals(380_000L, budget - expense)
    }

    @Test
    fun `lifetime totals cover every month not just the current one`() = runTest {
        repo.ensureSeeded()
        val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)

        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()

        // 跨两年的两笔，逐年累加预算消耗。
        spend(trip, 100_000L, category.id, account.id, day(10, 2))
        repo.insertTransaction(
            TransactionEntity(
                type = TxType.EXPENSE, amountCents = 200_000L, categoryId = category.id,
                accountId = account.id, toAccountId = null,
                dateEpochDay = LocalDate.of(2027, 3, 8).toEpochDay(),
                note = "", createdAt = 0L, updatedAt = 0L, ledgerId = trip
            )
        )

        val lifetime = repo.observeTypeTotals(trip, 0L, 100_000L).first()
            .first { it.type == TxType.EXPENSE }.totalCents
        assertEquals(300_000L, lifetime)

        // 单月口径只应看到当月的 1000.00
        val october = repo.observeTypeTotals(trip, day(10, 1), day(10, 31)).first()
            .first { it.type == TxType.EXPENSE }.totalCents
        assertEquals(100_000L, october)
    }
}
