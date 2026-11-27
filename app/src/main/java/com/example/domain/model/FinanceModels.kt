package com.example.domain.model

enum class Workspace(val id: String, val defaultDisplayName: String) {
    PERSONAL("PERSONAL", "Personal"),
    GVVS("GVVS", "GVVS Business");

    companion object {
        fun fromId(id: String): Workspace = entries.firstOrNull { it.id == id } ?: PERSONAL
    }
}

enum class WorkspaceFilter(val label: String) {
    ALL("All Finances"),
    PERSONAL("Personal"),
    GVVS("GVVS Business");

    fun toWorkspaceOrNull(): Workspace? = when (this) {
        ALL -> null
        PERSONAL -> Workspace.PERSONAL
        GVVS -> Workspace.GVVS
    }
}

enum class TransactionType(val id: String, val label: String) {
    INCOME("INCOME", "Income"),
    EXPENSE("EXPENSE", "Expense"),
    TRANSFER("TRANSFER", "Transfer");

    companion object {
        fun fromId(id: String): TransactionType = entries.firstOrNull { it.id == id } ?: EXPENSE
    }
}

enum class AccountType(val id: String, val label: String) {
    CASH("CASH", "Cash"),
    BANK("BANK", "Bank Account"),
    UPI("UPI", "UPI / Digital Wallet"),
    OTHER("OTHER", "Other");

    companion object {
        fun fromId(id: String): AccountType = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

enum class LoanType(val id: String, val label: String) {
    LENT("LENT", "Money Lent"),
    BORROWED("BORROWED", "Money Borrowed");

    companion object {
        fun fromId(id: String): LoanType = entries.firstOrNull { it.id == id } ?: LENT
    }
}

enum class LoanStatus(val label: String) {
    PENDING("Pending"),
    PARTIALLY_PAID("Partially Paid"),
    COMPLETED("Completed")
}

enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("SYSTEM", "System Default"),
    LIGHT("LIGHT", "Light Mode"),
    DARK("DARK", "Dark Mode");

    companion object {
        fun fromId(id: String): ThemeMode = entries.firstOrNull { it.id == id } ?: LIGHT
    }
}

enum class DateFormatOption(val id: String, val pattern: String, val label: String) {
    DD_MM_YYYY("DD_MM_YYYY", "dd/MM/yyyy", "DD/MM/YYYY"),
    DD_MMM_YYYY("DD_MMM_YYYY", "dd MMM yyyy", "DD MMM YYYY"),
    YYYY_MM_DD("YYYY_MM_DD", "yyyy-MM-dd", "YYYY-MM-DD");

