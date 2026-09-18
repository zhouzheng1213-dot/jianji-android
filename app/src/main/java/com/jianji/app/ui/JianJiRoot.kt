package com.jianji.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jianji.app.data.TxRow
import com.jianji.app.ui.component.Shape as JianJiShape
import com.jianji.app.ui.glass.GlassBottomBarHeight
import com.jianji.app.ui.glass.GlassMotion
import com.jianji.app.ui.glass.GlassOrigin
import com.jianji.app.ui.glass.GlassStyle
import com.jianji.app.ui.glass.GlassSurface
import com.jianji.app.ui.glass.LocalGlassBackdrop
import com.jianji.app.ui.glass.LocalGlassContentInset
import com.jianji.app.ui.glass.glassIconBounce
import com.jianji.app.ui.glass.glassIconRotation
import com.jianji.app.ui.glass.glassOriginSource
import com.jianji.app.ui.glass.glassPressBounce
import com.jianji.app.ui.glass.glassTabTransform
import com.jianji.app.ui.screen.AccountsScreen
import com.jianji.app.ui.screen.EntryScreen
import com.jianji.app.ui.screen.LedgerManageScreen
import com.jianji.app.ui.screen.LedgerScreen
import com.jianji.app.ui.screen.SearchScreen
import com.jianji.app.ui.screen.SettingsScreen
import com.jianji.app.ui.screen.StatsScreen
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette
import com.jianji.app.vm.LedgerViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** 覆盖在 Tab 之上的整屏页面。 */
private sealed interface Overlay {
    data object Search : Overlay
    data object Ledgers : Overlay
    data class Entry(val row: TxRow?) : Overlay
}

private data class TabSpec(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec("明细", Icons.AutoMirrored.Filled.ReceiptLong),
    TabSpec("统计", Icons.Filled.PieChart),
    TabSpec("账户", Icons.Filled.AccountBalanceWallet),
    TabSpec("设置", Icons.Filled.Settings)
)

/**
 * 整屏骨架。这里同时管三件事：
 *
 * 1. **玻璃背板**：可滚动内容整体录进一张 [LayerBackdrop]，所有玻璃表面从它采样。
 *    悬浮件（底栏、记一笔、整页浮层）必须留在背板**之外**，
 *    否则它们会把自己的像素也录进去，然后折射出自身的重影。
 * 2. **源点感知的动效**：浮层从手指按下的那一点长出来、再缩回同一点（[GlassOrigin]）。
 * 3. **账本作用域**：所有页面拿到的 state 都已经是当前账本的。
 */
