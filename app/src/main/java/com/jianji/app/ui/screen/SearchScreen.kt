package com.jianji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jianji.app.data.CategoryEntity
import com.jianji.app.domain.AmountInput
import com.jianji.app.domain.Money
import com.jianji.app.domain.TxType
import com.jianji.app.ui.component.EmptyHint
import com.jianji.app.ui.component.Hairline
import com.jianji.app.ui.component.LedgerCard
import com.jianji.app.ui.component.PillChip
import com.jianji.app.ui.component.Shape
import com.jianji.app.ui.component.TransactionItem
import com.jianji.app.ui.component.amountStyle
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette
import com.jianji.app.vm.RangeMode
import com.jianji.app.vm.SearchFilter
import com.jianji.app.vm.SearchUiState

/**
 * 流水搜索：时间范围 / 类型 / 分类 / 关键词 / 金额区间 五维筛选，
 * 结果按时间倒序，点进去直接编辑。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    categories: List<CategoryEntity>,
    onFilterChange: ((SearchFilter) -> SearchFilter) -> Unit,
    onReset: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLedgerColors.current
    val filter = state.filter
    var showAmountDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 进入搜索页直接聚焦输入框，少点一次。
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // ---------- 顶栏 + 关键词 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(Shape.pill)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Palette.Ink,
                    modifier = Modifier.size(21.dp)
                )
            }
            OutlinedTextField(
                value = filter.keyword,
                onValueChange = { text ->
                    onFilterChange { it.copy(keyword = text.take(20)) }
                },
                singleLine = true,
                placeholder = { Text("搜索备注、分类或账户", color = colors.inkFaint) },
                trailingIcon = {
                    if (filter.keyword.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "清空关键词",
                            tint = colors.inkFaint,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onFilterChange { it.copy(keyword = "") } }
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
        }

        // ---------- 筛选条件 ----------
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RangeMode.entries.forEach { mode ->
                PillChip(
                    label = mode.label,
                    selected = filter.range == mode,
                    onClick = {
                        onFilterChange {
                            it.copy(range = if (it.range == mode) RangeMode.ALL else mode)
                        }
                    }
                )
            }
            PillChip(
                label = "转账",
                selected = filter.type == TxType.TRANSFER,
                onClick = {
                    onFilterChange {
                        it.copy(type = if (it.type == TxType.TRANSFER) TxType.ANY else TxType.TRANSFER)
                    }
                }
            )
            PillChip(
                label = "金额区间",
                selected = filter.minCents != -1L || filter.maxCents != -1L,
                onClick = { showAmountDialog = true }
            )
            if (filter.activeCount > 0) {
                PillChip(
                    label = "重置（${filter.activeCount}）",
                    selected = false,
                    onClick = onReset
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PillChip(
                label = "全部类型",
                selected = filter.type == TxType.ANY,
                onClick = { onFilterChange { it.copy(type = TxType.ANY, categoryId = -1L) } }
            )
            PillChip(
                label = "支出",
                selected = filter.type == TxType.EXPENSE,
                onClick = { onFilterChange { it.copy(type = TxType.EXPENSE, categoryId = -1L) } }
            )
            PillChip(
                label = "收入",
                selected = filter.type == TxType.INCOME,
                onClick = { onFilterChange { it.copy(type = TxType.INCOME, categoryId = -1L) } }
            )
        }

        if (filter.type == TxType.EXPENSE || filter.type == TxType.INCOME) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val scoped = categories.filter { it.type == filter.type }
                PillChip(
                    label = "全部分类",
                    selected = filter.categoryId == -1L,
                    onClick = { onFilterChange { it.copy(categoryId = -1L) } }
                )
                scoped.forEach { category ->
                    PillChip(
                        label = category.name,
                        selected = filter.categoryId == category.id,
                        onClick = {
                            onFilterChange {
                                it.copy(
                                    categoryId = if (it.categoryId == category.id) -1L else category.id
                                )
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- 结果统计 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "共 ${state.results.size} 笔",
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkFaint
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "合计 ¥" + Money.formatCompact(state.totalCents),
                style = amountStyle(size = 13, weight = FontWeight.Medium, color = colors.inkSoft)
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---------- 结果列表 ----------
        if (state.results.isEmpty()) {
            EmptyHint(
                title = "没有匹配的流水",
                subtitle = "换个关键词或放宽筛选条件试试"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                items(state.results, key = { it.id }) { row ->
                    LedgerCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp)
                    ) {
                        TransactionItem(
                            row = row,
                            onClick = { onOpenTransaction(row.id) },
                            showDate = true
                        )
                    }
                }
            }
        }
    }

    if (showAmountDialog) {
        AmountRangeDialog(
            minCents = filter.minCents,
            maxCents = filter.maxCents,
            onDismiss = { showAmountDialog = false },
            onApply = { min, max ->
                onFilterChange { it.copy(minCents = min, maxCents = max) }
                showAmountDialog = false
            }
        )
    }
}

@Composable
private fun AmountRangeDialog(
    minCents: Long,
    maxCents: Long,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit
) {
    val colors = LocalLedgerColors.current
    var minText by remember {
        mutableStateOf(if (minCents == -1L) "" else AmountInput.fromCents(minCents))
    }
    var maxText by remember {
        mutableStateOf(if (maxCents == -1L) "" else AmountInput.fromCents(maxCents))
    }

    fun sanitize(input: String) = input.all { it.isDigit() || it == '.' } && input.length <= 10

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("金额区间", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { if (sanitize(it)) minText = it },
                        singleLine = true,
                        label = { Text("最小金额") },
                        prefix = { Text("¥") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("—", color = colors.inkFaint)
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { if (sanitize(it)) maxText = it },
                        singleLine = true,
                        label = { Text("最大金额") },
                        prefix = { Text("¥") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "留空表示不限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkFaint
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    if (minText.isBlank()) -1L else AmountInput.toCents(minText),
                    if (maxText.isBlank()) -1L else AmountInput.toCents(maxText)
                )
            }) { Text("应用", color = Palette.Ink) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(-1L, -1L) }) {
                    Text("清除", color = colors.inkSoft)
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", color = colors.inkSoft)
                }
            }
        }
    )
}
