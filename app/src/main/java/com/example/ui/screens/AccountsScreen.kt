package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.export.CurrencyFormatter
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.AccountWithBalance
import com.example.domain.model.AppPreferences
import com.example.domain.model.LoanRecord
import com.example.domain.model.LoanRepayment
import com.example.domain.model.LoanStatus
import com.example.domain.model.LoanType
import com.example.domain.model.TransactionDisplay
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceFilter
import com.example.ui.components.AccountDropdown
import com.example.ui.components.BabbalCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.TransactionRowCard
import com.example.ui.components.WorkspaceBadge
import com.example.ui.components.WorkspaceSelectorRow
import com.example.ui.components.accountTypeIcon
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferBlue
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    accountsWithBalance: List<AccountWithBalance>,
    allTransactions: List<TransactionDisplay>,
    loans: List<LoanRecord>,
    loanRepayments: List<LoanRepayment>,
    preferences: AppPreferences,
    initialLoansTab: Boolean = false,
    onQuickTransfer: (Workspace) -> Unit,
    onQuickDepositWithdraw: (TransactionType, Workspace) -> Unit,
    onAddAccount: (String, Workspace, AccountType, Long) -> Unit,
    onUpdateAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onAddLoan: (Workspace, LoanType, String, Long, Long, Long, Long?, String) -> Unit,
    onRecordRepayment: (Long, Long, Long, Long, String) -> Unit,
    onDeleteLoan: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLoansTab by remember(initialLoansTab) { mutableStateOf(initialLoansTab) }
    var wsFilter by remember { mutableStateOf(WorkspaceFilter.ALL) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var historyAccount by remember { mutableStateOf<AccountWithBalance?>(null) }
    var deletingAccount by remember { mutableStateOf<Account?>(null) }

    var showAddLoanDialog by remember { mutableStateOf(false) }
    var repayingLoan by remember { mutableStateOf<LoanRecord?>(null) }
    var deletingLoan by remember { mutableStateOf<LoanRecord?>(null) }

    val sym = preferences.currencySymbol
    val targetWs = wsFilter.toWorkspaceOrNull()

    val scopedAccounts = remember(accountsWithBalance, targetWs) {
        if (targetWs == null) accountsWithBalance else accountsWithBalance.filter { it.account.workspace == targetWs }
    }
    val scopedLoans = remember(loans, targetWs) {
        if (targetWs == null) loans else loans.filter { it.workspace == targetWs }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !showLoansTab,
                    onClick = { showLoansTab = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
                    modifier = Modifier.testTag("tab_accounts")
                ) {
                    Text("Accounts & Transfers")
                }
                SegmentedButton(
                    selected = showLoansTab,
                    onClick = { showLoansTab = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Filled.Handshake, contentDescription = null) },
                    modifier = Modifier.testTag("tab_lending")
                ) {
                    Text("Lending & Borrowing")
                }
            }

            WorkspaceSelectorRow(
                selected = wsFilter,
                preferences = preferences,
                onSelect = { wsFilter = it }
            )
        }

        if (!showLoansTab) {
            val totalNetWorth = scopedAccounts.sumOf { it.currentBalancePaise }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    BabbalCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Total Account Balance",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.formatPaise(totalNetWorth, sym),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onQuickTransfer(targetWs ?: Workspace.PERSONAL) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Transfer")
                            }
                            OutlinedButton(
                                onClick = { showAddAccountDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Account")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onQuickDepositWithdraw(TransactionType.INCOME, targetWs ?: Workspace.PERSONAL) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Deposit (Income)")
                            }
                            OutlinedButton(
                                onClick = { onQuickDepositWithdraw(TransactionType.EXPENSE, targetWs ?: Workspace.PERSONAL) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Withdraw (Expense)")
                            }
                        }
                    }
                }

                items(scopedAccounts, key = { it.account.id }) { item ->
                    val acc = item.account
                    BabbalCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { historyAccount = item }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = accountTypeIcon(acc.type),
                                    contentDescription = acc.type.label,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(acc.name, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    WorkspaceBadge(acc.workspace, preferences.workspaceLabel(acc.workspace))
                                }
                                Text(
                                    text = "Opening: ${CurrencyFormatter.formatPaise(acc.openingBalancePaise, sym)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyFormatter.formatPaise(item.currentBalancePaise, sym),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (item.currentBalancePaise >= 0) MaterialTheme.colorScheme.onSurface else ExpenseRed
                                )
                                Row {
                                    IconButton(
                                        onClick = { historyAccount = item },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.History, contentDescription = "Account history", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { editingAccount = acc },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit account", modifier = Modifier.size(16.dp))
                                    }
                                    if (!acc.isDefault) {
                                        IconButton(
                                            onClick = { deletingAccount = acc },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete account", tint = ExpenseRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Deposits: ${CurrencyFormatter.formatPaise(item.totalDepositsPaise, sym)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = IncomeGreen
                            )
                            Text(
                                text = "Withdrawals: ${CurrencyFormatter.formatPaise(item.totalWithdrawalsPaise, sym)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseRed
                            )
                        }
                    }
                }
            }
        } else {
            // Lending & Borrowing View
            val totalLentOut = scopedLoans.filter { it.type == LoanType.LENT }.sumOf { it.remainingPaise }
            val totalBorrowedOut = scopedLoans.filter { it.type == LoanType.BORROWED }.sumOf { it.remainingPaise }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BabbalCard(modifier = Modifier.weight(1f)) {
                            Text("To Receive (Lent)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyFormatter.formatPaise(totalLentOut, sym),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = IncomeGreen
                            )
                        }
                        BabbalCard(modifier = Modifier.weight(1f)) {
                            Text("To Repay (Borrowed)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyFormatter.formatPaise(totalBorrowedOut, sym),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = ExpenseRed
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = { showAddLoanDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_loan_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Lent / Borrowed Money")
                    }
                }

                if (scopedLoans.isEmpty()) {
                    item {
                        BabbalCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No lending or borrowing records yet.",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Track money lent to friends or borrowed funds without distorting your monthly income or expense totals.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(scopedLoans, key = { it.id }) { loan ->
                        val statusColor = when (loan.status) {
                            LoanStatus.PENDING -> ExpenseRed
                            LoanStatus.PARTIALLY_PAID -> WarningAmber
                            LoanStatus.COMPLETED -> IncomeGreen
                        }
                        val reps = loanRepayments.filter { it.loanId == loan.id }

                        BabbalCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(loan.personName, style = MaterialTheme.typography.titleMedium)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        WorkspaceBadge(loan.workspace, preferences.workspaceLabel(loan.workspace))
                                    }
                                    Text(
                                        text = "${loan.type.label} • ${CurrencyFormatter.formatDate(loan.dateMillis, preferences.dateFormat)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = statusColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = loan.status.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyFormatter.formatPaise(loan.principalPaise, sym), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                                Column {
                                    Text("Repaid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyFormatter.formatPaise(loan.repaidPaise, sym), style = MaterialTheme.typography.bodyMedium, color = IncomeGreen)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyFormatter.formatPaise(loan.remainingPaise, sym), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = statusColor)
                                }
                            }

                            loan.expectedRepaymentDateMillis?.let { due ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Expected repayment: ${CurrencyFormatter.formatDate(due, preferences.dateFormat)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (loan.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = loan.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (reps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(6.dp))
                                reps.take(3).forEach { rep ->
                                    Text(
                                        text = "• Repaid ${CurrencyFormatter.formatPaise(rep.amountPaise, sym)} on ${CurrencyFormatter.formatDate(rep.dateMillis, preferences.dateFormat)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { deletingLoan = loan }) {
                                    Text("Delete", color = ExpenseRed)
                                }
                                if (loan.remainingPaise > 0L) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = { repayingLoan = loan }) {
                                        Text("Record Repayment")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddAccountDialog || editingAccount != null) {
        AccountEditorDialog(
            existing = editingAccount,
            defaultWorkspace = targetWs ?: Workspace.PERSONAL,
            preferences = preferences,
            onSave = { name, ws, type, openingPaise ->
                if (editingAccount != null) {
                    onUpdateAccount(
                        editingAccount!!.copy(
                            name = name,
                            workspace = ws,
                            type = type,
                            openingBalancePaise = openingPaise
                        )
                    )
                } else {
                    onAddAccount(name, ws, type, openingPaise)
                }
                showAddAccountDialog = false
                editingAccount = null
            },
            onDismiss = {
                showAddAccountDialog = false
                editingAccount = null
            }
        )
    }

    historyAccount?.let { accBal ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val accTx = remember(allTransactions, accBal.account.id) {
            allTransactions.filter {
                it.record.accountId == accBal.account.id || it.record.toAccountId == accBal.account.id
            }
        }
        ModalBottomSheet(onDismissRequest = { historyAccount = null }, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${accBal.account.name} History", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Current Balance: ${CurrencyFormatter.formatPaise(accBal.currentBalancePaise, sym)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (accTx.isEmpty()) {
                    Text("No transactions for this account yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(accTx, key = { it.record.id }) { item ->
                            TransactionRowCard(
                                item = item,
                                preferences = preferences,
                                onEdit = {},
                                onDelete = {}
                            )
                        }
                    }
                }
            }
        }
    }

    deletingAccount?.let { acc ->
        ConfirmDeleteDialog(
            title = "Delete Account?",
            message = "Delete ${acc.name}? Accounts with existing transactions cannot be deleted.",
            onConfirm = {
                onDeleteAccount(acc)
                deletingAccount = null
            },
            onDismiss = { deletingAccount = null }
        )
    }

    if (showAddLoanDialog) {
        AddLoanDialog(
            accounts = accountsWithBalance.map { it.account },
            defaultWorkspace = targetWs ?: Workspace.PERSONAL,
            preferences = preferences,
            onSave = { ws, type, person, amount, accId, date, due, notes ->
                onAddLoan(ws, type, person, amount, accId, date, due, notes)
                showAddLoanDialog = false
            },
            onDismiss = { showAddLoanDialog = false }
        )
    }

    repayingLoan?.let { loan ->
        RecordRepaymentDialog(
            loan = loan,
            accounts = accountsWithBalance.map { it.account }.filter { it.workspace == loan.workspace },
            preferences = preferences,
            onSave = { amount, accId, date, notes ->
                onRecordRepayment(loan.id, amount, accId, date, notes)
                repayingLoan = null
            },
            onDismiss = { repayingLoan = null }
        )
    }

    deletingLoan?.let { loan ->
        ConfirmDeleteDialog(
            title = "Delete Loan Record?",
            message = "Permanently remove this ${loan.type.label.lowercase()} record with ${loan.personName}?",
            onConfirm = {
                onDeleteLoan(loan.id)
                deletingLoan = null
            },
            onDismiss = { deletingLoan = null }
        )
    }
}

@Composable
private fun AccountEditorDialog(
    existing: Account?,
    defaultWorkspace: Workspace,
    preferences: AppPreferences,
    onSave: (String, Workspace, AccountType, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var workspace by remember { mutableStateOf(existing?.workspace ?: defaultWorkspace) }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.BANK) }
    var openingText by remember {
        mutableStateOf(existing?.let { CurrencyFormatter.paiseToEditableString(it.openingBalancePaise) } ?: "0")
    }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Account" else "New Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; err = null },
                    label = { Text("Account Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openingText,
                    onValueChange = { openingText = it; err = null },
                    label = { Text("Opening Balance (${preferences.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existing == null) {
                    Text("Workspace", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Workspace.entries.forEach { ws ->
                            FilterChip(
                                selected = workspace == ws,
                                onClick = { workspace = ws },
                                label = { Text(preferences.workspaceLabel(ws)) }
                            )
                        }
                    }
                    Text("Type", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountType.entries.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(t.name) }
                            )
                        }
                    }
                }
                if (err != null) {
                    Text(err!!, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val paise = CurrencyFormatter.parseRupeesToPaise(openingText.ifBlank { "0" })
                    if (name.trim().isEmpty()) {
                        err = "Enter account name."
                    } else if (paise == null) {
                        err = "Enter a valid opening balance."
                    } else {
                        onSave(name.trim(), workspace, type, paise)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLoanDialog(
    accounts: List<Account>,
    defaultWorkspace: Workspace,
    preferences: AppPreferences,
    onSave: (Workspace, LoanType, String, Long, Long, Long, Long?, String) -> Unit,
    onDismiss: () -> Unit
) {
    var workspace by remember { mutableStateOf(defaultWorkspace) }
    var type by remember { mutableStateOf(LoanType.LENT) }
    var personName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var dueMillis by remember { mutableStateOf<Long?>(null) }
    var pickingDue by remember { mutableStateOf(false) }

    val wsAccounts = remember(accounts, workspace) { accounts.filter { it.workspace == workspace } }
    var accountId by remember(wsAccounts) { mutableStateOf(wsAccounts.firstOrNull()?.id ?: 0L) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Lending / Borrowing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoanType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Workspace.entries.forEach { ws ->
                        FilterChip(
                            selected = workspace == ws,
                            onClick = { workspace = ws },
                            label = { Text(preferences.workspaceLabel(ws)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it; err = null },
                    label = { Text("Person's Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = null },
                    label = { Text("Amount (${preferences.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AccountDropdown(
                    label = "Account",
                    accounts = wsAccounts,
                    selectedId = accountId,
                    onSelect = { accountId = it }
                )
                OutlinedButton(
                    onClick = { pickingDue = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        dueMillis?.let { "Expected Due: ${CurrencyFormatter.formatDate(it, preferences.dateFormat)}" }
                            ?: "Set Expected Repayment Date (Optional)"
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
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
                    val paise = CurrencyFormatter.parseRupeesToPaise(amountText)
                    if (personName.trim().isEmpty()) err = "Enter person's name."
                    else if (paise == null || paise <= 0L) err = "Enter a valid amount."
                    else if (accountId == 0L) err = "Select an account."
                    else onSave(workspace, type, personName.trim(), paise, accountId, dateMillis, dueMillis, notes)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (pickingDue) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickingDue = false },
            confirmButton = {
                TextButton(onClick = {
                    dueMillis = state.selectedDateMillis
                    pickingDue = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDue = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun RecordRepaymentDialog(
    loan: LoanRecord,
    accounts: List<Account>,
    preferences: AppPreferences,
    onSave: (Long, Long, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(CurrencyFormatter.paiseToEditableString(loan.remainingPaise)) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull { it.id == loan.accountId }?.id ?: accounts.firstOrNull()?.id ?: 0L) }
    var notes by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Repayment (${loan.personName})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Remaining balance: ${CurrencyFormatter.formatPaise(loan.remainingPaise, preferences.currencySymbol)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = null },
                    label = { Text("Repayment Amount (${preferences.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AccountDropdown(
                    label = "Account",
                    accounts = accounts,
                    selectedId = accountId,
                    onSelect = { accountId = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
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
                    val paise = CurrencyFormatter.parseRupeesToPaise(amountText)
                    if (paise == null || paise <= 0L) err = "Enter a valid amount."
                    else if (paise > loan.remainingPaise) err = "Cannot exceed remaining balance."
                    else if (accountId == 0L) err = "Select an account."
                    else onSave(paise, accountId, System.currentTimeMillis(), notes)
                }
            ) { Text("Record") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
