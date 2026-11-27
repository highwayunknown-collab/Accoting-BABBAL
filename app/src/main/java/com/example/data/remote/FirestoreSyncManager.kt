package com.example.data.remote

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.room.withTransaction
import com.example.R
import com.example.data.crypto.CryptoEngine
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
import com.example.data.repository.CloudSyncBridge
import com.example.domain.model.AppPreferences
import com.example.domain.model.DateFormatOption
import com.example.domain.model.ThemeMode
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class FirestoreSyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val cryptoEngine: CryptoEngine
) : CloudSyncBridge {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mergeMutex = Mutex()
    private val listeners = mutableListOf<ListenerRegistration>()

    @Volatile
    private var activeUid: String? = null

    private val _syncStatus = MutableStateFlow(CloudSyncStatus())
    val syncStatusFlow: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private val deviceName: String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }.take(120)

    @Volatile
    private var latestTransactionsSnap: QuerySnapshot? = null
    @Volatile
    private var latestBudgetsSnap: QuerySnapshot? = null
    @Volatile
    private var latestLoansSnap: QuerySnapshot? = null
    @Volatile
    private var latestRepaymentsSnap: QuerySnapshot? = null

    fun startSync(user: FirebaseUser) {
        val uid = user.uid
        if (activeUid == uid && listeners.isNotEmpty()) return
        stopSync()
        activeUid = uid
        _syncStatus.value = CloudSyncStatus(
            syncState = SyncState.SYNCING,
            lastSyncedDevice = deviceName
        )

        scope.launch {
            runCatching {
                ensureUserProfileExists(user)
                uploadPendingLocalRecords(uid)
            }.onFailure { err ->
                Log.w("FirestoreSyncManager", "Initial cloud sync upload error", err)
                _syncStatus.value = _syncStatus.value.copy(
                    syncState = SyncState.ERROR,
                    errorMessage = err.message
                )
            }
            attachRealtimeListeners(uid)
        }
    }

    fun stopSync() {
        activeUid = null
        synchronized(listeners) {
            listeners.forEach { it.remove() }
            listeners.clear()
        }
        latestTransactionsSnap = null
        latestBudgetsSnap = null
        latestLoansSnap = null
        latestRepaymentsSnap = null
        _syncStatus.value = CloudSyncStatus(syncState = SyncState.DISCONNECTED)
    }

    fun triggerManualSync(user: FirebaseUser?) {
        val u = user ?: return
        scope.launch {
            _syncStatus.value = _syncStatus.value.copy(syncState = SyncState.SYNCING, errorMessage = null)
            runCatching {
                ensureUserProfileExists(u)
                uploadPendingLocalRecords(u.uid)
                val prefs = preferencesRepository.getSnapshot()
                pushPreferencesUpdate(prefs)
            }.onSuccess {
                _syncStatus.value = _syncStatus.value.copy(
                    syncState = SyncState.SYNCED,
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    lastSyncedDevice = deviceName,
                    errorMessage = null
                )
            }.onFailure { err ->
                _syncStatus.value = _syncStatus.value.copy(
                    syncState = SyncState.ERROR,
                    errorMessage = err.message ?: "Sync failed"
                )
            }
        }
    }

    private fun attachRealtimeListeners(uid: String) {
        if (activeUid != uid) return
        val userDoc = firestore.collection("users").document(uid)

        val profileReg = userDoc.addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) {
                reportSyncError(err)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                scope.launch { applyRemoteProfile(snap) }
            }
        }

        val accountsReg = userDoc.collection("accounts")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) { reportSyncError(err); return@addSnapshotListener }
                if (snap != null) scope.launch { applyRemoteAccounts(uid, snap) }
            }

        val categoriesReg = userDoc.collection("categories")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) { reportSyncError(err); return@addSnapshotListener }
                if (snap != null) scope.launch { applyRemoteCategories(uid, snap) }
            }

        val budgetsReg = userDoc.collection("budgets")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) { reportSyncError(err); return@addSnapshotListener }
                if (snap != null) {
                    latestBudgetsSnap = snap
                    scope.launch { applyRemoteBudgets(uid, snap) }
                }
            }

        val goalsReg = userDoc.collection("savings_goals")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) { reportSyncError(err); return@addSnapshotListener }
                if (snap != null) scope.launch { applyRemoteSavingsGoals(uid, snap) }
            }

        val loansReg = userDoc.collection("loans")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) { reportSyncError(err); return@addSnapshotListener }
                if (snap != null) {
                    latestLoansSnap = snap
                    scope.launch { applyRemoteLoans(uid, snap) }
                }
            }

        val repaymentsReg = userDoc.collection("loan_repayments")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) { reportSyncError(err); return@addSnapshotListener }
                if (snap != null) {
                    latestRepaymentsSnap = snap
                    scope.launch { applyRemoteLoanRepayments(uid, snap) }
                }
            }

        val transactionsReg = userDoc.collection("transactions")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) { reportSyncError(err); return@addSnapshotListener }
                if (snap != null) {
                    latestTransactionsSnap = snap
                    scope.launch { applyRemoteTransactions(uid, snap) }
                }
            }

        synchronized(listeners) {
            if (activeUid == uid) {
                listeners.addAll(
                    listOf(
                        profileReg,
                        accountsReg,
                        categoriesReg,
                        budgetsReg,
                        goalsReg,
                        loansReg,
                        repaymentsReg,
                        transactionsReg
                    )
                )
            } else {
                listOf(
                    profileReg, accountsReg, categoriesReg, budgetsReg,
                    goalsReg, loansReg, repaymentsReg, transactionsReg
                ).forEach { it.remove() }
            }
        }
    }

    private fun reportSyncError(err: Exception) {
        Log.e("FirestoreSyncManager", "Snapshot listener error: ${err.message}", err)
        _syncStatus.value = _syncStatus.value.copy(
            syncState = SyncState.ERROR,
            errorMessage = err.message ?: "Cloud sync error"
        )
    }

    private fun updateSyncStatusFromMetadata(isFromCache: Boolean, remoteDevice: String? = null) {
        if (activeUid == null) return
        val newState = if (isFromCache) SyncState.OFFLINE else SyncState.SYNCED
        _syncStatus.value = _syncStatus.value.copy(
            syncState = newState,
            lastSyncedAtMillis = if (!isFromCache) System.currentTimeMillis() else _syncStatus.value.lastSyncedAtMillis,
            lastSyncedDevice = remoteDevice?.takeIf { it.isNotBlank() } ?: _syncStatus.value.lastSyncedDevice.ifBlank { deviceName },
            errorMessage = null
        )
    }

    private suspend fun ensureUserProfileExists(user: FirebaseUser) {
        val uid = user.uid
        val docRef = firestore.collection("users").document(uid)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val prefs = preferencesRepository.getSnapshot()
            val payload = hashMapOf<String, Any>(
                "uid" to uid,
                "email" to (user.email ?: "").take(254),
                "displayName" to (user.displayName ?: "").take(120),
                "personalWorkspaceName" to prefs.personalWorkspaceName.ifBlank { "Personal" }.take(60),
                "businessWorkspaceName" to prefs.businessWorkspaceName.ifBlank { "GVVS Business" }.take(60),
                "currencySymbol" to prefs.currencySymbol.ifBlank { "₹" }.take(8),
                "currencyCode" to prefs.currencyCode.ifBlank { "INR" }.take(10),
                "dateFormatId" to prefs.dateFormat.id,
                "financialYearStartMonth" to prefs.financialYearStartMonth.coerceIn(1, 12),
                "themeModeId" to prefs.themeMode.id,
                "lastSyncedDevice" to deviceName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        }
    }

    private suspend fun applyRemoteProfile(snap: DocumentSnapshot) {
        if (activeUid == null) return
        mergeMutex.withLock {
            val personal = snap.getString("personalWorkspaceName") ?: "Personal"
            val business = snap.getString("businessWorkspaceName") ?: "GVVS Business"
            val symbol = snap.getString("currencySymbol") ?: "₹"
            val code = snap.getString("currencyCode") ?: "INR"
            val dateFormat = DateFormatOption.fromId(snap.getString("dateFormatId") ?: DateFormatOption.DD_MM_YYYY.id)
            val fyMonth = (snap.getLong("financialYearStartMonth") ?: 4L).toInt().coerceIn(1, 12)
            val theme = ThemeMode.fromId(snap.getString("themeModeId") ?: ThemeMode.LIGHT.id)
            val remoteDevice = snap.getString("lastSyncedDevice")

            val current = preferencesRepository.getSnapshot()
            if (current.personalWorkspaceName != personal ||
                current.businessWorkspaceName != business ||
                current.currencySymbol != symbol ||
                current.currencyCode != code ||
                current.dateFormat != dateFormat ||
                current.financialYearStartMonth != fyMonth ||
                current.themeMode != theme
            ) {
                preferencesRepository.restoreNonSecuritySettings(
                    personalName = personal,
                    businessName = business,
                    currencySymbol = symbol,
                    currencyCode = code,
                    dateFormat = dateFormat,
                    fyStartMonth = fyMonth,
                    themeMode = theme
                )
            }
            updateSyncStatusFromMetadata(snap.metadata.isFromCache, remoteDevice)
        }
    }

    private suspend fun applyRemoteAccounts(uid: String, snap: QuerySnapshot) {
        if (activeUid != uid) return
        mergeMutex.withLock {
            database.withTransaction {
                val dao = database.accountDao()
                val localBySync = dao.getAllSnapshot().associateBy { it.syncId }
                val remoteIds = HashSet<String>(snap.documents.size)

                for (doc in snap.documents) {
                    val syncId = doc.id
                    remoteIds.add(syncId)
                    val name = doc.getString("name") ?: continue
                    val workspaceId = doc.getString("workspaceId") ?: continue
                    val typeId = doc.getString("typeId") ?: continue
                    val opening = doc.getLong("openingBalancePaise") ?: 0L
                    val isDefault = doc.getBoolean("isDefault") ?: false
                    val createdAtMillis = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()
                    val remoteUpdatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: createdAtMillis

                    val existing = localBySync[syncId]
                    if (existing == null) {
                        dao.insert(
                            AccountEntity(
                                name = name,
                                workspaceId = workspaceId,
                                typeId = typeId,
                                openingBalancePaise = opening,
                                isDefault = isDefault,
                                createdAt = createdAtMillis,
                                syncId = syncId,
                                hasBeenSynced = true,
                                updatedAt = remoteUpdatedAt
                            )
                        )
                    } else if (!existing.hasBeenSynced || remoteUpdatedAt >= existing.updatedAt) {
                        if (existing.name != name ||
                            existing.workspaceId != workspaceId ||
                            existing.typeId != typeId ||
                            existing.openingBalancePaise != opening ||
                            !existing.hasBeenSynced
                        ) {
                            dao.update(
                                existing.copy(
                                    name = name,
                                    workspaceId = workspaceId,
                                    typeId = typeId,
                                    openingBalancePaise = opening,
                                    isDefault = isDefault,
                                    hasBeenSynced = true,
                                    updatedAt = maxOf(existing.updatedAt, remoteUpdatedAt)
                                )
                            )
                        }
                    }
                }

                if (!snap.metadata.hasPendingWrites()) {
                    for (local in localBySync.values) {
                        if (local.hasBeenSynced && local.syncId !in remoteIds && !local.isDefault) {
                            dao.deleteById(local.id)
                        }
                    }
                }
            }
            updateSyncStatusFromMetadata(snap.metadata.isFromCache)
        }
        latestBudgetsSnap?.let { applyRemoteBudgets(uid, it) }
        latestLoansSnap?.let { applyRemoteLoans(uid, it) }
        latestTransactionsSnap?.let { applyRemoteTransactions(uid, it) }
    }

    private suspend fun applyRemoteCategories(uid: String, snap: QuerySnapshot) {
        if (activeUid != uid) return
        mergeMutex.withLock {
            database.withTransaction {
                val dao = database.categoryDao()
                val localBySync = dao.getAllSnapshot().associateBy { it.syncId }
                val remoteIds = HashSet<String>(snap.documents.size)

                for (doc in snap.documents) {
                    val syncId = doc.id
                    remoteIds.add(syncId)
                    val name = doc.getString("name") ?: continue
                    val workspaceId = doc.getString("workspaceId") ?: continue
                    val typeId = doc.getString("typeId") ?: continue
                    val colorHex = doc.getLong("colorHex") ?: 0xFF167B59L
                    val iconName = doc.getString("iconName") ?: "category"
                    val isDefault = doc.getBoolean("isDefault") ?: false
                    val remoteUpdatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()

                    val existing = localBySync[syncId]
                    if (existing == null) {
                        dao.insert(
                            CategoryEntity(
                                name = name,
                                workspaceId = workspaceId,
                                typeId = typeId,
                                colorHex = colorHex,
                                iconName = iconName,
                                isDefault = isDefault,
                                syncId = syncId,
                                hasBeenSynced = true,
                                updatedAt = remoteUpdatedAt
                            )
                        )
                    } else if (!existing.hasBeenSynced || remoteUpdatedAt >= existing.updatedAt) {
                        if (existing.name != name ||
                            existing.workspaceId != workspaceId ||
                            existing.typeId != typeId ||
                            existing.colorHex != colorHex ||
                            existing.iconName != iconName ||
                            !existing.hasBeenSynced
                        ) {
                            dao.update(
                                existing.copy(
                                    name = name,
                                    workspaceId = workspaceId,
                                    typeId = typeId,
                                    colorHex = colorHex,
                                    iconName = iconName,
                                    isDefault = isDefault,
                                    hasBeenSynced = true,
                                    updatedAt = maxOf(existing.updatedAt, remoteUpdatedAt)
                                )
                            )
                        }
                    }
                }

                if (!snap.metadata.hasPendingWrites()) {
                    for (local in localBySync.values) {
                        if (local.hasBeenSynced && local.syncId !in remoteIds && !local.isDefault) {
                            dao.deleteById(local.id)
                        }
                    }
                }
            }
            updateSyncStatusFromMetadata(snap.metadata.isFromCache)
        }
        latestBudgetsSnap?.let { applyRemoteBudgets(uid, it) }
        latestTransactionsSnap?.let { applyRemoteTransactions(uid, it) }
    }

    private suspend fun applyRemoteBudgets(uid: String, snap: QuerySnapshot) {
        if (activeUid != uid) return
        mergeMutex.withLock {
            database.withTransaction {
                val dao = database.budgetDao()
                val categoryMap = database.categoryDao().getAllSnapshot().associateBy { it.syncId }
                val localBySync = dao.getAllSnapshot().associateBy { it.syncId }
                val remoteIds = HashSet<String>(snap.documents.size)

                for (doc in snap.documents) {
                    val syncId = doc.id
                    remoteIds.add(syncId)
                    val workspaceId = doc.getString("workspaceId") ?: continue
                    val catSyncId = doc.getString("categorySyncId") ?: ""
                    val resolvedCatId = if (catSyncId.isBlank()) null else categoryMap[catSyncId]?.id ?: continue
                    val month = (doc.getLong("month") ?: continue).toInt()
                    val year = (doc.getLong("year") ?: continue).toInt()
                    val limitPaise = doc.getLong("limitPaise") ?: continue
                    val remoteUpdatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()

                    val existing = localBySync[syncId]
                    if (existing == null) {
                        dao.insert(
                            BudgetEntity(
                                workspaceId = workspaceId,
                                categoryId = resolvedCatId,
                                month = month,
                                year = year,
                                limitPaise = limitPaise,
                                syncId = syncId,
                                hasBeenSynced = true,
                                updatedAt = remoteUpdatedAt
                            )
                        )
                    } else if (!existing.hasBeenSynced || remoteUpdatedAt >= existing.updatedAt) {
                        if (existing.limitPaise != limitPaise ||
                            existing.categoryId != resolvedCatId ||
                            existing.month != month ||
                            existing.year != year ||
                            !existing.hasBeenSynced
                        ) {
                            dao.update(
                                existing.copy(
                                    workspaceId = workspaceId,
                                    categoryId = resolvedCatId,
                                    month = month,
                                    year = year,
                                    limitPaise = limitPaise,
                                    hasBeenSynced = true,
                                    updatedAt = maxOf(existing.updatedAt, remoteUpdatedAt)
                                )
                            )
                        }
                    }
                }

                if (!snap.metadata.hasPendingWrites()) {
                    for (local in localBySync.values) {
                        if (local.hasBeenSynced && local.syncId !in remoteIds) {
                            dao.deleteById(local.id)
                        }
                    }
                }
            }
            updateSyncStatusFromMetadata(snap.metadata.isFromCache)
        }
    }

    private suspend fun applyRemoteSavingsGoals(uid: String, snap: QuerySnapshot) {
        if (activeUid != uid) return
        mergeMutex.withLock {
            database.withTransaction {
                val dao = database.savingsGoalDao()
                val localBySync = dao.getAllSnapshot().associateBy { it.syncId }
                val remoteIds = HashSet<String>(snap.documents.size)

                for (doc in snap.documents) {
                    val syncId = doc.id
                    remoteIds.add(syncId)
                    val workspaceId = doc.getString("workspaceId") ?: continue
                    val name = doc.getString("name") ?: continue
                    val isEmergency = doc.getBoolean("isEmergencyFund") ?: false
                    val targetPaise = doc.getLong("targetPaise") ?: continue
                    val currentSaved = doc.getLong("currentSavedPaise") ?: 0L
                    val monthlyTarget = doc.getLong("monthlyTargetPaise") ?: 0L
                    val remoteUpdatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()

                    val existing = localBySync[syncId]
                    if (existing == null) {
                        dao.insert(
                            SavingsGoalEntity(
                                workspaceId = workspaceId,
                                nameEncrypted = cryptoEngine.encryptString(name),
                                isEmergencyFund = isEmergency,
                                targetPaise = targetPaise,
                                currentSavedPaise = currentSaved,
                                monthlyTargetPaise = monthlyTarget,
                                syncId = syncId,
                                hasBeenSynced = true,
                                updatedAt = remoteUpdatedAt
                            )
                        )
                    } else if (!existing.hasBeenSynced || remoteUpdatedAt >= existing.updatedAt) {
                        val existingName = cryptoEngine.decryptString(existing.nameEncrypted)
                        if (existingName != name ||
                            existing.isEmergencyFund != isEmergency ||
                            existing.targetPaise != targetPaise ||
                            existing.currentSavedPaise != currentSaved ||
                            existing.monthlyTargetPaise != monthlyTarget ||
                            !existing.hasBeenSynced
                        ) {
                            dao.update(
                                existing.copy(
                                    workspaceId = workspaceId,
                                    nameEncrypted = cryptoEngine.encryptString(name),
                                    isEmergencyFund = isEmergency,
                                    targetPaise = targetPaise,
                                    currentSavedPaise = currentSaved,
                                    monthlyTargetPaise = monthlyTarget,
                                    hasBeenSynced = true,
                                    updatedAt = maxOf(existing.updatedAt, remoteUpdatedAt)
                                )
                            )
                        }
                    }
                }

                if (!snap.metadata.hasPendingWrites()) {
                    for (local in localBySync.values) {
                        if (local.hasBeenSynced && local.syncId !in remoteIds) {
                            dao.deleteById(local.id)
                        }
                    }
                }
            }
            updateSyncStatusFromMetadata(snap.metadata.isFromCache)
        }
    }

    private suspend fun applyRemoteLoans(uid: String, snap: QuerySnapshot) {
        if (activeUid != uid) return
        mergeMutex.withLock {
            database.withTransaction {
                val dao = database.loanDao()
                val accountMap = database.accountDao().getAllSnapshot().associateBy { it.syncId }
                val localBySync = dao.getAllSnapshot().associateBy { it.syncId }
                val remoteIds = HashSet<String>(snap.documents.size)

                for (doc in snap.documents) {
                    val syncId = doc.id
                    remoteIds.add(syncId)
                    val workspaceId = doc.getString("workspaceId") ?: continue
                    val typeId = doc.getString("typeId") ?: continue
                    val personName = doc.getString("personName") ?: continue
                    val principalPaise = doc.getLong("principalPaise") ?: continue
                    val repaidPaise = doc.getLong("repaidPaise") ?: 0L
                    val accountSyncId = doc.getString("accountSyncId") ?: continue
                    val resolvedAccId = accountMap[accountSyncId]?.id ?: continue
                    val dateMillis = doc.getLong("dateMillis") ?: continue
                    val expectedDate = (doc.getLong("expectedRepaymentDateMillis") ?: 0L).takeIf { it > 0L }
                    val notes = doc.getString("notes") ?: ""
                    val remoteUpdatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()

                    val existing = localBySync[syncId]
                    if (existing == null) {
                        dao.insert(
                            LoanEntity(
                                workspaceId = workspaceId,
                                typeId = typeId,
                                personNameEncrypted = cryptoEngine.encryptString(personName),
                                principalPaise = principalPaise,
                                repaidPaise = repaidPaise,
                                accountId = resolvedAccId,
                                dateMillis = dateMillis,
                                expectedRepaymentDateMillis = expectedDate,
                                notesEncrypted = cryptoEngine.encryptString(notes),
                                syncId = syncId,
                                hasBeenSynced = true,
                                updatedAt = remoteUpdatedAt
                            )
                        )
                    } else if (!existing.hasBeenSynced || remoteUpdatedAt >= existing.updatedAt) {
                        val existingName = cryptoEngine.decryptString(existing.personNameEncrypted)
                        val existingNotes = cryptoEngine.decryptString(existing.notesEncrypted)
                        if (existingName != personName ||
                            existing.repaidPaise != repaidPaise ||
                            existing.principalPaise != principalPaise ||
                            existingNotes != notes ||
                            !existing.hasBeenSynced
                        ) {
                            dao.update(
                                existing.copy(
                                    workspaceId = workspaceId,
                                    typeId = typeId,
                                    personNameEncrypted = cryptoEngine.encryptString(personName),
                                    principalPaise = principalPaise,
                                    repaidPaise = repaidPaise,
                                    accountId = resolvedAccId,
                                    dateMillis = dateMillis,
                                    expectedRepaymentDateMillis = expectedDate,
                                    notesEncrypted = cryptoEngine.encryptString(notes),
                                    hasBeenSynced = true,
                                    updatedAt = maxOf(existing.updatedAt, remoteUpdatedAt)
                                )
                            )
                        }
                    }
                }

                if (!snap.metadata.hasPendingWrites()) {
                    for (local in localBySync.values) {
                        if (local.hasBeenSynced && local.syncId !in remoteIds) {
                            database.loanRepaymentDao().deleteByLoanId(local.id)
                            dao.deleteById(local.id)
                        }
                    }
                }
            }
            updateSyncStatusFromMetadata(snap.metadata.isFromCache)
        }
        latestRepaymentsSnap?.let { applyRemoteLoanRepayments(uid, it) }
    }

    private suspend fun applyRemoteLoanRepayments(uid: String, snap: QuerySnapshot) {
        if (activeUid != uid) return
        mergeMutex.withLock {
            database.withTransaction {
                val dao = database.loanRepaymentDao()
                val loanMap = database.loanDao().getAllSnapshot().associateBy { it.syncId }
                val accountMap = database.accountDao().getAllSnapshot().associateBy { it.syncId }
                val localBySync = dao.getAllSnapshot().associateBy { it.syncId }
                val remoteIds = HashSet<String>(snap.documents.size)

                for (doc in snap.documents) {
                    val syncId = doc.id
                    remoteIds.add(syncId)
                    val loanSyncId = doc.getString("loanSyncId") ?: continue
                    val resolvedLoanId = loanMap[loanSyncId]?.id ?: continue
                    val amountPaise = doc.getLong("amountPaise") ?: continue
                    val dateMillis = doc.getLong("dateMillis") ?: continue
                    val accountSyncId = doc.getString("accountSyncId") ?: continue
                    val resolvedAccId = accountMap[accountSyncId]?.id ?: continue
                    val notes = doc.getString("notes") ?: ""
                    val remoteUpdatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()

                    val existing = localBySync[syncId]
                    if (existing == null) {
                        dao.insert(
                            LoanRepaymentEntity(
                                loanId = resolvedLoanId,
                                amountPaise = amountPaise,
                                dateMillis = dateMillis,
                                accountId = resolvedAccId,
                                notesEncrypted = cryptoEngine.encryptString(notes),
                                syncId = syncId,
                                hasBeenSynced = true,
                                updatedAt = remoteUpdatedAt
                            )
                        )
                    } else if (!existing.hasBeenSynced) {
                        dao.markSynced(syncId)
                    }
                }

                if (!snap.metadata.hasPendingWrites()) {
                    for (local in localBySync.values) {
                        if (local.hasBeenSynced && local.syncId !in remoteIds) {
                            dao.deleteById(local.id)
                        }
                    }
                }
            }
            updateSyncStatusFromMetadata(snap.metadata.isFromCache)
        }
    }

    private suspend fun applyRemoteTransactions(uid: String, snap: QuerySnapshot) {
        if (activeUid != uid) return
        mergeMutex.withLock {
            database.withTransaction {
                val dao = database.transactionDao()
                val accountMap = database.accountDao().getAllSnapshot().associateBy { it.syncId }
                val categoryMap = database.categoryDao().getAllSnapshot().associateBy { it.syncId }
                val localBySync = dao.getAllSnapshot().associateBy { it.syncId }
                val remoteIds = HashSet<String>(snap.documents.size)

                for (doc in snap.documents) {
                    val syncId = doc.id
                    remoteIds.add(syncId)
                    val workspaceId = doc.getString("workspaceId") ?: continue
                    val typeId = doc.getString("typeId") ?: continue
                    val amountPaise = doc.getLong("amountPaise") ?: continue
                    val dateMillis = doc.getLong("dateMillis") ?: continue
                    val catSyncId = doc.getString("categorySyncId") ?: ""
                    val accSyncId = doc.getString("accountSyncId") ?: continue
                    val toAccSyncId = doc.getString("toAccountSyncId") ?: ""
                    val description = doc.getString("description") ?: ""
                    val createdAtMillis = doc.getLong("createdAtMillis") ?: dateMillis
                    val updatedAtMillis = doc.getLong("updatedAtMillis") ?: createdAtMillis

                    val resolvedAccountId = accountMap[accSyncId]?.id ?: continue
                    val resolvedToAccountId = if (toAccSyncId.isBlank()) null else accountMap[toAccSyncId]?.id ?: continue
                    val resolvedCategoryId = if (catSyncId.isBlank()) null else categoryMap[catSyncId]?.id

                    val existing = localBySync[syncId]
                    if (existing == null) {
                        dao.insert(
                            TransactionEntity(
                                workspaceId = workspaceId,
                                typeId = typeId,
                                amountPaise = amountPaise,
                                dateMillis = dateMillis,
                                categoryId = resolvedCategoryId,
                                accountId = resolvedAccountId,
                                toAccountId = resolvedToAccountId,
                                descriptionEncrypted = cryptoEngine.encryptString(description),
                                receiptPath = null,
                                createdAt = createdAtMillis,
                                updatedAt = updatedAtMillis,
                                syncId = syncId,
                                hasBeenSynced = true
                            )
                        )
                    } else if (!existing.hasBeenSynced || updatedAtMillis >= existing.updatedAt) {
                        val existingDesc = cryptoEngine.decryptString(existing.descriptionEncrypted)
                        if (existing.amountPaise != amountPaise ||
                            existing.dateMillis != dateMillis ||
                            existing.workspaceId != workspaceId ||
                            existing.typeId != typeId ||
                            existing.categoryId != resolvedCategoryId ||
                            existing.accountId != resolvedAccountId ||
                            existing.toAccountId != resolvedToAccountId ||
                            existingDesc != description ||
                            !existing.hasBeenSynced
                        ) {
                            dao.update(
                                existing.copy(
                                    workspaceId = workspaceId,
                                    typeId = typeId,
                                    amountPaise = amountPaise,
                                    dateMillis = dateMillis,
                                    categoryId = resolvedCategoryId,
                                    accountId = resolvedAccountId,
                                    toAccountId = resolvedToAccountId,
                                    descriptionEncrypted = cryptoEngine.encryptString(description),
                                    updatedAt = updatedAtMillis,
                                    hasBeenSynced = true
                                )
                            )
                        }
                    }
                }

                if (!snap.metadata.hasPendingWrites()) {
                    for (local in localBySync.values) {
                        if (local.hasBeenSynced && local.syncId !in remoteIds) {
                            dao.deleteById(local.id)
                        }
                    }
                }
            }
            updateSyncStatusFromMetadata(snap.metadata.isFromCache)
        }
    }

    private suspend fun uploadPendingLocalRecords(uid: String) {
        val accounts = database.accountDao().getAllSnapshot()
        for (acc in accounts) {
            if (!acc.hasBeenSynced || acc.syncId.isBlank()) {
                val ensured = ensureSyncId(acc)
                upsertAccountDoc(uid, ensured)
            }
        }

        val categories = database.categoryDao().getAllSnapshot()
        for (cat in categories) {
            if (!cat.hasBeenSynced || cat.syncId.isBlank()) {
                val ensured = ensureSyncId(cat)
                upsertCategoryDoc(uid, ensured)
            }
        }

        val freshAccMap = database.accountDao().getAllSnapshot().associate { it.id to it.syncId }
        val freshCatMap = database.categoryDao().getAllSnapshot().associate { it.id to it.syncId }

        for (budget in database.budgetDao().getAllSnapshot()) {
            if (!budget.hasBeenSynced) {
                upsertBudgetDoc(uid, budget, freshCatMap)
            }
        }

        for (goal in database.savingsGoalDao().getAllSnapshot()) {
            if (!goal.hasBeenSynced) {
                upsertGoalDoc(uid, goal)
            }
        }

        for (loan in database.loanDao().getAllSnapshot()) {
            if (!loan.hasBeenSynced) {
                upsertLoanDoc(uid, loan, freshAccMap)
            }
        }

        val freshLoanMap = database.loanDao().getAllSnapshot().associate { it.id to it.syncId }
        for (repayment in database.loanRepaymentDao().getAllSnapshot()) {
            if (!repayment.hasBeenSynced) {
                upsertLoanRepaymentDoc(uid, repayment, freshLoanMap, freshAccMap)
            }
        }

        for (tx in database.transactionDao().getAllSnapshot()) {
            if (!tx.hasBeenSynced) {
                upsertTransactionDoc(uid, tx, freshAccMap, freshCatMap)
            }
        }
    }

    private suspend fun ensureSyncId(acc: AccountEntity): AccountEntity {
        if (acc.syncId.isNotBlank()) return acc
        val newSyncId = if (acc.isDefault) {
            DefaultSeeds.defaultAccountSyncId(acc.workspaceId, acc.typeId)
        } else {
            "acc_${acc.id}"
        }
        val updated = acc.copy(syncId = newSyncId)
        database.accountDao().update(updated)
        return updated
    }

    private suspend fun ensureSyncId(cat: CategoryEntity): CategoryEntity {
        if (cat.syncId.isNotBlank()) return cat
        val newSyncId = if (cat.isDefault) {
            DefaultSeeds.defaultCategorySyncId(cat.workspaceId, cat.typeId, cat.name)
        } else {
            "cat_${cat.id}"
        }
        val updated = cat.copy(syncId = newSyncId)
        database.categoryDao().update(updated)
        return updated
    }

    override fun onAccountSaved(account: AccountEntity) {
        val uid = activeUid ?: return
        scope.launch {
            runCatching { upsertAccountDoc(uid, account) }
                .onFailure { Log.w("FirestoreSyncManager", "onAccountSaved failed", it) }
        }
    }

    override fun onAccountDeleted(syncId: String) {
        val uid = activeUid ?: return
        if (syncId.isBlank()) return
        scope.launch {
            runCatching {
                firestore.collection("users").document(uid)
                    .collection("accounts").document(syncId).delete().await()
            }
        }
    }

    override fun onCategorySaved(category: CategoryEntity) {
        val uid = activeUid ?: return
        scope.launch {
            runCatching { upsertCategoryDoc(uid, category) }
                .onFailure { Log.w("FirestoreSyncManager", "onCategorySaved failed", it) }
        }
    }

    override fun onCategoryDeleted(syncId: String) {
        val uid = activeUid ?: return
        if (syncId.isBlank()) return
        scope.launch {
            runCatching {
                firestore.collection("users").document(uid)
                    .collection("categories").document(syncId).delete().await()
            }
        }
    }

    override fun onTransactionSaved(tx: TransactionEntity) {
        val uid = activeUid ?: return
        scope.launch {
            runCatching {
                val accMap = database.accountDao().getAllSnapshot().associate { it.id to it.syncId }
                val catMap = database.categoryDao().getAllSnapshot().associate { it.id to it.syncId }
                upsertTransactionDoc(uid, tx, accMap, catMap)
            }.onFailure { Log.w("FirestoreSyncManager", "onTransactionSaved failed", it) }
        }
    }

    override fun onTransactionDeleted(syncId: String) {
        val uid = activeUid ?: return
        if (syncId.isBlank()) return
        scope.launch {
            runCatching {
                firestore.collection("users").document(uid)
                    .collection("transactions").document(syncId).delete().await()
            }
        }
    }

    override fun onBudgetSaved(budget: BudgetEntity) {
        val uid = activeUid ?: return
        scope.launch {
            runCatching {
                val catMap = database.categoryDao().getAllSnapshot().associate { it.id to it.syncId }
                upsertBudgetDoc(uid, budget, catMap)
            }.onFailure { Log.w("FirestoreSyncManager", "onBudgetSaved failed", it) }
        }
    }

    override fun onBudgetDeleted(syncId: String) {
        val uid = activeUid ?: return
        if (syncId.isBlank()) return
        scope.launch {
            runCatching {
                firestore.collection("users").document(uid)
                    .collection("budgets").document(syncId).delete().await()
            }
        }
    }

    override fun onSavingsGoalSaved(goal: SavingsGoalEntity) {
        val uid = activeUid ?: return
        scope.launch {
            runCatching { upsertGoalDoc(uid, goal) }
                .onFailure { Log.w("FirestoreSyncManager", "onSavingsGoalSaved failed", it) }
        }
    }

    override fun onSavingsGoalDeleted(syncId: String) {
        val uid = activeUid ?: return
        if (syncId.isBlank()) return
        scope.launch {
            runCatching {
                firestore.collection("users").document(uid)
                    .collection("savings_goals").document(syncId).delete().await()
            }
        }
    }

    override fun onLoanSaved(loan: LoanEntity) {
        val uid = activeUid ?: return
        scope.launch {
            runCatching {
                val accMap = database.accountDao().getAllSnapshot().associate { it.id to it.syncId }
                upsertLoanDoc(uid, loan, accMap)
            }.onFailure { Log.w("FirestoreSyncManager", "onLoanSaved failed", it) }
        }
    }

    override fun onLoanRepaymentSaved(repayment: LoanRepaymentEntity) {
        val uid = activeUid ?: return
        scope.launch {
            runCatching {
                val loanMap = database.loanDao().getAllSnapshot().associate { it.id to it.syncId }
                val accMap = database.accountDao().getAllSnapshot().associate { it.id to it.syncId }
                upsertLoanRepaymentDoc(uid, repayment, loanMap, accMap)
            }.onFailure { Log.w("FirestoreSyncManager", "onLoanRepaymentSaved failed", it) }
        }
    }

    override fun onLoanDeleted(loanSyncId: String, repaymentSyncIds: List<String>) {
        val uid = activeUid ?: return
        scope.launch {
            runCatching {
                val userDoc = firestore.collection("users").document(uid)
                repaymentSyncIds.filter { it.isNotBlank() }.forEach { repId ->
                    userDoc.collection("loan_repayments").document(repId).delete().await()
                }
                if (loanSyncId.isNotBlank()) {
                    userDoc.collection("loans").document(loanSyncId).delete().await()
                }
            }
        }
    }

    fun pushPreferencesUpdate(prefs: AppPreferences) {
        val uid = activeUid ?: return
        scope.launch {
            runCatching {
                val docRef = firestore.collection("users").document(uid)
                val snap = docRef.get().await()
                if (snap.exists()) {
                    docRef.update(
                        mapOf(
                            "personalWorkspaceName" to prefs.personalWorkspaceName.ifBlank { "Personal" }.take(60),
                            "businessWorkspaceName" to prefs.businessWorkspaceName.ifBlank { "GVVS Business" }.take(60),
                            "currencySymbol" to prefs.currencySymbol.ifBlank { "₹" }.take(8),
                            "currencyCode" to prefs.currencyCode.ifBlank { "INR" }.take(10),
                            "dateFormatId" to prefs.dateFormat.id,
                            "financialYearStartMonth" to prefs.financialYearStartMonth.coerceIn(1, 12),
                            "themeModeId" to prefs.themeMode.id,
                            "lastSyncedDevice" to deviceName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }.onFailure { Log.w("FirestoreSyncManager", "pushPreferencesUpdate failed", it) }
        }
    }

    private suspend fun upsertAccountDoc(uid: String, entity: AccountEntity) {
        if (entity.syncId.isBlank()) return
        val docRef = firestore.collection("users").document(uid)
            .collection("accounts").document(entity.syncId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.update(
                mapOf(
                    "name" to entity.name.take(80),
                    "workspaceId" to entity.workspaceId,
                    "typeId" to entity.typeId,
                    "openingBalancePaise" to entity.openingBalancePaise,
                    "isDefault" to entity.isDefault,
                    "createdAtMillis" to entity.createdAt.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                hashMapOf(
                    "name" to entity.name.take(80),
                    "workspaceId" to entity.workspaceId,
                    "typeId" to entity.typeId,
                    "openingBalancePaise" to entity.openingBalancePaise,
                    "isDefault" to entity.isDefault,
                    "createdAtMillis" to entity.createdAt.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        database.accountDao().markSynced(entity.syncId)
    }

    private suspend fun upsertCategoryDoc(uid: String, entity: CategoryEntity) {
        if (entity.syncId.isBlank()) return
        val docRef = firestore.collection("users").document(uid)
            .collection("categories").document(entity.syncId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.update(
                mapOf(
                    "name" to entity.name.take(80),
                    "workspaceId" to entity.workspaceId,
                    "typeId" to entity.typeId,
                    "colorHex" to entity.colorHex,
                    "iconName" to entity.iconName.take(60),
                    "isDefault" to entity.isDefault,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                hashMapOf(
                    "name" to entity.name.take(80),
                    "workspaceId" to entity.workspaceId,
                    "typeId" to entity.typeId,
                    "colorHex" to entity.colorHex,
                    "iconName" to entity.iconName.take(60),
                    "isDefault" to entity.isDefault,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        database.categoryDao().markSynced(entity.syncId)
    }

    private suspend fun upsertTransactionDoc(
        uid: String,
        entity: TransactionEntity,
        accMap: Map<Long, String>,
        catMap: Map<Long, String>
    ) {
        if (entity.syncId.isBlank()) return
        val accountSyncId = accMap[entity.accountId]?.takeIf { it.isNotBlank() } ?: return
        val toAccountSyncId = entity.toAccountId?.let { accMap[it] } ?: ""
        val categorySyncId = entity.categoryId?.let { catMap[it] } ?: ""
        val description = cryptoEngine.decryptString(entity.descriptionEncrypted).take(500)

        val docRef = firestore.collection("users").document(uid)
            .collection("transactions").document(entity.syncId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.update(
                mapOf(
                    "workspaceId" to entity.workspaceId,
                    "typeId" to entity.typeId,
                    "amountPaise" to entity.amountPaise,
                    "dateMillis" to entity.dateMillis.coerceAtLeast(1L),
                    "categorySyncId" to categorySyncId,
                    "accountSyncId" to accountSyncId,
                    "toAccountSyncId" to toAccountSyncId,
                    "description" to description,
                    "createdAtMillis" to entity.createdAt.coerceAtLeast(0L),
                    "updatedAtMillis" to entity.updatedAt.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                hashMapOf(
                    "workspaceId" to entity.workspaceId,
                    "typeId" to entity.typeId,
                    "amountPaise" to entity.amountPaise,
                    "dateMillis" to entity.dateMillis.coerceAtLeast(1L),
                    "categorySyncId" to categorySyncId,
                    "accountSyncId" to accountSyncId,
                    "toAccountSyncId" to toAccountSyncId,
                    "description" to description,
                    "createdAtMillis" to entity.createdAt.coerceAtLeast(0L),
                    "updatedAtMillis" to entity.updatedAt.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        database.transactionDao().markSynced(entity.syncId)
    }

    private suspend fun upsertBudgetDoc(
        uid: String,
        entity: BudgetEntity,
        catMap: Map<Long, String>
    ) {
        if (entity.syncId.isBlank()) return
        val categorySyncId = entity.categoryId?.let { catMap[it] } ?: ""
        val docRef = firestore.collection("users").document(uid)
            .collection("budgets").document(entity.syncId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.update(
                mapOf(
                    "workspaceId" to entity.workspaceId,
                    "categorySyncId" to categorySyncId,
                    "month" to entity.month,
                    "year" to entity.year,
                    "limitPaise" to entity.limitPaise,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                hashMapOf(
                    "workspaceId" to entity.workspaceId,
                    "categorySyncId" to categorySyncId,
                    "month" to entity.month,
                    "year" to entity.year,
                    "limitPaise" to entity.limitPaise,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        database.budgetDao().markSynced(entity.syncId)
    }

    private suspend fun upsertGoalDoc(uid: String, entity: SavingsGoalEntity) {
        if (entity.syncId.isBlank()) return
        val name = cryptoEngine.decryptString(entity.nameEncrypted).ifBlank { "Goal" }.take(120)
        val docRef = firestore.collection("users").document(uid)
            .collection("savings_goals").document(entity.syncId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.update(
                mapOf(
                    "workspaceId" to entity.workspaceId,
                    "name" to name,
                    "isEmergencyFund" to entity.isEmergencyFund,
                    "targetPaise" to entity.targetPaise,
                    "currentSavedPaise" to entity.currentSavedPaise,
                    "monthlyTargetPaise" to entity.monthlyTargetPaise,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                hashMapOf(
                    "workspaceId" to entity.workspaceId,
                    "name" to name,
                    "isEmergencyFund" to entity.isEmergencyFund,
                    "targetPaise" to entity.targetPaise,
                    "currentSavedPaise" to entity.currentSavedPaise,
                    "monthlyTargetPaise" to entity.monthlyTargetPaise,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        database.savingsGoalDao().markSynced(entity.syncId)
    }

    private suspend fun upsertLoanDoc(
        uid: String,
        entity: LoanEntity,
        accMap: Map<Long, String>
    ) {
        if (entity.syncId.isBlank()) return
        val accountSyncId = accMap[entity.accountId]?.takeIf { it.isNotBlank() } ?: return
        val personName = cryptoEngine.decryptString(entity.personNameEncrypted).ifBlank { "Contact" }.take(120)
        val notes = cryptoEngine.decryptString(entity.notesEncrypted).take(500)
        val expectedMillis = entity.expectedRepaymentDateMillis ?: 0L

        val docRef = firestore.collection("users").document(uid)
            .collection("loans").document(entity.syncId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.update(
                mapOf(
                    "workspaceId" to entity.workspaceId,
                    "typeId" to entity.typeId,
                    "personName" to personName,
                    "principalPaise" to entity.principalPaise,
                    "repaidPaise" to entity.repaidPaise,
                    "accountSyncId" to accountSyncId,
                    "dateMillis" to entity.dateMillis.coerceAtLeast(1L),
                    "expectedRepaymentDateMillis" to expectedMillis.coerceAtLeast(0L),
                    "notes" to notes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                hashMapOf(
                    "workspaceId" to entity.workspaceId,
                    "typeId" to entity.typeId,
                    "personName" to personName,
                    "principalPaise" to entity.principalPaise,
                    "repaidPaise" to entity.repaidPaise,
                    "accountSyncId" to accountSyncId,
                    "dateMillis" to entity.dateMillis.coerceAtLeast(1L),
                    "expectedRepaymentDateMillis" to expectedMillis.coerceAtLeast(0L),
                    "notes" to notes,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        database.loanDao().markSynced(entity.syncId)
    }

    private suspend fun upsertLoanRepaymentDoc(
        uid: String,
        entity: LoanRepaymentEntity,
        loanMap: Map<Long, String>,
        accMap: Map<Long, String>
    ) {
        if (entity.syncId.isBlank()) return
        val loanSyncId = loanMap[entity.loanId]?.takeIf { it.isNotBlank() } ?: return
        val accountSyncId = accMap[entity.accountId]?.takeIf { it.isNotBlank() } ?: return
        val notes = cryptoEngine.decryptString(entity.notesEncrypted).take(500)

        val docRef = firestore.collection("users").document(uid)
            .collection("loan_repayments").document(entity.syncId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.update(
                mapOf(
                    "loanSyncId" to loanSyncId,
                    "amountPaise" to entity.amountPaise,
                    "dateMillis" to entity.dateMillis.coerceAtLeast(1L),
                    "accountSyncId" to accountSyncId,
                    "notes" to notes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                hashMapOf(
                    "loanSyncId" to loanSyncId,
                    "amountPaise" to entity.amountPaise,
                    "dateMillis" to entity.dateMillis.coerceAtLeast(1L),
                    "accountSyncId" to accountSyncId,
                    "notes" to notes,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        database.loanRepaymentDao().markSynced(entity.syncId)
    }
}
