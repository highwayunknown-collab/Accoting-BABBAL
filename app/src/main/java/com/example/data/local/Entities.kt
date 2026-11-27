package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["syncId"])]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val workspaceId: String,
    val typeId: String,
    val openingBalancePaise: Long = 0L,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val syncId: String = "",
    val hasBeenSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["syncId"])]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val workspaceId: String,
    val typeId: String,
    val colorHex: Long = 0xFF167B59,
    val iconName: String = "category",
    val isDefault: Boolean = false,
    val syncId: String = "",
    val hasBeenSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["workspaceId", "dateMillis"]),
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["syncId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workspaceId: String,
    val typeId: String,
    val amountPaise: Long,
    val dateMillis: Long,
    val categoryId: Long?,
    val accountId: Long,
    val toAccountId: Long? = null,
    val descriptionEncrypted: String = "",
    val receiptPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncId: String = "",
    val hasBeenSynced: Boolean = false
)

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["syncId"])]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workspaceId: String,
    val categoryId: Long?,
    val month: Int,
    val year: Int,
    val limitPaise: Long,
    val syncId: String = "",
    val hasBeenSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "savings_goals",
    indices = [Index(value = ["syncId"])]
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workspaceId: String,
    val nameEncrypted: String,
    val isEmergencyFund: Boolean = false,
    val targetPaise: Long,
    val currentSavedPaise: Long,
    val monthlyTargetPaise: Long = 0L,
    val syncId: String = "",
    val hasBeenSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "loans",
    indices = [Index(value = ["syncId"])]
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workspaceId: String,
    val typeId: String,
    val personNameEncrypted: String,
    val principalPaise: Long,
    val repaidPaise: Long = 0L,
    val accountId: Long,
    val dateMillis: Long,
    val expectedRepaymentDateMillis: Long? = null,
    val notesEncrypted: String = "",
    val syncId: String = "",
    val hasBeenSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "loan_repayments",
    indices = [
        Index(value = ["loanId"]),
        Index(value = ["syncId"])
    ]
)
data class LoanRepaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val loanId: Long,
    val amountPaise: Long,
    val dateMillis: Long,
    val accountId: Long,
    val notesEncrypted: String = "",
    val syncId: String = "",
    val hasBeenSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
