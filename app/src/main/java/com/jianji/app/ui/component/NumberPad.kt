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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianji.app.ui.theme.LocalLedgerColors
import com.jianji.app.ui.theme.Palette

private val KEY_HEIGHT = 54.dp
private val KEY_GAP = 8.dp

/**
 * 自研数字键盘。左侧 3 列数字，右侧一列功能键（退格 / 今天 / 完成），
 * 完成键纵向占两行，拇指落点更舒服。
 */
@Composable
fun NumberPad(
    onDigit: (Char) -> Unit,
    onDot: () -> Unit,
    onDoubleZero: () -> Unit,
    onBackspace: () -> Unit,
    onToday: () -> Unit,
    onDone: () -> Unit,
    doneEnabled: Boolean,
    modifier: Modifier = Modifier,
    todayIsActive: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                DigitKey("1", Modifier.weight(1f)) { onDigit('1') }
                DigitKey("2", Modifier.weight(1f)) { onDigit('2') }
                DigitKey("3", Modifier.weight(1f)) { onDigit('3') }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                DigitKey("4", Modifier.weight(1f)) { onDigit('4') }
                DigitKey("5", Modifier.weight(1f)) { onDigit('5') }
                DigitKey("6", Modifier.weight(1f)) { onDigit('6') }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                DigitKey("7", Modifier.weight(1f)) { onDigit('7') }
                DigitKey("8", Modifier.weight(1f)) { onDigit('8') }
                DigitKey("9", Modifier.weight(1f)) { onDigit('9') }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                DigitKey("00", Modifier.weight(1f)) { onDoubleZero() }
                DigitKey("0", Modifier.weight(1f)) { onDigit('0') }
                DigitKey(".", Modifier.weight(1f)) { onDot() }
            }
        }

        Column(
            modifier = Modifier.width(76.dp),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP)
        ) {
            FunctionKey(
                modifier = Modifier.fillMaxWidth().height(KEY_HEIGHT),
                onClick = onBackspace
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "退格",
                    tint = Palette.Ink,
                    modifier = Modifier.size(22.dp)
                )
            }
            FunctionKey(
                modifier = Modifier.fillMaxWidth().height(KEY_HEIGHT),
                onClick = onToday,
                active = todayIsActive
            ) {
                Text(
                    text = "今天",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (todayIsActive) Color.White else Palette.Ink
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(KEY_HEIGHT * 2 + KEY_GAP)
                    .clip(Shape.field)
                    .background(if (doneEnabled) Palette.Ink else Palette.InkFaint.copy(alpha = 0.35f))
                    .clickable(enabled = doneEnabled, onClick = onDone),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "完成",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun DigitKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(KEY_HEIGHT)
            .clip(Shape.field)
            .background(Color.White)
            .border(BorderStroke(1.dp, LocalLedgerColors.current.line), Shape.field)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = if (label.length > 1) 19.sp else 22.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.Ink
        )
    }
}

@Composable
private fun FunctionKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(Shape.field)
            .background(if (active) Palette.Ink else Palette.Sunken)
            .then(
                if (active) Modifier
                else Modifier.border(BorderStroke(1.dp, LocalLedgerColors.current.line), Shape.field)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** 键盘上方的一条固定高度占位，避免键盘出现/消失时金额位置跳动。 */
@Composable
fun KeypadSpacer(height: Dp, modifier: Modifier = Modifier) {
    Spacer(modifier.height(height))
}
