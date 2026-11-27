package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.export.CurrencyFormatter
import com.example.domain.model.AppPreferences
import com.example.domain.model.BudgetAlertLevel
import com.example.domain.model.BudgetWithProgress
import com.example.domain.model.DashboardSummary
import com.example.domain.model.TransactionDisplay
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.WorkspaceFilter
import com.example.ui.components.BabbalCard
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DailyActivityChart
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.MonthSelectorBar
import com.example.ui.components.MonthlyTrendLineChart
import com.example.ui.components.TransactionRowCard
import com.example.ui.components.WorkspaceSelectorRow
import com.example.ui.theme.AlertOrange
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryDark
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferBlue
import com.example.ui.theme.WarningAmber
import java.util.Locale

@Composable
fun DashboardScreen(
    workspaceFilter: WorkspaceFilter,
    selectedYear: Int,
    selectedMonth: Int,
    summary: DashboardSummary,
    recentTransactions: List<TransactionDisplay>,
    budgetsWithProgress: List<BudgetWithProgress>,
    showBackupReminder: Boolean,
    preferences: AppPreferences,
    onSelectWorkspace: (WorkspaceFilter) -> Unit,
    onStepMonth: (Int) -> Unit,
    onQuickAdd: (TransactionType) -> Unit,
    onEditTransaction: (TransactionRecord) -> Unit,
    onDeleteTransaction: (TransactionRecord) -> Unit,
    onViewReceipt: (String) -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenBackup: () -> Unit,
    onDismissBackupReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<TransactionRecord?>(null) }
    val sym = preferences.currencySymbol

    val activeAlerts = remember(budgetsWithProgress, selectedYear, selectedMonth, workspaceFilter) {
        val targetWs = workspaceFilter.toWorkspaceOrNull()
        budgetsWithProgress.filter {
            it.budget.year == selectedYear &&
                it.budget.month == selectedMonth &&
                (targetWs == null || it.budget.workspace == targetWs) &&
                it.alertLevel != BudgetAlertLevel.SAFE
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Workspace & Month Controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WorkspaceSelectorRow(
                    selected = workspaceFilter,
                    preferences = preferences,
                    onSelect = onSelectWorkspace
                )
                MonthSelectorBar(
                    year = selectedYear,
                    month = selectedMonth,
                    onPrevious = { onStepMonth(-1) },
                    onNext = { onStepMonth(1) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Backup Reminder Banner
        if (showBackupReminder) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Backup Recommended",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Data is stored only on this device. Export a JSON backup safely.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = onOpenBackup) { Text("Backup") }
                        TextButton(onClick = onDismissBackupReminder) { Text("Later") }
                    }
                }
            }
        }

        // Budget Threshold Alerts (75%, 90%, 100%)
        if (activeAlerts.isNotEmpty()) {
            item {
                val worst = activeAlerts.maxBy { it.utilizationPercent }
                val alertColor = when (worst.alertLevel) {
                    BudgetAlertLevel.EXCEEDED_100 -> ExpenseRed
                    BudgetAlertLevel.ALERT_90 -> AlertOrange
                    else -> WarningAmber
                }
                Card(
                    onClick = onOpenBudgets,
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = alertColor.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, alertColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = alertColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Budget Alert (${String.format(Locale.US, "%.0f%%", worst.utilizationPercent)})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = alertColor
                            )
                            Text(
                                text = "${worst.categoryName}: ${CurrencyFormatter.formatPaise(worst.spentPaise, sym)} of ${CurrencyFormatter.formatPaise(worst.budget.limitPaise, sym)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "View",
                            style = MaterialTheme.typography.labelLarge,
                            color = alertColor
                        )
                    }
                }
            }
        }

        // Hero Financial Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = EmeraldPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(EmeraldPrimaryDark, EmeraldPrimary)
                            )
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_finance_banner),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.14f)
                    )
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Net Savings / Surplus",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = CurrencyFormatter.formatPaise(summary.netSavingsPaise, sym),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = Color.White
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "Save ${String.format(Locale.US, "%.0f%%", summary.savingsPercentage)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HeroMetricPill(
                                label = "Monthly Income",
                                amount = CurrencyFormatter.formatPaise(summary.monthlyIncomePaise, sym),
                                deltaPercent = summary.incomeChangePercent,
                                isIncome = true,
                                modifier = Modifier.weight(1f)
                            )
                            HeroMetricPill(
                                label = "Monthly Expenses",
                                amount = CurrencyFormatter.formatPaise(summary.monthlyExpensePaise, sym),
                                deltaPercent = summary.expenseChangePercent,
                                isIncome = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Quick Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onQuickAdd(TransactionType.INCOME) },
                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_income")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Income", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = { onQuickAdd(TransactionType.EXPENSE) },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_expense")
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Expense", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { onQuickAdd(TransactionType.TRANSFER) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_transfer_money")
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Transfer", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Account Balances Strip: Cash in Hand, Bank Balance, UPI Balance
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Liquidity & Balances",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BalanceMiniCard(
                        title = "Cash in Hand",
                        amount = CurrencyFormatter.formatPaise(summary.cashBalancePaise, sym),
                        icon = Icons.Filled.Payments,
                        accent = EmeraldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    BalanceMiniCard(
                        title = "Bank Balance",
                        amount = CurrencyFormatter.formatPaise(summary.bankBalancePaise, sym),
                        icon = Icons.Filled.AccountBalance,
                        accent = TransferBlue,
                        modifier = Modifier.weight(1f)
                    )
                    BalanceMiniCard(
                        title = "UPI Balance",
                        amount = CurrencyFormatter.formatPaise(summary.upiBalancePaise, sym),
                        icon = Icons.Filled.QrCode2,
                        accent = Color(0xFF0F766E),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Charts: Income vs Expense Bar Chart
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Income vs Expenses (6 Months)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                IncomeExpenseBarChart(
                    points = summary.monthlyTrend,
                    currencySymbol = sym
                )
            }
        }

        // Charts: Category-Wise Expense Donut
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Category-Wise Expenses",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                CategoryDonutChart(
                    breakdown = summary.expenseCategoryBreakdown,
                    totalAmountPaise = summary.monthlyExpensePaise,
                    currencySymbol = sym
                )
            }
        }

        // Charts: Monthly Spending Trend & Daily Activity
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Monthly Spending Trends",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                MonthlyTrendLineChart(points = summary.monthlyTrend)
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Daily Transaction Activity",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                DailyActivityChart(points = summary.dailyActivity)
            }
        }

        // Recent Transactions Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onOpenTransactions) {
                    Text("See All")
                }
            }
        }

        if (recentTransactions.isEmpty()) {
            item {
                BabbalCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No transactions recorded yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Tap Income, Expense, or Transfer above to start tracking your Personal or GVVS Business finances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentTransactions, key = { it.record.id }) { item ->
                TransactionRowCard(
                    item = item,
                    preferences = preferences,
                    onEdit = { onEditTransaction(item.record) },
                    onDelete = { pendingDelete = item.record },
                    onViewReceipt = item.record.receiptPath?.let { path ->
                        { onViewReceipt(path) }
                    }
                )
            }
        }
    }

    pendingDelete?.let { tx ->
        ConfirmDeleteDialog(
            title = "Delete Transaction?",
            message = "This will permanently remove this ${tx.type.label.lowercase()} record and update the account balance.",
            onConfirm = {
                onDeleteTransaction(tx)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun HeroMetricPill(
    label: String,
    amount: String,
    deltaPercent: Double?,
    isIncome: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.14f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isIncome) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (deltaPercent != null) {
                val sign = if (deltaPercent >= 0) "+" else ""
                Text(
                    text = "$sign${String.format(Locale.US, "%.0f%%", deltaPercent)} vs last mo",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun BalanceMiniCard(
    title: String,
    amount: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    BabbalCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
