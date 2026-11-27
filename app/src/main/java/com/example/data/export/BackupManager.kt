package com.example.data.export

import androidx.room.withTransaction
import com.example.data.crypto.CryptoEngine
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.LoanEntity
import com.example.data.local.LoanRepaymentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.SavingsGoalEntity
import com.example.data.local.TransactionEntity
import com.example.domain.model.DateFormatOption
import com.example.domain.model.ThemeMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class BabbalBackupPayload(
    val formatVersion: Int = 1,
    val appName: String = "BABBAL Finance",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val personalWorkspaceName: String = "Personal",
    val businessWorkspaceName: String = "GVVS Business",
    val currencySymbol: String = "₹",
    val currencyCode: String = "INR",
    val dateFormatId: String = DateFormatOption.DD_MM_YYYY.id,
    val financialYearStartMonth: Int = 4,
    val themeModeId: String = ThemeMode.LIGHT.id,
    val accounts: List<BackupAccount> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
    val budgets: List<BackupBudget> = emptyList(),
    val savingsGoals: List<BackupSavingsGoal> = emptyList(),
    val loans: List<BackupLoan> = emptyList(),
    val loanRepayments: List<BackupLoanRepayment> = emptyList()
)

data class BackupAccount(
    val id: Long,
    val name: String,
    val workspaceId: String,
    val typeId: String,
    val openingBalancePaise: Long,
    val isDefault: Boolean,
    val createdAt: Long
)

data class BackupCategory(
    val id: Long,
    val name: String,
    val workspaceId: String,
    val typeId: String,
    val colorHex: Long,
    val iconName: String,
    val isDefault: Boolean
)

