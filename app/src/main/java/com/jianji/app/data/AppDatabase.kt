package com.jianji.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LedgerEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        TransactionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        private const val DB_NAME = "jianji.db"

        /**
         * v1 → v2：引入独立账本。
         *
         * 三件事：建 `ledgers` 表、给 `transactions` 加 `ledgerId` 列、
         * 写入内置账本「日常」（id 固定为 0）。历史流水靠列的 DEFAULT 0 自动落到这本，
         * 因此无需 UPDATE 语句，老用户升级后打开看到的还是原来那些账。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ledgers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`icon` TEXT NOT NULL, " +
                        "`colorHex` TEXT NOT NULL, " +
                        "`budgetCents` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`archived` INTEGER NOT NULL, " +
                        "`builtIn` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledgers_archived` ON `ledgers` (`archived`)"
                )

                db.execSQL(
                    "ALTER TABLE `transactions` " +
                        "ADD COLUMN `ledgerId` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_ledgerId` " +
                        "ON `transactions` (`ledgerId`)"
                )

                db.execSQL(
                    "INSERT OR IGNORE INTO `ledgers` " +
                        "(`id`, `name`, `icon`, `colorHex`, `budgetCents`, `sortOrder`, `archived`, `builtIn`) " +
                        "VALUES (0, '日常', 'wallet', '#2B2724', 0, 0, 0, 1)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
