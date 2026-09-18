package com.jianji.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jianji.app.domain.Dates
import com.jianji.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * 用真实 SQLite（内存库）跑一遍全部手写 SQL。
 * 这些查询里有 JOIN、子查询余额、GROUP BY 聚合，是最容易写错、也最难靠肉眼发现的地方，
 * 所以这里针对每一条都留了断言。
 *
 * 引入独立账本后，所有流水查询都多了一个 `ledgerId` 前置参数；
 * 这一份用例统一使用默认账本，断言与引入账本之前**逐条一致** ——
 * 也就是说，它同时充当「账本重构没有改变原有行为」的回归证据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    /** 默认账本 id。v1 历史数据与这里的用例都落在这一本。 */
    private val ledger = LedgerEntity.DEFAULT_ID

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

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate.of(year, month, dayOfMonth).toEpochDay()

    private suspend fun expense(amount: Long, categoryId: Long, accountId: Long, epochDay: Long, note: String = "") =
        repo.insertTransaction(
            TransactionEntity(
                type = TxType.EXPENSE, amountCents = amount, categoryId = categoryId,
                accountId = accountId, toAccountId = null, dateEpochDay = epochDay,
                note = note, createdAt = epochDay, updatedAt = epochDay
            )
        )

    private suspend fun income(amount: Long, categoryId: Long, accountId: Long, epochDay: Long) =
        repo.insertTransaction(
            TransactionEntity(
                type = TxType.INCOME, amountCents = amount, categoryId = categoryId,
                accountId = accountId, toAccountId = null, dateEpochDay = epochDay,
                note = "", createdAt = epochDay, updatedAt = epochDay
            )
        )

    private suspend fun transfer(amount: Long, from: Long, to: Long, epochDay: Long) =
        repo.insertTransaction(
            TransactionEntity(
                type = TxType.TRANSFER, amountCents = amount, categoryId = null,
                accountId = from, toAccountId = to, dateEpochDay = epochDay,
                note = "", createdAt = epochDay, updatedAt = epochDay
            )
        )

    // ---------- 种子 ----------

    @Test
    fun `seeding is idempotent and covers both directions`() = runTest {
        repo.ensureSeeded()
        repo.ensureSeeded()

        val all = repo.observeAllCategories().first()
        assertEquals(18, all.size)
        assertTrue(all.any { it.type == TxType.EXPENSE })
        assertTrue(all.any { it.type == TxType.INCOME })

        val accounts = repo.observeActiveAccounts().first()
        assertEquals(4, accounts.size)
        assertTrue(accounts.all { it.initialBalanceCents == 0L })
    }

    @Test
    fun `hidden categories disappear from the entry panel but stay in the list`() = runTest {
        repo.ensureSeeded()
        val target = repo.observeCategories(TxType.EXPENSE).first().first()

        repo.setCategoryHidden(target.id, true)

        assertTrue(repo.observeCategories(TxType.EXPENSE).first().none { it.id == target.id })
        assertTrue(repo.observeAllCategories().first().any { it.id == target.id })
    }

    // ---------- 余额 ----------

    @Test
    fun `account balance folds initial amount income expense and both transfer legs`() = runTest {
        repo.ensureSeeded()
        val accounts = repo.observeActiveAccounts().first()
        val cash = accounts[0]
        val wallet = accounts[1]
        val expenseCategory = repo.observeCategories(TxType.EXPENSE).first().first()
        val incomeCategory = repo.observeCategories(TxType.INCOME).first().first()

        // 现金：期初 1000.00
        repo.updateAccount(cash.copy(initialBalanceCents = 100_000L))
        // 支出 30.00，收入 200.00
        expense(3_000L, expenseCategory.id, cash.id, day(2026, 9, 10))
        income(20_000L, incomeCategory.id, cash.id, day(2026, 9, 11))
        // 转出 50.00 到微信
        transfer(5_000L, cash.id, wallet.id, day(2026, 9, 12))

        val balances = repo.observeAccounts().first().associateBy { it.id }

        // 100000 - 3000 + 20000 - 5000 = 112000
        assertEquals(112_000L, balances.getValue(cash.id).balanceCents)
        // 微信只收到转入 5000
        assertEquals(5_000L, balances.getValue(wallet.id).balanceCents)
    }

    @Test
    fun `a transfer never shows up as income or expense`() = runTest {
        repo.ensureSeeded()
        val accounts = repo.observeActiveAccounts().first()
        val expenseCategory = repo.observeCategories(TxType.EXPENSE).first().first()

        expense(1_000L, expenseCategory.id, accounts[0].id, day(2026, 9, 5))
        transfer(9_999L, accounts[0].id, accounts[1].id, day(2026, 9, 6))

        val totals = repo.observeTypeTotals(
            ledger,
            Dates.monthStart(YearMonth.of(2026, 9)),
            Dates.monthEnd(YearMonth.of(2026, 9))
        ).first()
        assertEquals(1_000L, totals.firstOrNull { it.type == TxType.EXPENSE }?.totalCents)
        assertNull(totals.firstOrNull { it.type == TxType.INCOME })
        assertNull(totals.firstOrNull { it.type == TxType.TRANSFER })
    }

    // ---------- 区间与分组 ----------

    @Test
    fun `the month query returns only the requested month, newest first`() = runTest {
        repo.ensureSeeded()
        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()

        expense(100L, category.id, account.id, day(2026, 8, 31), "上个月")
        expense(200L, category.id, account.id, day(2026, 9, 1), "月初")
        expense(300L, category.id, account.id, day(2026, 9, 30), "月末")
        expense(400L, category.id, account.id, day(2026, 10, 1), "下个月")

        val rows = repo.observeRange(
            ledger,
            Dates.monthStart(YearMonth.of(2026, 9)),
            Dates.monthEnd(YearMonth.of(2026, 9))
        ).first()

        assertEquals(2, rows.size)
        assertEquals("月末", rows.first().note)
        assertEquals("月初", rows.last().note)
        // JOIN 出来的分类名要能正确带出来
        assertEquals(category.name, rows.first().categoryName)
        assertEquals(account.name, rows.first().accountName)
    }

    @Test
    fun `daily totals group by day and skip transfers`() = runTest {
        repo.ensureSeeded()
        val accounts = repo.observeActiveAccounts().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()

        val target = day(2026, 9, 15)
        expense(1_000L, category.id, accounts[0].id, target)
        expense(2_500L, category.id, accounts[0].id, target)
        transfer(500L, accounts[0].id, accounts[1].id, target)
        expense(700L, category.id, accounts[0].id, day(2026, 9, 16))

        val days = repo.observeDayTotals(
            ledger,
            Dates.monthStart(YearMonth.of(2026, 9)),
            Dates.monthEnd(YearMonth.of(2026, 9))
        ).first()

        val fifteenth = days.first { it.dateEpochDay == target }
        assertEquals(3_500L, fifteenth.totalCents)
        assertEquals(2, days.size)
    }

    // ---------- 统计 ----------

    @Test
    fun `category stats aggregate, count and sort by total descending`() = runTest {
        repo.ensureSeeded()
        val account = repo.observeActiveAccounts().first().first()
        val categories = repo.observeCategories(TxType.EXPENSE).first()
        val food = categories.first { it.name == "餐饮" }
        val transport = categories.first { it.name == "交通" }

        expense(1_000L, food.id, account.id, day(2026, 9, 3))
        expense(2_000L, food.id, account.id, day(2026, 9, 4))
        expense(5_000L, transport.id, account.id, day(2026, 9, 5))

        val stats = repo.observeCategoryStats(
            ledger,
            TxType.EXPENSE,
            Dates.monthStart(YearMonth.of(2026, 9)),
            Dates.monthEnd(YearMonth.of(2026, 9))
        ).first()

        assertEquals(2, stats.size)
        assertEquals(transport.id, stats[0].categoryId)
        assertEquals(5_000L, stats[0].totalCents)
        assertEquals(1, stats[0].txCount)
        assertEquals(food.id, stats[1].categoryId)
        assertEquals(3_000L, stats[1].totalCents)
        assertEquals(2, stats[1].txCount)
        assertNotNull(stats[1].colorHex)
        assertNotNull(stats[1].icon)
    }

    @Test
    fun `income stats never mix in expenses`() = runTest {
        repo.ensureSeeded()
        val account = repo.observeActiveAccounts().first().first()
        val expenseCategory = repo.observeCategories(TxType.EXPENSE).first().first()
        val incomeCategory = repo.observeCategories(TxType.INCOME).first().first()

        val from = Dates.monthStart(YearMonth.of(2026, 9))
        val to = Dates.monthEnd(YearMonth.of(2026, 9))
        expense(4_000L, expenseCategory.id, account.id, day(2026, 9, 8))
        income(9_000L, incomeCategory.id, account.id, day(2026, 9, 9))

        val incomeStats = repo.observeCategoryStats(ledger, TxType.INCOME, from, to).first()
        assertEquals(1, incomeStats.size)
        assertEquals(9_000L, incomeStats[0].totalCents)
        assertEquals(incomeCategory.id, incomeStats[0].categoryId)
    }

    // ---------- 搜索 ----------

    @Test
    fun `search combines keyword type category and amount range`() = runTest {
        repo.ensureSeeded()
        val account = repo.observeActiveAccounts().first().first()
        val categories = repo.observeCategories(TxType.EXPENSE).first()
        val food = categories.first { it.name == "餐饮" }
        val transport = categories.first { it.name == "交通" }

        expense(1_200L, food.id, account.id, day(2026, 9, 3), note = "公司楼下的面")
        expense(8_800L, food.id, account.id, day(2026, 9, 4), note = "聚餐")
        expense(9_900L, transport.id, account.id, day(2026, 9, 5), note = "打车回家")

        val (allFrom, allTo) = ALL_TIME
        // 关键词命中备注
        val byKeyword = repo.search(ledger, allFrom, allTo, -1, -1L, "聚餐", -1L, -1L).first()
        assertEquals(1, byKeyword.size)
        assertEquals("聚餐", byKeyword.first().note)

        // 关键词也能命中分类名
        val byCategoryName = repo.search(ledger, allFrom, allTo, -1, -1L, "交通", -1L, -1L).first()
        assertEquals(1, byCategoryName.size)

        // 类型 + 分类 + 金额下限
        val combo = repo.search(ledger, allFrom, allTo, TxType.EXPENSE, food.id, "", 5_000L, -1L).first()
        assertEquals(1, combo.size)
        assertEquals(8_800L, combo.first().amountCents)

        // 金额上限
        val capped = repo.search(ledger, allFrom, allTo, -1, -1L, "", -1L, 2_000L).first()
        assertEquals(1, capped.size)
        assertEquals(1_200L, capped.first().amountCents)

        // 无命中
        assertTrue(repo.search(ledger, allFrom, allTo, -1, -1L, "不存在的备注", -1L, -1L).first().isEmpty())
    }

    @Test
    fun `search matches account names too`() = runTest {
        repo.ensureSeeded()
        val accounts = repo.observeActiveAccounts().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()

        expense(500L, category.id, accounts[0].id, day(2026, 9, 7))
        expense(600L, category.id, accounts[1].id, day(2026, 9, 8))

        val hit = repo.search(
            ledger, ALL_TIME.first, ALL_TIME.second, -1, -1L, accounts[1].name, -1L, -1L
        ).first()

        assertEquals(1, hit.size)
        assertEquals(accounts[1].name, hit.first().accountName)
    }

    @Test
    fun `rows carry enough data to open the editor without another query`() = runTest {
        repo.ensureSeeded()
        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()

        val id = expense(4_321L, category.id, account.id, day(2026, 9, 13), note = "保留原值")

        val row = repo.observeRange(ledger, day(2026, 9, 13), day(2026, 9, 13)).first().first()
        assertEquals(id, row.id)
        assertEquals(4_321L, row.amountCents)
        assertEquals("保留原值", row.note)
        assertEquals(TxType.EXPENSE, row.type)

        // 行能直接还原成实体，编辑保存不会丢字段
        val entity = row.toEntity()
        assertEquals(id, entity.id)
        assertEquals(category.id, entity.categoryId)
        assertEquals(account.id, entity.accountId)
        assertEquals(ledger, entity.ledgerId)
        assertNull(entity.toAccountId)
    }

    // ---------- 删除与清理 ----------

    @Test
    fun `built-in categories and in-use categories refuse deletion`() = runTest {
        repo.ensureSeeded()
        val account = repo.observeActiveAccounts().first().first()
        val builtIn = repo.observeCategories(TxType.EXPENSE).first().first()

        assertEquals(DeleteOutcome.BUILT_IN, repo.deleteCategory(builtIn.id))

        val customId = repo.addCategory("咖啡", TxType.EXPENSE, "cafe", "#A0725A")
        assertEquals(DeleteOutcome.OK, repo.deleteCategory(customId))

        val usedId = repo.addCategory("宠物粮", TxType.EXPENSE, "pets", "#C9A227")
        expense(2_000L, usedId, account.id, day(2026, 9, 20))
        assertEquals(DeleteOutcome.IN_USE, repo.deleteCategory(usedId))
    }

    @Test
    fun `clearing transactions keeps categories and accounts`() = runTest {
        repo.ensureSeeded()
        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()
        expense(1_000L, category.id, account.id, day(2026, 9, 21))

        repo.clearLedger(ledger)

        assertEquals(0, repo.observeCountIn(ledger).first())
        assertEquals(18, repo.observeAllCategories().first().size)
        assertEquals(4, repo.observeActiveAccounts().first().size)
    }

    @Test
    fun `deleting a transaction updates the count and the balance`() = runTest {
        repo.ensureSeeded()
        val account = repo.observeActiveAccounts().first().first()
        val category = repo.observeCategories(TxType.EXPENSE).first().first()
        val id = expense(7_000L, category.id, account.id, day(2026, 9, 22))

        assertEquals(1, repo.observeCountIn(ledger).first())
        assertEquals(-7_000L, repo.observeAccounts().first().first { it.id == account.id }.balanceCents)

        repo.deleteTransaction(id)

        assertEquals(0, repo.observeCountIn(ledger).first())
        assertEquals(0L, repo.observeAccounts().first().first { it.id == account.id }.balanceCents)
    }

    @Test
    fun `archiving hides an account from the active list but keeps it in the full list`() = runTest {
        repo.ensureSeeded()
        val target = repo.observeActiveAccounts().first().last()

        repo.archiveAccount(target.id)

        assertTrue(repo.observeActiveAccounts().first().none { it.id == target.id })
        assertTrue(repo.observeAllAccounts().first().any { it.id == target.id && it.archived })

        repo.restoreAccount(target.id)
        assertTrue(repo.observeActiveAccounts().first().any { it.id == target.id })
    }

    private companion object {
        /** 搜索里的「不限时间」哨兵，与 ViewModel 中的定义保持一致。 */
        val ALL_TIME = 0L to 100_000L
    }
}
