package com.jianji.app.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图标动效族。
 *
 * 共同的取舍：**过冲来自弹簧本身，而不是手写关键帧**。
 * 用 `dampingRatio < 1` 的弹簧，数值会自然越过 1 再回落，
 * 于是「压一下—弹起来—稳住」不需要任何 if/else 分段。
 */

/**
 * 选中弹跳：底栏图标被选中时整体放大并微微上浮。
 *
 * @param amplitude 放大倍率增量，0.22 约等于放大 22%。
 * @param lift 上浮距离。位移很小是有意的 —— 底栏图标只有 21dp，
 *   移多了会和文字标签对不齐。
 */
@Composable
fun Modifier.glassIconBounce(
    selected: Boolean,
    amplitude: Float = 0.22f,
    lift: Dp = 2.dp,
    interactionSource: MutableInteractionSource? = null
): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.42f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
    return this.graphicsLayer {
        val value = progress.value
        val scale = 1f + amplitude * value
        scaleX = scale
        scaleY = scale
        translationY = -lift.toPx() * value
    }
}

/**
 * 点按回弹：按下缩小、松开弹回原位。
 * 用于「记一笔」这类需要手感的按钮 —— 按下有反馈，松手有回弹。
 */
@Composable
fun Modifier.glassPressBounce(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.9f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        scale.animateTo(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec = spring(
                dampingRatio = if (pressed) 0.95f else 0.4f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * 弹性放大：分类 / 账户图标被选中时「啵」地弹一下。
 * 幅度比底栏更大，因为这些图标没有文字标签需要对齐。
 */
@Composable
fun Modifier.glassSelectionPop(
    selected: Boolean,
    amplitude: Float = 0.32f
): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
        }
    }
    return this.graphicsLayer {
        val scale = 1f + amplitude * progress.value
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 图标自转：展开态转 135°，收起时原路转回 0°。
 * 「记一笔」的加号用它 —— 加号转 45° 就是叉，语义上正好是「打开 / 关闭」。
 */
@Composable
fun Modifier.glassIconRotation(
    expanded: Boolean,
    degrees: Float = 45f
): Modifier {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        angle.animateTo(
            targetValue = if (expanded) degrees else 0f,
            animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return this.graphicsLayer {
        rotationZ = angle.value
    }
}

/**
 * 返回箭头：点一下先往回缩一点，再弹回来。
 * 单独一个是因为它只需要「方向感」，不需要放大。
 */
@Composable
fun Modifier.glassBackRebound(
    interactionSource: MutableInteractionSource,
    offset: Dp = 3.dp
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val shift = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        shift.animateTo(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = spring(
                dampingRatio = if (pressed) 0.9f else 0.35f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return this.graphicsLayer {
        translationX = -offset.toPx() * shift.value
        scaleX = 1f - 0.08f * shift.value
        scaleY = 1f - 0.08f * shift.value
    }
}
