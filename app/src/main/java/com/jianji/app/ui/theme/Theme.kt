package com.jianji.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 暖白底 + 墨色单色强调。整站只有红/绿两个语义色（支出红、收入绿）。 */
object Palette {
    val WarmWhite = Color(0xFFFBFBF9)
    val Surface = Color(0xFFFFFFFF)
    val Sunken = Color(0xFFF3F2ED)
    val Line = Color(0xFFE9E6DF)
    val Ink = Color(0xFF2B2724)
    val InkSoft = Color(0xFF6E6862)
    val InkFaint = Color(0xFFA39C94)
    val Expense = Color(0xFFC8503C)
    val Income = Color(0xFF3F8F6B)
    val Warning = Color(0xFFC9853C)
}

@Immutable
data class LedgerColors(
    val expense: Color = Palette.Expense,
    val income: Color = Palette.Income,
    val line: Color = Palette.Line,
    val sunken: Color = Palette.Sunken,
    val inkSoft: Color = Palette.InkSoft,
    val inkFaint: Color = Palette.InkFaint
)

val LocalLedgerColors = staticCompositionLocalOf { LedgerColors() }

private val JianJiColorScheme = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Color.White,
    primaryContainer = Palette.Sunken,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.InkSoft,
    onSecondary = Color.White,
    secondaryContainer = Palette.Sunken,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Expense,
    onTertiary = Color.White,
    background = Palette.WarmWhite,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Sunken,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Sunken,
    surfaceContainerLow = Palette.WarmWhite,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Expense,
    onError = Color.White,
    scrim = Color(0x66000000)
)

private val JianJiTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun JianJiTheme(content: @Composable () -> Unit) {
    // 故意不跟随系统深色模式：这套「暖白极简」是按浅色纸面设计的，
    // 强行反色会让金额与分类色的对比度失控。
    CompositionLocalProvider(LocalLedgerColors provides LedgerColors()) {
        MaterialTheme(
            colorScheme = JianJiColorScheme,
            typography = JianJiTypography,
            content = content
        )
    }
}
