package com.jianji.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jianji.app.data.AccountEntity
import com.jianji.app.data.AccountWithBalance
import com.jianji.app.domain.AccountType
import com.jianji.app.domain.AmountInput
import com.jianji.app.domain.Money
import com.jianji.app.ui.component.CategoryAvatar
import com.jianji.app.ui.component.Hairline
import com.jianji.app.ui.component.LedgerCard
import com.jianji.app.ui.component.PillChip
import com.jianji.app.ui.component.Shape
import com.jianji.app.ui.component.amountStyle
import com.jianji.app.ui.glass.LocalGlassContentInset
import com.jianji.app.ui.glass.glassRecord
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette

private val ACCOUNT_ICONS = listOf("wallet", "virtual", "bank", "card", "savings", "money")
private val ACCOUNT_TYPE_OPTIONS = listOf(
    AccountType.CASH,
    AccountType.DEBIT,
    AccountType.CREDIT,
    AccountType.VIRTUAL
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(
    balances: List<AccountWithBalance>,
    archivedAccounts: List<AccountEntity>,
    onCreate: (String, Int, String, Long) -> Unit,
    onUpdate: (AccountEntity) -> Unit,
    onSetArchived: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLedgerColors.current
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }

    val totalAssets = balances.sumOf { it.balanceCents }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 录制进背板：玻璃底栏折射这一页的内容。
            .glassRecord()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "账户",
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            color = Palette.Ink
        )

        // ---- 总资产 ----
        LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    text = "总资产",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkFaint
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "¥" + Money.format(totalAssets),
                    style = amountStyle(
                        size = 30,
                        weight = FontWeight.SemiBold,
                        color = if (totalAssets < 0) colors.expense else Palette.Ink
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "共 ${balances.size} 个账户",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkFaint
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- 账户列表 ----
        if (balances.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有账户，先添加一个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkFaint
                )
            }
        } else {
            LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Column {
                    balances.forEachIndexed { index, item ->
                        if (index > 0) Hairline(Modifier.padding(start = 64.dp))
                        AccountRow(
                            iconKey = item.icon,
                            name = item.name,
                            typeLabel = AccountType.label(item.type),
                            balanceCents = item.balanceCents,
                            onClick = {
                                editing = AccountEntity(
                                    id = item.id,
                                    name = item.name,
                                    type = item.type,
                                    icon = item.icon,
                                    initialBalanceCents = item.initialBalanceCents
                                )
                                showEditor = true
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- 新增账户 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(Shape.field)
                .border(BorderStroke(1.dp, colors.line), Shape.field)
                .clickable {
                    editing = null
                    showEditor = true
                }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = Palette.Ink,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "新增账户",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Ink
            )
        }

        // ---- 已归档 ----
        if (archivedAccounts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showArchived = !showArchived }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已归档（${archivedAccounts.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkFaint
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (showArchived) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.inkFaint,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (showArchived) {
                LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Column {
                        archivedAccounts.forEachIndexed { index, account ->
                            if (index > 0) Hairline(Modifier.padding(start = 64.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetArchived(account.id, false) }
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryAvatar(
                                    iconKey = account.icon,
                                    colorHex = null,
                                    size = 36.dp,
                                    iconSize = 18.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.inkSoft
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "恢复",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Palette.Ink
                                )
                            }
                        }
                    }
                }
            }
        }

        // 内容滚到玻璃底栏下面，底部余量由这里给。
        Spacer(Modifier.height(LocalGlassContentInset.current + 24.dp))
    }

    if (showEditor) {
        AccountEditorDialog(
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = { name, type, icon, initialCents ->
                val current = editing
                if (current == null) {
                    onCreate(name, type, icon, initialCents)
                } else {
                    onUpdate(
                        current.copy(
                            name = name,
                            type = type,
                            icon = icon,
                            initialBalanceCents = initialCents
                        )
                    )
                }
                showEditor = false
            },
            onArchive = {
                editing?.let { onSetArchived(it.id, true) }
                showEditor = false
            }
        )
    }
}

@Composable
private fun AccountRow(
    iconKey: String,
    name: String,
    typeLabel: String,
    balanceCents: Long,
    onClick: () -> Unit
) {
    val colors = LocalLedgerColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(iconKey = iconKey, colorHex = null, size = 38.dp, iconSize = 19.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "¥" + Money.format(balanceCents),
            style = amountStyle(
                size = 16,
                weight = FontWeight.SemiBold,
                color = if (balanceCents < 0) colors.expense else Palette.Ink
            ),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditorDialog(
    existing: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, Long) -> Unit,
    onArchive: () -> Unit
) {
    val colors = LocalLedgerColors.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.CASH) }
    var icon by remember { mutableStateOf(existing?.icon ?: ACCOUNT_ICONS.first()) }
    var initialText by remember {
        mutableStateOf(existing?.let { AmountInput.fromCents(it.initialBalanceCents) } ?: "")
    }
    val nameValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = if (existing == null) "新增账户" else "编辑账户",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 12) name = it },
                    singleLine = true,
                    label = { Text("账户名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text("类型", style = MaterialTheme.typography.labelMedium, color = colors.inkFaint)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ACCOUNT_TYPE_OPTIONS.forEach { option ->
                        PillChip(
                            label = AccountType.label(option),
                            selected = option == type,
                            onClick = { type = option }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("图标", style = MaterialTheme.typography.labelMedium, color = colors.inkFaint)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ACCOUNT_ICONS.forEach { key ->
                        val selected = key == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(Shape.field)
                                .background(if (selected) colors.sunken else Color.Transparent)
                                .border(
                                    BorderStroke(1.dp, if (selected) Palette.Ink else colors.line),
                                    Shape.field
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            CategoryAvatar(iconKey = key, colorHex = null, size = 30.dp, iconSize = 16.dp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = initialText,
                    onValueChange = {
                        if (it.length <= 12 && it.all { ch -> ch.isDigit() || ch == '.' }) {
                            initialText = it
                        }
                    },
                    singleLine = true,
                    label = { Text("期初余额") },
                    prefix = { Text("¥") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "建账时这个账户里已有的钱，之后由流水自动累加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkFaint
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameValid,
                onClick = {
                    onSave(name.trim(), type, icon, AmountInput.toCents(initialText))
                }
            ) {
                Text("保存", color = if (nameValid) Palette.Ink else colors.inkFaint)
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onArchive) {
                        Text("归档", color = colors.inkSoft)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", color = colors.inkSoft)
                }
            }
        }
    )
}
