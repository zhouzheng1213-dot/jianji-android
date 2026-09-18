package com.jianji.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianji.app.ui.AppIcons
import com.jianji.app.ui.glass.GlassStyle
import com.jianji.app.ui.glass.GlassSurface
import com.jianji.app.ui.glass.glassPressBounce
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette

/** 全局统一的圆角与描边，避免各页面各写一套。 */
object Shape {
    val card = RoundedCornerShape(18.dp)
    val field = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(50)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/** 金额专用文本样式：等宽数字（tnum），列对齐时不会跳动。 */
fun amountStyle(
    size: Int = 16,
    weight: FontWeight = FontWeight.SemiBold,
    color: Color = Palette.Ink
) = TextStyle(
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    fontFeatureSettings = "tnum"
)

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalLedgerColors.current.line)
    )
}

/**
 * 玻璃卡片。全套界面里的卡片都走这里，材质只有一种解释：**薄玻璃**。
 *
 * 为什么是薄玻璃而不是厚玻璃：卡片都在滚动内容里，而滚动内容本身已经录进了背板层。
 * 如果卡片采样背板，就会采到自己刚画上去的那一帧 —— 结果是重影，不是折射。
 * 所以滚动区一律用「半透底 + 上缘高光」，真折射留给底栏与整页浮层。
 */
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = modifier,
        style = GlassStyle.Thin,
        shape = Shape.card,
        onClick = onClick
    ) {
        content()
    }
}

/** 分类圆形图标：分类色做 12% 底，图标用分类色本身。 */
@Composable
fun CategoryAvatar(
    iconKey: String?,
    colorHex: String?,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    fallbackColor: Color = Palette.InkFaint,
    modifier: Modifier = Modifier
) {
    val color = parseColor(colorHex, fallbackColor)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.of(iconKey),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun NeutralAvatar(
    icon: ImageVector,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = Palette.Ink
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Palette.Sunken),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 极简胶囊按钮：选中为墨底白字，未选中为透明底灰字 + 细描边。 */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val border = if (selected) Palette.Ink else LocalLedgerColors.current.line
    val bg = if (selected) Palette.Ink else Color.Transparent
    val fg = if (selected) Color.White else Palette.InkSoft
    Row(
        modifier = modifier
            .clip(Shape.pill)
            .background(bg)
            .border(BorderStroke(1.dp, border), Shape.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            maxLines = 1
        )
    }
}

/** 等宽分段控件，比 Material3 的 SegmentedButton 更贴合纸面风格。 */
@Composable
fun SegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val line = LocalLedgerColors.current.line
    Row(
        modifier = modifier
            .clip(Shape.pill)
            .background(Palette.Sunken)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(Shape.pill)
                    .background(if (selected) Color.White else Color.Transparent)
                    .then(
                        if (selected) Modifier.border(BorderStroke(1.dp, line), Shape.pill)
                        else Modifier
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Palette.Ink else Palette.InkSoft
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = LocalLedgerColors.current.inkFaint,
        letterSpacing = 0.6.sp
    )
}

@Composable
fun EmptyHint(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalLedgerColors.current.inkFaint,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalLedgerColors.current.inkFaint.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 颜色工具：解析 "#RRGGBB"，解析失败退回兜底色，避免脏数据导致崩溃。 */
fun parseColor(hex: String?, fallback: Color = Palette.InkFaint): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val cleaned = hex.trim().removePrefix("#")
        val value = cleaned.toLong(16)
        when (cleaned.length) {
            6 -> Color(0xFF000000L or value)
            8 -> Color(value)
            else -> fallback
        }
    } catch (_: NumberFormatException) {
        fallback
    }
}

/**
 * 月份选择器：明细页与统计页共用，保证两处的翻月交互完全一致。
 */
@Composable
fun MonthSelector(
    label: String,
    isCurrentMonth: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBackToCurrent: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
        }
        Row(
            modifier = Modifier
                .clip(Shape.pill)
                .border(BorderStroke(1.dp, LocalLedgerColors.current.line), Shape.pill)
                .padding(horizontal = 3.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChevronStep(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月", onPrev)
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink
            )
            ChevronStep(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月", onNext)
        }

        if (!isCurrentMonth) {
            Spacer(Modifier.size(10.dp))
            Text(
                text = "回本月",
                modifier = Modifier
                    .clip(Shape.pill)
                    .background(Palette.Sunken)
                    .clickable(onClick = onBackToCurrent)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.InkSoft
            )
        }

        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun ChevronStep(icon: ImageVector, description: String, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(30.dp)
            .glassPressBounce(press, pressedScale = 0.84f)
            .clip(Shape.pill)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Palette.InkSoft,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** 细进度条，用于分类排行。 */
@Composable
fun ThinProgress(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(Shape.pill)
            .background(Palette.Sunken)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(Shape.pill)
                .background(color)
        )
    }
}
