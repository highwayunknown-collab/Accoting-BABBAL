package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.AccountType
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        SavingsGoalEntity::class,
        LoanEntity::class,
        LoanRepaymentEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun loanDao(): LoanDao
    abstract fun loanRepaymentDao(): LoanRepaymentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE accounts ADD COLUMN hasBeenSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE accounts ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_accounts_syncId ON accounts(syncId)")
                db.execSQL(
                    """
                    UPDATE accounts SET syncId = CASE
                        WHEN isDefault = 1 THEN 'default_' || workspaceId || '_' || typeId
                        ELSE 'acc_m_' || id
                    END
                    """.trimIndent()
                )

                db.execSQL("ALTER TABLE categories ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE categories ADD COLUMN hasBeenSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_syncId ON categories(syncId)")
                db.execSQL(
                    """
                    UPDATE categories SET syncId = CASE
                        WHEN isDefault = 1 THEN 'default_' || workspaceId || '_' || typeId || '_' || lower(replace(replace(replace(name, ' & ', '_'), ' ', '_'), '&', '_'))
                        ELSE 'cat_m_' || id
                    END
                    """.trimIndent()
                )

                db.execSQL("ALTER TABLE transactions ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN hasBeenSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_syncId ON transactions(syncId)")
                db.execSQL("UPDATE transactions SET syncId = 'tx_m_' || id")

                db.execSQL("ALTER TABLE budgets ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE budgets ADD COLUMN hasBeenSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE budgets ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budgets_syncId ON budgets(syncId)")
                db.execSQL("UPDATE budgets SET syncId = 'budget_m_' || id")

                db.execSQL("ALTER TABLE savings_goals ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE savings_goals ADD COLUMN hasBeenSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE savings_goals ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_goals_syncId ON savings_goals(syncId)")
                db.execSQL("UPDATE savings_goals SET syncId = 'goal_m_' || id")

                db.execSQL("ALTER TABLE loans ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loans ADD COLUMN hasBeenSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE loans ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loans_syncId ON loans(syncId)")
                db.execSQL("UPDATE loans SET syncId = 'loan_m_' || id")

                db.execSQL("ALTER TABLE loan_repayments ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loan_repayments ADD COLUMN hasBeenSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE loan_repayments ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loan_repayments_syncId ON loan_repayments(syncId)")
                db.execSQL("UPDATE loan_repayments SET syncId = 'repay_m_' || id")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "babbal_finance.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

object DefaultSeeds {

    fun slugify(value: String): String =
        value.lowercase()
            .replace("&", " ")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    fun defaultAccountSyncId(workspaceId: String, typeId: String): String =
        "default_${workspaceId}_${typeId}"

    fun defaultCategorySyncId(workspaceId: String, typeId: String, name: String): String =
        "default_${workspaceId}_${typeId}_${slugify(name)}"

    fun defaultAccounts(): List<AccountEntity> {
        val now = System.currentTimeMillis()
        val list = mutableListOf<AccountEntity>()
        listOf(Workspace.PERSONAL, Workspace.GVVS).forEach { ws ->
            list.add(
                AccountEntity(
                    name = "Cash",
                    workspaceId = ws.id,
                    typeId = AccountType.CASH.id,
                    openingBalancePaise = 0L,
                    isDefault = true,
                    createdAt = now,
                    syncId = defaultAccountSyncId(ws.id, AccountType.CASH.id),
                    updatedAt = now
                )
            )
            list.add(
                AccountEntity(
                    name = "Bank Account",
                    workspaceId = ws.id,
                    typeId = AccountType.BANK.id,
                    openingBalancePaise = 0L,
                    isDefault = true,
                    createdAt = now,
                    syncId = defaultAccountSyncId(ws.id, AccountType.BANK.id),
                    updatedAt = now
                )
            )
            list.add(
                AccountEntity(
                    name = "UPI / Digital Wallet",
                    workspaceId = ws.id,
                    typeId = AccountType.UPI.id,
                    openingBalancePaise = 0L,
                    isDefault = true,
                    createdAt = now,
                    syncId = defaultAccountSyncId(ws.id, AccountType.UPI.id),
                    updatedAt = now
                )
            )
            list.add(
                AccountEntity(
                    name = "Other",
                    workspaceId = ws.id,
                    typeId = AccountType.OTHER.id,
                    openingBalancePaise = 0L,
                    isDefault = true,
                    createdAt = now,
                    syncId = defaultAccountSyncId(ws.id, AccountType.OTHER.id),
                    updatedAt = now
                )
            )
        }
        return list
    }

