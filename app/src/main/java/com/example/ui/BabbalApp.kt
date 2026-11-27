package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.SyncState
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import com.example.ui.components.EncryptedReceiptViewerDialog
import com.example.ui.components.TransactionFormSheet
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.AuthSyncScreen
import com.example.ui.screens.BudgetScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MoreAndSettingsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.findActivityContext
import com.example.ui.security.SecurityLockScreen
import com.example.ui.theme.BabbalTheme
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen

enum class BabbalTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    TRANSACTIONS("Ledger", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    REPORTS("Reports", Icons.Filled.Assessment, Icons.Outlined.Assessment),
    ACCOUNTS("Accounts", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    BUDGET("Budget", Icons.Filled.Savings, Icons.Outlined.Savings),
    MORE("More", Icons.Filled.Menu, Icons.Outlined.Menu)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabbalApp(viewModel: BabbalViewModel) {
    val context = LocalContext.current
    val activityCtx = remember(context) { context.findActivityContext() }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val offlineBypass by viewModel.offlineModeBypass.collectAsStateWithLifecycle()

    val workspaceFilter by viewModel.workspaceFilter.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val recentTransactions by viewModel.recentTransactions.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactionDisplays.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val accountsWithBalance by viewModel.accountsWithBalance.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val budgetsWithProgress by viewModel.budgetsWithProgress.collectAsStateWithLifecycle()
    val savingsGoals by viewModel.savingsGoals.collectAsStateWithLifecycle()
    val loans by viewModel.loans.collectAsStateWithLifecycle()
    val loanRepayments by viewModel.loanRepayments.collectAsStateWithLifecycle()
    val reportConfig by viewModel.reportConfig.collectAsStateWithLifecycle()
    val reportData by viewModel.reportData.collectAsStateWithLifecycle()
    val showBackupReminder by viewModel.showBackupReminder.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(BabbalTab.DASHBOARD) }
    var openCalendarInitially by remember { mutableStateOf(false) }
    var openLoansInitially by remember { mutableStateOf(false) }

    var showTxSheet by remember { mutableStateOf(false) }
    var txSheetInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var txSheetInitialWs by remember { mutableStateOf(Workspace.PERSONAL) }
    var editingTx by remember { mutableStateOf<TransactionRecord?>(null) }
    var viewingReceiptPath by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.attemptAutoSignIn(activityCtx)
    }

    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (currentTab != BabbalTab.DASHBOARD) {
        BackHandler {
            currentTab = BabbalTab.DASHBOARD
        }
    }

    BabbalTheme(themeMode = preferences.themeMode) {
        if (isLocked && preferences.isPinEnabled) {
            SecurityLockScreen(
                biometricEnabled = preferences.biometricEnabled,
                onVerifyPin = { pin -> viewModel.verifyAndUnlockPin(pin) },
                onBiometricUnlocked = { viewModel.unlockWithBiometric() }
            )
        } else if (currentUser == null && !offlineBypass) {
            AuthSyncScreen(
                isLoading = isSigningIn,
                errorMessage = authError,
                onGoogleSignIn = { actCtx -> viewModel.signInWithGoogle(actCtx) },
                onContinueOffline = viewModel::continueOffline
            )
        } else {
            key(currentUser?.uid ?: "offline") {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    val compactTabs = listOf(
                        BabbalTab.DASHBOARD,
                        BabbalTab.TRANSACTIONS,
                        BabbalTab.REPORTS,
                        BabbalTab.ACCOUNTS,
                        BabbalTab.MORE
                    )
                    val railTabs = BabbalTab.entries

                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = when (currentTab) {
                                            BabbalTab.DASHBOARD -> "BABBAL Finance"
                                            BabbalTab.TRANSACTIONS -> "Transactions & Calendar"
                                            BabbalTab.REPORTS -> "Financial Reports"
                                            BabbalTab.ACCOUNTS -> "Accounts & Lending"
                                            BabbalTab.BUDGET -> "Budgets & Goals"
                                            BabbalTab.MORE -> "More & Settings"
                                        },
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                actions = {
                                    val syncColor = when {
                                        currentUser == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                        cloudSyncStatus.syncState == SyncState.SYNCED -> IncomeGreen
                                        cloudSyncStatus.syncState == SyncState.SYNCING -> EmeraldPrimary
                                        cloudSyncStatus.syncState == SyncState.ERROR -> ExpenseRed
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    val syncLabel = when {
                                        currentUser == null -> "Offline"
                                        cloudSyncStatus.syncState == SyncState.SYNCED -> "Synced"
                                        cloudSyncStatus.syncState == SyncState.SYNCING -> "Syncing"
                                        cloudSyncStatus.syncState == SyncState.OFFLINE -> "Queued"
                                        cloudSyncStatus.syncState == SyncState.ERROR -> "Retry"
                                        else -> "Sync"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = syncColor.copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .clickable {
                                                if (currentUser != null) {
                                                    viewModel.triggerManualSync()
                                                } else {
                                                    viewModel.reopenSignInScreen()
                                                }
                                            }
                                            .testTag("topbar_sync_status")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    currentUser == null -> Icons.Filled.CloudOff
                                                    cloudSyncStatus.syncState == SyncState.SYNCING -> Icons.Filled.Sync
                                                    else -> Icons.Filled.CloudDone
                                                },
                                                contentDescription = "Cloud Sync Status: $syncLabel",
                                                tint = syncColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = syncLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = syncColor
                                            )
                                        }
                                    }

                                    if (!useRail && currentTab != BabbalTab.BUDGET) {
                                        IconButton(
                                            onClick = { currentTab = BabbalTab.BUDGET },
                                            modifier = Modifier.testTag("topbar_budget_button")
                                        ) {
                                            Icon(Icons.Outlined.Savings, contentDescription = "Budgets & Goals")
                                        }
                                    }
                                    if (currentTab != BabbalTab.MORE) {
                                        IconButton(
                                            onClick = { currentTab = BabbalTab.MORE },
                                            modifier = Modifier.testTag("topbar_settings_button")
                                        ) {
                                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        },
                    floatingActionButton = {
                        if (currentTab == BabbalTab.DASHBOARD || currentTab == BabbalTab.TRANSACTIONS) {
                            FloatingActionButton(
                                onClick = {
                                    editingTx = null
                                    txSheetInitialType = TransactionType.EXPENSE
                                    txSheetInitialWs = workspaceFilter.toWorkspaceOrNull() ?: Workspace.PERSONAL
                                    showTxSheet = true
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("fab_add_transaction")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                            }
                        }
                    },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar(windowInsets = WindowInsets.navigationBars) {
                                compactTabs.forEach { tab ->
                                    val selected = currentTab == tab ||
                                        (tab == BabbalTab.MORE && currentTab == BabbalTab.BUDGET)
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            openCalendarInitially = false
                                            openLoansInitially = false
                                            currentTab = tab
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useRail) {
                            NavigationRail {
                                railTabs.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            openCalendarInitially = false
                                            openLoansInitially = false
                                            currentTab = tab
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val contentModifier = Modifier
                                .widthIn(max = 1040.dp)
                                .fillMaxSize()

                            when (currentTab) {
                                BabbalTab.DASHBOARD -> DashboardScreen(
                                    workspaceFilter = workspaceFilter,
                                    selectedYear = selectedMonth.first,
                                    selectedMonth = selectedMonth.second,
                                    summary = dashboardSummary,
                                    recentTransactions = recentTransactions,
                                    budgetsWithProgress = budgetsWithProgress,
                                    showBackupReminder = showBackupReminder,
                                    preferences = preferences,
                                    onSelectWorkspace = viewModel::setWorkspaceFilter,
                                    onStepMonth = viewModel::stepSelectedMonth,
                                    onQuickAdd = { type ->
                                        editingTx = null
                                        txSheetInitialType = type
                                        txSheetInitialWs = workspaceFilter.toWorkspaceOrNull() ?: Workspace.PERSONAL
                                        showTxSheet = true
                                    },
                                    onEditTransaction = { tx ->
                                        editingTx = tx
                                        showTxSheet = true
                                    },
                                    onDeleteTransaction = viewModel::deleteTransaction,
                                    onViewReceipt = { viewingReceiptPath = it },
                                    onOpenTransactions = {
                                        openCalendarInitially = false
                                        currentTab = BabbalTab.TRANSACTIONS
                                    },
                                    onOpenBudgets = { currentTab = BabbalTab.BUDGET },
                                    onOpenBackup = { currentTab = BabbalTab.MORE },
                                    onDismissBackupReminder = viewModel::dismissBackupReminder,
                                    modifier = contentModifier
                                )

                                BabbalTab.TRANSACTIONS -> TransactionsScreen(
                                    transactions = filteredTransactions,
                                    allTransactions = allTransactions,
                                    filterState = filterState,
                                    accounts = accountsWithBalance.map { it.account },
                                    categories = categories,
                                    preferences = preferences,
                                    initialCalendarMode = openCalendarInitially,
                                    onUpdateFilter = viewModel::updateFilterState,
                                    onResetFilters = viewModel::resetFilters,
                                    onEditTransaction = { tx ->
                                        editingTx = tx
                                        showTxSheet = true
                                    },
                                    onDeleteTransaction = viewModel::deleteTransaction,
                                    onViewReceipt = { viewingReceiptPath = it },
                                    modifier = contentModifier
                                )

                                BabbalTab.REPORTS -> ReportsScreen(
                                    reportConfig = reportConfig,
                                    reportData = reportData,
                                    preferences = preferences,
                                    onUpdateConfig = viewModel::updateReportConfig,
                                    onNotify = viewModel::notifyMessage,
                                    modifier = contentModifier
                                )

                                BabbalTab.ACCOUNTS -> AccountsScreen(
                                    accountsWithBalance = accountsWithBalance,
                                    allTransactions = allTransactions,
                                    loans = loans,
                                    loanRepayments = loanRepayments,
                                    preferences = preferences,
                                    initialLoansTab = openLoansInitially,
                                    onQuickTransfer = { ws ->
                                        editingTx = null
                                        txSheetInitialType = TransactionType.TRANSFER
                                        txSheetInitialWs = ws
                                        showTxSheet = true
                                    },
                                    onQuickDepositWithdraw = { type, ws ->
                                        editingTx = null
                                        txSheetInitialType = type
                                        txSheetInitialWs = ws
                                        showTxSheet = true
                                    },
                                    onAddAccount = viewModel::addAccount,
                                    onUpdateAccount = viewModel::updateAccount,
                                    onDeleteAccount = viewModel::deleteAccount,
                                    onAddLoan = viewModel::addLoan,
                                    onRecordRepayment = viewModel::recordLoanRepayment,
                                    onDeleteLoan = viewModel::deleteLoan,
                                    modifier = contentModifier
                                )

                                BabbalTab.BUDGET -> BudgetScreen(
                                    selectedYear = selectedMonth.first,
                                    selectedMonth = selectedMonth.second,
                                    budgetsWithProgress = budgetsWithProgress,
                                    savingsGoals = savingsGoals,
                                    categories = categories,
                                    preferences = preferences,
                                    onStepMonth = viewModel::stepSelectedMonth,
                                    onSaveBudget = viewModel::saveBudget,
                                    onDeleteBudget = viewModel::deleteBudget,
                                    onSaveGoal = viewModel::saveSavingsGoal,
                                    onDeleteGoal = viewModel::deleteSavingsGoal,
                                    modifier = contentModifier
                                )

                                BabbalTab.MORE -> MoreAndSettingsScreen(
                                    preferences = preferences,
                                    categories = categories,
                                    backupManager = viewModel.backupManager,
                                    currentUserEmail = currentUser?.email,
                                    currentUserName = currentUser?.displayName,
                                    cloudSyncStatus = cloudSyncStatus,
                                    isSigningIn = isSigningIn,
                                    onGoogleSignIn = { actCtx -> viewModel.signInWithGoogle(actCtx) },
                                    onSignOut = viewModel::signOut,
                                    onSyncNow = viewModel::triggerManualSync,
                                    onNavigateToBudgets = { currentTab = BabbalTab.BUDGET },
                                    onNavigateToLoans = {
                                        openLoansInitially = true
                                        currentTab = BabbalTab.ACCOUNTS
                                    },
                                    onNavigateToCalendar = {
                                        openCalendarInitially = true
                                        currentTab = BabbalTab.TRANSACTIONS
                                    },
                                    onNavigateToReports = { currentTab = BabbalTab.REPORTS },
                                    onUpdateProfileNames = viewModel::updateProfileNames,
                                    onUpdateCurrency = viewModel::updateCurrency,
                                    onUpdateDateFormat = viewModel::updateDateFormat,
                                    onUpdateFyStartMonth = viewModel::updateFinancialYearStartMonth,
                                    onUpdateThemeMode = viewModel::updateThemeMode,
                                    onAddCategory = viewModel::addCategory,
                                    onDeleteCategory = viewModel::deleteCategory,
                                    onSetPin = viewModel::setPin,
                                    onClearPin = viewModel::clearPin,
                                    onSetBiometric = viewModel::setBiometricEnabled,
                                    onSetAutoLockMinutes = viewModel::setAutoLockMinutes,
                                    onLockNow = viewModel::lockNow,
                                    onNotify = viewModel::notifyMessage,
                                    modifier = contentModifier
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showTxSheet) {
        TransactionFormSheet(
            initialType = txSheetInitialType,
            initialWorkspace = txSheetInitialWs,
            editingRecord = editingTx,
            accounts = accountsWithBalance.map { it.account },
            categories = categories,
            preferences = preferences,
            receiptStorageManager = viewModel.repository.receiptStorageManager,
            onAddCustomCategory = viewModel::addCategory,
            onSave = { existingId, ws, type, amt, date, catId, accId, toAccId, desc, newUri, oldPath, removeOld ->
                viewModel.saveTransaction(
                    existingId = existingId,
                    workspace = ws,
                    type = type,
                    amountPaise = amt,
                    dateMillis = date,
                    categoryId = catId,
                    accountId = accId,
                    toAccountId = toAccId,
                    description = desc,
                    newReceiptUri = newUri,
                    existingReceiptPath = oldPath,
                    removeExistingReceipt = removeOld,
                    onSuccess = {
                        showTxSheet = false
                        editingTx = null
                    }
                )
            },
            onDismiss = {
                showTxSheet = false
                editingTx = null
            }
        )
    }

    viewingReceiptPath?.let { path ->
        EncryptedReceiptViewerDialog(
            receiptPath = path,
            receiptStorageManager = viewModel.repository.receiptStorageManager,
            onDismiss = { viewingReceiptPath = null }
        )
    }
}
