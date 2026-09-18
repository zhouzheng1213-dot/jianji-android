package com.jianji.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 日期一律以「自 1970-01-01 起的天数」（LocalDate.toEpochDay）存储。
 * 相比时间戳，它天然规避时区与夏令时导致的「跨天」问题，分组与区间比较也最简单。
 */
object Dates {

    fun today(): Long = LocalDate.now().toEpochDay()

    fun toDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun monthStart(ym: YearMonth): Long = ym.atDay(1).toEpochDay()

    fun monthEnd(ym: YearMonth): Long = ym.atEndOfMonth().toEpochDay()

    fun currentMonth(): YearMonth = YearMonth.now()

    fun shiftMonth(ym: YearMonth, delta: Long): YearMonth = ym.plusMonths(delta)

    fun formatMonth(ym: YearMonth): String = "${ym.year}年${ym.monthValue}月"

    fun formatMonthShort(ym: YearMonth): String = "${ym.monthValue}月"

    fun formatDay(epochDay: Long): String {
        val date = toDate(epochDay)
        return "${date.monthValue}月${date.dayOfMonth}日 ${weekday(date)}"
    }

    /** 紧凑写法：9/18，用于流水行的次要信息。 */
    fun formatDayShort(epochDay: Long): String {
        val date = toDate(epochDay)
        return "${date.monthValue}/${date.dayOfMonth}"
    }

    fun formatFull(epochDay: Long): String {
        val date = toDate(epochDay)
        return "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }

    /** 列表分组标题：今天 / 昨天 / 9月16日 周三 */
    fun formatDayLabel(epochDay: Long): String {
        val today = today()
        return when (epochDay) {
            today -> "今天"
            today - 1 -> "昨天"
            today - 2 -> "前天"
            else -> formatDay(epochDay)
        }
    }

    fun weekday(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    fun monthOf(epochDay: Long): YearMonth = YearMonth.from(toDate(epochDay))

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun isoDate(epochDay: Long): String = toDate(epochDay).format(ISO)
}
