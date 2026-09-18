package com.jianji.app.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
 * @param blurRadius 背景模糊半径。0 表示不模糊（不产生离屏缓冲，见 [Thin]）。
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

    companion object {

        /**
         * 厚重玻璃：悬浮在滚动内容之上（底栏、记一笔按钮、滑入的整页）。
         * 真模糊 + 折射，是整套视觉里唯一会「动」的材质。
         */
        val Thick: GlassStyle = GlassStyle(
            blurRadius = 22.dp,
            lensHeight = 18.dp,
            lensAmount = 20.dp,
            chromaticAberration = false,
            tint = Palette.Surface.copy(alpha = 0.56f),
            highlightWidth = 0.7.dp,
            highlightAlpha = 0.75f,
            borderWidth = 0.7.dp,
            borderColor = Palette.Line,
            shadow = Shadow(
                radius = 26.dp,
                offset = androidx.compose.ui.unit.DpOffset(0.dp, 10.dp),
                color = Palette.Ink.copy(alpha = 0.10f)
            )
        )

        /**
         * 大块玻璃：整页覆盖层（记一笔 / 搜索）与弹窗。
         * 雾更浓（保证数字与表单可读），折射更克制，避免长时间阅读时边缘发晕。
         */
        val Sheet: GlassStyle = GlassStyle(
            blurRadius = 30.dp,
            lensHeight = 26.dp,
            lensAmount = 14.dp,
            chromaticAberration = true,
            tint = Palette.WarmWhite.copy(alpha = 0.86f),
            highlightWidth = 0.7.dp,
            highlightAlpha = 0.5f,
            borderWidth = 0.dp,
            borderColor = Palette.Line,
            shadow = null
        )

        /**
         * 轻薄玻璃：大量出现在列表里（汇总卡、账本卡、行内控件）。
         * **不做逐行模糊** —— 每个玻璃面都会新开一个离屏缓冲，列表里几十个必然掉帧。
         * 这里只用「半透底色 + 高光边」提炼出玻璃感，滚动开销与普通卡片一致。
         */
        val Thin: GlassStyle = GlassStyle(
            blurRadius = 0.dp,
            lensHeight = 0.dp,
            lensAmount = 0.dp,
            chromaticAberration = false,
            tint = Palette.Surface.copy(alpha = 0.62f),
            highlightWidth = 0.6.dp,
            highlightAlpha = 0.55f,
            borderWidth = 0.9.dp,
            borderColor = Palette.Line,
            shadow = null
        )
    }
}
