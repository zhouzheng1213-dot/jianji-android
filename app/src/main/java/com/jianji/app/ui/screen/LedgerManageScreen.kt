package com.jianji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
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
import com.jianji.app.data.DeleteOutcome
import com.jianji.app.data.LedgerEntity
import com.jianji.app.data.Seed
import com.jianji.app.domain.AmountInput
import com.jianji.app.domain.Money
import com.jianji.app.ui.AppIcons
import com.jianji.app.ui.component.Hairline
import com.jianji.app.ui.component.LedgerCard
import com.jianji.app.ui.component.Shape
import com.jianji.app.ui.component.SectionLabel
import com.jianji.app.ui.component.amountStyle
import com.jianji.app.ui.glass.glassBackRebound
import com.jianji.app.ui.glass.glassPressBounce
import com.jianji.app.ui.component.parseColor
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette

/**
 * 账本管理页。整页覆盖在 Tab 之上，负责账本的新建 / 改名 / 预算 / 归档 / 删除。
 *
 * 只有内置的「日常」不可删、不可归档 —— 它是 v1 历史数据的落点，
 * 一旦消失，那些流水就会变成谁也看不见的孤儿数据。
 */
@Composable
fun LedgerManageScreen(
    ledgers: List<LedgerEntity>,
    currentLedgerId: Long,
    onSelect: (Long) -> Unit,
    onCreate: (String, String, String, Long) -> Unit,
    onUpdate: (LedgerEntity) -> Unit,
    onSetArchived: (Long, Boolean) -> Unit,
    onDelete: (Long, (DeleteOutcome) -> Unit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalLedgerColors.current
    var editorTarget by remember { mutableStateOf<LedgerEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<LedgerEntity?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val active = ledgers.filter { !it.archived }
    val archived = ledgers.filter { it.archived }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.WarmWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "返回",
                onClick = onClose,
                isBack = true
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "账本",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Ink
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(Shape.pill)
                    .background(Palette.Sunken)
                    .clickable {
                        editorTarget = null
                        showEditor = true
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = Palette.Ink,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("新建", style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            item(key = "hint") {
                Text(
                    text = "每一本账本是独立的一盘账：明细、统计、搜索都只看当前账本。" +
                        "分类与账户是所有账本共用的。",
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkFaint
                )
            }

            items(active, key = { "l-${it.id}" }) { ledger ->
                LedgerRow(
                    ledger = ledger,
                    isCurrent = ledger.id == currentLedgerId,
                    onClick = { onSelect(ledger.id) },
                    onEdit = {
                        editorTarget = ledger
                        showEditor = true
                    },
                    onArchive = { onSetArchived(ledger.id, true) },
                    onRestore = { onSetArchived(ledger.id, false) },
                    onDelete = { deleteTarget = ledger }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (archived.isNotEmpty()) {
                item(key = "archived-label") {
                    SectionLabel(
                        text = "已归档",
                        modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 8.dp)
                    )
                }
                items(archived, key = { "a-${it.id}" }) { ledger ->
                    LedgerRow(
                        ledger = ledger,
                        isCurrent = false,
                        onClick = { onSelect(ledger.id) },
                        onEdit = {
                            editorTarget = ledger
                            showEditor = true
                        },
                        onArchive = { onSetArchived(ledger.id, true) },
                        onRestore = { onSetArchived(ledger.id, false) },
                        onDelete = { deleteTarget = ledger }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showEditor) {
        LedgerEditorDialog(
            existing = editorTarget,
            onDismiss = { showEditor = false },
            onSave = { name, icon, colorHex, budgetCents ->
                val current = editorTarget
                if (current == null) {
                    onCreate(name, icon, colorHex, budgetCents)
                } else {
                    onUpdate(current.copy(name = name, icon = icon, colorHex = colorHex, budgetCents = budgetCents))
                }
                showEditor = false
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Color.White,
            title = { Text("删除「${target.name}」？", style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = {
                Text(
                    text = "删除后无法恢复。只有里面没有任何流水时才能删掉 —— " +
                        "还有账的话，先归档它。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSoft
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    deleteTarget = null
                    onDelete(id) { outcome ->
                        toast = when (outcome) {
                            DeleteOutcome.OK -> "已删除「${target.name}」"
                            DeleteOutcome.BUILT_IN -> "内置账本不能删除"
                            DeleteOutcome.IN_USE -> "这本里还有流水，先清空或归档它"
                        }
                    }
                }) { Text("确认删除", color = colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消", color = colors.inkSoft)
                }
            }
        )
    }

    toast?.let { message ->
        AlertDialog(
            onDismissRequest = { toast = null },
            containerColor = Color.White,
            title = { Text("提示", style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft) },
            confirmButton = {
                TextButton(onClick = { toast = null }) { Text("知道了", color = Palette.Ink) }
            }
        )
    }
}

@Composable
private fun LedgerRow(
    ledger: LedgerEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalLedgerColors.current
    val accent = parseColor(ledger.colorHex, Palette.Ink)

    LedgerCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        onClick = onClick
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.of(ledger.icon),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ledger.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isCurrent) {
                            Spacer(Modifier.width(6.dp))
                            Row(
                                modifier = Modifier
                                    .clip(Shape.pill)
                                    .background(Palette.Ink)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                        if (ledger.builtIn) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "内置",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkFaint
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (ledger.budgetCents > 0L) {
                            "预算 ¥" + Money.formatCompact(ledger.budgetCents)
                        } else {
                            "未设预算"
                        },
                        style = amountStyle(size = 12, weight = FontWeight.Normal, color = colors.inkFaint)
                    )
                }
                if (ledger.archived) {
                    Text(
                        text = "已归档",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkFaint
                    )
                }
            }

            Hairline(Modifier.padding(start = 68.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RowAction(Icons.Filled.Edit, "编辑", Palette.InkSoft, onEdit)
                if (!ledger.builtIn) {
                    if (ledger.archived) {
                        RowAction(Icons.Filled.Unarchive, "恢复", Palette.InkSoft, onRestore)
                    } else {
                        RowAction(Icons.Filled.Archive, "归档", Palette.InkSoft, onArchive)
                    }
                    RowAction(Icons.Filled.Delete, "删除", colors.expense, onDelete)
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(Shape.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerEditorDialog(
    existing: LedgerEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long) -> Unit
) {
    val colors = LocalLedgerColors.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: LedgerEntity.DEFAULT_ICON) }
    var colorHex by remember { mutableStateOf(existing?.colorHex ?: Seed.ledgerPalette.first()) }
    var budgetText by remember {
        mutableStateOf(existing?.let { AmountInput.fromCents(it.budgetCents) } ?: "")
    }
    val nameValid = name.isNotBlank()
    val accent = parseColor(colorHex, Palette.Ink)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = if (existing == null) "新建账本" else "编辑账本",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 12) name = it },
                    singleLine = true,
                    label = { Text("名称，例如「国庆新疆行」") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text("图标", style = MaterialTheme.typography.labelMedium, color = colors.inkFaint)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LedgerEntity.ICON_KEYS.forEach { key ->
                        val selected = key == icon
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selected) accent.copy(alpha = 0.16f) else Palette.Sunken)
                                .then(
                                    if (selected) Modifier.border(1.5.dp, accent, CircleShape)
                                    else Modifier
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.of(key),
                                contentDescription = null,
                                tint = if (selected) accent else Palette.InkSoft,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("颜色", style = MaterialTheme.typography.labelMedium, color = colors.inkFaint)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Seed.ledgerPalette.forEach { hex ->
                        val value = parseColor(hex, Palette.Ink)
                        val selected = hex == colorHex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(value)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, Palette.Ink, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = AmountInput.display(budgetText),
                    onValueChange = { text ->
                        val raw = text.filter { it.isDigit() || it == '.' }
                        if (raw.length <= 12) budgetText = raw
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("预算（留空表示不设）") },
                    prefix = { Text("¥") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "预算按这本账累计消耗计算，不按月重置 —— " +
                        "一次旅行的专项资金就该看到总数。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkFaint
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameValid,
                onClick = {
                    onSave(name.trim(), icon, colorHex, AmountInput.toCents(budgetText))
                }
            ) {
                Text(
                    text = "保存",
                    color = if (nameValid) Palette.Ink else colors.inkFaint
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = colors.inkSoft) }
        }
    )
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    /**
     * 返回类按钮走「先缩再弹回」的回落动效，方向感更强；
     * 其它圆形图标按钮走通用点按回弹。
     */
    isBack: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val motion = if (isBack) Modifier.glassBackRebound(interaction)
    else Modifier.glassPressBounce(interaction, pressedScale = 0.88f)
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(motion)
            .clip(Shape.pill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Palette.Ink,
            modifier = Modifier.size(21.dp)
        )
    }
}

