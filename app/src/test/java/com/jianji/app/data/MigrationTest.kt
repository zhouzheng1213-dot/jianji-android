package com.jianji.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jianji.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * v1 → v2 迁移：老用户装上新版之后，原来那些账必须**一条不少地**还在。
 *
 * 做法是先在磁盘上手工造一个真正的 v1 库（表结构与 v1 导出的 schema 逐字一致），
 * 再让 Room 带着 [AppDatabase.MIGRATION_1_2] 打开它。
 *
 * 这里最有价值的一点是：**Room 会在迁移跑完后自己校验表结构**。
 * 只要迁移 SQL 与实体定义有一处对不上（列名、类型、NOT NULL、默认值、索引），
 * Room 就会抛 `Migration didn't properly handle: ...`，用例直接失败。
 * 也就是说，这个用例不需要我逐列比对，靠 Room 的校验就足以证明迁移脚本是对的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** 造一个 v1 库：v1 没有 ledgers 表，transactions 也没有 ledgerId 列。 */
    private fun createV1Database(): File {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`type` INTEGER NOT NULL, `icon` TEXT NOT NULL, `colorHex` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_type` ON `categories` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_hidden` ON `categories` (`hidden`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `accounts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`type` INTEGER NOT NULL, `initialBalanceCents` INTEGER NOT NULL, " +
                    "`icon` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `archived` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_archived` ON `accounts` (`archived`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transactions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, " +
                    "`amountCents` INTEGER NOT NULL, `categoryId` INTEGER, `accountId` INTEGER NOT NULL, " +
                    "`toAccountId` INTEGER, `dateEpochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dateEpochDay` ON `transactions` (`dateEpochDay`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)")

            // 塞进去一批「老数据」：分类、账户、以及三种类型的流水。
            db.execSQL(
                "INSERT INTO `categories` (`id`, `name`, `type`, `icon`, `colorHex`, `sortOrder`, `hidden`, `builtIn`) " +
                    "VALUES (1, '餐饮', 0, 'restaurant', '#C8503C', 0, 0, 1)"
            )
            db.execSQL(
                "INSERT INTO `accounts` (`id`, `name`, `type`, `initialBalanceCents`, `icon`, `sortOrder`, `archived`) " +
                    "VALUES (1, '现金', 0, 200000, 'wallet', 0, 0), (2, '微信', 0, 0, 'card', 1, 0)"
            )
            db.execSQL(
                "INSERT INTO `transactions` " +
                    "(`id`, `type`, `amountCents`, `categoryId`, `accountId`, `toAccountId`, `dateEpochDay`, `note`, `createdAt`, `updatedAt`) " +
                    "VALUES (1, 0, 3000, 1, 1, NULL, 20326, '国庆午饭', 100, 100), " +
                    "(2, 1, 20000, NULL, 1, NULL, 20327, '报销', 200, 200), " +
                    "(3, 2, 5000, NULL, 1, 2, 20328, '取现', 300, 300)"
            )

            db.version = 1
        } finally {
            db.close()
        }
        return file
    }

    private fun openV2(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .allowMainThreadQueries()
        .build()

    @Test
    fun `every v1 row survives the upgrade and lands in the built-in ledger`() = runTest {
        createV1Database()

        // 打开动作本身就是断言：结构对不上，Room 会在这里抛异常。
        val db = openV2()
        try {
            val repo = LedgerRepository(
                db.ledgerDao(),
                db.categoryDao(),
                db.accountDao(),
                db.transactionDao()
            )

            val rows = repo.observeRange(LedgerEntity.DEFAULT_ID, 0L, 100_000L).first()
            assertEquals(3, rows.size)

            // 逐条核对：金额、备注、类型一个都不能丢。
            val byId = rows.associateBy { it.id }
            assertEquals(3_000L, byId.getValue(1L).amountCents)
            assertEquals("国庆午饭", byId.getValue(1L).note)
            assertEquals(20_000L, byId.getValue(2L).amountCents)
            assertEquals("取现", byId.getValue(3L).note)
            assertEquals(2L, byId.getValue(3L).toAccountId)

            // 历史流水一律归到默认账本，靠的是列的 DEFAULT 0，不需要 UPDATE 语句。
            assertTrue(rows.all { it.ledgerId == LedgerEntity.DEFAULT_ID })
            assertEquals(3, repo.observeCountIn(LedgerEntity.DEFAULT_ID).first())

            // 分类与账户原样保留
            assertEquals("餐饮", repo.observeAllCategories().first().single().name)
            assertEquals(2, repo.observeActiveAccounts().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the upgrade plants the built-in ledger so the switcher is never empty`() = runTest {
        createV1Database()

        val db = openV2()
        try {
            val repo = LedgerRepository(
                db.ledgerDao(),
                db.categoryDao(),
                db.accountDao(),
                db.transactionDao()
            )

            val ledgers = repo.observeAllLedgers().first()
            val daily = ledgers.single()
            assertEquals(LedgerEntity.DEFAULT_ID, daily.id)
            assertEquals(LedgerEntity.DEFAULT_NAME, daily.name)
            assertTrue(daily.builtIn)
            // 内置账本默认不设预算，迁移不该凭空给老用户加上预算。
            assertEquals(0L, daily.budgetCents)

            // 余额口径跨账本一致：期初 2000.00 - 30.00 + 200.00 - 50.00 = 2120.00
            val cash = repo.observeAccounts().first().first { it.id == 1L }
            assertEquals(212_000L, cash.balanceCents)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the migrated database keeps working after new ledgers are added`() = runTest {
        createV1Database()

        val db = openV2()
        try {
            val repo = LedgerRepository(
                db.ledgerDao(),
                db.categoryDao(),
                db.accountDao(),
                db.transactionDao()
            )

            val trip = repo.addLedger("国庆北疆", "flight", "#176B66", 500_000L)
            assertNotNull(repo.findLedger(trip))

            val category = repo.observeCategories(TxType.EXPENSE).first().single()
            val account = repo.observeActiveAccounts().first().first()
            repo.insertTransaction(
                TransactionEntity(
                    type = TxType.EXPENSE,
                    amountCents = 88_000L,
                    categoryId = category.id,
                    accountId = account.id,
                    toAccountId = null,
                    dateEpochDay = 20_331L,
                    note = "机票",
                    createdAt = 0L,
                    updatedAt = 0L,
                    ledgerId = trip
                )
            )

            // 老数据在默认账本里不受影响，新账本只看到自己那一笔。
            assertEquals(3, repo.observeCountIn(LedgerEntity.DEFAULT_ID).first())
            assertEquals(1, repo.observeCountIn(trip).first())
            assertEquals(4, repo.observeTotalCount().first())

            // 迁移后的库上，账本的删除资格逻辑照常工作。
            assertEquals(DeleteOutcome.IN_USE, repo.deleteLedger(trip))
            assertEquals(DeleteOutcome.BUILT_IN, repo.deleteLedger(LedgerEntity.DEFAULT_ID))
        } finally {
            db.close()
        }
    }
}
