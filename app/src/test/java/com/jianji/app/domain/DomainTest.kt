package com.jianji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 金额是记账应用里唯一「错一次就毁掉全部数据可信度」的地方，
 * 所以格式化与键盘输入的每个边界都在这里钉死。
 */
class MoneyTest {

    @Test
    fun `format adds thousand separators and two decimals`() {
        assertEquals("0.00", Money.format(0))
        assertEquals("0.01", Money.format(1))
        assertEquals("0.99", Money.format(99))
        assertEquals("1.00", Money.format(100))
        assertEquals("1,234.56", Money.format(123456))
        assertEquals("1,000,000.00", Money.format(100_000_000))
        assertEquals("12,345,678.90", Money.format(1_234_567_890))
    }

    @Test
    fun `format keeps the sign for negative amounts`() {
        assertEquals("-0.01", Money.format(-1))
        assertEquals("-1,234.56", Money.format(-123456))
    }

    @Test
    fun `formatCompact drops a zero-decimal tail`() {
        assertEquals("1,234", Money.formatCompact(123400))
        assertEquals("1,234.56", Money.formatCompact(123456))
        assertEquals("0", Money.formatCompact(0))
    }

    @Test
    fun `formatSigned follows the transaction direction`() {
        assertEquals("-12.00", Money.formatSigned(1200, TxType.EXPENSE))
        assertEquals("+12.00", Money.formatSigned(1200, TxType.INCOME))
        assertEquals("12.00", Money.formatSigned(1200, TxType.TRANSFER))
    }
}

class AmountInputTest {

    @Test
    fun `typing digits builds the amount from the left`() {
        var s = ""
        "123".forEach { s = AmountInput.digit(s, it) }
        assertEquals("123", s)
        assertEquals(12300L, AmountInput.toCents(s))
    }

    @Test
    fun `a leading zero is replaced by the next digit`() {
        var s = AmountInput.digit("", '0')
        assertEquals("0", s)
        s = AmountInput.digit(s, '5')
        assertEquals("5", s)
    }

    @Test
    fun `the decimal part is capped at two digits`() {
        var s = ""
        listOf('1', '2', '.').forEach { s = if (it == '.') AmountInput.dot(s) else AmountInput.digit(s, it) }
        listOf('3', '4', '5').forEach { s = AmountInput.digit(s, it) }
        assertEquals("12.34", s)
        assertEquals(1234L, AmountInput.toCents(s))
    }

    @Test
    fun `a second decimal point is ignored`() {
        val s = AmountInput.dot("1.2")
        assertEquals("1.2", s)
    }

    @Test
    fun `dot on an empty amount becomes zero dot`() {
        assertEquals("0.", AmountInput.dot(""))
    }

    @Test
    fun `backspace removes the last character and normalizes zero`() {
        assertEquals("12.3", AmountInput.backspace("12.34"))
        assertEquals("", AmountInput.backspace("0"))
        assertEquals("", AmountInput.backspace(""))
    }

    @Test
    fun `toCents pads a short decimal part`() {
        assertEquals(1250L, AmountInput.toCents("12.5"))
        assertEquals(1205L, AmountInput.toCents("12.05"))
        assertEquals(1200L, AmountInput.toCents("12."))
        assertEquals(0L, AmountInput.toCents(""))
    }

    @Test
    fun `display groups the integer part but keeps what the user typed`() {
        assertEquals("0", AmountInput.display(""))
        assertEquals("1,234.5", AmountInput.display("1234.5"))
        assertEquals("1,234.", AmountInput.display("1234."))
        assertEquals("1,234", AmountInput.display("1234"))
    }

    @Test
    fun `fromCents round-trips back through toCents`() {
        listOf(0L, 1L, 5L, 99L, 100L, 1234L, 100_000L).forEach { cents ->
            val text = AmountInput.fromCents(cents)
            assertEquals("round trip failed for $cents cents", cents, AmountInput.toCents(text))
        }
    }

    @Test
    fun `amounts never lose precision through text`() {
        // 0.1 + 0.2 这类浮点陷阱在整数分模型下不存在
        val a = AmountInput.toCents("0.1")
        val b = AmountInput.toCents("0.2")
        assertEquals(30L, a + b)
    }

    @Test
    fun `integer part is capped so the display cannot overflow`() {
        var s = ""
        repeat(20) { s = AmountInput.digit(s, '9') }
        assertTrue("integer part should be capped", s.length <= 9)
    }
}

class DatesTest {

    @Test
    fun `epochDay round trips`() {
        val day = Dates.today()
        assertEquals(day, Dates.toDate(day).toEpochDay())
    }

    @Test
    fun `month bounds cover the whole month`() {
        val ym = java.time.YearMonth.of(2026, 2)
        assertEquals(28, Dates.monthEnd(ym) - Dates.monthStart(ym) + 1)
        val leap = java.time.YearMonth.of(2028, 2)
        assertEquals(29, Dates.monthEnd(leap) - Dates.monthStart(leap) + 1)
    }

    @Test
    fun `month arithmetic crosses the year boundary`() {
        val jan = java.time.YearMonth.of(2026, 1)
        assertEquals(java.time.YearMonth.of(2025, 12), Dates.shiftMonth(jan, -1))
        assertEquals(java.time.YearMonth.of(2026, 2), Dates.shiftMonth(jan, 1))
    }

    @Test
    fun `a known date maps to the expected weekday label`() {
        // 2026-09-18 是周五
        val day = java.time.LocalDate.of(2026, 9, 18).toEpochDay()
        assertTrue(Dates.formatDay(day).endsWith("周五"))
    }

    @Test
    fun `month of an epochDay is derived correctly`() {
        val day = java.time.LocalDate.of(2026, 9, 18).toEpochDay()
        assertEquals(java.time.YearMonth.of(2026, 9), Dates.monthOf(day))
    }
}
