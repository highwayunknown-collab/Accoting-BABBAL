package com.example.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.example.data.crypto.CryptoEngine
import com.example.data.crypto.ReceiptStorageManager
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.DefaultSeeds
import com.example.data.local.LoanEntity
import com.example.data.local.LoanRepaymentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.SavingsGoalEntity
import com.example.data.local.TransactionEntity
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.AccountWithBalance
import com.example.domain.model.AppPreferences
import com.example.domain.model.Budget
import com.example.domain.model.BudgetWithProgress
import com.example.domain.model.Category
import com.example.domain.model.LoanRecord
import com.example.domain.model.LoanRepayment
import com.example.domain.model.LoanType
import com.example.domain.model.SavingsGoal
import com.example.domain.model.TransactionDisplay
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

interface CloudSyncBridge {
    fun onAccountSaved(account: AccountEntity)
    fun onAccountDeleted(syncId: String)
    fun onCategorySaved(category: CategoryEntity)
    fun onCategoryDeleted(syncId: String)
    fun onTransactionSaved(tx: TransactionEntity)
    fun onTransactionDeleted(syncId: String)
    fun onBudgetSaved(budget: BudgetEntity)
    fun onBudgetDeleted(syncId: String)
    fun onSavingsGoalSaved(goal: SavingsGoalEntity)
    fun onSavingsGoalDeleted(syncId: String)
    fun onLoanSaved(loan: LoanEntity)
    fun onLoanRepaymentSaved(repayment: LoanRepaymentEntity)
    fun onLoanDeleted(loanSyncId: String, repaymentSyncIds: List<String>)
}

