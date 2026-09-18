package com.jianji.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianji.app.domain.Money
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette
import java.time.YearMonth

/**
 * 环形占比图。用自绘 Canvas 实现，不引第三方图表库，包体与启动开销都可控。
 * [slices] 为 (颜色, 数值) 列表，按数值降序传入。
 */
@Composable
fun RingChart(
    slices: List<Pair<Color, Long>>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp,
    trackColor: Color = Palette.Sunken,
    content: @Composable () -> Unit = {}
) {
    val total = slices.sumOf { it.second }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 620, easing = FastOutSlowInEasing))
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )

            if (total > 0L) {
                // 段间留 1.2° 缝隙，让相邻分类不会糊成一片。
                val gap = 1.2f
                var startAngle = -90f
                slices.forEach { (color, value) ->
                    val sweep = value.toFloat() / total.toFloat() * 360f * progress.value
                    val drawn = (sweep - gap).coerceAtLeast(0.6f)
                    drawArc(
                        color = color,
                        startAngle = startAngle + gap / 2f,
                        sweepAngle = if (slices.size == 1) sweep else drawn,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    startAngle += sweep
                }
            }
        }
        content()
    }
}

/** 近 6 个月收支柱状图：左侧支出（红），右侧收入（绿）。 */
@Composable
fun TrendBars(
    points: List<Pair<String, Pair<Long, Long>>>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 116.dp
) {
    val expenseColor = LocalLedgerColors.current.expense
    val incomeColor = LocalLedgerColors.current.income
    val maxValue = points.maxOfOrNull { maxOf(it.second.first, it.second.second) } ?: 0L
    val progress = remember { Animatable(0f) }

    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 560, easing = FastOutSlowInEasing))
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(chartHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEach { (_, values) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.height(chartHeight),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Bar(values.first, maxValue, expenseColor, progress.value, chartHeight)
                        Bar(values.second, maxValue, incomeColor, progress.value, chartHeight)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { (label, _) ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                    color = LocalLedgerColors.current.inkFaint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun Bar(
    value: Long,
    maxValue: Long,
    color: Color,
    progress: Float,
    chartHeight: Dp
) {
    val fraction = if (maxValue <= 0L) 0f else value.toFloat() / maxValue.toFloat()
    val maxPx = chartHeight
    val height = if (value <= 0L) 0.dp else (maxPx * fraction * progress).coerceAtLeast(2.dp)
    Box(
        modifier = Modifier
            .width(11.dp)
            .height(height)
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .background(color)
    )
}

/** 当月每日支出柱状图。用 Canvas 画细柱与刻度，避免 31 个子组件带来的布局开销。 */
@Composable
fun DailyBarChart(
    month: YearMonth,
    values: Map<Int, Long>,
    modifier: Modifier = Modifier,
    barColor: Color = LocalLedgerColors.current.expense,
    chartHeight: Dp = 104.dp
) {
    val days = month.lengthOfMonth()
    val maxValue = values.values.maxOrNull() ?: 0L
    val trackColor = Palette.Sunken
    val labelColor = LocalLedgerColors.current.inkFaint
    val measurer = rememberTextMeasurer()
    val progress = remember { Animatable(0f) }

    LaunchedEffect(month, values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight + 20.dp)) {
        val labelArea = 20.dp.toPx()
        val barsHeight = size.height - labelArea
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (days - 1)) / days).coerceAtLeast(1f)
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (day in 1..days) {
            val x = (day - 1) * (barWidth + gap)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, barsHeight - 3.dp.toPx()),
                size = Size(barWidth, 3.dp.toPx()),
                cornerRadius = radius
            )
            val value = values[day] ?: 0L
            if (value > 0L && maxValue > 0L) {
                val h = (value.toFloat() / maxValue.toFloat() * (barsHeight - 6.dp.toPx()) * progress.value)
                    .coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, barsHeight - h),
                    size = Size(barWidth, h),
                    cornerRadius = radius
                )
            }
        }

        // 刻度与柱子共用同一套坐标，保证「15」正对第 15 根柱子。
        listOf(1, 8, 15, 22, days).distinct().forEach { day ->
            val layout = measurer.measure(
                AnnotatedString("$day"),
                style = TextStyle(fontSize = 11.sp, color = labelColor)
            )
            val centerX = (day - 1) * (barWidth + gap) + barWidth / 2f
            val x = (centerX - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x, barsHeight + 3.dp.toPx()))
        }
    }
}

/** 图表图例。 */
@Composable
fun Legend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = label,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = LocalLedgerColors.current.inkSoft
                )
            }
        }
    }
}

/** 金额 + 标签的两行小统计块。 */
@Composable
fun StatBlock(
    label: String,
    amountCents: Long,
    amountColor: Color,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            color = LocalLedgerColors.current.inkFaint
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "¥" + Money.formatCompact(amountCents),
            style = amountStyle(
                size = if (emphasize) 24 else 18,
                weight = FontWeight.SemiBold,
                color = amountColor
            ),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SpacerHeight(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun SpacerWidth(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun PaddedSection(
    modifier: Modifier = Modifier,
    horizontal: Dp = 18.dp,
    vertical: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Box(modifier.padding(horizontal = horizontal, vertical = vertical)) { content() }
}
