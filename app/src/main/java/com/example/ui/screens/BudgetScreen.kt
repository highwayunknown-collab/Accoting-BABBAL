package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.export.CurrencyFormatter
import com.example.domain.model.AppPreferences
import com.example.domain.model.BudgetAlertLevel
import com.example.domain.model.BudgetWithProgress
import com.example.domain.model.Category
import com.example.domain.model.SavingsGoal
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import com.example.ui.components.BabbalCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.MonthSelectorBar
import com.example.ui.theme.AlertOrange
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.WarningAmber
import java.util.Locale

@Composable
fun BudgetScreen(
    selectedYear: Int,
    selectedMonth: Int,
    budgetsWithProgress: List<BudgetWithProgress>,
    savingsGoals: List<SavingsGoal>,
    categories: List<Category>,
    preferences: AppPreferences,
    onStepMonth: (Int) -> Unit,
    onSaveBudget: (Long?, Workspace, Long?, Int, Int, Long) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onSaveGoal: (Long?, Workspace, String, Boolean, Long, Long, Long) -> Unit,
    onDeleteGoal: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var workspace by remember { mutableStateOf(Workspace.PERSONAL) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<BudgetWithProgress?>(null) }
    var deletingBudgetId by remember { mutableStateOf<Long?>(null) }

    var showGoalDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var deletingGoalId by remember { mutableStateOf<Long?>(null) }

    val sym = preferences.currencySymbol
    val scopedBudgets = remember(budgetsWithProgress, workspace, selectedYear, selectedMonth) {
        budgetsWithProgress.filter {
            it.budget.workspace == workspace &&
                it.budget.year == selectedYear &&
                it.budget.month == selectedMonth
        }
    }
    val scopedGoals = remember(savingsGoals, workspace) {
        savingsGoals.filter { it.workspace == workspace }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Workspace.entries.forEach { ws ->
                    FilterChip(
                        selected = workspace == ws,
                        onClick = { workspace = ws },
                        label = { Text(preferences.workspaceLabel(ws)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            MonthSelectorBar(
                year = selectedYear,
                month = selectedMonth,
                onPrevious = { onStepMonth(-1) },
                onNext = { onStepMonth(1) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Budgets Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Expense Budgets", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = { showBudgetDialog = true },
                    modifier = Modifier.testTag("add_budget_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Set Budget")
                }
            }
        }

        if (scopedBudgets.isEmpty()) {
            item {
                BabbalCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No budgets set for ${preferences.workspaceLabel(workspace)} in ${CurrencyFormatter.formatMonthYear(selectedYear, selectedMonth)}.",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set an overall monthly limit or category budgets to receive alerts at 75%, 90%, and 100%.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(scopedBudgets, key = { it.budget.id }) { item ->
                val barColor = when (item.alertLevel) {
                    BudgetAlertLevel.EXCEEDED_100 -> ExpenseRed
                    BudgetAlertLevel.ALERT_90 -> AlertOrange
                    BudgetAlertLevel.WARNING_75 -> WarningAmber
                    BudgetAlertLevel.SAFE -> EmeraldPrimary
                }
                val alertTag = when (item.alertLevel) {
                    BudgetAlertLevel.EXCEEDED_100 -> "100%+ Exceeded"
                    BudgetAlertLevel.ALERT_90 -> "90% High Alert"
                    BudgetAlertLevel.WARNING_75 -> "75% Warning"
                    BudgetAlertLevel.SAFE -> "On Track"
                }

                BabbalCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.categoryName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${CurrencyFormatter.formatPaise(item.spentPaise, sym)} spent of ${CurrencyFormatter.formatPaise(item.budget.limitPaise, sym)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = barColor.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.alertLevel != BudgetAlertLevel.SAFE) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = barColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = "$alertTag (${String.format(Locale.US, "%.0f%%", item.utilizationPercent)})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = barColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (item.utilizationPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { editingBudget = item }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit budget", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { deletingBudgetId = item.budget.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete budget", tint = ExpenseRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Savings & Emergency Fund Targets Section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Savings & Emergency Targets", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = { showGoalDialog = true },
                    modifier = Modifier.testTag("add_savings_goal_button")
                ) {
                    Icon(Icons.Filled.Savings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Target")
                }
            }
        }

        if (scopedGoals.isEmpty()) {
            item {
                BabbalCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No savings targets yet", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create a monthly savings target or an emergency fund goal for ${preferences.workspaceLabel(workspace)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(scopedGoals, key = { it.id }) { goal ->
                val pct = if (goal.targetPaise > 0L) {
                    (goal.currentSavedPaise.toDouble() / goal.targetPaise.toDouble()) * 100.0
                } else 0.0

                BabbalCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (goal.isEmergencyFund) "${goal.name} (Emergency Fund)" else goal.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${CurrencyFormatter.formatPaise(goal.currentSavedPaise, sym)} / ${CurrencyFormatter.formatPaise(goal.targetPaise, sym)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (goal.monthlyTargetPaise > 0L) {
                                Text(
                                    text = "Monthly Target: ${CurrencyFormatter.formatPaise(goal.monthlyTargetPaise, sym)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IncomeGreen
                                )
                            }
                        }
                        Text(
                            text = String.format(Locale.US, "%.0f%%", pct),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = IncomeGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = IncomeGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { editingGoal = goal }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit savings goal", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { deletingGoalId = goal.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete savings goal", tint = ExpenseRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog || editingBudget != null) {
        BudgetEditorDialog(
            existing = editingBudget,
            workspace = workspace,
            categories = categories.filter { it.workspace == workspace && it.type == TransactionType.EXPENSE },
            preferences = preferences,
            onSave = { catId, limitPaise ->
                onSaveBudget(editingBudget?.budget?.id, workspace, catId, selectedMonth, selectedYear, limitPaise)
                showBudgetDialog = false
                editingBudget = null
            },
            onDismiss = {
                showBudgetDialog = false
                editingBudget = null
            }
        )
    }

    deletingBudgetId?.let { id ->
        ConfirmDeleteDialog(
            title = "Delete Budget?",
            message = "Remove this monthly budget limit?",
            onConfirm = {
                onDeleteBudget(id)
                deletingBudgetId = null
            },
            onDismiss = { deletingBudgetId = null }
        )
    }

    if (showGoalDialog || editingGoal != null) {
        SavingsGoalDialog(
            existing = editingGoal,
            preferences = preferences,
            onSave = { name, isEmergency, targetPaise, savedPaise, monthlyPaise ->
                onSaveGoal(editingGoal?.id, workspace, name, isEmergency, targetPaise, savedPaise, monthlyPaise)
                showGoalDialog = false
                editingGoal = null
            },
            onDismiss = {
                showGoalDialog = false
                editingGoal = null
            }
        )
    }

    deletingGoalId?.let { id ->
        ConfirmDeleteDialog(
            title = "Delete Savings Target?",
            message = "Remove this savings goal?",
            onConfirm = {
                onDeleteGoal(id)
                deletingGoalId = null
            },
            onDismiss = { deletingGoalId = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditorDialog(
    existing: BudgetWithProgress?,
    workspace: Workspace,
    categories: List<Category>,
    preferences: AppPreferences,
    onSave: (Long?, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategoryId by remember { mutableStateOf(existing?.budget?.categoryId) }
    var limitText by remember {
        mutableStateOf(existing?.let { CurrencyFormatter.paiseToEditableString(it.budget.limitPaise) } ?: "")
    }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Budget" else "Set Monthly Budget (${preferences.workspaceLabel(workspace)})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Scope", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text("Overall Monthly") }
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it; err = null },
                    label = { Text("Monthly Limit (${preferences.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) {
                    Text(err!!, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val paise = CurrencyFormatter.parseRupeesToPaise(limitText)
                    if (paise == null || paise <= 0L) err = "Enter a valid budget limit."
                    else onSave(selectedCategoryId, paise)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SavingsGoalDialog(
    existing: SavingsGoal?,
    preferences: AppPreferences,
    onSave: (String, Boolean, Long, Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var isEmergency by remember { mutableStateOf(existing?.isEmergencyFund ?: false) }
    var targetText by remember { mutableStateOf(existing?.let { CurrencyFormatter.paiseToEditableString(it.targetPaise) } ?: "") }
    var savedText by remember { mutableStateOf(existing?.let { CurrencyFormatter.paiseToEditableString(it.currentSavedPaise) } ?: "0") }
    var monthlyText by remember { mutableStateOf(existing?.let { CurrencyFormatter.paiseToEditableString(it.monthlyTargetPaise) } ?: "0") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Savings Target" else "New Savings Target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; err = null },
                    label = { Text("Goal Name (e.g., Emergency Fund)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isEmergency, onCheckedChange = { isEmergency = it })
                    Text("Mark as Emergency Fund Target", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it; err = null },
                    label = { Text("Target Amount (${preferences.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = savedText,
                    onValueChange = { savedText = it; err = null },
                    label = { Text("Current Saved (${preferences.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = monthlyText,
                    onValueChange = { monthlyText = it; err = null },
                    label = { Text("Monthly Target (${preferences.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) {
                    Text(err!!, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = CurrencyFormatter.parseRupeesToPaise(targetText)
                    val saved = CurrencyFormatter.parseRupeesToPaise(savedText.ifBlank { "0" }) ?: 0L
                    val monthly = CurrencyFormatter.parseRupeesToPaise(monthlyText.ifBlank { "0" }) ?: 0L
                    if (name.trim().isEmpty()) err = "Enter goal name."
                    else if (target == null || target <= 0L) err = "Enter a valid target amount."
                    else onSave(name.trim(), isEmergency, target, saved, monthly)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
