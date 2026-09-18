package com.jianji.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jianji.app.data.AccountEntity
import com.jianji.app.data.CategoryEntity
import com.jianji.app.data.TransactionEntity
import com.jianji.app.data.TxRow
import com.jianji.app.domain.AmountInput
import com.jianji.app.domain.Dates
import com.jianji.app.domain.TxType
import com.jianji.app.ui.component.CategoryAvatar
import com.jianji.app.ui.component.Hairline
import com.jianji.app.ui.component.LedgerCard
import com.jianji.app.ui.component.NumberPad
import com.jianji.app.ui.component.SegmentedTabs
import com.jianji.app.ui.component.Shape
import com.jianji.app.ui.component.amountStyle
import com.jianji.app.ui.glass.glassBackRebound
import com.jianji.app.ui.glass.glassPressBounce
import com.jianji.app.ui.glass.glassSelectionPop
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette

private val TYPE_LABELS = listOf("支出", "收入", "转账")
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * 记一笔 / 编辑流水。整屏沉浸式，自研键盘常驻，主流程完全不唤起系统输入法
 * （备注走弹窗里的文本框，避免两套键盘互相打架）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    editing: TxRow?,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    fallbackAccountId: Long,
    lastExpenseCategoryId: Long,
    lastIncomeCategoryId: Long,
    ledgerName: String,
    onSave: (TransactionEntity, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLedgerColors.current
    val isEditing = editing != null
    val stateKey = editing?.id ?: -1L

    // 编辑时用传入的流水初始化一次；key 变化（切到另一笔）会重新初始化。
    var typeIndex by remember(stateKey) {
        mutableStateOf(
            when (editing?.type) {
                TxType.INCOME -> 1
                TxType.TRANSFER -> 2
                else -> 0
            }
        )
    }
    var amountText by remember(stateKey) {
        mutableStateOf(editing?.let { AmountInput.fromCents(it.amountCents) } ?: "")
    }
    var categoryId by remember(stateKey) { mutableLongStateOf(editing?.categoryId ?: -1L) }
    var accountId by remember(stateKey) { mutableLongStateOf(editing?.accountId ?: fallbackAccountId) }
    var toAccountId by remember(stateKey) { mutableLongStateOf(editing?.toAccountId ?: -1L) }
    var dateEpochDay by remember(stateKey) {
        mutableLongStateOf(editing?.dateEpochDay ?: Dates.today())
    }
    var note by remember(stateKey) { mutableStateOf(editing?.note ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showToAccountPicker by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentType = when (typeIndex) {
        1 -> TxType.INCOME
        2 -> TxType.TRANSFER
        else -> TxType.EXPENSE
    }
    val categories = if (currentType == TxType.INCOME) incomeCategories else expenseCategories

    // 分类/账户是异步到达的：到齐后补齐默认选择，且不覆盖已有的合法选择。
    LaunchedEffect(currentType, categories) {
        if (currentType == TxType.TRANSFER || categories.isEmpty()) return@LaunchedEffect
        if (categories.none { it.id == categoryId }) {
            val preferred = if (currentType == TxType.INCOME) lastIncomeCategoryId else lastExpenseCategoryId
            categoryId = categories.firstOrNull { it.id == preferred }?.id ?: categories.first().id
        }
    }

    LaunchedEffect(accounts) {
        if (accounts.isEmpty()) return@LaunchedEffect
        if (accounts.none { it.id == accountId }) {
            accountId = accounts.firstOrNull { it.id == fallbackAccountId }?.id ?: accounts.first().id
        }
    }

    val amountCents = AmountInput.toCents(amountText)

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // ---------- 顶部：关闭 / 类型切换 / 删除 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val closePress = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .glassBackRebound(closePress)
                    .clip(Shape.pill)
                    .clickable(interactionSource = closePress, indication = null, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Palette.Ink,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            SegmentedTabs(
                items = TYPE_LABELS,
                selectedIndex = typeIndex,
                onSelect = { typeIndex = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            val deletePress = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .glassPressBounce(deletePress, pressedScale = 0.86f)
                    .clip(Shape.pill)
                    .clickable(
                        interactionSource = deletePress,
                        indication = null,
                        enabled = isEditing
                    ) { showDeleteConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                if (isEditing) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除这一笔",
                        tint = colors.expense,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 记进哪一本，是有了独立账本之后最容易搞错的一件事，所以明写在页头。
        Text(
            text = if (isEditing) "编辑「$ledgerName」里的流水" else "记入「$ledgerName」",
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkFaint
        )

        // ---------- 金额区 ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = buildCaption(currentType, categories, categoryId, accounts, accountId, toAccountId),
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "¥",
                    style = amountStyle(size = 21, weight = FontWeight.Medium, color = colors.inkFaint),
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = AmountInput.display(amountText),
                    style = amountStyle(
                        size = 42,
                        weight = FontWeight.SemiBold,
                        color = when (currentType) {
                            TxType.INCOME -> colors.income
                            TxType.EXPENSE -> colors.expense
                            else -> Palette.Ink
                        }
                    ),
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---------- 中部：分类宫格 / 转账账户 ----------
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (currentType == TxType.TRANSFER) {
                TransferPanel(
                    accounts = accounts,
                    fromName = accounts.firstOrNull { it.id == accountId }?.name,
                    toName = accounts.firstOrNull { it.id == toAccountId }?.name,
                    onPickFrom = { showAccountPicker = true },
                    onPickTo = { showToAccountPicker = true }
                )
            } else {
                CategoryGrid(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it }
                )
            }
        }

        // ---------- 元信息行 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetaPill(
                label = Dates.formatDayLabel(dateEpochDay),
                active = dateEpochDay != Dates.today(),
                modifier = Modifier.weight(1f),
                onClick = { showDatePicker = true }
            )
            MetaPill(
                label = note.ifBlank { "备注" },
                active = note.isNotBlank(),
                modifier = Modifier.weight(1f),
                onClick = { showNoteEditor = true }
            )
            if (currentType != TxType.TRANSFER) {
                MetaPill(
                    label = accounts.firstOrNull { it.id == accountId }?.name ?: "账户",
                    active = false,
                    modifier = Modifier.weight(1f),
                    onClick = { showAccountPicker = true }
                )
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.expense
            )
        }

        // ---------- 数字键盘 ----------
        NumberPad(
            onDigit = { amountText = AmountInput.digit(amountText, it) },
            onDot = { amountText = AmountInput.dot(amountText) },
            onDoubleZero = {
                amountText = AmountInput.digit(AmountInput.digit(amountText, '0'), '0')
            },
            onBackspace = { amountText = AmountInput.backspace(amountText) },
            onToday = { dateEpochDay = Dates.today() },
            onDone = {
                val problem = validate(currentType, amountCents, categoryId, accountId, toAccountId)
                if (problem != null) {
                    errorMessage = problem
                } else {
                    errorMessage = null
                    val now = System.currentTimeMillis()
                    onSave(
                        TransactionEntity(
                            id = editing?.id ?: 0L,
                            type = currentType,
                            amountCents = amountCents,
                            categoryId = if (currentType == TxType.TRANSFER) null else categoryId,
                            accountId = accountId,
                            toAccountId = if (currentType == TxType.TRANSFER) toAccountId else null,
                            dateEpochDay = dateEpochDay,
                            note = note.trim(),
                            createdAt = editing?.createdAt ?: now,
                            updatedAt = now
                        ),
                        isEditing
                    )
                }
            },
            doneEnabled = true,
            modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp),
            todayIsActive = dateEpochDay == Dates.today()
        )
    }

    // ---------- 弹窗区 ----------

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateEpochDay * MILLIS_PER_DAY
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        dateEpochDay = millis / MILLIS_PER_DAY
                    }
                    showDatePicker = false
                }) { Text("确定", color = Palette.Ink) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消", color = colors.inkSoft)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = Color.White)
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showAccountPicker) {
        AccountPickerDialog(
            title = if (currentType == TxType.TRANSFER) "转出账户" else "账户",
            accounts = accounts,
            selectedId = accountId,
            excludeId = if (currentType == TxType.TRANSFER) toAccountId else -1L,
            onPick = {
                accountId = it
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    if (showToAccountPicker) {
        AccountPickerDialog(
            title = "转入账户",
            accounts = accounts,
            selectedId = toAccountId,
            excludeId = accountId,
            onPick = {
                toAccountId = it
                showToAccountPicker = false
            },
            onDismiss = { showToAccountPicker = false }
        )
    }

    if (showNoteEditor) {
        var draft by remember { mutableStateOf(note) }
        AlertDialog(
            onDismissRequest = { showNoteEditor = false },
            containerColor = Color.White,
            title = { Text("备注", style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 60) draft = it },
                    singleLine = true,
                    placeholder = { Text("例如：和同事的午饭", color = colors.inkFaint) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    note = draft
                    showNoteEditor = false
                }) { Text("保存", color = Palette.Ink) }
            },
            dismissButton = {
                TextButton(onClick = { showNoteEditor = false }) {
                    Text("取消", color = colors.inkSoft)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color.White,
            title = {
                Text(
                    "删除这一笔？",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Ink
                )
            },
            text = {
                Text(
                    text = "删除后无法恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSoft
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    editing?.let { onDelete(it.id) }
                }) { Text("删除", color = colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = colors.inkSoft)
                }
            }
        )
    }
}

private fun buildCaption(
    type: Int,
    categories: List<CategoryEntity>,
    categoryId: Long,
    accounts: List<AccountEntity>,
    accountId: Long,
    toAccountId: Long
): String = buildString {
    if (type == TxType.TRANSFER) {
        append("转账")
    } else {
        append(categories.firstOrNull { it.id == categoryId }?.name ?: "请选分类")
    }
    append(" · ")
    append(accounts.firstOrNull { it.id == accountId }?.name ?: "请选账户")
    if (type == TxType.TRANSFER) {
        append(" → ")
        append(accounts.firstOrNull { it.id == toAccountId }?.name ?: "请选转入")
    }
}

private fun validate(
    type: Int,
    amountCents: Long,
    categoryId: Long,
    accountId: Long,
    toAccountId: Long
): String? = when {
    amountCents <= 0L -> "请先输入金额"
    type != TxType.TRANSFER && categoryId == -1L -> "请选择一个分类"
    accountId == -1L -> "请选择账户"
    type == TxType.TRANSFER && toAccountId == -1L -> "请选择转入账户"
    type == TxType.TRANSFER && toAccountId == accountId -> "转出与转入不能是同一个账户"
    else -> null
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit
) {
    val colors = LocalLedgerColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "还没有可用分类，去「设置」里添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkFaint
                )
            }
            return@Column
        }
        categories.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowItems.forEach { category ->
                    val selected = category.id == selectedId
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(Shape.field)
                            .background(if (selected) colors.sunken else Color.Transparent)
                            .clickable { onSelect(category.id) }
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CategoryAvatar(
                            iconKey = category.icon,
                            colorHex = category.colorHex,
                            size = 42.dp,
                            iconSize = 21.dp,
                            modifier = Modifier.glassSelectionPop(selected = selected)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) Palette.Ink else colors.inkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TransferPanel(
    accounts: List<AccountEntity>,
    fromName: String?,
    toName: String?,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit
) {
    val colors = LocalLedgerColors.current
    if (accounts.size < 2) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "至少需要两个账户才能转账",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkFaint
            )
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        LedgerCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                TransferAccountRow("转出账户", fromName, onPickFrom)
                Box(Modifier.padding(start = 18.dp)) { Hairline() }
                TransferAccountRow("转入账户", toName, onPickTo)
            }
        }
    }
}

