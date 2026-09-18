package com.jianji.app

import android.app.Application
import com.jianji.app.data.AppDatabase
import com.jianji.app.data.LedgerRepository
import com.jianji.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JianJiApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val repository: LedgerRepository by lazy {
        LedgerRepository(
            ledgerDao = database.ledgerDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
            txDao = database.transactionDao()
        )
    }

    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        // 首次运行写入默认分类与账户；种子化幂等，重复调用无副作用。
        appScope.launch { repository.ensureSeeded() }
    }
}