class FinanceRepository(
    private val database: AppDatabase,
    val preferencesRepository: PreferencesRepository,
    val cryptoEngine: CryptoEngine,
    val receiptStorageManager: ReceiptStorageManager?,
    var cloudSyncBridge: CloudSyncBridge? = null
) {
    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val transactionDao = database.transactionDao()
    private val budgetDao = database.budgetDao()
    private val savingsGoalDao = database.savingsGoalDao()
    private val loanDao = database.loanDao()
    private val loanRepaymentDao = database.loanRepaymentDao()

    private val seedMutex = Mutex()
    @Volatile
    private var seeded = false

    suspend fun ensureInitialized() {
        if (seeded) return
        seedMutex.withLock {
            if (seeded) return
            if (accountDao.count() == 0) {
                accountDao.insertAll(DefaultSeeds.defaultAccounts())
            }
            if (categoryDao.count() == 0) {
                categoryDao.insertAll(DefaultSeeds.defaultCategories())
            }
            seeded = true
        }
    }

    val preferencesFlow: Flow<AppPreferences> = preferencesRepository.preferencesFlow

    val accountsFlow: Flow<List<Account>> = accountDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val categoriesFlow: Flow<List<Category>> = categoryDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val transactionsFlow: Flow<List<TransactionRecord>> = transactionDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val loansFlow: Flow<List<LoanRecord>> = loanDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val loanRepaymentsFlow: Flow<List<LoanRepayment>> = loanRepaymentDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val budgetsFlow: Flow<List<Budget>> = budgetDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val savingsGoalsFlow: Flow<List<SavingsGoal>> = savingsGoalDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val accountBalancesFlow: Flow<List<AccountWithBalance>> = combine(
        accountsFlow,
        transactionsFlow,
        loansFlow,
        loanRepaymentsFlow
    ) { accounts, transactions, loans, repayments ->
        AccountingCalculator.computeAccountBalances(accounts, transactions, loans, repayments)
    }

    val transactionDisplaysFlow: Flow<List<TransactionDisplay>> = combine(
        transactionsFlow,
        categoriesFlow,
        accountsFlow
    ) { transactions, categories, accounts ->
        AccountingCalculator.toTransactionDisplays(transactions, categories, accounts)
    }

    val budgetsWithProgressFlow: Flow<List<BudgetWithProgress>> = combine(
        budgetsFlow,
        transactionsFlow,
        categoriesFlow
    ) { budgets, transactions, categories ->
        AccountingCalculator.computeBudgetProgress(budgets, transactions, categories)
    }

    // --- Transactions ---

    suspend fun saveTransaction(
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
        removeExistingReceipt: Boolean = false
    ): Result<Long> = runCatching {
        require(amountPaise > 0L) { "Amount must be greater than zero." }
        if (type == TransactionType.TRANSFER) {
            require(toAccountId != null && toAccountId != accountId) {
                "Select two different accounts for transfer."
            }
        } else {
            require(categoryId != null) { "Please select a category." }
        }

        val resolvedReceiptPath = when {
            newReceiptUri != null && receiptStorageManager != null -> {
                if (!existingReceiptPath.isNullOrBlank()) {
                    receiptStorageManager.deleteReceipt(existingReceiptPath)
                }
                receiptStorageManager.saveEncryptedReceipt(newReceiptUri)
            }
            removeExistingReceipt -> {
                receiptStorageManager?.deleteReceipt(existingReceiptPath)
                null
            }
            else -> existingReceiptPath
        }

        val now = System.currentTimeMillis()
        val existingEntity = existingId?.let { transactionDao.getById(it) }
        val syncId = existingEntity?.syncId?.takeIf { it.isNotBlank() }
            ?: "tx_${UUID.randomUUID()}"
        val entity = TransactionEntity(
            id = existingId ?: 0L,
            workspaceId = workspace.id,
            typeId = type.id,
            amountPaise = amountPaise,
            dateMillis = dateMillis,
            categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
            accountId = accountId,
            toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
            descriptionEncrypted = cryptoEngine.encryptString(description.trim()),
            receiptPath = resolvedReceiptPath,
            createdAt = existingEntity?.createdAt ?: now,
            updatedAt = now,
            syncId = syncId,
            hasBeenSynced = existingEntity?.hasBeenSynced ?: false
        )
        val savedId = transactionDao.insert(entity)
        cloudSyncBridge?.onTransactionSaved(entity.copy(id = savedId))
        savedId
    }

    suspend fun deleteTransaction(tx: TransactionRecord) {
        val syncId = tx.syncId.ifBlank { transactionDao.getById(tx.id)?.syncId ?: "" }
        receiptStorageManager?.deleteReceipt(tx.receiptPath)
        transactionDao.deleteById(tx.id)
        if (syncId.isNotBlank()) {
            cloudSyncBridge?.onTransactionDeleted(syncId)
        }
    }

    // --- Accounts ---

    suspend fun addAccount(
        name: String,
        workspace: Workspace,
        type: AccountType,
        openingBalancePaise: Long
    ): Result<Long> = runCatching {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Account name cannot be empty." }
        val now = System.currentTimeMillis()
        val entity = AccountEntity(
            name = cleanName,
            workspaceId = workspace.id,
            typeId = type.id,
            openingBalancePaise = openingBalancePaise,
            isDefault = false,
            createdAt = now,
            syncId = "acc_${UUID.randomUUID()}",
            hasBeenSynced = false,
            updatedAt = now
        )
        val id = accountDao.insert(entity)
        cloudSyncBridge?.onAccountSaved(entity.copy(id = id))
        id
    }

    suspend fun updateAccount(account: Account): Result<Unit> = runCatching {
        val cleanName = account.name.trim()
        require(cleanName.isNotEmpty()) { "Account name cannot be empty." }
        val existing = accountDao.getById(account.id)
        val syncId = account.syncId.ifBlank {
            existing?.syncId?.takeIf { it.isNotBlank() }
                ?: if (account.isDefault) DefaultSeeds.defaultAccountSyncId(account.workspace.id, account.type.id)
                else "acc_${UUID.randomUUID()}"
        }
        val entity = AccountEntity(
            id = account.id,
            name = cleanName,
            workspaceId = account.workspace.id,
            typeId = account.type.id,
            openingBalancePaise = account.openingBalancePaise,
            isDefault = account.isDefault,
            createdAt = account.createdAt,
            syncId = syncId,
            hasBeenSynced = existing?.hasBeenSynced ?: false,
            updatedAt = System.currentTimeMillis()
        )
        accountDao.update(entity)
        cloudSyncBridge?.onAccountSaved(entity)
    }

    suspend fun deleteAccount(account: Account): Result<Unit> = runCatching {
        val txCount = transactionDao.countForAccount(account.id)
        require(txCount == 0) { "Cannot delete account with existing transactions." }
        val syncId = account.syncId.ifBlank { accountDao.getById(account.id)?.syncId ?: "" }
        accountDao.deleteById(account.id)
        if (syncId.isNotBlank()) {
            cloudSyncBridge?.onAccountDeleted(syncId)
        }
    }

    // --- Categories ---

    suspend fun addCategory(
        name: String,
        workspace: Workspace,
        type: TransactionType,
        colorHex: Long,
        iconName: String = "category"
    ): Result<Long> = runCatching {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Category name cannot be empty." }
        val entity = CategoryEntity(
            name = cleanName,
            workspaceId = workspace.id,
            typeId = type.id,
            colorHex = colorHex,
            iconName = iconName,
            isDefault = false,
            syncId = "cat_${UUID.randomUUID()}",
            hasBeenSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        val id = categoryDao.insert(entity)
        cloudSyncBridge?.onCategorySaved(entity.copy(id = id))
        id
    }

    suspend fun updateCategory(category: Category): Result<Unit> = runCatching {
        val cleanName = category.name.trim()
        require(cleanName.isNotEmpty()) { "Category name cannot be empty." }
        val existing = categoryDao.getById(category.id)
        val syncId = category.syncId.ifBlank {
            existing?.syncId?.takeIf { it.isNotBlank() } ?: "cat_${UUID.randomUUID()}"
        }
        val entity = CategoryEntity(
            id = category.id,
            name = cleanName,
            workspaceId = category.workspace.id,
            typeId = category.type.id,
            colorHex = category.colorHex,
            iconName = category.iconName,
            isDefault = category.isDefault,
            syncId = syncId,
            hasBeenSynced = existing?.hasBeenSynced ?: false,
            updatedAt = System.currentTimeMillis()
        )
        categoryDao.update(entity)
        cloudSyncBridge?.onCategorySaved(entity)
    }

    suspend fun deleteCategory(category: Category): Result<Unit> = runCatching {
        val txCount = transactionDao.countForCategory(category.id)
        require(txCount == 0) { "Cannot delete category that is used by transactions." }
        val syncId = category.syncId.ifBlank { categoryDao.getById(category.id)?.syncId ?: "" }
        categoryDao.deleteById(category.id)
        if (syncId.isNotBlank()) {
            cloudSyncBridge?.onCategoryDeleted(syncId)
        }
    }

    // --- Budgets ---

    suspend fun saveBudget(
        existingId: Long? = null,
        workspace: Workspace,
        categoryId: Long?,
        month: Int,
        year: Int,
        limitPaise: Long
    ): Result<Long> = runCatching {
        require(limitPaise > 0L) { "Budget limit must be greater than zero." }
        val existing = existingId?.let { budgetDao.getById(it) }
        val catSync = categoryId?.let { categoryDao.getById(it)?.syncId }?.takeIf { it.isNotBlank() } ?: "overall"
        val syncId = existing?.syncId?.takeIf { it.isNotBlank() }
            ?: "budget_${workspace.id}_${catSync}_${year}_${month}"
        val entity = BudgetEntity(
            id = existingId ?: 0L,
            workspaceId = workspace.id,
            categoryId = categoryId,
            month = month,
            year = year,
            limitPaise = limitPaise,
            syncId = syncId,
            hasBeenSynced = existing?.hasBeenSynced ?: false,
            updatedAt = System.currentTimeMillis()
        )
        val savedId = budgetDao.insert(entity)
        cloudSyncBridge?.onBudgetSaved(entity.copy(id = savedId))
        savedId
    }

    suspend fun deleteBudget(budgetId: Long) {
        val syncId = budgetDao.getById(budgetId)?.syncId ?: ""
        budgetDao.deleteById(budgetId)
        if (syncId.isNotBlank()) {
            cloudSyncBridge?.onBudgetDeleted(syncId)
        }
    }

    // --- Savings Goals ---

    suspend fun saveSavingsGoal(
        existingId: Long? = null,
        workspace: Workspace,
        name: String,
        isEmergencyFund: Boolean,
        targetPaise: Long,
        currentSavedPaise: Long,
        monthlyTargetPaise: Long
    ): Result<Long> = runCatching {
        require(name.trim().isNotEmpty()) { "Goal name cannot be empty." }
        require(targetPaise > 0L) { "Target amount must be greater than zero." }
        val existing = existingId?.let { savingsGoalDao.getById(it) }
        val syncId = existing?.syncId?.takeIf { it.isNotBlank() } ?: "goal_${UUID.randomUUID()}"
        val entity = SavingsGoalEntity(
            id = existingId ?: 0L,
            workspaceId = workspace.id,
            nameEncrypted = cryptoEngine.encryptString(name.trim()),
            isEmergencyFund = isEmergencyFund,
            targetPaise = targetPaise,
            currentSavedPaise = currentSavedPaise.coerceAtLeast(0L),
            monthlyTargetPaise = monthlyTargetPaise.coerceAtLeast(0L),
            syncId = syncId,
            hasBeenSynced = existing?.hasBeenSynced ?: false,
            updatedAt = System.currentTimeMillis()
        )
        val savedId = savingsGoalDao.insert(entity)
        cloudSyncBridge?.onSavingsGoalSaved(entity.copy(id = savedId))
        savedId
    }

    suspend fun deleteSavingsGoal(goalId: Long) {
        val syncId = savingsGoalDao.getById(goalId)?.syncId ?: ""
        savingsGoalDao.deleteById(goalId)
        if (syncId.isNotBlank()) {
            cloudSyncBridge?.onSavingsGoalDeleted(syncId)
        }
    }

    // --- Lending & Borrowing ---

    suspend fun addLoan(
        workspace: Workspace,
        type: LoanType,
        personName: String,
        principalPaise: Long,
        accountId: Long,
        dateMillis: Long,
        expectedRepaymentDateMillis: Long?,
        notes: String
    ): Result<Long> = runCatching {
        require(personName.trim().isNotEmpty()) { "Person name is required." }
        require(principalPaise > 0L) { "Amount must be greater than zero." }
        val entity = LoanEntity(
            workspaceId = workspace.id,
            typeId = type.id,
            personNameEncrypted = cryptoEngine.encryptString(personName.trim()),
            principalPaise = principalPaise,
            repaidPaise = 0L,
            accountId = accountId,
            dateMillis = dateMillis,
            expectedRepaymentDateMillis = expectedRepaymentDateMillis,
            notesEncrypted = cryptoEngine.encryptString(notes.trim()),
            syncId = "loan_${UUID.randomUUID()}",
            hasBeenSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        val id = loanDao.insert(entity)
        cloudSyncBridge?.onLoanSaved(entity.copy(id = id))
        id
    }

    suspend fun recordLoanRepayment(
        loanId: Long,
        amountPaise: Long,
        accountId: Long,
        dateMillis: Long,
        notes: String
    ): Result<Unit> = runCatching {
        require(amountPaise > 0L) { "Repayment amount must be greater than zero." }
        var savedRepayment: LoanRepaymentEntity? = null
        var updatedLoan: LoanEntity? = null
        database.withTransaction {
            val loan = loanDao.getById(loanId) ?: error("Loan record not found")
            val remaining = (loan.principalPaise - loan.repaidPaise).coerceAtLeast(0L)
            require(amountPaise <= remaining) { "Repayment cannot exceed remaining balance." }
            val now = System.currentTimeMillis()
            val repaymentEntity = LoanRepaymentEntity(
                loanId = loanId,
                amountPaise = amountPaise,
                dateMillis = dateMillis,
                accountId = accountId,
                notesEncrypted = cryptoEngine.encryptString(notes.trim()),
                syncId = "repay_${UUID.randomUUID()}",
                hasBeenSynced = false,
                updatedAt = now
            )
            val repId = loanRepaymentDao.insert(repaymentEntity)
            val newLoan = loan.copy(repaidPaise = loan.repaidPaise + amountPaise, updatedAt = now)
            loanDao.update(newLoan)
            savedRepayment = repaymentEntity.copy(id = repId)
            updatedLoan = newLoan
        }
        updatedLoan?.let { cloudSyncBridge?.onLoanSaved(it) }
        savedRepayment?.let { cloudSyncBridge?.onLoanRepaymentSaved(it) }
    }

    suspend fun deleteLoan(loanId: Long) {
        var loanSyncId = ""
        var repaymentSyncIds = emptyList<String>()
        database.withTransaction {
            loanSyncId = loanDao.getById(loanId)?.syncId ?: ""
            repaymentSyncIds = loanRepaymentDao.getByLoanId(loanId).map { it.syncId }
            loanRepaymentDao.deleteByLoanId(loanId)
            loanDao.deleteById(loanId)
        }
        cloudSyncBridge?.onLoanDeleted(loanSyncId, repaymentSyncIds)
    }

    // --- Mapping helpers ---

    private fun AccountEntity.toDomain() = Account(
        id = id,
        name = name,
        workspace = Workspace.fromId(workspaceId),
        type = AccountType.fromId(typeId),
        openingBalancePaise = openingBalancePaise,
        isDefault = isDefault,
        createdAt = createdAt,
        syncId = syncId
    )

    private fun CategoryEntity.toDomain() = Category(
        id = id,
        name = name,
        workspace = Workspace.fromId(workspaceId),
        type = TransactionType.fromId(typeId),
        colorHex = colorHex,
        iconName = iconName,
        isDefault = isDefault,
        syncId = syncId
    )

    private fun TransactionEntity.toDomain() = TransactionRecord(
        id = id,
        workspace = Workspace.fromId(workspaceId),
        type = TransactionType.fromId(typeId),
        amountPaise = amountPaise,
        dateMillis = dateMillis,
        categoryId = categoryId,
        accountId = accountId,
        toAccountId = toAccountId,
        description = cryptoEngine.decryptString(descriptionEncrypted),
        receiptPath = receiptPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncId = syncId
    )

    private fun BudgetEntity.toDomain() = Budget(
        id = id,
        workspace = Workspace.fromId(workspaceId),
        categoryId = categoryId,
        month = month,
        year = year,
        limitPaise = limitPaise,
        syncId = syncId
    )

    private fun SavingsGoalEntity.toDomain() = SavingsGoal(
        id = id,
        workspace = Workspace.fromId(workspaceId),
        name = cryptoEngine.decryptString(nameEncrypted),
        isEmergencyFund = isEmergencyFund,
        targetPaise = targetPaise,
        currentSavedPaise = currentSavedPaise,
        monthlyTargetPaise = monthlyTargetPaise,
        syncId = syncId
    )

    private fun LoanEntity.toDomain() = LoanRecord(
        id = id,
        workspace = Workspace.fromId(workspaceId),
        type = LoanType.fromId(typeId),
        personName = cryptoEngine.decryptString(personNameEncrypted),
        principalPaise = principalPaise,
        repaidPaise = repaidPaise,
        accountId = accountId,
        dateMillis = dateMillis,
        expectedRepaymentDateMillis = expectedRepaymentDateMillis,
        notes = cryptoEngine.decryptString(notesEncrypted),
        syncId = syncId
    )

    private fun LoanRepaymentEntity.toDomain() = LoanRepayment(
        id = id,
        loanId = loanId,
        amountPaise = amountPaise,
        dateMillis = dateMillis,
        accountId = accountId,
        notes = cryptoEngine.decryptString(notesEncrypted),
        syncId = syncId
    )
}