    companion object {
        fun fromId(id: String): DateFormatOption = entries.firstOrNull { it.id == id } ?: DD_MM_YYYY
    }
}

enum class SortOption(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    HIGHEST_AMOUNT("Highest Amount"),
    LOWEST_AMOUNT("Lowest Amount")
}

enum class ReportPeriodMode(val label: String) {
    MONTHLY("Monthly"),
    CALENDAR_YEAR("Jan–Dec Year"),
    FINANCIAL_YEAR("Apr–Mar FY"),
    CUSTOM("Custom Range")
}

enum class BudgetAlertLevel {
    SAFE,
    WARNING_75,
    ALERT_90,
    EXCEEDED_100
}

data class Account(
    val id: Long = 0L,
    val name: String,
    val workspace: Workspace,
    val type: AccountType,
    val openingBalancePaise: Long = 0L,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val syncId: String = ""
)

data class AccountWithBalance(
    val account: Account,
    val currentBalancePaise: Long,
    val totalDepositsPaise: Long,
    val totalWithdrawalsPaise: Long
)

data class Category(
    val id: Long = 0L,
    val name: String,
    val workspace: Workspace,
    val type: TransactionType,
    val colorHex: Long = 0xFF167B59,
    val iconName: String = "category",
    val isDefault: Boolean = false,
    val syncId: String = ""
)

data class TransactionRecord(
    val id: Long = 0L,
    val workspace: Workspace,
    val type: TransactionType,
    val amountPaise: Long,
    val dateMillis: Long,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long? = null,
    val description: String = "",
    val receiptPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncId: String = ""
)

data class TransactionDisplay(
    val record: TransactionRecord,
    val categoryName: String,
    val categoryColorHex: Long,
    val categoryIconName: String,
    val accountName: String,
    val accountType: AccountType,
    val toAccountName: String? = null,
    val toAccountType: AccountType? = null
)

data class Budget(
    val id: Long = 0L,
    val workspace: Workspace,
    val categoryId: Long?, // null means overall workspace monthly expense budget
    val month: Int, // 1..12
    val year: Int,
    val limitPaise: Long,
    val syncId: String = ""
)

data class BudgetWithProgress(
    val budget: Budget,
    val categoryName: String,
    val spentPaise: Long,
    val utilizationPercent: Double,
    val alertLevel: BudgetAlertLevel
)

data class SavingsGoal(
    val id: Long = 0L,
    val workspace: Workspace,
    val name: String,
    val isEmergencyFund: Boolean = false,
    val targetPaise: Long,
    val currentSavedPaise: Long,
    val monthlyTargetPaise: Long = 0L,
    val syncId: String = ""
)

data class LoanRepayment(
    val id: Long = 0L,
    val loanId: Long,
    val amountPaise: Long,
    val dateMillis: Long,
    val accountId: Long,
    val notes: String = "",
    val syncId: String = ""
)

data class LoanRecord(
    val id: Long = 0L,
    val workspace: Workspace,
    val type: LoanType,
    val personName: String,
    val principalPaise: Long,
    val repaidPaise: Long = 0L,
    val accountId: Long,
    val dateMillis: Long,
    val expectedRepaymentDateMillis: Long? = null,
    val notes: String = "",
    val syncId: String = ""
) {
    val remainingPaise: Long
        get() = (principalPaise - repaidPaise).coerceAtLeast(0L)

    val status: LoanStatus
        get() = when {
            repaidPaise <= 0L -> LoanStatus.PENDING
            repaidPaise >= principalPaise -> LoanStatus.COMPLETED
            else -> LoanStatus.PARTIALLY_PAID
        }
}

data class AppPreferences(
    val personalWorkspaceName: String = "Personal",
    val businessWorkspaceName: String = "GVVS Business",
    val currencySymbol: String = "₹",
    val currencyCode: String = "INR",
    val dateFormat: DateFormatOption = DateFormatOption.DD_MM_YYYY,
    val financialYearStartMonth: Int = 4, // April
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 1, // 0 = immediate on background, -1 = never
    val lastBackupTimestamp: Long = 0L,
    val backupReminderDismissedAt: Long = 0L,
    val backupReminderDays: Int = 7
) {
    val isPinEnabled: Boolean
        get() = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()

    fun workspaceLabel(workspace: Workspace): String = when (workspace) {
        Workspace.PERSONAL -> personalWorkspaceName.ifBlank { Workspace.PERSONAL.defaultDisplayName }
        Workspace.GVVS -> businessWorkspaceName.ifBlank { Workspace.GVVS.defaultDisplayName }
    }
}

data class CategoryBreakdown(
    val categoryId: Long?,
    val categoryName: String,
    val amountPaise: Long,
    val percentage: Double,
    val colorHex: Long
)

data class PaymentMethodBreakdown(
    val accountType: AccountType,
    val label: String,
    val incomePaise: Long,
    val expensePaise: Long
)

data class MonthlyComparisonPoint(
    val year: Int,
    val month: Int, // 1..12
    val label: String,
    val incomePaise: Long,
    val expensePaise: Long
)

data class DailyActivityPoint(
    val dayOfMonth: Int,
    val dateMillis: Long,
    val incomePaise: Long,
    val expensePaise: Long
) {
    val netPaise: Long get() = incomePaise - expensePaise
}

data class DashboardSummary(
    val monthlyIncomePaise: Long,
    val monthlyExpensePaise: Long,
    val netSavingsPaise: Long,
    val savingsPercentage: Double,
    val previousMonthIncomePaise: Long,
    val previousMonthExpensePaise: Long,
    val incomeChangePercent: Double?,
    val expenseChangePercent: Double?,
    val cashBalancePaise: Long,
    val bankBalancePaise: Long,
    val upiBalancePaise: Long,
    val otherBalancePaise: Long,
    val expenseCategoryBreakdown: List<CategoryBreakdown>,
    val monthlyTrend: List<MonthlyComparisonPoint>,
    val dailyActivity: List<DailyActivityPoint>
)

data class ReportData(
    val startMillis: Long,
    val endMillis: Long,
    val totalIncomePaise: Long,
    val totalExpensePaise: Long,
    val netSavingsPaise: Long,
    val savingsPercentage: Double,
    val averageDailyExpensePaise: Long,
    val highestSpendingDayMillis: Long?,
    val highestSpendingDayPaise: Long,
    val highestExpenseCategory: CategoryBreakdown?,
    val incomeBreakdown: List<CategoryBreakdown>,
    val expenseBreakdown: List<CategoryBreakdown>,
    val paymentMethodBreakdown: List<PaymentMethodBreakdown>,
    val monthlyComparison: List<MonthlyComparisonPoint>,
    val transactions: List<TransactionDisplay>
)

data class TransactionFilterState(
    val searchQuery: String = "",
    val workspaceFilter: WorkspaceFilter = WorkspaceFilter.ALL,
    val typeFilter: TransactionType? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val minAmountPaise: Long? = null,
    val maxAmountPaise: Long? = null,
    val sortOption: SortOption = SortOption.NEWEST
)