@Composable
fun JianJiRoot(factory: LedgerViewModel.Factory) {
    val vm: LedgerViewModel = viewModel(factory = factory)
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val statsState by vm.statsState.collectAsStateWithLifecycle()
    val searchState by vm.searchState.collectAsStateWithLifecycle()
    val categories by vm.allCategories.collectAsStateWithLifecycle()
    val ledgers by vm.allLedgers.collectAsStateWithLifecycle()
    val currentLedgerId by vm.currentLedgerId.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    val backdrop = rememberLayerBackdrop()
    val origin = remember { GlassOrigin() }

    // 记一笔时优先沿用上次用过的账户，没有就退回第一个可用账户。
    val lastAccountId = remember(uiState.accounts) {
        val stored = vm.lastAccountIdHint()
        if (uiState.accounts.any { it.id == stored }) stored
        else uiState.accounts.firstOrNull()?.id ?: -1L
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentInset = GlassBottomBarHeight + navBarBottom

    fun open(next: Overlay) {
        // 先钉住「从哪儿来」，再切状态 —— 顺序反了就采不到坐标了。
        origin.freeze()
        overlay = next
    }

    BackHandler(enabled = overlay != null) { overlay = null }

    Box(modifier = Modifier.fillMaxSize().background(Palette.WarmWhite).glassOriginSource(origin)) {
        // ---------- 背板层：主界面 ----------
        CompositionLocalProvider(
            LocalGlassBackdrop provides backdrop,
            LocalGlassContentInset provides contentInset
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { glassTabTransform() },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .layerBackdrop(backdrop),
                label = "tabs"
            ) { current ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (current) {
                        0 -> LedgerScreen(
                            state = uiState,
                            onPrevMonth = { vm.stepMonth(-1) },
                            onNextMonth = { vm.stepMonth(1) },
                            onBackToCurrentMonth = { vm.setMonth(java.time.YearMonth.now()) },
                            onOpenSearch = { open(Overlay.Search) },
                            onOpenLedgers = { open(Overlay.Ledgers) },
                            onOpenTransaction = { id ->
                                val row = uiState.dayGroups
                                    .firstNotNullOfOrNull { group -> group.rows.firstOrNull { it.id == id } }
                                open(Overlay.Entry(row))
                            }
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
                            onClearLedger = { vm.clearCurrentLedger() },
                            onOpenLedgers = { open(Overlay.Ledgers) },
                            ledgerName = uiState.ledger?.name ?: "日常",
                            ledgerCount = ledgers.count { !it.archived }
                        )
                    }
                }
            }
        }

        // ---------- 玻璃悬浮件 ----------
        GlassBottomBar(
            current = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (tab == 0 && overlay == null) {
            AddEntryFab(
                onClick = { open(Overlay.Entry(null)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = contentInset + 14.dp)
            )
        }

        // ---------- 整页浮层（同样在背板之外，才能真玻璃） ----------
        AnimatedContent(
            targetState = overlay,
            transitionSpec = {
                val fadeInSpec: FiniteAnimationSpec<Float> = tween(
                    durationMillis = GlassMotion.FadeInMillis,
                    easing = GlassMotion.EmphasizedDecelerate
                )
                val fadeOutSpec: FiniteAnimationSpec<Float> = tween(
                    durationMillis = GlassMotion.FadeOutMillis,
                    easing = GlassMotion.EmphasizedAccelerate
                )
                if (targetState != null) {
                    origin.expandIn() togetherWith fadeOut(fadeOutSpec)
                } else {
                    fadeIn(fadeInSpec) togetherWith origin.shrinkOut()
                }
            },
            label = "overlay"
        ) { target ->
            when (target) {
                null -> Box(Modifier.fillMaxSize())

                Overlay.Ledgers -> GlassPage {
                    LedgerManageScreen(
                        ledgers = ledgers,
                        currentLedgerId = currentLedgerId,
                        onSelect = { id ->
                            vm.selectLedger(id)
                            overlay = null
                        },
                        onCreate = { name, icon, color, budget ->
                            vm.addLedger(name, icon, color, budget)
                        },
                        onUpdate = { vm.updateLedger(it) },
                        onSetArchived = { id, archived -> vm.setLedgerArchived(id, archived) },
                        onDelete = { id, onResult -> vm.deleteLedger(id, onResult) },
                        onClose = { overlay = null }
                    )
                }

                Overlay.Search -> GlassPage {
                    SearchScreen(
                        state = searchState,
                        categories = categories,
                        onFilterChange = { transform -> vm.updateFilter(transform) },
                        onReset = { vm.resetFilter() },
                        onOpenTransaction = { id ->
                            val row = searchState.results.firstOrNull { it.id == id }
                            open(Overlay.Entry(row))
                        },
                        onClose = { overlay = null }
                    )
                }

                is Overlay.Entry -> GlassPage {
                    EntryScreen(
                        editing = target.row,
                        expenseCategories = uiState.expenseCategories,
                        incomeCategories = uiState.incomeCategories,
                        accounts = uiState.accounts,
                        fallbackAccountId = lastAccountId,
                        lastExpenseCategoryId = vm.lastExpenseCategoryIdHint(),
                        lastIncomeCategoryId = vm.lastIncomeCategoryIdHint(),
                        ledgerName = uiState.ledger?.name ?: "日常",
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

/** 整页浮层统一用同一档玻璃，保证「打开一页」的手感每次一样。 */
@Composable
private fun GlassPage(content: @Composable () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        style = GlassStyle.Sheet,
        shape = RoundedCornerShape(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

/**
 * 玻璃底栏。
 *
 * 只圆上两个角、贴住屏幕底边 —— 底栏要和导航栏连成一片，
 * 底部再留个圆角就会露出一条底色缝，玻璃的「整块感」立刻散掉。
 */
@Composable
private fun GlassBottomBar(
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLedgerColors.current
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        style = GlassStyle.Thick,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(GlassBottomBarHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.forEachIndexed { index, spec ->
                val selected = index == current
                val interaction = remember(spec.label) { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab
                        ) { onSelect(index) }
                        .glassIconBounce(selected = selected),
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

/**
 * 「记一笔」。按下回弹 + 加号自转 45°（加号转成叉 = 「正在记 / 关掉」）。
 *
 * 它用墨色实底而不是玻璃：这颗按钮压在底栏上方，
 * 在纯暖白背景上做折射几乎看不出厚度，反而会把墨色的对比度冲淡。
 */
@Composable
private fun AddEntryFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = modifier
            .glassPressBounce(interaction)
            .clip(JianJiShape.pill)
            .background(Palette.Ink)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(19.dp)
                .glassIconRotation(expanded = pressed, degrees = 45f)
        )
        Text(
            text = "记一笔",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}
