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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jianji.app.data.CategoryEntity
import com.jianji.app.data.DeleteOutcome
import com.jianji.app.data.Seed
import com.jianji.app.domain.TxType
import com.jianji.app.ui.AppIcons
import com.jianji.app.ui.component.CategoryAvatar
import com.jianji.app.ui.component.Hairline
import com.jianji.app.ui.component.LedgerCard
import com.jianji.app.ui.component.SegmentedTabs
import com.jianji.app.ui.component.Shape
import com.jianji.app.ui.component.parseColor
import com.jianji.app.ui.glass.LocalGlassContentInset
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette

@Composable
fun SettingsScreen(
    categories: List<CategoryEntity>,
    transactionCount: Int,
    onAddCategory: (String, Int, String, String) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onSetCategoryHidden: (Long, Boolean) -> Unit,
    onDeleteCategory: (Long, (DeleteOutcome) -> Unit) -> Unit,
    onClearLedger: () -> Unit,
    onOpenLedgers: () -> Unit,
    ledgerName: String,
    ledgerCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalLedgerColors.current
    var typeTab by remember { mutableStateOf(0) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val currentType = if (typeTab == 0) TxType.EXPENSE else TxType.INCOME
    val scoped = categories.filter { it.type == currentType }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "设置",
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            color = Palette.Ink
        )

        // ---------- 分类管理 ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "分类管理",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.Ink
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "点分类可改名/换图标，右侧眼睛控制是否出现在记账面板",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint,
                modifier = Modifier.width(180.dp),
                maxLines = 2
            )
        }

        Spacer(Modifier.height(10.dp))

        SegmentedTabs(
            items = listOf("支出分类", "收入分类"),
            selectedIndex = typeTab,
            onSelect = { typeTab = it },
            modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Column {
                scoped.forEachIndexed { index, category ->
                    if (index > 0) Hairline(Modifier.padding(start = 64.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingCategory = category
                                showCategoryEditor = true
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryAvatar(
                            iconKey = category.icon,
                            colorHex = category.colorHex,
                            size = 38.dp,
                            iconSize = 19.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (category.hidden) colors.inkFaint else Palette.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (category.hidden) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "已隐藏",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkFaint
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(Shape.pill)
                                .clickable {
                                    onSetCategoryHidden(category.id, !category.hidden)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (category.hidden) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = if (category.hidden) "显示" else "隐藏",
                                tint = if (category.hidden) colors.inkFaint else Palette.InkSoft,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(Shape.field)
                .border(BorderStroke(1.dp, colors.line), Shape.field)
                .clickable {
                    editingCategory = null
                    showCategoryEditor = true
                }
                .padding(vertical = 13.dp),
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
            Text("新增分类", style = MaterialTheme.typography.labelLarge, color = Palette.Ink)
        }

        Spacer(Modifier.height(22.dp))

        // ---------- 数据 ----------
        Text(
            text = "数据",
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Ink
        )
        LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Column {
                SettingRow(
                    title = "累计记录",
                    subtitle = "当前账本所有月份加起来的流水笔数",
                    trailing = "$transactionCount 笔"
                )
                Hairline(Modifier.padding(start = 18.dp))
                SettingRow(
                    title = "账本管理",
                    subtitle = "新建、改名、设置预算或归档账本",
                    trailing = "$ledgerCount 本",
                    onClick = onOpenLedgers
                )
                Hairline(Modifier.padding(start = 18.dp))
                SettingRow(
                    title = "清空「$ledgerName」流水",
                    subtitle = "只清空当前账本；分类与账户保留，删掉的流水无法恢复",
                    trailing = null,
                    danger = true,
                    onClick = { showClearConfirm = true }
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---------- 关于 ----------
        Text(
            text = "关于",
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Ink
        )
        LedgerCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    text = "简记 1.0.0",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.Ink
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "纯本地记账。没有账号体系，不联网，App 未申请任何网络权限，" +
                        "所有数据都只写在这台手机的数据库里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkFaint
                )
            }
        }

        // 内容滚到玻璃底栏下面，底部余量由这里给。
        Spacer(Modifier.height(LocalGlassContentInset.current + 28.dp))
    }

    toast?.let { message ->
        AlertDialog(
            onDismissRequest = { toast = null },
            containerColor = Color.White,
            title = { Text("提示", style = MaterialTheme.typography.titleMedium, color = Palette.Ink) },
            text = {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
            },
            confirmButton = {
                TextButton(onClick = { toast = null }) { Text("知道了", color = Palette.Ink) }
            }
        )
    }

    if (showCategoryEditor) {
        CategoryEditorDialog(
            existing = editingCategory,
            type = currentType,
            onDismiss = { showCategoryEditor = false },
            onSave = { name, icon, colorHex ->
                val current = editingCategory
                if (current == null) {
                    onAddCategory(name, currentType, icon, colorHex)
                } else {
                    onUpdateCategory(current.copy(name = name, icon = icon, colorHex = colorHex))
                }
                showCategoryEditor = false
            },
            onDelete = {
                editingCategory?.let { category ->
                    onDeleteCategory(category.id) { outcome ->
                        toast = when (outcome) {
                            DeleteOutcome.BUILT_IN -> "内置分类不能删除，用右边的眼睛把它隐藏即可。"
                            DeleteOutcome.IN_USE -> "这个分类下还有流水，先把那些流水改成别的分类再删。"
                            DeleteOutcome.OK -> "已删除。"
                        }
                    }
                }
                showCategoryEditor = false
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = Color.White,
            title = {
                Text("清空「$ledgerName」？", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            },
            text = {
                Text(
                    text = "这会删除「$ledgerName」里的全部 $transactionCount 笔流水，且无法撤销。分类和账户会保留，其他账本不受影响。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSoft
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearLedger()
                }) { Text("确认清空", color = colors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = colors.inkSoft)
                }
            }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: String?,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalLedgerColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) colors.expense else Palette.Ink
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkFaint
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                color = colors.inkSoft
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    existing: CategoryEntity?,
    type: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalLedgerColors.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: AppIcons.keys.first()) }
    var colorHex by remember { mutableStateOf(existing?.colorHex ?: Seed.palette.first()) }
    val canDelete = existing != null && !existing.builtIn

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = when {
                    existing == null -> "新增${if (type == TxType.EXPENSE) "支出" else "收入"}分类"
                    else -> "编辑分类"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(iconKey = icon, colorHex = colorHex, size = 46.dp, iconSize = 23.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 8) name = it },
                        singleLine = true,
                        label = { Text("分类名称") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("颜色", style = MaterialTheme.typography.labelMedium, color = colors.inkFaint)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Seed.palette.forEach { hex ->
                        val selected = hex.equals(colorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(parseColor(hex))
                                .border(
                                    BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) Palette.Ink else colors.line
                                    ),
                                    CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("图标", style = MaterialTheme.typography.labelMedium, color = colors.inkFaint)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppIcons.keys.forEach { key ->
                        val selected = key == icon
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(Shape.field)
                                .background(if (selected) colors.sunken else Color.Transparent)
                                .border(
                                    BorderStroke(1.dp, if (selected) Palette.Ink else colors.line),
                                    Shape.field
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            CategoryAvatar(iconKey = key, colorHex = colorHex, size = 28.dp, iconSize = 15.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), icon, colorHex) }
            ) {
                Text("保存", color = if (name.isNotBlank()) Palette.Ink else colors.inkFaint)
            }
        },
        dismissButton = {
            Row {
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = colors.expense)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", color = colors.inkSoft)
                }
            }
        }
    )
}
