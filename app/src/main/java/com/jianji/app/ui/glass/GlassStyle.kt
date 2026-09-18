package com.jianji.app.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.jianji.app.ui.theme.Palette
import com.kyant.backdrop.shadow.Shadow

/**
 * 玻璃的「厚薄」规格。
 *
 * 简记只有一个本色基底（暖白纸面 + 墨色），玻璃层只做两件事：
 * 把底下的内容透上来、再压一层暖白薄雾。**不允许**出现冷色玻璃或高饱和色，
 * 否则会和 Expense / Income 两个语义色打架。
 *
 * **tint 的取值逻辑（这是上一版翻车的地方）**：
 * 玻璃能不能被看见，取决于「模糊出来的内容」与「压上去的薄雾」谁强。
 * 薄雾 alpha 一过 0.6，模糊与折射就基本看不见了 —— 玻璃退化成一张不透明的白卡。
 * 所以这里所有走真实采样路径的档位，tint 都压在 0.5 以下；
 * 可读性靠模糊半径与高光边去补，而不是靠把底色调浓。
 *
 * @param blurRadius 背景模糊半径。0 表示不采样背板（见 [Thin]）。
 * @param lensHeight 折射带的高度，越大边缘的「厚度感」越强。
 * @param lensAmount 折射位移量，超过 24dp 会开始扭曲文字。
 * @param chromaticAberration 是否开启色散（边缘泛出彩虹）。只在大块玻璃上用。
 * @param tint 压在玻璃面上的暖白薄雾。
 * @param highlightWidth / highlightAlpha 顶部高光描边的宽度与强度。
 * @param borderColor 未开启高光时的静态描边（降级档也会用）。
 */
@Immutable
data class GlassStyle(
    val blurRadius: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val chromaticAberration: Boolean,
    val tint: Color,
    val highlightWidth: Dp,
    val highlightAlpha: Float,
    val borderWidth: Dp,
    val borderColor: Color,
    val shadow: Shadow?
) {

    /**
     * 这个档位**是否需要**采样背板。
     * 想采而采不到（没接背板、或设备能力不够）才需要抬底色保可读性；
     * 本来就不打算采样的档位（[Thin]）底色已经是按「无背板」调的，不该再抬。
     */
    val samplesBackdrop: Boolean
        get() = blurRadius > 0.dp || lensHeight > 0.dp

    companion object {

        /**
         * 厚重玻璃：悬浮在滚动内容之上（页头控件：账本胶囊 / 搜索 / 月份选择器）。
         *
         * 配方对齐「琉音 Lyra」的 GlassIconButton：blur 10 + lens(24,24) +
         * surface 0.45 —— 边缘折射带宽而明显，字体经过时会被掰弯。
         * 底栏本身不走这档（它用 LiquidBottomBar 的三层结构）。
         */
        val Thick: GlassStyle = GlassStyle(
            blurRadius = 10.dp,
            lensHeight = 24.dp,
            lensAmount = 24.dp,
            chromaticAberration = false,
            tint = Palette.Surface.copy(alpha = 0.45f),
            highlightWidth = 1.dp,
            highlightAlpha = 0.9f,
            borderWidth = 0.8.dp,
            borderColor = Palette.Line,
            shadow = Shadow(
                radius = 30.dp,
                offset = DpOffset(0.dp, 12.dp),
                color = Palette.Ink.copy(alpha = 0.14f)
            )
        )

        /**
         * 轻薄「纸面」卡：大量出现在列表里（汇总卡、账本卡、行内控件）。
         *
         * **这一档不叫玻璃，也不采样背板**，原因有二：
         *  1. 卡片本身就录在背板里，采样等于采到上一帧的自己 —— 会拖出一条重影；
         *  2. 卡片底下是整片的暖白纯色，纯色没有内容可折射，模糊了也看不出来。
         *
         * 所以它是半透纸面 + 细描边，滚动开销与普通卡片一致。真玻璃只出现在
         * 「底下确实有内容」的地方：底栏、记一笔、整页浮层。
         */
        val Thin: GlassStyle = GlassStyle(
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            chromaticAberration = false,
            tint = Palette.Surface.copy(alpha = 0.62f),
            highlightWidth = 0.6.dp,
            highlightAlpha = 0.5f,
            borderWidth = 0.9.dp,
            borderColor = Palette.Line,
            shadow = null
        )
    }
}
