package com.example

import com.example.data.export.CurrencyFormatter
import com.example.data.repository.AccountingCalculator
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertLevel
import com.example.domain.model.Category
import com.example.domain.model.LoanRecord
import com.example.domain.model.LoanRepayment
import com.example.domain.model.LoanStatus
import com.example.domain.model.LoanType
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AccountingCalculatorTest {

    @Test
    fun `indian currency formatting groups lakhs and crores accurately`() {
        assertEquals("₹500", CurrencyFormatter.formatPaise(50_000L))
        assertEquals("₹12,34,567", CurrencyFormatter.formatPaise(123_456_700L))
        assertEquals("₹12,34,567.89", CurrencyFormatter.formatPaise(123_456_789L))
        assertEquals("-₹5,000", CurrencyFormatter.formatPaise(-500_000L))
        assertEquals(500_050L, CurrencyFormatter.parseRupeesToPaise("5,000.50"))
    }

    @Test
    fun `transfers move funds between accounts without counting as income or expense`() {
        val cash = Account(id = 1L, name = "Cash", workspace = Workspace.PERSONAL, type = AccountType.CASH, openingBalancePaise = 1_000_000L) // ₹10,000
        val bank = Account(id = 2L, name = "Bank", workspace = Workspace.PERSONAL, type = AccountType.BANK, openingBalancePaise = 2_000_000L) // ₹20,000
        val salaryCat = Category(id = 10L, name = "Salary", workspace = Workspace.PERSONAL, type = TransactionType.INCOME)
        val foodCat = Category(id = 11L, name = "Food", workspace = Workspace.PERSONAL, type = TransactionType.EXPENSE)

        val cal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15, 12, 0, 0) }
        val txDate = cal.timeInMillis

        val txs = listOf(
            // Income ₹50,000 to Bank
            TransactionRecord(id = 1L, workspace = Workspace.PERSONAL, type = TransactionType.INCOME, amountPaise = 5_000_000L, dateMillis = txDate, categoryId = 10L, accountId = 2L),
            // Expense ₹2,000 from Cash
            TransactionRecord(id = 2L, workspace = Workspace.PERSONAL, type = TransactionType.EXPENSE, amountPaise = 200_000L, dateMillis = txDate, categoryId = 11L, accountId = 1L),
            // Transfer ₹5,000 from Cash to Bank
            TransactionRecord(id = 3L, workspace = Workspace.PERSONAL, type = TransactionType.TRANSFER, amountPaise = 500_000L, dateMillis = txDate, categoryId = null, accountId = 1L, toAccountId = 2L)
        )

        val balances = AccountingCalculator.computeAccountBalances(listOf(cash, bank), txs, emptyList(), emptyList())
        val cashBal = balances.first { it.account.id == 1L }.currentBalancePaise
        val bankBal = balances.first { it.account.id == 2L }.currentBalancePaise

        // Cash: 10,000 - 2,000 - 5,000 = ₹3,000 (300,000 paise)
        assertEquals(300_000L, cashBal)
        // Bank: 20,000 + 50,000 + 5,000 = ₹75,000 (7,500,000 paise)
        assertEquals(7_500_000L, bankBal)

        val displays = AccountingCalculator.toTransactionDisplays(txs, listOf(salaryCat, foodCat), listOf(cash, bank))
        val summary = AccountingCalculator.computeDashboardSummary(
            workspaceFilter = WorkspaceFilter.PERSONAL,
            year = 2026,
            month = 11,
            allTransactions = displays,
            accountBalances = balances
        )

        // Opening balances & transfer must NOT count as income or expense!
        assertEquals(5_000_000L, summary.monthlyIncomePaise)
        assertEquals(200_000L, summary.monthlyExpensePaise)
        assertEquals(4_800_000L, summary.netSavingsPaise)
    }

    @Test
    fun `personal and GVVS workspaces remain isolated in calculations`() {
        val personalCash = Account(id = 1L, name = "Cash", workspace = Workspace.PERSONAL, type = AccountType.CASH, openingBalancePaise = 0L)
        val gvvsBank = Account(id = 2L, name = "Bank", workspace = Workspace.GVVS, type = AccountType.BANK, openingBalancePaise = 0L)
        val ts = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 10, 12, 0, 0) }.timeInMillis

        val txs = listOf(
            TransactionRecord(id = 1L, workspace = Workspace.PERSONAL, type = TransactionType.INCOME, amountPaise = 1_000_000L, dateMillis = ts, categoryId = 1L, accountId = 1L),
            TransactionRecord(id = 2L, workspace = Workspace.GVVS, type = TransactionType.INCOME, amountPaise = 2_500_000L, dateMillis = ts, categoryId = 2L, accountId = 2L)
        )
        val balances = AccountingCalculator.computeAccountBalances(listOf(personalCash, gvvsBank), txs, emptyList(), emptyList())
        val displays = AccountingCalculator.toTransactionDisplays(txs, emptyList(), listOf(personalCash, gvvsBank))

        val personalSummary = AccountingCalculator.computeDashboardSummary(WorkspaceFilter.PERSONAL, 2026, 11, displays, balances)
        val gvvsSummary = AccountingCalculator.computeDashboardSummary(WorkspaceFilter.GVVS, 2026, 11, displays, balances)
        val allSummary = AccountingCalculator.computeDashboardSummary(WorkspaceFilter.ALL, 2026, 11, displays, balances)

        assertEquals(1_000_000L, personalSummary.monthlyIncomePaise)
        assertEquals(2_500_000L, gvvsSummary.monthlyIncomePaise)
        assertEquals(3_500_000L, allSummary.monthlyIncomePaise)
    }

    @Test
    fun `loan repayments update balances and loan status without double counting income`() {
        val bank = Account(id = 1L, name = "Bank", workspace = Workspace.PERSONAL, type = AccountType.BANK, openingBalancePaise = 1_000_000L) // ₹10,000
        val ts = System.currentTimeMillis()
        val loan = LoanRecord(id = 5L, workspace = Workspace.PERSONAL, type = LoanType.LENT, personName = "Ramesh", principalPaise = 400_000L, repaidPaise = 150_000L, accountId = 1L, dateMillis = ts)
        val repayment = LoanRepayment(id = 1L, loanId = 5L, amountPaise = 150_000L, dateMillis = ts, accountId = 1L)

        assertEquals(LoanStatus.PARTIALLY_PAID, loan.status)
        assertEquals(250_000L, loan.remainingPaise)

        val balances = AccountingCalculator.computeAccountBalances(listOf(bank), emptyList(), listOf(loan), listOf(repayment))
        // 10,000 - 4,000 lent + 1,500 repaid = ₹7,500 (750,000 paise)
        assertEquals(750_000L, balances.first().currentBalancePaise)
    }

    @Test
    fun `budget alert thresholds trigger at 75 90 and 100 percent`() {
        val ts = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 5, 12, 0, 0) }.timeInMillis
        val cat = Category(id = 1L, name = "Food", workspace = Workspace.PERSONAL, type = TransactionType.EXPENSE)
        val budgets = listOf(
            Budget(id = 1L, workspace = Workspace.PERSONAL, categoryId = 1L, month = 11, year = 2026, limitPaise = 100_000L)
        )

        val tx75 = listOf(TransactionRecord(id = 1L, workspace = Workspace.PERSONAL, type = TransactionType.EXPENSE, amountPaise = 75_000L, dateMillis = ts, categoryId = 1L, accountId = 1L))
        val prog75 = AccountingCalculator.computeBudgetProgress(budgets, tx75, listOf(cat)).first()
        assertEquals(BudgetAlertLevel.WARNING_75, prog75.alertLevel)

        val tx90 = listOf(TransactionRecord(id = 2L, workspace = Workspace.PERSONAL, type = TransactionType.EXPENSE, amountPaise = 92_000L, dateMillis = ts, categoryId = 1L, accountId = 1L))
        val prog90 = AccountingCalculator.computeBudgetProgress(budgets, tx90, listOf(cat)).first()
        assertEquals(BudgetAlertLevel.ALERT_90, prog90.alertLevel)

        val tx100 = listOf(TransactionRecord(id = 3L, workspace = Workspace.PERSONAL, type = TransactionType.EXPENSE, amountPaise = 105_000L, dateMillis = ts, categoryId = 1L, accountId = 1L))
        val prog100 = AccountingCalculator.computeBudgetProgress(budgets, tx100, listOf(cat)).first()
        assertEquals(BudgetAlertLevel.EXCEEDED_100, prog100.alertLevel)
        assertTrue(prog100.utilizationPercent > 100.0)
    }
}
