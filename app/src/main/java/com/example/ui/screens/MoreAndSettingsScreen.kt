package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.export.BackupManager
import com.example.data.export.CurrencyFormatter
import com.example.data.remote.CloudSyncStatus
import com.example.data.remote.SyncState
import com.example.domain.model.AppPreferences
import com.example.domain.model.Category
import com.example.domain.model.DateFormatOption
import com.example.domain.model.ThemeMode
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import com.example.ui.components.AddCategoryDialog
import com.example.ui.components.BabbalCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.security.BiometricAuthHelper
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreAndSettingsScreen(
    preferences: AppPreferences,
    categories: List<Category>,
    backupManager: BackupManager,
    currentUserEmail: String?,
    currentUserName: String?,
    cloudSyncStatus: CloudSyncStatus,
    isSigningIn: Boolean,
    onGoogleSignIn: (Context) -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToReports: () -> Unit,
    onUpdateProfileNames: (String, String) -> Unit,
    onUpdateCurrency: (String, String) -> Unit,
    onUpdateDateFormat: (DateFormatOption) -> Unit,
    onUpdateFyStartMonth: (Int) -> Unit,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onAddCategory: (String, Workspace, TransactionType, Long) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onSetBiometric: (Boolean) -> Unit,
    onSetAutoLockMinutes: (Int) -> Unit,
    onLockNow: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activityCtx = remember(context) { context.findActivityContext() }
    val scope = rememberCoroutineScope()

    var personalName by remember(preferences.personalWorkspaceName) { mutableStateOf(preferences.personalWorkspaceName) }
    var businessName by remember(preferences.businessWorkspaceName) { mutableStateOf(preferences.businessWorkspaceName) }

    var showPinDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var pendingEncryptedRestoreContent by remember { mutableStateOf<String?>(null) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val content = backupManager.createBackupJson(pendingBackupPassword)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }.onSuccess {
                    pendingBackupPassword = null
                    onNotify("Local backup saved successfully")
                }.onFailure {
                    pendingBackupPassword = null
                    onNotify("Backup failed: ${it.message}")
                }
            }
        }
    }

    val openRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Could not read backup file")
                    if (backupManager.isEncryptedBackup(raw)) {
                        pendingEncryptedRestoreContent = raw
                    } else {
                        backupManager.restoreFromBackup(raw, null).getOrThrow()
                    }
                }.onSuccess { count ->
                    if (count is Int) {
                        onNotify("Restored $count transaction(s) from backup")
                    }
                }.onFailure {
                    onNotify("Restore failed: ${it.message}")
                }
            }
        }
    }

    val biometricSupported = remember { BiometricAuthHelper.isBiometricAvailable(context) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Automatic PC & Phone Cloud Sync
        item {
            val isConnected = !currentUserEmail.isNullOrBlank() || cloudSyncStatus.syncState != SyncState.DISCONNECTED
            val badgeColor = when {
                !isConnected -> MaterialTheme.colorScheme.onSurfaceVariant
                cloudSyncStatus.syncState == SyncState.SYNCED -> IncomeGreen
                cloudSyncStatus.syncState == SyncState.SYNCING -> EmeraldPrimary
                cloudSyncStatus.syncState == SyncState.ERROR -> ExpenseRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = badgeColor
                        )
                        Text("PC & Phone Auto-Sync", style = MaterialTheme.typography.titleMedium)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isConnected) cloudSyncStatus.syncState.label else "Offline Mode",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (isConnected) {
                    Text(
                        text = buildString {
                            append("Signed in as ")
                            append(currentUserName?.takeIf { it.isNotBlank() }?.let { "$it ($currentUserEmail)" } ?: (currentUserEmail ?: "Google User"))
                            append(". Your Personal and GVVS Business records sync automatically across your PC and phone.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (cloudSyncStatus.lastSyncedAtMillis > 0L) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last synced: ${CurrencyFormatter.formatDate(cloudSyncStatus.lastSyncedAtMillis, preferences.dateFormat)}" +
                                if (cloudSyncStatus.lastSyncedDevice.isNotBlank()) " • ${cloudSyncStatus.lastSyncedDevice}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!cloudSyncStatus.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cloudSyncStatus.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseRed
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSyncNow,
                            enabled = cloudSyncStatus.syncState != SyncState.SYNCING,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sync_now_button")
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Now")
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out")
                        }
                    }
                } else {
                    Text(
                        text = "Sign in with your Google Account to enable automatic real-time sync between your PC and phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onGoogleSignIn(activityCtx) },
                        enabled = !isSigningIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_google_sign_in_button")
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Signing in…")
                        } else {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign in with Google to Sync")
                        }
                    }
                }
            }
        }

        // Quick Navigation Hub
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text("Finance Modules", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onNavigateToBudgets, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Savings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Budgets")
                    }
                    OutlinedButton(onClick = onNavigateToLoans, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Handshake, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lending")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onNavigateToCalendar, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Calendar")
                    }
                    OutlinedButton(onClick = onNavigateToReports, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reports")
                    }
                }
            }
        }

        // Local Backup & Restore
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text("Offline Backup & Restore", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Export an optional AES-256 encrypted JSON snapshot for offline safekeeping or manual transfer alongside automatic cloud sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (preferences.lastBackupTimestamp > 0L) {
                        "Last backup: ${CurrencyFormatter.formatDate(preferences.lastBackupTimestamp, preferences.dateFormat)}"
                    } else {
                        "No backup exported yet"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showBackupPasswordDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_backup_button")
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup")
                    }
                    OutlinedButton(
                        onClick = { openRestoreLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_backup_button")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore")
                    }
                }
            }
        }

        // Security & Privacy
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text("Security & Privacy", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PIN Lock", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (preferences.isPinEnabled) "Active (PBKDF2 salted hash)" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (preferences.isPinEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { showPinDialog = true }) { Text("Change") }
                            TextButton(onClick = onClearPin) { Text("Disable", color = ExpenseRed) }
                        }
                    } else {
                        Button(onClick = { showPinDialog = true }) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Enable PIN")
                        }
                    }
                }

                if (preferences.isPinEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Unlock", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (biometricSupported) "Fingerprint / Face unlock" else "Not configured on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.biometricEnabled,
                            enabled = biometricSupported,
                            onCheckedChange = onSetBiometric
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Auto-Lock After Inactivity", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            0 to "Immediate",
                            1 to "1 min",
                            5 to "5 min",
                            15 to "15 min",
                            -1 to "Never"
                        ).forEach { (mins, label) ->
                            FilterChip(
                                selected = preferences.autoLockMinutes == mins,
                                onClick = { onSetAutoLockMinutes(mins) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onLockNow, modifier = Modifier.fillMaxWidth()) {
                        Text("Lock Vault Now")
                    }
                }
            }
        }

        // Workspace Profiles
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text("Workspace Profiles", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = personalName,
                    onValueChange = { personalName = it },
                    label = { Text("Personal Workspace Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business Workspace Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onUpdateProfileNames(personalName, businessName) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Names")
                }
            }
        }

        // Appearance, Date Format, Currency, Financial Year
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text("Appearance & Regional Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))

                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onUpdateThemeMode(mode) },
                            label = { Text(mode.label.replace(" Mode", "")) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Date Format", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateFormatOption.entries.forEach { fmt ->
                        FilterChip(
                            selected = preferences.dateFormat == fmt,
                            onClick = { onUpdateDateFormat(fmt) },
                            label = { Text(fmt.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Currency", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("₹" to "INR", "$" to "USD", "€" to "EUR").forEach { (sym, code) ->
                        FilterChip(
                            selected = preferences.currencyCode == code,
                            onClick = { onUpdateCurrency(sym, code) },
                            label = { Text("$code ($sym)") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Financial Year Start", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferences.financialYearStartMonth == 4,
                        onClick = { onUpdateFyStartMonth(4) },
                        label = { Text("April (India FY)") }
                    )
                    FilterChip(
                        selected = preferences.financialYearStartMonth == 1,
                        onClick = { onUpdateFyStartMonth(1) },
                        label = { Text("January (Calendar)") }
                    )
                }
            }
        }

        // Custom Categories Management
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Custom Categories", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${categories.size} total income & expense categories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { showCategoriesDialog = true }) {
                        Text("Manage")
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        SetupPinDialog(
            onSave = { pin ->
                onSetPin(pin)
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showBackupPasswordDialog) {
        BackupExportDialog(
            onExport = { password ->
                pendingBackupPassword = password
                showBackupPasswordDialog = false
                createBackupLauncher.launch("BABBAL_Backup_${System.currentTimeMillis()}.json")
            },
            onDismiss = { showBackupPasswordDialog = false }
        )
    }

    pendingEncryptedRestoreContent?.let { raw ->
        DecryptBackupDialog(
            onDecrypt = { pwd ->
                scope.launch {
                    backupManager.restoreFromBackup(raw, pwd)
                        .onSuccess { count ->
                            pendingEncryptedRestoreContent = null
                            onNotify("Decrypted and restored $count transaction(s)")
                        }
                        .onFailure {
                            onNotify("Incorrect password or corrupted backup")
                        }
                }
            },
            onDismiss = { pendingEncryptedRestoreContent = null }
        )
    }

    if (showCategoriesDialog) {
        ManageCategoriesDialog(
            categories = categories,
            preferences = preferences,
            onAdd = onAddCategory,
            onRequestDelete = { deletingCategory = it },
            onDismiss = { showCategoriesDialog = false }
        )
    }

    deletingCategory?.let { cat ->
        ConfirmDeleteDialog(
            title = "Delete Category?",
            message = "Delete \"${cat.name}\"? Categories with existing transactions cannot be deleted.",
            onConfirm = {
                onDeleteCategory(cat)
                deletingCategory = null
            },
            onDismiss = { deletingCategory = null }
        )
    }
}

@Composable
private fun SetupPinDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Vault PIN (4–6 digits)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { pin = it; err = null } },
                    label = { Text("Enter PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { confirmPin = it; err = null } },
                    label = { Text("Confirm PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
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
                    when {
                        pin.length !in 4..6 -> err = "PIN must be 4 to 6 digits."
                        pin != confirmPin -> err = "PINs do not match."
                        else -> onSave(pin)
                    }
                }
            ) { Text("Save PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun BackupExportDialog(
    onExport: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Local Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Optionally enter a password to AES-256 encrypt your backup file, or leave blank for standard JSON.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Encryption Password (Optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(password.takeIf { it.isNotBlank() }) }) {
                Text("Choose Location")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DecryptBackupDialog(
    onDecrypt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encrypted Backup") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Enter Backup Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onDecrypt(password) }) { Text("Restore") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManageCategoriesDialog(
    categories: List<Category>,
    preferences: AppPreferences,
    onAdd: (String, Workspace, TransactionType, Long) -> Unit,
    onRequestDelete: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedWs by remember { mutableStateOf(Workspace.PERSONAL) }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var showAdd by remember { mutableStateOf(false) }

    val filtered = remember(categories, selectedWs, selectedType) {
        categories.filter { it.workspace == selectedWs && it.type == selectedType }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Categories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Workspace.entries.forEach { ws ->
                        FilterChip(
                            selected = selectedWs == ws,
                            onClick = { selectedWs = ws },
                            label = { Text(preferences.workspaceLabel(ws)) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = { Text(t.label) }
                        )
                    }
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(filtered.size) { idx ->
                        val cat = filtered[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat.name + if (cat.isDefault) "" else " (Custom)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRequestDelete(cat) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete category", tint = ExpenseRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Custom Category")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )

    if (showAdd) {
        AddCategoryDialog(
            workspace = selectedWs,
            type = selectedType,
            onAdd = { name, color ->
                onAdd(name, selectedWs, selectedType, color)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
}
