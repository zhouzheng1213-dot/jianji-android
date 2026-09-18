package com.jianji.app.vm

import com.jianji.app.data.CategoryStatRow
import com.jianji.app.data.DayTotal
import com.jianji.app.data.TxRow
import java.time.YearMonth

/** 明细页按天分组；组内合计在构造时算好，滚动时不再遍历。 */
data class DayGroup(
    val dateEpochDay: Long,
    val rows: List<TxRow>
) {
    val expenseCents: Long = rows.sumOf { if (it.type == 0) it.amountCents else 0L }
    val incomeCents: Long = rows.sumOf { if (it.type == 1) it.amountCents else 0L }
}

/** 近几个月的收支趋势点。 */
data class TrendPoint(
    val month: YearMonth,
    val expenseCents: Long,
    val incomeCents: Long
)

/** 时间范围筛选的预设。 */
enum class RangeMode(val label: String) {
    ALL("全部"),
    THIS_MONTH("本月"),
    LAST_MONTH("上月"),
    RECENT_3M("近三月")
}

data class SearchFilter(
    val range: RangeMode = RangeMode.ALL,
    val type: Int = -1,
    val categoryId: Long = -1L,
    val keyword: String = "",
    val minCents: Long = -1L,
    val maxCents: Long = -1L
) {
    val activeCount: Int
        get() = listOf(
            range != RangeMode.ALL,
            type != -1,
            categoryId != -1L,
            keyword.isNotBlank(),
            minCents != -1L,
            maxCents != -1L
        ).count { it }
}
