package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.crypto.ReceiptStorageManager
import com.example.data.export.CurrencyFormatter
import com.example.domain.model.Account
import com.example.domain.model.AppPreferences
import com.example.domain.model.Category
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import com.example.ui.theme.ChartPalette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormSheet(
    initialType: TransactionType,
    initialWorkspace: Workspace,
    editingRecord: TransactionRecord? = null,
    accounts: List<Account>,
    categories: List<Category>,
    preferences: AppPreferences,
    receiptStorageManager: ReceiptStorageManager?,
    onAddCustomCategory: (String, Workspace, TransactionType, Long) -> Unit,
    onSave: (
        existingId: Long?,
        workspace: Workspace,
        type: TransactionType,
        amountPaise: Long,
        dateMillis: Long,
        categoryId: Long?,
        accountId: Long,
        toAccountId: Long?,
        description: String,
        newReceiptUri: Uri?,
        existingReceiptPath: String?,
        removeExistingReceipt: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf(editingRecord?.type ?: initialType) }
    var selectedWorkspace by remember { mutableStateOf(editingRecord?.workspace ?: initialWorkspace) }
    var amountText by remember {
        mutableStateOf(editingRecord?.let { CurrencyFormatter.paiseToEditableString(it.amountPaise) } ?: "")
    }
    var dateMillis by remember { mutableStateOf(editingRecord?.dateMillis ?: System.currentTimeMillis()) }
    var description by remember { mutableStateOf(editingRecord?.description ?: "") }

    val wsAccounts = remember(accounts, selectedWorkspace) {
        accounts.filter { it.workspace == selectedWorkspace }
    }
    val wsCategories = remember(categories, selectedWorkspace, selectedType) {
        categories.filter { it.workspace == selectedWorkspace && it.type == selectedType }
    }

    var selectedAccountId by remember(wsAccounts) {
        mutableStateOf(
            editingRecord?.takeIf { it.workspace == selectedWorkspace }?.accountId
                ?: wsAccounts.firstOrNull()?.id
                ?: 0L
        )
    }
    var selectedToAccountId by remember(wsAccounts) {
        mutableStateOf(
            editingRecord?.takeIf { it.workspace == selectedWorkspace }?.toAccountId
                ?: wsAccounts.getOrNull(1)?.id
                ?: wsAccounts.firstOrNull()?.id
                ?: 0L
        )
    }
    var selectedCategoryId by remember(wsCategories) {
        mutableStateOf(
            editingRecord?.takeIf { it.workspace == selectedWorkspace && it.type == selectedType }?.categoryId
                ?: wsCategories.firstOrNull()?.id
        )
    }

    var newReceiptUri by remember { mutableStateOf<Uri?>(null) }
    var removeExistingReceipt by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                newReceiptUri = uri
                removeExistingReceipt = false
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingRecord != null) "Edit ${selectedType.label}" else "Record ${selectedType.label}",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // Transaction Type Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionType.entries.forEach { t ->
                    FilterChip(
                        selected = selectedType == t,
                        onClick = {
                            selectedType = t
                            val matchingCats = categories.filter { it.workspace == selectedWorkspace && it.type == t }
                            selectedCategoryId = matchingCats.firstOrNull()?.id
                        },
                        label = { Text(t.label) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tx_type_${t.name.lowercase()}")
                    )
                }
            }

            // Workspace Selector (Strictly Separate Personal vs GVVS Business)
            Text(
                text = "Workspace",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Workspace.entries.forEach { ws ->
                    FilterChip(
                        selected = selectedWorkspace == ws,
                        onClick = {
                            selectedWorkspace = ws
                            val nextAccs = accounts.filter { it.workspace == ws }
                            selectedAccountId = nextAccs.firstOrNull()?.id ?: 0L
                            selectedToAccountId = nextAccs.getOrNull(1)?.id ?: nextAccs.firstOrNull()?.id ?: 0L
                            val nextCats = categories.filter { it.workspace == ws && it.type == selectedType }
                            selectedCategoryId = nextCats.firstOrNull()?.id
                        },
                        label = { Text(preferences.workspaceLabel(ws)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tx_ws_${ws.name.lowercase()}")
                    )
                }
            }

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        amountText = input
                        errorText = null
                    }
                },
                label = { Text("Amount (${preferences.currencySymbol})") },
                prefix = { Text("${preferences.currencySymbol} ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_amount_input")
            )

            // Date Selector
            OutlinedTextField(
                value = CurrencyFormatter.formatDate(dateMillis, preferences.dateFormat),
                onValueChange = {},
                readOnly = true,
                label = { Text("Transaction Date") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarToday, contentDescription = "Pick date")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            )

            // Account / Payment Method Dropdown
            AccountDropdown(
                label = if (selectedType == TransactionType.TRANSFER) "From Account" else "Payment Method / Account",
                accounts = wsAccounts,
                selectedId = selectedAccountId,
                onSelect = { selectedAccountId = it }
            )

            if (selectedType == TransactionType.TRANSFER) {
                AccountDropdown(
                    label = "To Account",
                    accounts = wsAccounts,
                    selectedId = selectedToAccountId,
                    onSelect = { selectedToAccountId = it }
                )
            } else {
                // Category Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedType.label} Category",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Category")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    wsCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }

            // Description / Notes
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Notes (Encrypted locally)") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_description_input")
            )

            // Receipt Attachment (Android Photo Picker - zero permissions)
            if (selectedType != TransactionType.TRANSFER) {
                val hasExisting = !editingRecord?.receiptPath.isNullOrBlank() && !removeExistingReceipt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (newReceiptUri != null || hasExisting) "Change Receipt" else "Attach Receipt")
                    }
                    if (newReceiptUri != null || hasExisting) {
                        TextButton(
                            onClick = {
                                newReceiptUri = null
                                removeExistingReceipt = true
                            }
                        ) {
                            Text("Remove")
                        }
                    }
                }

                if (newReceiptUri != null) {
                    AsyncImage(
                        model = newReceiptUri,
                        contentDescription = "Selected receipt preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else if (hasExisting && receiptStorageManager != null) {
                    val decryptedBytes = remember(editingRecord?.receiptPath) {
                        editingRecord?.receiptPath?.let { receiptStorageManager.readDecryptedReceipt(it) }
                    }
                    val bitmap = remember(decryptedBytes) {
                        decryptedBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Saved encrypted receipt",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            if (errorText != null) {
                Text(
                    text = errorText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val paise = CurrencyFormatter.parseRupeesToPaise(amountText)
                    when {
                        paise == null || paise <= 0L -> errorText = "Enter a valid amount greater than zero."
                        selectedAccountId == 0L -> errorText = "Please select an account."
                        selectedType == TransactionType.TRANSFER && selectedToAccountId == selectedAccountId ->
                            errorText = "Source and destination accounts must be different."
                        selectedType != TransactionType.TRANSFER && selectedCategoryId == null ->
                            errorText = "Please select a category."
                        else -> {
                            onSave(
                                editingRecord?.id,
                                selectedWorkspace,
                                selectedType,
                                paise,
                                dateMillis,
                                selectedCategoryId,
                                selectedAccountId,
                                if (selectedType == TransactionType.TRANSFER) selectedToAccountId else null,
                                description,
                                newReceiptUri,
                                editingRecord?.receiptPath,
                                removeExistingReceipt
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("tx_save_button")
            ) {
                Text(
                    text = if (editingRecord != null) "Update ${selectedType.label}" else "Save ${selectedType.label}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { dateMillis = it }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            workspace = selectedWorkspace,
            type = selectedType,
            onAdd = { name, color ->
                onAddCustomCategory(name, selectedWorkspace, selectedType, color)
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDropdown(
    label: String,
    accounts: List<Account>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId } ?: accounts.firstOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected?.let { "${it.name} (${it.type.label})" } ?: "Select Account",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text("${acc.name} (${acc.type.label})") },
                    onClick = {
                        onSelect(acc.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AddCategoryDialog(
    workspace: Workspace,
    type: TransactionType,
    onAdd: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0xFF167B59L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ${type.label} Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color Accent", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChartPalette.take(6).forEach { color ->
                        val hex = color.value.toLong()
                        val isChosen = Color(selectedColor) == color
                        Surface(
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { selectedColor = colorToArgbLong(color) },
                            shape = RoundedCornerShape(50),
                            color = color,
                            border = if (isChosen) androidx.compose.foundation.BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface) else null
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onAdd(name.trim(), selectedColor)
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun colorToArgbLong(color: Color): Long {
    val a = (color.alpha * 255).toInt() and 0xFF
    val r = (color.red * 255).toInt() and 0xFF
    val g = (color.green * 255).toInt() and 0xFF
    val b = (color.blue * 255).toInt() and 0xFF
    return ((a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
}

@Composable
fun EncryptedReceiptViewerDialog(
    receiptPath: String,
    receiptStorageManager: ReceiptStorageManager?,
    onDismiss: () -> Unit
) {
    val bytes = remember(receiptPath) {
        receiptStorageManager?.readDecryptedReceipt(receiptPath)
    }
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receipt Attachment") },
        text = {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Decrypted receipt",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Receipt file could not be loaded.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
