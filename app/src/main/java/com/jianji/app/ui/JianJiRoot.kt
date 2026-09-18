package com.jianji.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.data.TxRow
import com.jianji.app.ui.component.Hairline
import com.jianji.app.ui.screen.AccountsScreen
import com.jianji.app.ui.screen.EntryScreen
import com.jianji.app.ui.screen.LedgerScreen
import com.jianji.app.ui.screen.SearchScreen
import com.jianji.app.ui.screen.SettingsScreen
import com.jianji.app.ui.screen.StatsScreen
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette
import com.jianji.app.vm.LedgerViewModel

/** 覆盖在 Tab 之上的整屏页面。 */
private sealed interface Overlay {
    data object Search : Overlay
    data class Entry(val row: TxRow?) : Overlay
}

private data class TabSpec(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec("明细", Icons.AutoMirrored.Filled.ReceiptLong),
    TabSpec("统计", Icons.Filled.PieChart),
    TabSpec("账户", Icons.Filled.AccountBalanceWallet),
    TabSpec("设置", Icons.Filled.Settings)
)

@Composable
fun JianJiRoot(factory: LedgerViewModel.Factory) {
    val vm: LedgerViewModel = viewModel(factory = factory)
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val statsState by vm.statsState.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val categories by vm.allCategories.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    // 记一笔时优先沿用上次用过的账户，没有就退回第一个可用账户。
    val lastAccountId = remember(uiState.accounts) {
        val stored = vm.lastAccountIdHint()
        if (uiState.accounts.any { it.id == stored }) stored
        else uiState.accounts.firstOrNull()?.id ?: -1L
    }

    BackHandler(enabled = overlay != null) { overlay = null }

    Box(modifier = Modifier.fillMaxSize().background(Palette.WarmWhite)) {
        // ---------- 主界面 ----------
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> LedgerScreen(
                        state = uiState,
                        onPrevMonth = { vm.stepMonth(-1) },
                        onNextMonth = { vm.stepMonth(1) },
                        onBackToCurrentMonth = { vm.setMonth(java.time.YearMonth.now()) },
                        onOpenSearch = { overlay = Overlay.Search },
                        onOpenTransaction = { id ->
                            val row = uiState.dayGroups
                                .firstNotNullOfOrNull { group -> group.rows.firstOrNull { it.id == id } }
                            overlay = Overlay.Entry(row)
                        },
                        onAddTransaction = { overlay = Overlay.Entry(null) }
                    )

                    1 -> StatsScreen(
                        state = statsState,
                        onPrevMonth = { vm.stepMonth(-1) },
                        onNextMonth = { vm.stepMonth(1) },
                        onBackToCurrentMonth = { vm.setMonth(java.time.YearMonth.now()) },
                        onModeChange = { vm.setStatsMode(it) }
                    )

                    2 -> AccountsScreen(
                        balances = uiState.balances,
                        archivedAccounts = uiState.archivedAccounts,
                        onCreate = { name, type, icon, initial ->
                            vm.addAccount(name, type, icon, initial)
                        },
                        onUpdate = { vm.updateAccount(it) },
                        onSetArchived = { id, archived -> vm.setAccountArchived(id, archived) }
                    )

                    else -> SettingsScreen(
                        categories = categories,
                        transactionCount = uiState.transactionCount,
                        onAddCategory = { name, type, icon, color ->
                            vm.addCategory(name, type, icon, color)
                        },
                        onUpdateCategory = { vm.updateCategory(it) },
                        onSetCategoryHidden = { id, hidden -> vm.setCategoryHidden(id, hidden) },
                        onDeleteCategory = { id, onResult -> vm.deleteCategory(id, onResult) },
                        onClearAll = { vm.clearAllTransactions() }
                    )
                }
            }

            BottomBar(current = tab, onSelect = { tab = it })
        }

        // ---------- 覆盖层 ----------
        AnimatedContent(
            targetState = overlay,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(180)))
                        .togetherWith(fadeOut(tween(120)))
                } else {
                    fadeIn(tween(140)).togetherWith(
                        slideOutHorizontally(tween(200)) { it / 6 } + fadeOut(tween(160))
                    )
                }
            },
            label = "overlay"
        ) { target ->
            when (target) {
                null -> Box(Modifier.fillMaxSize())
                Overlay.Search -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Palette.WarmWhite)
                        .statusBarsPadding()
                ) {
                    SearchScreen(
                        state = searchState,
                        categories = categories,
                        onFilterChange = { transform -> vm.updateFilter(transform) },
                        onReset = { vm.resetFilter() },
                        onOpenTransaction = { id ->
                            val row = searchState.results.firstOrNull { it.id == id }
                            overlay = Overlay.Entry(row)
                        },
                        onClose = { overlay = null }
                    )
                }

                is Overlay.Entry -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Palette.WarmWhite)
                        .statusBarsPadding()
                ) {
                    EntryScreen(
                        editing = target.row,
                        expenseCategories = uiState.expenseCategories,
                        incomeCategories = uiState.incomeCategories,
                        accounts = uiState.accounts,
                        fallbackAccountId = lastAccountId,
                        lastExpenseCategoryId = vm.lastExpenseCategoryIdHint(),
                        lastIncomeCategoryId = vm.lastIncomeCategoryIdHint(),
                        onSave = { entity, editing ->
                            vm.saveTransaction(entity, editing)
                            overlay = null
                        },
                        onDelete = { id ->
                            vm.deleteTransaction(id)
                            overlay = null
                        },
                        onClose = { overlay = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Int, onSelect: (Int) -> Unit) {
    val colors = LocalLedgerColors.current
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.forEachIndexed { index, spec ->
                val selected = index == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = spec.label,
                        tint = if (selected) Palette.Ink else colors.inkFaint,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = spec.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Palette.Ink else colors.inkFaint
                    )
                }
            }
        }
    }
}