data class BackupTransaction(
    val id: Long,
    val workspaceId: String,
    val typeId: String,
    val amountPaise: Long,
    val dateMillis: Long,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long? = null,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class BackupBudget(
    val id: Long,
    val workspaceId: String,
    val categoryId: Long?,
    val month: Int,
    val year: Int,
    val limitPaise: Long
)

data class BackupSavingsGoal(
    val id: Long,
    val workspaceId: String,
    val name: String,
    val isEmergencyFund: Boolean,
    val targetPaise: Long,
    val currentSavedPaise: Long,
    val monthlyTargetPaise: Long
)

data class BackupLoan(
    val id: Long,
    val workspaceId: String,
    val typeId: String,
    val personName: String,
    val principalPaise: Long,
    val repaidPaise: Long,
    val accountId: Long,
    val dateMillis: Long,
    val expectedRepaymentDateMillis: Long?,
    val notes: String
)

data class BackupLoanRepayment(
    val id: Long,
    val loanId: Long,
    val amountPaise: Long,
    val dateMillis: Long,
    val accountId: Long,
    val notes: String
)

class BackupManager(
    private val database: AppDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val cryptoEngine: CryptoEngine
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(BabbalBackupPayload::class.java).indent("  ")

    suspend fun createBackupJson(encryptionPassword: String? = null): String {
        val prefs = preferencesRepository.getSnapshot()
        val accounts = database.accountDao().getAllSnapshot()
        val categories = database.categoryDao().getAllSnapshot()
        val transactions = database.transactionDao().getAllSnapshot()
        val budgets = database.budgetDao().getAllSnapshot()
        val goals = database.savingsGoalDao().getAllSnapshot()
        val loans = database.loanDao().getAllSnapshot()
        val repayments = database.loanRepaymentDao().getAllSnapshot()

        val payload = BabbalBackupPayload(
            personalWorkspaceName = prefs.personalWorkspaceName,
            businessWorkspaceName = prefs.businessWorkspaceName,
            currencySymbol = prefs.currencySymbol,
            currencyCode = prefs.currencyCode,
            dateFormatId = prefs.dateFormat.id,
            financialYearStartMonth = prefs.financialYearStartMonth,
            themeModeId = prefs.themeMode.id,
            accounts = accounts.map {
                BackupAccount(it.id, it.name, it.workspaceId, it.typeId, it.openingBalancePaise, it.isDefault, it.createdAt)
            },
            categories = categories.map {
                BackupCategory(it.id, it.name, it.workspaceId, it.typeId, it.colorHex, it.iconName, it.isDefault)
            },
            transactions = transactions.map {
                BackupTransaction(
                    id = it.id,
                    workspaceId = it.workspaceId,
                    typeId = it.typeId,
                    amountPaise = it.amountPaise,
                    dateMillis = it.dateMillis,
                    categoryId = it.categoryId,
                    accountId = it.accountId,
                    toAccountId = it.toAccountId,
                    description = cryptoEngine.decryptString(it.descriptionEncrypted),
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            budgets = budgets.map {
                BackupBudget(it.id, it.workspaceId, it.categoryId, it.month, it.year, it.limitPaise)
            },
            savingsGoals = goals.map {
                BackupSavingsGoal(
                    id = it.id,
                    workspaceId = it.workspaceId,
                    name = cryptoEngine.decryptString(it.nameEncrypted),
                    isEmergencyFund = it.isEmergencyFund,
                    targetPaise = it.targetPaise,
                    currentSavedPaise = it.currentSavedPaise,
                    monthlyTargetPaise = it.monthlyTargetPaise
                )
            },
            loans = loans.map {
                BackupLoan(
                    id = it.id,
                    workspaceId = it.workspaceId,
                    typeId = it.typeId,
                    personName = cryptoEngine.decryptString(it.personNameEncrypted),
                    principalPaise = it.principalPaise,
                    repaidPaise = it.repaidPaise,
                    accountId = it.accountId,
                    dateMillis = it.dateMillis,
                    expectedRepaymentDateMillis = it.expectedRepaymentDateMillis,
                    notes = cryptoEngine.decryptString(it.notesEncrypted)
                )
            },
            loanRepayments = repayments.map {
                BackupLoanRepayment(
                    id = it.id,
                    loanId = it.loanId,
                    amountPaise = it.amountPaise,
                    dateMillis = it.dateMillis,
                    accountId = it.accountId,
                    notes = cryptoEngine.decryptString(it.notesEncrypted)
                )
            }
        )

        val json = adapter.toJson(payload)
        preferencesRepository.markBackupCompleted()

        return if (!encryptionPassword.isNullOrBlank()) {
            cryptoEngine.encryptWithPassword(json, encryptionPassword)
        } else {
            json
        }
    }

    fun isEncryptedBackup(rawContent: String): Boolean =
        rawContent.trimStart().startsWith("BABBAL_ENC_V1:")

    suspend fun restoreFromBackup(rawContent: String, decryptionPassword: String? = null): Result<Int> = runCatching {
        val trimmed = rawContent.trim()
        val json = if (isEncryptedBackup(trimmed)) {
            require(!decryptionPassword.isNullOrBlank()) { "Password required to decrypt backup file." }
            cryptoEngine.decryptWithPassword(trimmed, decryptionPassword)
        } else {
            trimmed
        }

        val payload = adapter.fromJson(json) ?: error("Invalid BABBAL backup file.")
        require(payload.accounts.isNotEmpty() || payload.categories.isNotEmpty() || payload.transactions.isNotEmpty()) {
            "Backup file appears empty or incompatible."
        }

        database.withTransaction {
            database.loanRepaymentDao().deleteAll()
            database.loanDao().deleteAll()
            database.savingsGoalDao().deleteAll()
            database.budgetDao().deleteAll()
            database.transactionDao().deleteAll()
            database.categoryDao().deleteAll()
            database.accountDao().deleteAll()

            database.accountDao().insertAll(
                payload.accounts.map {
                    AccountEntity(it.id, it.name, it.workspaceId, it.typeId, it.openingBalancePaise, it.isDefault, it.createdAt)
                }
            )
            database.categoryDao().insertAll(
                payload.categories.map {
                    CategoryEntity(it.id, it.name, it.workspaceId, it.typeId, it.colorHex, it.iconName, it.isDefault)
                }
            )
            database.transactionDao().insertAll(
                payload.transactions.map {
                    TransactionEntity(
                        id = it.id,
                        workspaceId = it.workspaceId,
                        typeId = it.typeId,
                        amountPaise = it.amountPaise,
                        dateMillis = it.dateMillis,
                        categoryId = it.categoryId,
                        accountId = it.accountId,
                        toAccountId = it.toAccountId,
                        descriptionEncrypted = cryptoEngine.encryptString(it.description),
                        receiptPath = null,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                }
            )
            database.budgetDao().insertAll(
                payload.budgets.map {
                    BudgetEntity(it.id, it.workspaceId, it.categoryId, it.month, it.year, it.limitPaise)
                }
            )
            database.savingsGoalDao().insertAll(
                payload.savingsGoals.map {
                    SavingsGoalEntity(
                        id = it.id,
                        workspaceId = it.workspaceId,
                        nameEncrypted = cryptoEngine.encryptString(it.name),
                        isEmergencyFund = it.isEmergencyFund,
                        targetPaise = it.targetPaise,
                        currentSavedPaise = it.currentSavedPaise,
                        monthlyTargetPaise = it.monthlyTargetPaise
                    )
                }
            )
            database.loanDao().insertAll(
                payload.loans.map {
                    LoanEntity(
                        id = it.id,
                        workspaceId = it.workspaceId,
                        typeId = it.typeId,
                        personNameEncrypted = cryptoEngine.encryptString(it.personName),
                        principalPaise = it.principalPaise,
                        repaidPaise = it.repaidPaise,
                        accountId = it.accountId,
                        dateMillis = it.dateMillis,
                        expectedRepaymentDateMillis = it.expectedRepaymentDateMillis,
                        notesEncrypted = cryptoEngine.encryptString(it.notes)
                    )
                }
            )
            database.loanRepaymentDao().insertAll(
                payload.loanRepayments.map {
                    LoanRepaymentEntity(
                        id = it.id,
                        loanId = it.loanId,
                        amountPaise = it.amountPaise,
                        dateMillis = it.dateMillis,
                        accountId = it.accountId,
                        notesEncrypted = cryptoEngine.encryptString(it.notes)
                    )
                }
            )
        }

        preferencesRepository.restoreNonSecuritySettings(
            personalName = payload.personalWorkspaceName,
            businessName = payload.businessWorkspaceName,
            currencySymbol = payload.currencySymbol,
            currencyCode = payload.currencyCode,
            dateFormat = DateFormatOption.fromId(payload.dateFormatId),
            fyStartMonth = payload.financialYearStartMonth,
            themeMode = ThemeMode.fromId(payload.themeModeId)
        )

        payload.transactions.size
    }
}
