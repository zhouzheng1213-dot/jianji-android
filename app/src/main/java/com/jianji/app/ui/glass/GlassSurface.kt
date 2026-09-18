package com.jianji.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jianji.app.ui.component.Shape as JianJiShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

/** 底部导航栏高度，正文列表靠它留出滚动余量。 */
val GlassBottomBarHeight = 60.dp

/**
 * 简记的玻璃表面。所有玻璃视觉都收敛到这一个入口，保证「厚薄」只有一种解释。
 *
 * 三种走向由运行时决定，调用方不需要关心：
 *  1. 有背板且能力足够 → 真实采样背景（模糊 / 折射）；
 *  2. 样式本身不需要采样（[GlassStyle.Thin]）→ 半透底色 + 高光边，不产生离屏缓冲；
 *  3. 没有背板，或设备低于 API 31 → 提高底色不透明度 + 静态描边。
 *
 * @param onClick 非空时整块可点击。
 * @param tintOverride 覆盖样式自带底色（例如警示态的浅红底）。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Thick,
    shape: Shape = JianJiShape.card,
    tier: GlassTier = LocalGlassTier.current,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    onClick: (() -> Unit)? = null,
    tintOverride: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val tint = tintOverride ?: style.tint
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    val samplesBackdrop = backdrop != null && tier != GlassTier.Solid && style.samplesBackdrop

    if (!samplesBackdrop) {
        // 降级 / 静态档：不采样背景，纯粹靠底色与高光边撑出材质。
        Box(
            modifier
                .clip(shape)
                .background(style.solidTint(tint))
                .glassEdge(style, shape)
                .then(clickable),
            content = content
        )
        return
    }

    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop!!,
                shape = { shape },
                effects = {
                    if (tier.canBlur && style.blurRadius > 0.dp) {
                        blur(style.blurRadius.toPx())
                    }
                    if (tier.canRefract && style.lensHeight > 0.dp && style.lensAmount > 0.dp) {
                        lens(
                            refractionHeight = style.lensHeight.toPx(),
                            refractionAmount = style.lensAmount.toPx(),
                            depthEffect = true,
                            chromaticAberration = style.chromaticAberration
                        )
                    }
                },
                highlight = {
                    Highlight(
                        width = style.highlightWidth,
                        blurRadius = style.highlightWidth / 2f,
                        alpha = style.highlightAlpha
                    )
                },
                shadow = style.shadow?.let { s -> { s } },
                onDrawSurface = { drawRect(tint) }
            )
            .then(clickable),
        content = content
    )
}

/** 只要模糊或折射任一开启，就必须走真实采样路径。 */
internal val GlassStyle.samplesBackdrop: Boolean
    get() = blurRadius > 0.dp || lensHeight > 0.dp

/**
 * 降级档的底色。玻璃没了，底色就必须自己扛住可读性 ——
 * 所以把 alpha 抬到 0.9 以上，而不是继续用半透。
 */
internal fun GlassStyle.solidTint(tint: Color): Color =
    tint.copy(alpha = (tint.alpha + 0.34f).coerceIn(0f, 0.97f))

/**
 * 玻璃的高光边：上缘亮、往下一半渐隐。
 *
 * 真实液态玻璃的「厚度」一半来自折射，另一半来自这条上缘高光；
 * 降级档里折射没了，这条边就是最后的材质暗示。
 */
internal fun Modifier.glassEdge(style: GlassStyle, shape: Shape): Modifier = drawWithCache {
    val strokeWidth = style.borderWidth.toPx()
    // 缓存轮廓路径与画刷，滚动时不再重建。
    val path = if (strokeWidth > 0f) {
        Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    } else {
        null
    }
    val stroke = Stroke(width = strokeWidth)
    val topAlpha = style.highlightAlpha * 0.9f
    val edgeBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = topAlpha),
            0.45f to Color.White.copy(alpha = topAlpha * 0.25f),
            1f to Color.White.copy(alpha = 0f)
        ),
        startY = 0f,
        endY = size.height
    )
    onDrawWithContent {
        drawContent()
        if (path != null) {
            drawPath(path, style.borderColor, style = stroke)
            drawPath(path, edgeBrush, style = stroke)
        }
    }
}
