package com.example.data.repository

import com.example.data.export.CurrencyFormatter
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.AccountWithBalance
import com.example.domain.model.Budget
import com.example.domain.model.BudgetAlertLevel
import com.example.domain.model.BudgetWithProgress
import com.example.domain.model.Category
import com.example.domain.model.CategoryBreakdown
import com.example.domain.model.DailyActivityPoint
import com.example.domain.model.DashboardSummary
import com.example.domain.model.LoanRecord
import com.example.domain.model.LoanRepayment
import com.example.domain.model.LoanType
import com.example.domain.model.MonthlyComparisonPoint
import com.example.domain.model.PaymentMethodBreakdown
import com.example.domain.model.ReportData
import com.example.domain.model.SortOption
import com.example.domain.model.TransactionDisplay
import com.example.domain.model.TransactionFilterState
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceFilter
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max

object AccountingCalculator {

    fun computeAccountBalances(
        accounts: List<Account>,
        transactions: List<TransactionRecord>,
        loans: List<LoanRecord>,
        repayments: List<LoanRepayment>
    ): List<AccountWithBalance> {
        val depositsMap = mutableMapOf<Long, Long>()
        val withdrawalsMap = mutableMapOf<Long, Long>()

        fun addDeposit(accountId: Long, amount: Long) {
            depositsMap[accountId] = (depositsMap[accountId] ?: 0L) + amount
        }

        fun addWithdrawal(accountId: Long, amount: Long) {
            withdrawalsMap[accountId] = (withdrawalsMap[accountId] ?: 0L) + amount
        }

        for (tx in transactions) {
            when (tx.type) {
                TransactionType.INCOME -> addDeposit(tx.accountId, tx.amountPaise)
                TransactionType.EXPENSE -> addWithdrawal(tx.accountId, tx.amountPaise)
                TransactionType.TRANSFER -> {
                    addWithdrawal(tx.accountId, tx.amountPaise)
                    tx.toAccountId?.let { addDeposit(it, tx.amountPaise) }
                }
            }
        }

        val loanById = loans.associateBy { it.id }
        for (loan in loans) {
            when (loan.type) {
                LoanType.LENT -> addWithdrawal(loan.accountId, loan.principalPaise)
                LoanType.BORROWED -> addDeposit(loan.accountId, loan.principalPaise)
            }
        }

        for (rep in repayments) {
            val parent = loanById[rep.loanId] ?: continue
            when (parent.type) {
                LoanType.LENT -> addDeposit(rep.accountId, rep.amountPaise)
                LoanType.BORROWED -> addWithdrawal(rep.accountId, rep.amountPaise)
            }
        }

        return accounts.map { acc ->
            val deposits = depositsMap[acc.id] ?: 0L
            val withdrawals = withdrawalsMap[acc.id] ?: 0L
            val current = acc.openingBalancePaise + deposits - withdrawals
            AccountWithBalance(
                account = acc,
                currentBalancePaise = current,
                totalDepositsPaise = deposits,
                totalWithdrawalsPaise = withdrawals
            )
        }
    }

