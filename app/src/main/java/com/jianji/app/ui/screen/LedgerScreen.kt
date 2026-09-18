package com.jianji.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianji.app.data.LedgerEntity
import com.jianji.app.data.TxRow
import com.jianji.app.domain.Dates
import com.jianji.app.domain.Money
import com.jianji.app.ui.AppIcons
import com.jianji.app.ui.component.EmptyHint
import com.jianji.app.ui.component.Hairline
import com.jianji.app.ui.component.LedgerCard
import com.jianji.app.ui.component.MonthSelector
import com.jianji.app.ui.component.Shape
import com.jianji.app.ui.component.ThinProgress
import com.jianji.app.ui.component.TransactionItem
import com.jianji.app.ui.component.amountStyle
import com.jianji.app.ui.component.parseColor
import com.jianji.app.ui.glass.GlassStyle
import com.jianji.app.ui.glass.GlassSurface
import com.jianji.app.ui.glass.LocalGlassContentInset
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette
import com.jianji.app.vm.DayGroup
import com.jianji.app.vm.LedgerUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBackToCurrentMonth: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 内容要一直滚到玻璃底栏下面，所以底部余量靠 contentPadding 给，
    // 而不是把列表视口缩短。LocalGlassContentInset 里含系统导航栏高度。
    val bottomInset = LocalGlassContentInset.current + 76.dp

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset)
        ) {
            item(key = "ledger") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 14.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LedgerSwitcher(ledger = state.ledger, onClick = onOpenLedgers)
                    Spacer(Modifier.weight(1f))
                    SearchButton(onClick = onOpenSearch)
                }
            }

            item(key = "header") {
                MonthSelector(
                    label = Dates.formatMonth(state.month),
                    isCurrentMonth = state.isCurrentMonth,
                    onPrev = onPrevMonth,
                    onNext = onNextMonth,
                    onBackToCurrent = onBackToCurrentMonth,
                    modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 6.dp, bottom = 12.dp)
                )
            }

            item(key = "summary") {
                SummaryCard(state)
                Spacer(Modifier.height(10.dp))
            }

            if (state.hasBudget) {
                item(key = "budget") {
                    BudgetCard(state)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (state.dayGroups.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(
                        title = "这个月还没有记录",
                        subtitle = "点右下角「记一笔」开始"
                    )
                }
            }

            state.dayGroups.forEach { group ->
                stickyHeader(key = "h-${group.dateEpochDay}") {
                    DayHeaderRow(group)
                }
                item(key = "c-${group.dateEpochDay}") {
                    DayCard(group = group, onOpenTransaction = onOpenTransaction)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/**
 * 账本切换入口。整颗胶囊用账本色着色，一眼就能看出「现在记的是哪一本」——
 * 这是独立账本最容易出错的地方：记完才发现记进了错的本子。
 */
@Composable
private fun LedgerSwitcher(ledger: LedgerEntity?, onClick: () -> Unit) {
    val accent = parseColor(ledger?.colorHex, Palette.Ink)
    Row(
        modifier = Modifier
            .clip(Shape.pill)
            .background(accent.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.30f)), Shape.pill)
            .clickable(onClick = onClick)
            .padding(start = 9.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppIcons.of(ledger?.icon),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = ledger?.name ?: LedgerEntity.DEFAULT_NAME,
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = "切换账本",
            tint = Palette.InkSoft,
            modifier = Modifier.size(17.dp)
        )
    }
}

/**
 * 专项资金卡。预算按**累计**消耗算，不随月份重置 ——
 * 「这次旅行一共花了多少 / 还剩多少」才是这本账真正要回答的问题。
 */
@Composable
private fun BudgetCard(state: LedgerUiState) {
    val colors = LocalLedgerColors.current
    val accent = parseColor(state.ledger?.colorHex, Palette.Ink)
    val budget = state.ledger?.budgetCents ?: 0L
    val fraction = state.budgetFraction
    val over = state.isOverBudget
    val barColor = if (over) colors.expense else accent

    LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "专项资金",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkFaint
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(fraction * 100f).roundToInt()}%",
                    style = amountStyle(
                        size = 13,
                        weight = FontWeight.Medium,
                        color = barColor
                    )
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = "¥" + Money.format(state.lifetimeExpenseCents) +
                    " / ¥" + Money.formatCompact(budget),
                style = amountStyle(
                    size = 24,
                    weight = FontWeight.SemiBold,
                    color = Palette.Ink
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(11.dp))
            ThinProgress(fraction = fraction, color = barColor, height = 6.dp)
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (over) "已超支" else "剩余",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkFaint
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "¥" + Money.formatCompact(kotlin.math.abs(state.budgetRemainingCents)),
                    style = amountStyle(
                        size = 14,
                        weight = FontWeight.Medium,
                        color = barColor
                    )
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "本月支出 ¥" + Money.formatCompact(state.monthExpenseCents),
                    style = amountStyle(size = 12, weight = FontWeight.Normal, color = colors.inkFaint)
                )
            }
        }
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier.size(38.dp),
        style = GlassStyle.Thin,
        shape = Shape.pill,
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索流水",
                tint = Palette.Ink,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SummaryCard(state: LedgerUiState) {
    val colors = LocalLedgerColors.current
    LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = "本月结余",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkFaint
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "¥" + Money.format(state.monthBalanceCents),
                style = amountStyle(
                    size = 32,
                    weight = FontWeight.SemiBold,
                    color = if (state.monthBalanceCents < 0) colors.expense else Palette.Ink
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                InlineStat(
                    label = "支出",
                    cents = state.monthExpenseCents,
                    color = colors.expense,
                    modifier = Modifier.weight(1f)
                )
                InlineStat(
                    label = "收入",
                    cents = state.monthIncomeCents,
                    color = colors.income,
                    modifier = Modifier.weight(1f)
                )
                InlineStat(
                    label = "笔数",
                    cents = null,
                    color = Palette.Ink,
                    extra = "${state.transactionCount} 笔",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun InlineStat(
    label: String,
    cents: Long?,
    color: Color,
    modifier: Modifier = Modifier,
    extra: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalLedgerColors.current.inkFaint
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = extra ?: ("¥" + Money.formatCompact(cents ?: 0L)),
            style = amountStyle(size = 15, weight = FontWeight.Medium, color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DayHeaderRow(group: DayGroup) {
    val colors = LocalLedgerColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.WarmWhite)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Dates.formatDayLabel(group.dateEpochDay),
            style = MaterialTheme.typography.labelMedium,
            color = Palette.InkSoft
        )
        Spacer(Modifier.weight(1f))
        if (group.expenseCents > 0L) {
            Text(
                text = "支出 " + Money.formatCompact(group.expenseCents),
                style = amountStyle(size = 12, weight = FontWeight.Normal, color = colors.inkFaint)
            )
        }
        if (group.expenseCents > 0L && group.incomeCents > 0L) {
            Spacer(Modifier.width(10.dp))
        }
        if (group.incomeCents > 0L) {
            Text(
                text = "收入 " + Money.formatCompact(group.incomeCents),
                style = amountStyle(size = 12, weight = FontWeight.Normal, color = colors.inkFaint)
            )
        }
    }
}

@Composable
private fun DayCard(
    group: DayGroup,
    onOpenTransaction: (Long) -> Unit
) {
    LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Column {
            group.rows.forEachIndexed { index, row ->
                if (index > 0) Hairline(Modifier.padding(start = 66.dp))
                TransactionItem(row = row, onClick = { onOpenTransaction(row.id) })
            }
        }
    }
}

