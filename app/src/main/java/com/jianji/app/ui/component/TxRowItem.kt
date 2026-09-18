package com.jianji.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jianji.app.data.TxRow
import com.jianji.app.domain.Dates
import com.jianji.app.domain.Money
import com.jianji.app.domain.TxType
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette

/**
 * 流水行。明细页与搜索结果页共用，保证两处的信息层级完全一致。
 */
@Composable
fun TransactionItem(
    row: TxRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false
) {
    val colors = LocalLedgerColors.current
    val amountColor = when (row.type) {
        TxType.EXPENSE -> colors.expense
        TxType.INCOME -> colors.income
        else -> Palette.InkSoft
    }
    val title = when (row.type) {
        TxType.TRANSFER -> "转账"
        else -> row.categoryName ?: "未分类"
    }
    val subtitle = when (row.type) {
        TxType.TRANSFER -> "${row.accountName ?: "?"} → ${row.toAccountName ?: "?"}"
        else -> row.note.ifBlank { row.accountName ?: "" }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(
            iconKey = if (row.type == TxType.TRANSFER) "replay" else row.categoryIcon,
            colorHex = if (row.type == TxType.TRANSFER) null else row.categoryColor,
            size = 38.dp,
            iconSize = 19.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank() || showDate) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        Dates.formatDayShort(row.dateEpochDay).takeIf { showDate },
                        subtitle.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = Money.formatSigned(row.amountCents, row.type),
            style = amountStyle(size = 16, weight = FontWeight.SemiBold, color = amountColor),
            maxLines = 1
        )
    }
}