    fun toTransactionDisplays(
        transactions: List<TransactionRecord>,
        categories: List<Category>,
        accounts: List<Account>
    ): List<TransactionDisplay> {
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }
        return transactions.map { tx ->
            val cat = tx.categoryId?.let { categoryMap[it] }
            val acc = accountMap[tx.accountId]
            val toAcc = tx.toAccountId?.let { accountMap[it] }
            val catName = when {
                tx.type == TransactionType.TRANSFER -> "Account Transfer"
                cat != null -> cat.name
                else -> "Uncategorized"
            }
            val catColor = when {
                tx.type == TransactionType.TRANSFER -> 0xFF2563EB
                cat != null -> cat.colorHex
                else -> 0xFF89938E
            }
            val catIcon = when {
                tx.type == TransactionType.TRANSFER -> "swap_horiz"
                cat != null -> cat.iconName
                else -> "category"
            }
            TransactionDisplay(
                record = tx,
                categoryName = catName,
                categoryColorHex = catColor,
                categoryIconName = catIcon,
                accountName = acc?.name ?: "Account",
                accountType = acc?.type ?: AccountType.OTHER,
                toAccountName = toAcc?.name,
                toAccountType = toAcc?.type
            )
        }
    }

    fun computeDashboardSummary(
        workspaceFilter: WorkspaceFilter,
        year: Int,
        month: Int, // 1..12
        allTransactions: List<TransactionDisplay>,
        accountBalances: List<AccountWithBalance>
    ): DashboardSummary {
        val targetWs = workspaceFilter.toWorkspaceOrNull()
        val scopedTx = if (targetWs == null) allTransactions else allTransactions.filter { it.record.workspace == targetWs }
        val scopedAccounts = if (targetWs == null) accountBalances else accountBalances.filter { it.account.workspace == targetWs }

        val (startMillis, endMillis) = CurrencyFormatter.monthBoundsMillis(year, month)
        val monthTx = scopedTx.filter { it.record.dateMillis in startMillis..endMillis }

        val prevCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        val (prevStart, prevEnd) = CurrencyFormatter.monthBoundsMillis(
            prevCal.get(Calendar.YEAR),
            prevCal.get(Calendar.MONTH) + 1
        )
        val prevTx = scopedTx.filter { it.record.dateMillis in prevStart..prevEnd }

        val monthlyIncome = monthTx.filter { it.record.type == TransactionType.INCOME }.sumOf { it.record.amountPaise }
        val monthlyExpense = monthTx.filter { it.record.type == TransactionType.EXPENSE }.sumOf { it.record.amountPaise }
        val netSavings = monthlyIncome - monthlyExpense
        val savingsPct = if (monthlyIncome > 0L) (netSavings.toDouble() / monthlyIncome.toDouble()) * 100.0 else 0.0

        val prevIncome = prevTx.filter { it.record.type == TransactionType.INCOME }.sumOf { it.record.amountPaise }
        val prevExpense = prevTx.filter { it.record.type == TransactionType.EXPENSE }.sumOf { it.record.amountPaise }

        val incomeDelta = if (prevIncome > 0L) ((monthlyIncome - prevIncome).toDouble() / prevIncome.toDouble()) * 100.0 else null
        val expenseDelta = if (prevExpense > 0L) ((monthlyExpense - prevExpense).toDouble() / prevExpense.toDouble()) * 100.0 else null

        val cashBal = scopedAccounts.filter { it.account.type == AccountType.CASH }.sumOf { it.currentBalancePaise }
        val bankBal = scopedAccounts.filter { it.account.type == AccountType.BANK }.sumOf { it.currentBalancePaise }
        val upiBal = scopedAccounts.filter { it.account.type == AccountType.UPI }.sumOf { it.currentBalancePaise }
        val otherBal = scopedAccounts.filter { it.account.type == AccountType.OTHER }.sumOf { it.currentBalancePaise }

        val expenseBreakdown = computeCategoryBreakdown(
            transactions = monthTx.filter { it.record.type == TransactionType.EXPENSE },
            totalAmount = monthlyExpense
        )

        val monthlyTrend = computeMonthlyTrend(
            anchorYear = year,
            anchorMonth = month,
            monthsCount = 6,
            scopedTransactions = scopedTx
        )

        val dailyActivity = computeDailyActivity(year, month, monthTx)

        return DashboardSummary(
            monthlyIncomePaise = monthlyIncome,
            monthlyExpensePaise = monthlyExpense,
            netSavingsPaise = netSavings,
            savingsPercentage = savingsPct,
            previousMonthIncomePaise = prevIncome,
            previousMonthExpensePaise = prevExpense,
            incomeChangePercent = incomeDelta,
            expenseChangePercent = expenseDelta,
            cashBalancePaise = cashBal,
            bankBalancePaise = bankBal,
            upiBalancePaise = upiBal,
            otherBalancePaise = otherBal,
            expenseCategoryBreakdown = expenseBreakdown,
            monthlyTrend = monthlyTrend,
            dailyActivity = dailyActivity
        )
    }

    fun computeCategoryBreakdown(
        transactions: List<TransactionDisplay>,
        totalAmount: Long
    ): List<CategoryBreakdown> {
        if (transactions.isEmpty()) return emptyList()
        return transactions
            .groupBy { it.record.categoryId to it.categoryName }
            .map { (key, list) ->
                val sum = list.sumOf { it.record.amountPaise }
                val pct = if (totalAmount > 0L) (sum.toDouble() / totalAmount.toDouble()) * 100.0 else 0.0
                CategoryBreakdown(
                    categoryId = key.first,
                    categoryName = key.second,
                    amountPaise = sum,
                    percentage = pct,
                    colorHex = list.first().categoryColorHex
                )
            }
            .sortedByDescending { it.amountPaise }
    }

    fun computeMonthlyTrend(
        anchorYear: Int,
        anchorMonth: Int,
        monthsCount: Int,
        scopedTransactions: List<TransactionDisplay>
    ): List<MonthlyComparisonPoint> {
        val result = mutableListOf<MonthlyComparisonPoint>()
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, anchorYear)
            set(Calendar.MONTH, anchorMonth - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -(monthsCount - 1))
        }
        repeat(monthsCount) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val (s, e) = CurrencyFormatter.monthBoundsMillis(y, m)
            val slice = scopedTransactions.filter { it.record.dateMillis in s..e }
            val inc = slice.filter { it.record.type == TransactionType.INCOME }.sumOf { it.record.amountPaise }
            val exp = slice.filter { it.record.type == TransactionType.EXPENSE }.sumOf { it.record.amountPaise }
            result.add(
                MonthlyComparisonPoint(
                    year = y,
                    month = m,
                    label = CurrencyFormatter.formatShortMonth(m),
                    incomePaise = inc,
                    expensePaise = exp
                )
            )
            cal.add(Calendar.MONTH, 1)
        }
        return result
    }

    fun computeDailyActivity(
        year: Int,
        month: Int,
        monthTransactions: List<TransactionDisplay>
    ): List<DailyActivityPoint> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayIncome = LongArray(maxDays + 1)
        val dayExpense = LongArray(maxDays + 1)

        val itemCal = Calendar.getInstance()
        for (tx in monthTransactions) {
            itemCal.timeInMillis = tx.record.dateMillis
            val d = itemCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, maxDays)
            when (tx.record.type) {
                TransactionType.INCOME -> dayIncome[d] += tx.record.amountPaise
                TransactionType.EXPENSE -> dayExpense[d] += tx.record.amountPaise
                TransactionType.TRANSFER -> Unit
            }
        }

        return (1..maxDays).map { day ->
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, 12)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            DailyActivityPoint(
                dayOfMonth = day,
                dateMillis = cal.timeInMillis,
                incomePaise = dayIncome[day],
                expensePaise = dayExpense[day]
            )
        }
    }

    fun filterTransactions(
        transactions: List<TransactionDisplay>,
        filter: TransactionFilterState
    ): List<TransactionDisplay> {
        val query = filter.searchQuery.trim().lowercase()
        val targetWs = filter.workspaceFilter.toWorkspaceOrNull()

        val filtered = transactions.filter { item ->
            val r = item.record
            val matchesWs = targetWs == null || r.workspace == targetWs
            val matchesType = filter.typeFilter == null || r.type == filter.typeFilter
            val matchesCat = filter.categoryId == null || r.categoryId == filter.categoryId
            val matchesAcc = filter.accountId == null ||
                r.accountId == filter.accountId ||
                r.toAccountId == filter.accountId
            val matchesStart = filter.startDateMillis == null || r.dateMillis >= filter.startDateMillis
            val matchesEnd = filter.endDateMillis == null || r.dateMillis <= filter.endDateMillis
            val matchesMin = filter.minAmountPaise == null || r.amountPaise >= filter.minAmountPaise
            val matchesMax = filter.maxAmountPaise == null || r.amountPaise <= filter.maxAmountPaise
            val matchesQuery = query.isEmpty() ||
                item.categoryName.lowercase().contains(query) ||
                item.accountName.lowercase().contains(query) ||
                (item.toAccountName?.lowercase()?.contains(query) == true) ||
                r.description.lowercase().contains(query) ||
                (r.amountPaise / 100L).toString().contains(query)

            matchesWs && matchesType && matchesCat && matchesAcc &&
                matchesStart && matchesEnd && matchesMin && matchesMax && matchesQuery
        }

        return when (filter.sortOption) {
            SortOption.NEWEST -> filtered.sortedWith(compareByDescending<TransactionDisplay> { it.record.dateMillis }.thenByDescending { it.record.id })
            SortOption.OLDEST -> filtered.sortedWith(compareBy<TransactionDisplay> { it.record.dateMillis }.thenBy { it.record.id })
            SortOption.HIGHEST_AMOUNT -> filtered.sortedByDescending { it.record.amountPaise }
            SortOption.LOWEST_AMOUNT -> filtered.sortedBy { it.record.amountPaise }
        }
    }

    fun computeBudgetProgress(
        budgets: List<Budget>,
        transactions: List<TransactionRecord>,
        categories: List<Category>
    ): List<BudgetWithProgress> {
        val categoryMap = categories.associateBy { it.id }
        return budgets.map { budget ->
            val (start, end) = CurrencyFormatter.monthBoundsMillis(budget.year, budget.month)
            val spent = transactions.asSequence()
                .filter {
                    it.workspace == budget.workspace &&
                        it.type == TransactionType.EXPENSE &&
                        it.dateMillis in start..end &&
                        (budget.categoryId == null || it.categoryId == budget.categoryId)
                }
                .sumOf { it.amountPaise }

            val pct = if (budget.limitPaise > 0L) {
                (spent.toDouble() / budget.limitPaise.toDouble()) * 100.0
            } else {
                0.0
            }

            val alert = when {
                pct >= 100.0 -> BudgetAlertLevel.EXCEEDED_100
                pct >= 90.0 -> BudgetAlertLevel.ALERT_90
                pct >= 75.0 -> BudgetAlertLevel.WARNING_75
                else -> BudgetAlertLevel.SAFE
            }

            val label = if (budget.categoryId == null) {
                "Overall Monthly Budget"
            } else {
                categoryMap[budget.categoryId]?.name ?: "Category Budget"
            }

            BudgetWithProgress(
                budget = budget,
                categoryName = label,
                spentPaise = spent,
                utilizationPercent = pct,
                alertLevel = alert
            )
        }
    }

    fun computeReportData(
        workspaceFilter: WorkspaceFilter,
        startMillis: Long,
        endMillis: Long,
        allTransactions: List<TransactionDisplay>
    ): ReportData {
        val targetWs = workspaceFilter.toWorkspaceOrNull()
        val scoped = allTransactions.filter { item ->
            val matchesWs = targetWs == null || item.record.workspace == targetWs
            val matchesDate = item.record.dateMillis in startMillis..endMillis
            matchesWs && matchesDate
        }.sortedByDescending { it.record.dateMillis }

        val incomeList = scoped.filter { it.record.type == TransactionType.INCOME }
        val expenseList = scoped.filter { it.record.type == TransactionType.EXPENSE }

        val totalIncome = incomeList.sumOf { it.record.amountPaise }
        val totalExpense = expenseList.sumOf { it.record.amountPaise }
        val netSavings = totalIncome - totalExpense
        val savingsPct = if (totalIncome > 0L) (netSavings.toDouble() / totalIncome.toDouble()) * 100.0 else 0.0

        val daysCount = max(1L, TimeUnit.MILLISECONDS.toDays(endMillis - startMillis) + 1L)
        val avgDailyExpense = totalExpense / daysCount

        val dayBuckets = expenseList.groupBy { tx ->
            CurrencyFormatter.dayBoundsMillis(tx.record.dateMillis).first
        }.mapValues { (_, list) -> list.sumOf { it.record.amountPaise } }

        val highestDayEntry = dayBuckets.maxByOrNull { it.value }

        val incomeBreakdown = computeCategoryBreakdown(incomeList, totalIncome)
        val expenseBreakdown = computeCategoryBreakdown(expenseList, totalExpense)
        val highestExpenseCat = expenseBreakdown.firstOrNull()

        val paymentMethodBreakdown = AccountType.entries.mapNotNull { type ->
            val inc = incomeList.filter { it.accountType == type }.sumOf { it.record.amountPaise }
            val exp = expenseList.filter { it.accountType == type }.sumOf { it.record.amountPaise }
            if (inc == 0L && exp == 0L) null
            else PaymentMethodBreakdown(
                accountType = type,
                label = type.label,
                incomePaise = inc,
                expensePaise = exp
            )
        }

        val monthlyComparison = buildMonthRangeComparison(startMillis, endMillis, scoped)

        return ReportData(
            startMillis = startMillis,
            endMillis = endMillis,
            totalIncomePaise = totalIncome,
            totalExpensePaise = totalExpense,
            netSavingsPaise = netSavings,
            savingsPercentage = savingsPct,
            averageDailyExpensePaise = avgDailyExpense,
            highestSpendingDayMillis = highestDayEntry?.key,
            highestSpendingDayPaise = highestDayEntry?.value ?: 0L,
            highestExpenseCategory = highestExpenseCat,
            incomeBreakdown = incomeBreakdown,
            expenseBreakdown = expenseBreakdown,
            paymentMethodBreakdown = paymentMethodBreakdown,
            monthlyComparison = monthlyComparison,
            transactions = scoped
        )
    }

    private fun buildMonthRangeComparison(
        startMillis: Long,
        endMillis: Long,
        scoped: List<TransactionDisplay>
    ): List<MonthlyComparisonPoint> {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = startMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = endMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val points = mutableListOf<MonthlyComparisonPoint>()
        var guard = 0
        while (!startCal.after(endCal) && guard < 36) {
            val y = startCal.get(Calendar.YEAR)
            val m = startCal.get(Calendar.MONTH) + 1
            val (mStart, mEnd) = CurrencyFormatter.monthBoundsMillis(y, m)
            val slice = scoped.filter { it.record.dateMillis in mStart..mEnd }
            val inc = slice.filter { it.record.type == TransactionType.INCOME }.sumOf { it.record.amountPaise }
            val exp = slice.filter { it.record.type == TransactionType.EXPENSE }.sumOf { it.record.amountPaise }
            points.add(
                MonthlyComparisonPoint(
                    year = y,
                    month = m,
                    label = CurrencyFormatter.formatShortMonth(m),
                    incomePaise = inc,
                    expensePaise = exp
                )
            )
            startCal.add(Calendar.MONTH, 1)
            guard++
        }
        return points
    }
}
