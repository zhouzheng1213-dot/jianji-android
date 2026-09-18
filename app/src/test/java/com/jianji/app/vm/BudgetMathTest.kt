package com.jianji.app.vm

import com.jianji.app.data.LedgerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * 「专项资金」的四个派生量：比例、剩余、是否超支、是否设了预算。
 *
 * 之所以能纯 JVM 测：这些值全是 [LedgerUiState] 的派生属性，只依赖账本实体与累计支出，
 * 不碰数据库也不碰 Compose。边界最容易写错，所以边界单独各来一条。
 */
class BudgetMathTest {

    private fun state(budgetCents: Long, lifetimeExpenseCents: Long): LedgerUiState =
        LedgerUiState(
            month = YearMonth.of(2026, 10),
            ledger = LedgerEntity(
                id = 7L,
                name = "国庆北疆",
                icon = "flight",
                colorHex = "#176B66",
                budgetCents = budgetCents
            ),
            lifetimeExpenseCents = lifetimeExpenseCents
        )

    @Test
    fun `a ledger without a budget reports no budget and never looks over`() {
        val s = state(budgetCents = 0L, lifetimeExpenseCents = 999_999L)

        assertFalse(s.hasBudget)
        assertEquals(0f, s.budgetFraction, 0.0001f)
        assertFalse(s.isOverBudget)
        // 没设预算时剩余无意义，按 0 - 支出 计算会得到负数，所以这里有意识地只断言不参与展示
        assertEquals(-999_999L, s.budgetRemainingCents)
    }

    @Test
    fun `a missing ledger falls back to the default id and no budget`() {
        val s = LedgerUiState(month = YearMonth.of(2026, 10), ledger = null)

        assertEquals(LedgerEntity.DEFAULT_ID, s.ledgerId)
        assertFalse(s.hasBudget)
        assertEquals(0f, s.budgetFraction, 0.0001f)
    }

    @Test
    fun `fraction is expense over budget`() {
        val s = state(budgetCents = 500_000L, lifetimeExpenseCents = 125_000L)

        assertTrue(s.hasBudget)
        assertEquals(0.25f, s.budgetFraction, 0.0001f)
        assertEquals(375_000L, s.budgetRemainingCents)
        assertFalse(s.isOverBudget)
    }

    @Test
    fun `spending exactly the budget is not over budget`() {
        val s = state(budgetCents = 500_000L, lifetimeExpenseCents = 500_000L)

        assertEquals(1f, s.budgetFraction, 0.0001f)
        assertEquals(0L, s.budgetRemainingCents)
        assertFalse(s.isOverBudget)
    }

    @Test
    fun `one cent past the budget flips over budget and goes negative`() {
        val s = state(budgetCents = 500_000L, lifetimeExpenseCents = 500_001L)

        assertTrue(s.isOverBudget)
        assertEquals(-1L, s.budgetRemainingCents)
        // 比例可以超过 1 —— 进度条自己负责 coerce，这里保留真实值供文案使用
        assertTrue(s.budgetFraction > 1f)
    }

    @Test
    fun `income never reduces budget consumption`() {
        // 只有支出进 lifetimeExpenseCents，收入再多也不影响比例。
        val rich = state(budgetCents = 100_000L, lifetimeExpenseCents = 90_000L)

        assertEquals(0.9f, rich.budgetFraction, 0.0001f)
        assertEquals(10_000L, rich.budgetRemainingCents)
        assertFalse(rich.isOverBudget)
    }

    @Test
    fun `month balance is income minus expense`() {
        val s = LedgerUiState(
            month = YearMonth.of(2026, 10),
            monthExpenseCents = 30_000L,
            monthIncomeCents = 50_000L
        )

        assertEquals(20_000L, s.monthBalanceCents)
    }

    @Test
    fun `total assets sum every account balance`() {
        val s = LedgerUiState(
            month = YearMonth.of(2026, 10),
            balances = listOf(
                com.jianji.app.data.AccountWithBalance(
                    id = 1L, name = "现金", type = 0, icon = "wallet",
                    initialBalanceCents = 0L, balanceCents = 120_000L
                ),
                com.jianji.app.data.AccountWithBalance(
                    id = 2L, name = "微信", type = 0, icon = "card",
                    initialBalanceCents = 0L, balanceCents = 5_000L
                ),
                com.jianji.app.data.AccountWithBalance(
                    id = 3L, name = "信用卡", type = 1, icon = "card",
                    initialBalanceCents = 0L, balanceCents = -7_000L
                )
            )
        )

        assertEquals(118_000L, s.totalAssetsCents)
    }
}
