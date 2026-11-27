package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.AndroidKeystoreCryptoEngine
import com.example.data.crypto.ReceiptStorageManager
import com.example.data.export.BackupManager
import com.example.data.export.CurrencyFormatter
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.remote.CloudAuthManager
import com.example.data.remote.CloudSyncStatus
import com.example.data.remote.FirestoreSyncManager
import com.example.data.repository.AccountingCalculator
import com.example.data.repository.FinanceRepository
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.AccountWithBalance
import com.example.domain.model.AppPreferences
import com.example.domain.model.BudgetWithProgress
import com.example.domain.model.Category
import com.example.domain.model.DateFormatOption
import com.example.domain.model.LoanRecord
import com.example.domain.model.LoanRepayment
import com.example.domain.model.LoanType
import com.example.domain.model.ReportData
import com.example.domain.model.ReportPeriodMode
import com.example.domain.model.SavingsGoal
import com.example.domain.model.ThemeMode
import com.example.domain.model.TransactionDisplay
import com.example.domain.model.TransactionFilterState
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceFilter
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class ReportConfig(
    val mode: ReportPeriodMode = ReportPeriodMode.MONTHLY,
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val customStartMillis: Long = CurrencyFormatter.monthBoundsMillis(
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH) + 1
    ).first,
    val customEndMillis: Long = CurrencyFormatter.monthBoundsMillis(
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH) + 1
    ).second,
    val workspaceFilter: WorkspaceFilter = WorkspaceFilter.ALL
)

