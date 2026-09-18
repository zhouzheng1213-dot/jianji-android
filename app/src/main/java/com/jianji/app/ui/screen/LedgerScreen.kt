package com.jianji.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.jianji.app.data.TxRow
import com.jianji.app.domain.Dates
import com.jianji.app.domain.Money
import com.jianji.app.ui.component.EmptyHint
import com.jianji.app.ui.component.Hairline
import com.jianji.app.ui.component.LedgerCard
import com.jianji.app.ui.component.MonthSelector
import com.jianji.app.ui.component.Shape
import com.jianji.app.ui.component.TransactionItem
import com.jianji.app.ui.component.amountStyle
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette
import com.jianji.app.vm.DayGroup
import com.jianji.app.vm.LedgerUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBackToCurrentMonth: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 104.dp)
        ) {
            item(key = "header") {
                MonthSelector(
                    label = Dates.formatMonth(state.month),
                    isCurrentMonth = state.isCurrentMonth,
                    onPrev = onPrevMonth,
                    onNext = onNextMonth,
                    onBackToCurrent = onBackToCurrentMonth,
                    modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                    trailing = { SearchButton(onClick = onOpenSearch) }
                )
            }

            item(key = "summary") {
                SummaryCard(state)
                Spacer(Modifier.height(10.dp))
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

        AddEntryButton(
            onClick = onAddTransaction,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 22.dp)
        )
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(Shape.pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "搜索流水",
            tint = Palette.Ink,
            modifier = Modifier.size(21.dp)
        )
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

@Composable
private fun AddEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(Shape.pill)
            .background(Palette.Ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(19.dp)
        )
        Text(
            text = "记一笔",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
