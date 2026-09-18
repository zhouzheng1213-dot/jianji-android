package com.jianji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jianji.app.data.CategoryStatRow
import com.jianji.app.domain.Dates
import com.jianji.app.domain.Money
import com.jianji.app.domain.TxType
import com.jianji.app.ui.component.CategoryAvatar
import com.jianji.app.ui.component.DailyBarChart
import com.jianji.app.ui.component.EmptyHint
import com.jianji.app.ui.component.Hairline
import com.jianji.app.ui.component.LedgerCard
import com.jianji.app.ui.component.MonthSelector
import com.jianji.app.ui.component.RingChart
import com.jianji.app.ui.component.SegmentedTabs
import com.jianji.app.ui.component.ThinProgress
import com.jianji.app.ui.component.TrendBars
import com.jianji.app.ui.component.amountStyle
import com.jianji.app.ui.component.parseColor
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette
import com.jianji.app.vm.StatsUiState
import java.time.YearMonth

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBackToCurrentMonth: () -> Unit,
    onModeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLedgerColors.current
    val isExpense = state.mode == TxType.EXPENSE
    val slices = remember(state.categoryStats) {
        state.categoryStats
            .filter { it.totalCents > 0L }
            .map { parseColor(it.colorHex) to it.totalCents }
    }
    val dailyMap = remember(state.month, state.dailyExpense) {
        state.dailyExpense.associate { (epochDay, cents) ->
            Dates.toDate(epochDay).dayOfMonth to cents
        }
    }
    val trendPoints = remember(state.trend) {
        state.trend.map { Dates.formatMonthShort(it.month) to (it.expenseCents to it.incomeCents) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        MonthSelector(
            label = Dates.formatMonth(state.month),
            isCurrentMonth = state.month == YearMonth.now(),
            onPrev = onPrevMonth,
            onNext = onNextMonth,
            onBackToCurrent = onBackToCurrentMonth,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp)
        )

        SegmentedTabs(
            items = listOf("支出", "收入"),
            selectedIndex = if (isExpense) 0 else 1,
            onSelect = { onModeChange(if (it == 0) TxType.EXPENSE else TxType.INCOME) },
            modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // ---- 占比环形图 ----
        LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Column(
                modifier = Modifier.padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.categoryStats.isEmpty()) {
                    EmptyHint(
                        title = if (isExpense) "本月还没有支出" else "本月还没有收入",
                        subtitle = "记一笔之后这里就会有图表"
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        RingChart(
                            slices = slices,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isExpense) "本月支出" else "本月收入",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.inkFaint
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "¥" + Money.formatCompact(state.totalCents),
                                style = amountStyle(
                                    size = 21,
                                    weight = FontWeight.SemiBold,
                                    color = if (isExpense) colors.expense else colors.income
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- 分类排行 ----
        if (state.categoryStats.isNotEmpty()) {
            LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    state.categoryStats.forEachIndexed { index, stat ->
                        if (index > 0) Hairline(Modifier.padding(start = 62.dp))
                        CategoryRankRow(stat = stat, totalCents = state.totalCents)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---- 近 6 个月趋势 ----
        LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "近六个月",
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.Ink
                    )
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LegendDot("支出", colors.expense)
                        LegendDot("收入", colors.income)
                    }
                }
                Spacer(Modifier.height(16.dp))
                TrendBars(points = trendPoints, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- 本月每日支出 ----
        LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "每日支出",
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.Ink
                    )
                    Spacer(Modifier.weight(1f))
                    val peak = dailyMap.maxByOrNull { it.value }
                    if (peak != null && peak.value > 0L) {
                        Text(
                            text = "最高 ${peak.key} 日 ¥${Money.formatCompact(peak.value)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkFaint
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                DailyBarChart(
                    month = state.month,
                    values = dailyMap,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CategoryRankRow(stat: CategoryStatRow, totalCents: Long) {
    val colors = LocalLedgerColors.current
    val color = parseColor(stat.colorHex)
    val fraction = if (totalCents <= 0L) 0f else stat.totalCents.toFloat() / totalCents.toFloat()
    val percent = fraction * 100f

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(iconKey = stat.icon, colorHex = stat.colorHex, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stat.name ?: "未分类",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${stat.txCount} 笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkFaint
                )
            }
            Spacer(Modifier.height(7.dp))
            ThinProgress(fraction = fraction, color = color)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "¥" + Money.formatCompact(stat.totalCents),
                style = amountStyle(size = 15, weight = FontWeight.SemiBold, color = Palette.Ink)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = String.format(java.util.Locale.US, "%.1f%%", percent),
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkFaint
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalLedgerColors.current.inkSoft
        )
    }
}
