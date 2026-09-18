package com.jianji.app.domain

/**
 * 金额一律以「分」为单位的 Long 存储与运算，彻底避免浮点误差。
 * 展示层再做千分位与两位小数格式化。
 */
object Money {

    /** 1,234.56 ；负数带前导减号。 */
    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else if (negative) -cents else cents
        val yuan = abs / 100
        val frac = abs % 100
        val grouped = yuan.toString().reversed().chunked(3).joinToString(",").reversed()
        val sign = if (negative) "-" else ""
        return "$sign$grouped.${frac.toString().padStart(2, '0')}"
    }

    /** 整数金额省略小数位：1,234 而不是 1,234.00。 */
    fun formatCompact(cents: Long): String {
        val text = format(cents)
        return if (text.endsWith(".00")) text.dropLast(3) else text
    }

    /** 用于流水行：支出 -12.00 / 收入 +12.00 / 转账 12.00。 */
    fun formatSigned(cents: Long, type: Int): String = when (type) {
        TxType.INCOME -> "+" + format(cents)
        TxType.EXPENSE -> "-" + format(cents)
        else -> format(cents)
    }
}

/**
 * 自研数字键盘的输入状态模型。
 * 内部状态就是一个用户逐步敲出来的字符串（"" / "0" / "1234.5"），
 * 千分位只在展示层加，避免把格式化字符混进输入状态。
 */
object AmountInput {

    fun digit(current: String, d: Char): String {
        // 整数部分最多 9 位，小数部分最多 2 位
        val dotCount = current.count { it == '.' }
        if (dotCount == 1 && current.substringAfter('.').length >= 2) return current
        if (dotCount == 0 && current.length >= 9) return current
        if (current == "0") return d.toString()
        if (current.isEmpty() && d == '0') return "0"
        if (current.length == 1 && current[0] == '0' && d == '0') return current
        return current + d
    }

    fun dot(current: String): String {
        if (current.contains('.')) return current
        return if (current.isEmpty()) "0." else "$current."
    }

    fun backspace(current: String): String {
        if (current.isEmpty()) return current
        val next = current.dropLast(1)
        return if (next == "0") "" else next
    }

    fun toCents(current: String): Long {
        if (current.isEmpty() || current == ".") return 0L
        val head = current.substringBefore('.')
        val tail = if (current.contains('.')) current.substringAfter('.') else ""
        val yuan = head.ifEmpty { "0" }.toLongOrNull() ?: 0L
        val cents = tail.padEnd(2, '0').take(2).ifEmpty { "00" }.toLongOrNull() ?: 0L
        return yuan * 100 + cents
    }

    /** 展示用：给整数部分加千分位，保留用户已敲出的小数位。 */
    fun display(current: String): String {
        if (current.isEmpty()) return "0"
        val head = current.substringBefore('.')
        val grouped = head.ifEmpty { "0" }.reversed().chunked(3).joinToString(",").reversed()
        return if (current.endsWith(".")) "$grouped." else if (current.contains('.')) {
            "$grouped.${current.substringAfter('.')}"
        } else {
            grouped
        }
    }

    fun fromCents(cents: Long): String {
        if (cents <= 0L) return ""
        val yuan = cents / 100
        val frac = cents % 100
        if (frac == 0L) return yuan.toString()
        val tail = frac.toString().padStart(2, '0').trimEnd('0')
        return "$yuan.$tail"
    }
}
