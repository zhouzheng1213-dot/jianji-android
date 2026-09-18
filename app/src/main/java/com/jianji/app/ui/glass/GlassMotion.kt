package com.jianji.app.ui.glass

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * 全局动效令牌。
 *
 * 全部是**非线性**曲线：弹簧，或 Material 3 的「强调」贝塞尔族。
 * 刻意不提供 `LinearEasing` —— 线性插值会让玻璃看起来像塑料。
 */
object GlassMotion {

    /** 强调曲线：起步快、收尾长，用于「改变位置/尺寸」的过渡。 */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 强调减速：进场用。开头就有速度，尾巴慢慢停下。 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 强调加速：退场用。开头慢，然后干脆地离开。 */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * 整页浮层进场。弹簧带一点过冲（damping 0.8）——
     * 页面从记一笔按钮长出来时先冲过头一点再稳住，这是「液态」的来源之一。
     * 过冲打在 `graphicsLayer` 缩放上，幅度可控，不会晃眼。
     */
    fun <T> sheetIn(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

    /** 整页浮层退场：干脆，不弹 —— 关闭要利落，回弹会显得犹豫。 */
    fun <T> sheetOut(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    const val FadeInMillis = 180
    const val FadeOutMillis = 140
    const val SlideMillis = 280
}

/**
 * 「从哪儿来回哪儿去」的锚点。
 *
 * 记录用户**手指最后按下的位置**（相对整屏归一化），
 * 界面元素展开时从这一点长出来，收起时缩回同一点。
 *
 * 关键约束：只在**打开的那一刻**调用一次 [freeze]。
 * 关闭过程中绝不能重新采样 —— 否则点了浮层里的「关闭」按钮，
 * 浮层就会改成朝那个按钮缩回去，而不是回到它当初长出来的位置。
 *
 * **为什么用 [transformOrigin] 而不是 Compose 自带的 `expandIn` / `shrinkOut`**：
 * 那两个动的是 **layout 尺寸**（从 0 长到全屏）。页面里只要有 `statusBarsPadding()`
 * 这类按约束算的 inset，尺寸为 0 时 inset 比节点还大，每一帧布局都被挤压重排，
 * 内容就会随尺寸乱跳 —— 看起来就是「瞎飞」。
 * `graphicsLayer` 只改绘制变换，**完全不碰布局**，inset 一次算好就不动了，
 * 而且缩放不触发布局，动画期间零重排开销。
 */
@Stable
class GlassOrigin {

    private var containerSize by mutableStateOf(IntSize.Zero)
    private var touch by mutableStateOf(Offset.Unspecified)
    private var frozenPoint by mutableStateOf<Offset?>(null)

    internal fun onContainerSize(size: IntSize) {
        containerSize = size
    }

    internal fun onTouch(position: Offset) {
        touch = position
    }

    /** 在「打开」的那一行调用：把来向钉死。 */
    fun freeze() {
        val point = touch
        val size = containerSize
        frozenPoint = if (point.isSpecified && size.width > 0 && size.height > 0) {
            Offset(
                (point.x / size.width).coerceIn(0f, 1f),
                (point.y / size.height).coerceIn(0f, 1f)
            )
        } else {
            null
        }
    }

    /** 归一化来向；没有采样到时退回屏幕中心，退化为对称展开。 */
    val fraction: Offset
        get() = frozenPoint ?: Offset(0.5f, 0.5f)

    /**
     * 缩放的支点。进场从这一点放大，退场缩回**同一个**点 ——
     * 两个方向读的是同一个值，这就是「回哪儿去」的保证。
     */
    val transformOrigin: TransformOrigin
        get() = TransformOrigin(fraction.x, fraction.y)
}

/**
 * 装在整屏根容器上：记录容器尺寸，并持续采样手指按下的位置。
 * 用 `PointerEventPass.Initial` 是为了在子节点消费事件之前就拿到坐标。
 */
fun Modifier.glassOriginSource(origin: GlassOrigin): Modifier = this
    .onSizeChanged { origin.onContainerSize(it) }
    .pointerInput(origin) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { change ->
                    if (change.pressed) origin.onTouch(change.position)
                }
            }
        }
    }

/**
 * Tab 之间的过渡：新页从它所在的那一侧滑进来，旧页朝反方向退回去。
 * 方向由索引差值决定 —— 从 0 跳 2 就是「往右走」，回来时原路返回。
 *
 * 这里用 `slideIn/Out` 是对的：Tab 是同层平移，没有 inset 要跟，
 * 尺寸也从头到尾都是全屏，不会触发上面说的重排问题。
 */
fun AnimatedContentTransitionScope<Int>.glassTabTransform(): ContentTransform {
    val forward = targetState > initialState
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(
            animationSpec = tween(GlassMotion.SlideMillis, easing = GlassMotion.EmphasizedDecelerate)
        ) { width -> direction * width / 7 } + fadeIn(
            animationSpec = tween(GlassMotion.FadeInMillis, easing = GlassMotion.EmphasizedDecelerate)
        )
        ) togetherWith (
        slideOutHorizontally(
            animationSpec = tween(GlassMotion.SlideMillis, easing = GlassMotion.EmphasizedAccelerate)
        ) { width -> -direction * width / 9 } + fadeOut(
            animationSpec = tween(GlassMotion.FadeOutMillis, easing = GlassMotion.EmphasizedAccelerate)
        )
        )
}