@Composable
private fun TransferAccountRow(label: String, value: String?, onClick: () -> Unit) {
    val colors = LocalLedgerColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkFaint
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value ?: "请选择",
            style = MaterialTheme.typography.bodyLarge,
            color = if (value == null) colors.inkFaint else Palette.Ink
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.inkFaint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MetaPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLedgerColors.current
    Box(
        modifier = modifier
            .clip(Shape.pill)
            .background(if (active) colors.sunken else Color.Transparent)
            .border(BorderStroke(1.dp, if (active) colors.sunken else colors.line), Shape.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Palette.Ink else colors.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AccountPickerDialog(
    title: String,
    accounts: List<AccountEntity>,
    selectedId: Long,
    excludeId: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalLedgerColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                accounts.forEachIndexed { index, account ->
                    val disabled = account.id == excludeId
                    val isPicked = account.id == selectedId
                    if (index > 0) Hairline()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !disabled) { onPick(account.id) }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryAvatar(
                            iconKey = account.icon,
                            colorHex = null,
                            size = 32.dp,
                            iconSize = 16.dp,
                            modifier = Modifier.glassSelectionPop(selected = isPicked, amplitude = 0.26f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (disabled) colors.inkFaint else Palette.Ink
                        )
                        Spacer(Modifier.weight(1f))
                        if (isPicked) {
                            Text(
                                text = "已选",
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.Ink
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = colors.inkSoft) }
        }
    )
}