class BabbalViewModel(
    val repository: FinanceRepository,
    val backupManager: BackupManager,
    private val authManager: CloudAuthManager,
    private val syncManager: FirestoreSyncManager
) : ViewModel() {

    private val nowCal = Calendar.getInstance()
    private val _selectedMonth = MutableStateFlow(nowCal.get(Calendar.YEAR) to (nowCal.get(Calendar.MONTH) + 1))
    val selectedMonth: StateFlow<Pair<Int, Int>> = _selectedMonth.asStateFlow()

    private val _workspaceFilter = MutableStateFlow(WorkspaceFilter.ALL)
    val workspaceFilter: StateFlow<WorkspaceFilter> = _workspaceFilter.asStateFlow()

    private val _filterState = MutableStateFlow(TransactionFilterState())
    val filterState: StateFlow<TransactionFilterState> = _filterState.asStateFlow()

    private val _reportConfig = MutableStateFlow(ReportConfig())
    val reportConfig: StateFlow<ReportConfig> = _reportConfig.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _offlineModeBypass = MutableStateFlow(false)
    val offlineModeBypass: StateFlow<Boolean> = _offlineModeBypass.asStateFlow()

    private var lastBackgroundTimestamp: Long = 0L
    private var initialLockEvaluated = false
    private var autoSignInAttempted = false

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages: SharedFlow<String> = _uiMessages.asSharedFlow()

    val currentUser: StateFlow<FirebaseUser?> = authManager.authStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = authManager.currentUser
    )

    val cloudSyncStatus: StateFlow<CloudSyncStatus> = syncManager.syncStatusFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CloudSyncStatus()
    )

    val preferences: StateFlow<AppPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppPreferences()
    )

    val accountsWithBalance: StateFlow<List<AccountWithBalance>> = repository.accountBalancesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categories: StateFlow<List<Category>> = repository.categoriesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTransactionDisplays: StateFlow<List<TransactionDisplay>> = repository.transactionDisplaysFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredTransactions: StateFlow<List<TransactionDisplay>> = combine(
        allTransactionDisplays,
        _filterState
    ) { all, filter ->
        AccountingCalculator.filterTransactions(all, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentTransactions: StateFlow<List<TransactionDisplay>> = combine(
        allTransactionDisplays,
        _workspaceFilter
    ) { all, wsFilter ->
        val ws = wsFilter.toWorkspaceOrNull()
        val scoped = if (ws == null) all else all.filter { it.record.workspace == ws }
        scoped.take(8)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val dashboardSummary = combine(
        _workspaceFilter,
        _selectedMonth,
        allTransactionDisplays,
        accountsWithBalance
    ) { wsFilter, (year, month), txs, accounts ->
        AccountingCalculator.computeDashboardSummary(wsFilter, year, month, txs, accounts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountingCalculator.computeDashboardSummary(
            WorkspaceFilter.ALL,
            nowCal.get(Calendar.YEAR),
            nowCal.get(Calendar.MONTH) + 1,
            emptyList(),
            emptyList()
        )
    )

    val budgetsWithProgress: StateFlow<List<BudgetWithProgress>> = repository.budgetsWithProgressFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val savingsGoals: StateFlow<List<SavingsGoal>> = repository.savingsGoalsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val loans: StateFlow<List<LoanRecord>> = repository.loansFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val loanRepayments: StateFlow<List<LoanRepayment>> = repository.loanRepaymentsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val reportData: StateFlow<ReportData> = combine(
        _reportConfig,
        allTransactionDisplays,
        preferences
    ) { cfg, txs, prefs ->
        val (start, end) = resolveReportBounds(cfg, prefs.financialYearStartMonth)
        AccountingCalculator.computeReportData(cfg.workspaceFilter, start, end, txs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountingCalculator.computeReportData(
            WorkspaceFilter.ALL,
            CurrencyFormatter.monthBoundsMillis(nowCal.get(Calendar.YEAR), nowCal.get(Calendar.MONTH) + 1).first,
            CurrencyFormatter.monthBoundsMillis(nowCal.get(Calendar.YEAR), nowCal.get(Calendar.MONTH) + 1).second,
            emptyList()
        )
    )

    val showBackupReminder: StateFlow<Boolean> = combine(
        preferences,
        allTransactionDisplays,
        currentUser
    ) { prefs, txs, user ->
        if (user != null) return@combine false
        if (txs.size < 3) return@combine false
        val now = System.currentTimeMillis()
        val thresholdMillis = TimeUnit.DAYS.toMillis(prefs.backupReminderDays.toLong().coerceAtLeast(1L))
        val sinceBackup = now - prefs.lastBackupTimestamp
        val sinceDismissed = now - prefs.backupReminderDismissedAt
        sinceBackup > thresholdMillis && sinceDismissed > TimeUnit.DAYS.toMillis(2L)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    init {
        repository.cloudSyncBridge = syncManager

        viewModelScope.launch {
            repository.ensureInitialized()
            val prefs = repository.preferencesFlow.first()
            if (!initialLockEvaluated) {
                initialLockEvaluated = true
                if (prefs.isPinEnabled) {
                    _isLocked.value = true
                }
            }
        }

        viewModelScope.launch {
            currentUser.collect { user ->
                repository.ensureInitialized()
                if (user != null) {
                    _offlineModeBypass.value = false
                    _authError.value = null
                    syncManager.startSync(user)
                } else {
                    syncManager.stopSync()
                }
            }
        }
    }

    fun attemptAutoSignIn(activityContext: Context) {
        if (autoSignInAttempted || currentUser.value != null) return
        autoSignInAttempted = true
        viewModelScope.launch {
            authManager.attemptAutoSignIn(activityContext)
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _isSigningIn.value = true
            _authError.value = null
            authManager.signInWithGoogle(activityContext)
                .onSuccess { user ->
                    _offlineModeBypass.value = false
                    _uiMessages.tryEmit("Signed in as ${user.email ?: "Google User"} — PC & Phone sync active")
                }
                .onFailure { err ->
                    val msg = err.message ?: "Google Sign-In failed"
                    _authError.value = msg
                    _uiMessages.tryEmit(msg)
                }
            _isSigningIn.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            syncManager.stopSync()
            authManager.signOut()
            _offlineModeBypass.value = false
            _uiMessages.tryEmit("Signed out of cloud sync")
        }
    }

    fun continueOffline() {
        _offlineModeBypass.value = true
        _authError.value = null
    }

    fun reopenSignInScreen() {
        _offlineModeBypass.value = false
        _authError.value = null
    }

    fun triggerManualSync() {
        val user = currentUser.value
        if (user == null) {
            _uiMessages.tryEmit("Sign in with Google to sync across PC & phone")
            return
        }
        syncManager.triggerManualSync(user)
        _uiMessages.tryEmit("Syncing with cloud…")
    }

    fun setWorkspaceFilter(filter: WorkspaceFilter) {
        _workspaceFilter.value = filter
    }

    fun stepSelectedMonth(deltaMonths: Int) {
        val (y, m) = _selectedMonth.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        _selectedMonth.value = cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }

    fun setSelectedMonth(year: Int, month: Int) {
        _selectedMonth.value = year to month.coerceIn(1, 12)
    }

    fun updateFilterState(transform: (TransactionFilterState) -> TransactionFilterState) {
        _filterState.value = transform(_filterState.value)
    }

    fun resetFilters() {
        _filterState.value = TransactionFilterState()
    }

    fun updateReportConfig(transform: (ReportConfig) -> ReportConfig) {
        _reportConfig.value = transform(_reportConfig.value)
    }

    fun resolveReportBounds(cfg: ReportConfig, fyStartMonth: Int): Pair<Long, Long> {
        return when (cfg.mode) {
            ReportPeriodMode.MONTHLY -> CurrencyFormatter.monthBoundsMillis(cfg.year, cfg.month)
            ReportPeriodMode.CALENDAR_YEAR -> {
                val s = CurrencyFormatter.monthBoundsMillis(cfg.year, 1).first
                val e = CurrencyFormatter.monthBoundsMillis(cfg.year, 12).second
                s to e
            }
            ReportPeriodMode.FINANCIAL_YEAR -> {
                val startMonth = fyStartMonth.coerceIn(1, 12)
                val s = CurrencyFormatter.monthBoundsMillis(cfg.year, startMonth).first
                val endCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, cfg.year)
                    set(Calendar.MONTH, startMonth - 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, 11)
                }
                val e = CurrencyFormatter.monthBoundsMillis(
                    endCal.get(Calendar.YEAR),
                    endCal.get(Calendar.MONTH) + 1
                ).second
                s to e
            }
            ReportPeriodMode.CUSTOM -> {
                val s = CurrencyFormatter.dayBoundsMillis(minOf(cfg.customStartMillis, cfg.customEndMillis)).first
                val e = CurrencyFormatter.dayBoundsMillis(maxOf(cfg.customStartMillis, cfg.customEndMillis)).second
                s to e
            }
        }
    }

    fun saveTransaction(
        existingId: Long? = null,
        workspace: Workspace,
        type: TransactionType,
        amountPaise: Long,
        dateMillis: Long,
        categoryId: Long?,
        accountId: Long,
        toAccountId: Long? = null,
        description: String,
        newReceiptUri: Uri? = null,
        existingReceiptPath: String? = null,
        removeExistingReceipt: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveTransaction(
                existingId = existingId,
                workspace = workspace,
                type = type,
                amountPaise = amountPaise,
                dateMillis = dateMillis,
                categoryId = categoryId,
                accountId = accountId,
                toAccountId = toAccountId,
                description = description,
                newReceiptUri = newReceiptUri,
                existingReceiptPath = existingReceiptPath,
                removeExistingReceipt = removeExistingReceipt
            ).onSuccess {
                _uiMessages.tryEmit("${type.label} saved")
                onSuccess()
            }.onFailure { err ->
                _uiMessages.tryEmit(err.message ?: "Failed to save transaction")
            }
        }
    }

    fun deleteTransaction(tx: TransactionRecord) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
            _uiMessages.tryEmit("Transaction deleted")
        }
    }

    fun addAccount(name: String, workspace: Workspace, type: AccountType, openingBalancePaise: Long) {
        viewModelScope.launch {
            repository.addAccount(name, workspace, type, openingBalancePaise)
                .onSuccess { _uiMessages.tryEmit("Account created") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Could not create account") }
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch {
            repository.updateAccount(account)
                .onSuccess { _uiMessages.tryEmit("Account updated") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Could not update account") }
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            repository.deleteAccount(account)
                .onSuccess { _uiMessages.tryEmit("Account deleted") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Could not delete account") }
        }
    }

    fun addCategory(name: String, workspace: Workspace, type: TransactionType, colorHex: Long) {
        viewModelScope.launch {
            repository.addCategory(name, workspace, type, colorHex)
                .onSuccess { _uiMessages.tryEmit("Category added") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Could not add category") }
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
                .onSuccess { _uiMessages.tryEmit("Category updated") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Could not update category") }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
                .onSuccess { _uiMessages.tryEmit("Category deleted") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Cannot delete category in use") }
        }
    }

    fun saveBudget(existingId: Long? = null, workspace: Workspace, categoryId: Long?, month: Int, year: Int, limitPaise: Long) {
        viewModelScope.launch {
            repository.saveBudget(existingId, workspace, categoryId, month, year, limitPaise)
                .onSuccess { _uiMessages.tryEmit("Budget saved") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Invalid budget") }
        }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            repository.deleteBudget(id)
            _uiMessages.tryEmit("Budget removed")
        }
    }

    fun saveSavingsGoal(
        existingId: Long? = null,
        workspace: Workspace,
        name: String,
        isEmergencyFund: Boolean,
        targetPaise: Long,
        currentSavedPaise: Long,
        monthlyTargetPaise: Long
    ) {
        viewModelScope.launch {
            repository.saveSavingsGoal(existingId, workspace, name, isEmergencyFund, targetPaise, currentSavedPaise, monthlyTargetPaise)
                .onSuccess { _uiMessages.tryEmit("Savings goal saved") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Invalid savings goal") }
        }
    }

    fun deleteSavingsGoal(id: Long) {
        viewModelScope.launch {
            repository.deleteSavingsGoal(id)
            _uiMessages.tryEmit("Savings goal removed")
        }
    }

    fun addLoan(
        workspace: Workspace,
        type: LoanType,
        personName: String,
        principalPaise: Long,
        accountId: Long,
        dateMillis: Long,
        expectedRepaymentDateMillis: Long?,
        notes: String
    ) {
        viewModelScope.launch {
            repository.addLoan(workspace, type, personName, principalPaise, accountId, dateMillis, expectedRepaymentDateMillis, notes)
                .onSuccess { _uiMessages.tryEmit("${type.label} recorded") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Could not save loan") }
        }
    }

    fun recordLoanRepayment(loanId: Long, amountPaise: Long, accountId: Long, dateMillis: Long, notes: String) {
        viewModelScope.launch {
            repository.recordLoanRepayment(loanId, amountPaise, accountId, dateMillis, notes)
                .onSuccess { _uiMessages.tryEmit("Repayment recorded") }
                .onFailure { _uiMessages.tryEmit(it.message ?: "Could not record repayment") }
        }
    }

    fun deleteLoan(loanId: Long) {
        viewModelScope.launch {
            repository.deleteLoan(loanId)
            _uiMessages.tryEmit("Record deleted")
        }
    }

    // --- Settings & Security ---

    private suspend fun pushSyncedPreferences() {
        if (currentUser.value != null) {
            syncManager.pushPreferencesUpdate(repository.preferencesRepository.getSnapshot())
        }
    }

    fun updateProfileNames(personal: String, business: String) {
        viewModelScope.launch {
            repository.preferencesRepository.updateProfileNames(personal, business)
            pushSyncedPreferences()
            _uiMessages.tryEmit("Workspace names updated")
        }
    }

    fun updateCurrency(symbol: String, code: String) {
        viewModelScope.launch {
            repository.preferencesRepository.updateCurrency(symbol, code)
            pushSyncedPreferences()
            _uiMessages.tryEmit("Currency updated")
        }
    }

    fun updateDateFormat(option: DateFormatOption) {
        viewModelScope.launch {
            repository.preferencesRepository.updateDateFormat(option)
            pushSyncedPreferences()
        }
    }

    fun updateFinancialYearStartMonth(month: Int) {
        viewModelScope.launch {
            repository.preferencesRepository.updateFinancialYearStartMonth(month)
            pushSyncedPreferences()
            _uiMessages.tryEmit("Financial year setting updated")
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.preferencesRepository.updateThemeMode(mode)
            pushSyncedPreferences()
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            repository.preferencesRepository.setPin(pin)
            _uiMessages.tryEmit("PIN lock enabled")
        }
    }

    fun clearPin() {
        viewModelScope.launch {
            repository.preferencesRepository.clearPin()
            _isLocked.value = false
            _uiMessages.tryEmit("PIN lock disabled")
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setBiometricEnabled(enabled)
        }
    }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.preferencesRepository.setAutoLockMinutes(minutes)
        }
    }

    fun dismissBackupReminder() {
        viewModelScope.launch {
            repository.preferencesRepository.dismissBackupReminder()
        }
    }

    suspend fun verifyAndUnlockPin(pin: String): Boolean {
        val ok = repository.preferencesRepository.verifyPin(pin)
        if (ok) {
            _isLocked.value = false
        }
        return ok
    }

    fun unlockWithBiometric() {
        _isLocked.value = false
    }

    fun lockNow() {
        if (preferences.value.isPinEnabled) {
            _isLocked.value = true
        }
    }

    fun onAppBackgrounded() {
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        val prefs = preferences.value
        if (!prefs.isPinEnabled || prefs.autoLockMinutes < 0) return
        if (lastBackgroundTimestamp == 0L) return
        val elapsedMillis = System.currentTimeMillis() - lastBackgroundTimestamp
        val thresholdMillis = prefs.autoLockMinutes * 60_000L
        if (elapsedMillis >= thresholdMillis) {
            _isLocked.value = true
        }
    }

    fun notifyMessage(msg: String) {
        _uiMessages.tryEmit(msg)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            val crypto = AndroidKeystoreCryptoEngine()
            val db = AppDatabase.getInstance(appCtx)
            val prefs = PreferencesRepository(appCtx, crypto)
            val receipts = ReceiptStorageManager(appCtx, crypto)
            val authManager = CloudAuthManager(appCtx)
            val syncManager = FirestoreSyncManager(appCtx, db, prefs, crypto)
            val repo = FinanceRepository(db, prefs, crypto, receipts, syncManager)
            val backup = BackupManager(db, prefs, crypto)
            return BabbalViewModel(repo, backup, authManager, syncManager) as T
        }
    }
}
