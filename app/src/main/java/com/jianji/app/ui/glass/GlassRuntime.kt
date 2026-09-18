package com.jianji.app.ui.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/**
 * 液态玻璃的渲染能力分级。
 *
 * 这套玻璃效果压在两个系统能力上：
 *  - `RenderEffect`（API 31 / S 起）：模糊、色彩矩阵；
 *  - `RuntimeShader` + AGSL（API 33 / Tiramisu 起）：折射、色散。
 *
 * 能力不足时**逐级降级**而不是直接不画 —— 老机器上看到的仍然是同一套版式与配色，
 * 只是少了折射那层「厚度」。
 */
enum class GlassTier {
    /** API < 31：只有半透底色 + 高光描边，没有真实模糊。 */
    Solid,

    /** API 31..32：可以真实模糊、可以调色，但跑不了 AGSL 折射。 */
    Blur,

    /** API >= 33：完整液态玻璃（模糊 + 折射 + 色散）。 */
    Liquid;

    val canBlur: Boolean get() = this != Solid

    val canRefract: Boolean get() = this == Liquid

    companion object {
        /** 按当前设备 API 等级推导。 */
        val system: GlassTier
            get() = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Liquid
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Blur
                else -> Solid
            }
    }
}

/**
 * 当前生效的能力等级。默认取 [GlassTier.system]，
 * 但它同时是一个可覆盖点 —— 预览与单元测试会把它固定成某一档来验证三种降级表现。
 */
val LocalGlassTier = staticCompositionLocalOf { GlassTier.system }

/**
 * 玻璃要「透过」什么。
 *
 * 这是一个 [LayerBackdrop]：根节点把主内容录进一张图形层，玻璃表面在绘制时反向采样它，
 * 于是滚动内容从玻璃底下穿过去的时候会被真实地折射。
 *
 * 为 null 表示当前没有可用背板（例如独立预览、或还没接根节点），此时玻璃退化为纯色卡片。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 把调用者「录」进玻璃背板。
 *
 * 挂在**滚动内容**上：列表滚到玻璃底栏 / 页头控件底下时，
 * 它们采样这张背板就能看到被折射的内容。
 * 关键分工——录制归滚动内容，采样归悬浮件；谁也不采自己，就没有重影。
 * 没有背板时（预览等场景）静默跳过。
 */
@Composable
fun Modifier.glassRecord(): Modifier {
    val backdrop = LocalGlassBackdrop.current
    return if (backdrop != null) this.layerBackdrop(backdrop) else this
}
/**
 * 玻璃底栏压住的高度（含系统导航栏）。
 *
 * 内容要**滚到底栏下面**才谈得上折射，所以列表底部不能靠布局留白，
 * 只能靠滚动内容自己的尾部内边距把最后一条顶上来。各页面统一读这个值。
 */
val LocalGlassContentInset = staticCompositionLocalOf { 0.dp }