    fun defaultCategories(): List<CategoryEntity> {
        val now = System.currentTimeMillis()
        val list = mutableListOf<CategoryEntity>()

        val personalIncome = listOf(
            Triple("Salary", 0xFF16A36A, "payments"),
            Triple("Freelance Income", 0xFF0EA5E9, "laptop"),
            Triple("Business Income", 0xFF167B59, "storefront"),
            Triple("Interest", 0xFF14B8A6, "savings"),
            Triple("Gifts", 0xFF8B5CF6, "card_giftcard"),
            Triple("Other Income", 0xFF64748B, "attach_money")
        )
        personalIncome.forEach { (name, color, icon) ->
            list.add(
                CategoryEntity(
                    name = name,
                    workspaceId = Workspace.PERSONAL.id,
                    typeId = TransactionType.INCOME.id,
                    colorHex = color,
                    iconName = icon,
                    isDefault = true,
                    syncId = defaultCategorySyncId(Workspace.PERSONAL.id, TransactionType.INCOME.id, name),
                    updatedAt = now
                )
            )
        }

        val gvvsIncome = listOf(
            Triple("Business Income", 0xFF167B59, "storefront"),
            Triple("Product Sales", 0xFF16A36A, "inventory_2"),
            Triple("Interest", 0xFF14B8A6, "savings"),
            Triple("Other Income", 0xFF64748B, "attach_money")
        )
        gvvsIncome.forEach { (name, color, icon) ->
            list.add(
                CategoryEntity(
                    name = name,
                    workspaceId = Workspace.GVVS.id,
                    typeId = TransactionType.INCOME.id,
                    colorHex = color,
                    iconName = icon,
                    isDefault = true,
                    syncId = defaultCategorySyncId(Workspace.GVVS.id, TransactionType.INCOME.id, name),
                    updatedAt = now
                )
            )
        }

        val personalExpense = listOf(
            Triple("Food", 0xFFF59E0B, "restaurant"),
            Triple("Shopping", 0xFFEC4899, "shopping_bag"),
            Triple("Travel & Petrol", 0xFF0EA5E9, "directions_car"),
            Triple("Electricity & Utilities", 0xFFEAB308, "bolt"),
            Triple("Rent", 0xFF8B5CF6, "home"),
            Triple("Medical", 0xFFEF4444, "medical_services"),
            Triple("Entertainment", 0xFF6366F1, "movie"),
            Triple("Education", 0xFF14B8A6, "school"),
            Triple("Personal Care", 0xFFF97316, "spa"),
            Triple("Other Expenses", 0xFF64748B, "receipt_long")
        )
        personalExpense.forEach { (name, color, icon) ->
            list.add(
                CategoryEntity(
                    name = name,
                    workspaceId = Workspace.PERSONAL.id,
                    typeId = TransactionType.EXPENSE.id,
                    colorHex = color,
                    iconName = icon,
                    isDefault = true,
                    syncId = defaultCategorySyncId(Workspace.PERSONAL.id, TransactionType.EXPENSE.id, name),
                    updatedAt = now
                )
            )
        }

        val gvvsExpense = listOf(
            Triple("Business Supplies", 0xFF167B59, "inventory"),
            Triple("Raw Materials", 0xFF0F766E, "forest"),
            Triple("Packaging Materials", 0xFFF59E0B, "package_2"),
            Triple("Transportation", 0xFF0EA5E9, "local_shipping"),
            Triple("Office Expenses", 0xFF6366F1, "business_center"),
            Triple("Employee Payments", 0xFF8B5CF6, "groups"),
            Triple("Electricity & Utilities", 0xFFEAB308, "bolt"),
            Triple("Rent", 0xFFEC4899, "store"),
            Triple("Other Expenses", 0xFF64748B, "receipt_long")
        )
        gvvsExpense.forEach { (name, color, icon) ->
            list.add(
                CategoryEntity(
                    name = name,
                    workspaceId = Workspace.GVVS.id,
                    typeId = TransactionType.EXPENSE.id,
                    colorHex = color,
                    iconName = icon,
                    isDefault = true,
                    syncId = defaultCategorySyncId(Workspace.GVVS.id, TransactionType.EXPENSE.id, name),
                    updatedAt = now
                )
            )
        }

        return list
    }
}
