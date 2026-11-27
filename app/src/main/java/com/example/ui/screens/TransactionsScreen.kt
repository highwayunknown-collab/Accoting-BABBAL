package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.CurrencyFormatter
import com.example.domain.model.Account
import com.example.domain.model.AppPreferences
import com.example.domain.model.Category
import com.example.domain.model.SortOption
import com.example.domain.model.TransactionDisplay
import com.example.domain.model.TransactionFilterState
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.WorkspaceFilter
import com.example.ui.components.BabbalCard
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.MonthSelectorBar
import com.example.ui.components.TransactionRowCard
import com.example.ui.components.WorkspaceSelectorRow
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    transactions: List<TransactionDisplay>,
    allTransactions: List<TransactionDisplay>,
    filterState: TransactionFilterState,
    accounts: List<Account>,
    categories: List<Category>,
    preferences: AppPreferences,
    initialCalendarMode: Boolean = false,
    onUpdateFilter: ((TransactionFilterState) -> TransactionFilterState) -> Unit,
    onResetFilters: () -> Unit,
    onEditTransaction: (TransactionRecord) -> Unit,
    onDeleteTransaction: (TransactionRecord) -> Unit,
    onViewReceipt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var calendarMode by remember(initialCalendarMode) { mutableStateOf(initialCalendarMode) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TransactionRecord?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // List vs Calendar Mode toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !calendarMode,
                    onClick = { calendarMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Filled.FormatListBulleted, contentDescription = null) },
                    modifier = Modifier.testTag("mode_list_button")
                ) {
                    Text("All Transactions")
                }
                SegmentedButton(
                    selected = calendarMode,
                    onClick = { calendarMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    modifier = Modifier.testTag("mode_calendar_button")
                ) {
                    Text("Calendar View")
                }
            }

            if (!calendarMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filterState.searchQuery,
                        onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
                        placeholder = { Text("Search category, note, account...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filterState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "") } }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tx_search_input")
                    )
                    OutlinedButton(
                        onClick = { showFilterSheet = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("tx_filter_button")
                    ) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Filters")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Filter")
                    }
                }

                WorkspaceSelectorRow(
                    selected = filterState.workspaceFilter,
                    preferences = preferences,
                    onSelect = { ws -> onUpdateFilter { it.copy(workspaceFilter = ws) } }
                )
            }
        }

        if (calendarMode) {
            CalendarTransactionsView(
                allTransactions = allTransactions,
                preferences = preferences,
                onEditTransaction = onEditTransaction,
                onDeleteTransaction = { pendingDelete = it },
                onViewReceipt = onViewReceipt
            )
        } else {
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No matching transactions",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Adjust your filters or add a new income or expense entry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = onResetFilters) {
                            Text("Reset All Filters")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "${transactions.size} record(s) • Sorted by ${filterState.sortOption.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(transactions, key = { it.record.id }) { item ->
                        TransactionRowCard(
                            item = item,
                            preferences = preferences,
                            onEdit = { onEditTransaction(item.record) },
                            onDelete = { pendingDelete = item.record },
                            onViewReceipt = item.record.receiptPath?.let { path ->
                                { onViewReceipt(path) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        TransactionFilterSheet(
            filterState = filterState,
            accounts = accounts,
            categories = categories,
            preferences = preferences,
            onApply = { updated ->
                onUpdateFilter { updated }
                showFilterSheet = false
            },
            onReset = {
                onResetFilters()
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }

    pendingDelete?.let { tx ->
        ConfirmDeleteDialog(
            title = "Delete Transaction?",
            message = "Are you sure you want to permanently delete this ${tx.type.label.lowercase()} entry?",
            onConfirm = {
                onDeleteTransaction(tx)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun CalendarTransactionsView(
    allTransactions: List<TransactionDisplay>,
    preferences: AppPreferences,
    onEditTransaction: (TransactionRecord) -> Unit,
    onDeleteTransaction: (TransactionRecord) -> Unit,
    onViewReceipt: (String) -> Unit
) {
    val now = Calendar.getInstance()
    var year by remember { mutableStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(now.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableStateOf(now.get(Calendar.DAY_OF_MONTH)) }
    var wsFilter by remember { mutableStateOf(WorkspaceFilter.ALL) }

    val targetWs = wsFilter.toWorkspaceOrNull()
    val scoped = remember(allTransactions, targetWs) {
        if (targetWs == null) allTransactions else allTransactions.filter { it.record.workspace == targetWs }
    }

    val (mStart, mEnd) = CurrencyFormatter.monthBoundsMillis(year, month)
    val monthTx = remember(scoped, mStart, mEnd) {
        scoped.filter { it.record.dateMillis in mStart..mEnd }
    }

    val calMeta = remember(year, month) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOffset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceAtLeast(0)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        firstDayOffset to daysInMonth
    }

    val dayStats = remember(monthTx, calMeta.second) {
        val inc = LongArray(calMeta.second + 1)
        val exp = LongArray(calMeta.second + 1)
        val grouped = mutableMapOf<Int, MutableList<TransactionDisplay>>()
        val c = Calendar.getInstance()
        for (item in monthTx) {
            c.timeInMillis = item.record.dateMillis
            val d = c.get(Calendar.DAY_OF_MONTH).coerceIn(1, calMeta.second)
            when (item.record.type) {
                TransactionType.INCOME -> inc[d] += item.record.amountPaise
                TransactionType.EXPENSE -> exp[d] += item.record.amountPaise
                TransactionType.TRANSFER -> Unit
            }
            grouped.getOrPut(d) { mutableListOf() }.add(item)
        }
        Triple(inc, exp, grouped)
    }

    val clampedSelectedDay = selectedDay.coerceIn(1, calMeta.second)
    val selectedDayInc = dayStats.first[clampedSelectedDay]
    val selectedDayExp = dayStats.second[clampedSelectedDay]
    val selectedDayNet = selectedDayInc - selectedDayExp
    val selectedDayTx = dayStats.third[clampedSelectedDay].orEmpty()
    val sym = preferences.currencySymbol

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WorkspaceSelectorRow(
                selected = wsFilter,
                preferences = preferences,
                onSelect = { wsFilter = it }
            )
        }
        item {
            MonthSelectorBar(
                year = year,
                month = month,
                onPrevious = {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month - 1)
                        add(Calendar.MONTH, -1)
                    }
                    year = cal.get(Calendar.YEAR)
                    month = cal.get(Calendar.MONTH) + 1
                },
                onNext = {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month - 1)
                        add(Calendar.MONTH, 1)
                    }
                    year = cal.get(Calendar.YEAR)
                    month = cal.get(Calendar.MONTH) + 1
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Calendar Grid
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEach { d ->
                        Text(
                            text = d,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val totalCells = calMeta.first + calMeta.second
                val rows = (totalCells + 6) / 7
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (c in 0 until 7) {
                                val cellIndex = r * 7 + c
                                val dayNum = cellIndex - calMeta.first + 1
                                if (dayNum in 1..calMeta.second) {
                                    val inc = dayStats.first[dayNum]
                                    val exp = dayStats.second[dayNum]
                                    val isSelected = dayNum == clampedSelectedDay
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { selectedDay = dayNum }
                                            .padding(3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = dayNum.toString(),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (inc > 0L) {
                                                Text(
                                                    text = "+${inc / 100L}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = IncomeGreen,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                            if (exp > 0L) {
                                                Text(
                                                    text = "-${exp / 100L}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ExpenseRed,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Summary
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Day $clampedSelectedDay • ${CurrencyFormatter.formatMonthYear(year, month)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Daily Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyFormatter.formatPaise(selectedDayInc, sym),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = IncomeGreen
                        )
                    }
                    Column {
                        Text("Daily Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyFormatter.formatPaise(selectedDayExp, sym),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = ExpenseRed
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Daily Net", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = CurrencyFormatter.formatPaise(selectedDayNet, sym),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedDayNet >= 0) IncomeGreen else ExpenseRed
                        )
                    }
                }
            }
        }

        if (selectedDayTx.isEmpty()) {
            item {
                Text(
                    text = "No transactions recorded on day $clampedSelectedDay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(selectedDayTx, key = { it.record.id }) { item ->
                TransactionRowCard(
                    item = item,
                    preferences = preferences,
                    onEdit = { onEditTransaction(item.record) },
                    onDelete = { onDeleteTransaction(item.record) },
                    onViewReceipt = item.record.receiptPath?.let { p -> { onViewReceipt(p) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TransactionFilterSheet(
    filterState: TransactionFilterState,
    accounts: List<Account>,
    categories: List<Category>,
    preferences: AppPreferences,
    onApply: (TransactionFilterState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(filterState) }
    var minAmtText by remember { mutableStateOf(filterState.minAmountPaise?.let { CurrencyFormatter.paiseToEditableString(it) } ?: "") }
    var maxAmtText by remember { mutableStateOf(filterState.maxAmountPaise?.let { CurrencyFormatter.paiseToEditableString(it) } ?: "") }
    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Filter & Sort Transactions", style = MaterialTheme.typography.headlineSmall)

            Text("Sort Order", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOption.entries.forEach { sort ->
                    FilterChip(
                        selected = draft.sortOption == sort,
                        onClick = { draft = draft.copy(sortOption = sort) },
                        label = { Text(sort.label) }
                    )
                }
            }

            Text("Transaction Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.typeFilter == null,
                    onClick = { draft = draft.copy(typeFilter = null) },
                    label = { Text("All") }
                )
                TransactionType.entries.forEach { t ->
                    FilterChip(
                        selected = draft.typeFilter == t,
                        onClick = { draft = draft.copy(typeFilter = t) },
                        label = { Text(t.label) }
                    )
                }
            }

            Text("Date Range", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { pickingStartDate = true }, modifier = Modifier.weight(1f)) {
                    Text(
                        draft.startDateMillis?.let { CurrencyFormatter.formatDate(it, preferences.dateFormat) }
                            ?: "From Date"
                    )
                }
                OutlinedButton(onClick = { pickingEndDate = true }, modifier = Modifier.weight(1f)) {
                    Text(
                        draft.endDateMillis?.let { CurrencyFormatter.formatDate(it, preferences.dateFormat) }
                            ?: "To Date"
                    )
                }
            }

            Text("Amount Range (${preferences.currencySymbol})", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minAmtText,
                    onValueChange = { minAmtText = it },
                    label = { Text("Min Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxAmtText,
                    onValueChange = { maxAmtText = it },
                    label = { Text("Max Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Payment Method / Account", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = draft.accountId == null,
                    onClick = { draft = draft.copy(accountId = null) },
                    label = { Text("All Accounts") }
                )
                accounts.forEach { acc ->
                    FilterChip(
                        selected = draft.accountId == acc.id,
                        onClick = { draft = draft.copy(accountId = acc.id) },
                        label = { Text("${acc.name} (${preferences.workspaceLabel(acc.workspace)})") }
                    )
                }
            }

            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = draft.categoryId == null,
                    onClick = { draft = draft.copy(categoryId = null) },
                    label = { Text("All Categories") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = draft.categoryId == cat.id,
                        onClick = { draft = draft.copy(categoryId = cat.id) },
                        label = { Text(cat.name) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text("Reset")
                }
                Button(
                    onClick = {
                        val minP = CurrencyFormatter.parseRupeesToPaise(minAmtText)
                        val maxP = CurrencyFormatter.parseRupeesToPaise(maxAmtText)
                        onApply(draft.copy(minAmountPaise = minP, maxAmountPaise = maxP))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply Filters")
                }
            }
        }
    }

    if (pickingStartDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = draft.startDateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        draft = draft.copy(startDateMillis = CurrencyFormatter.dayBoundsMillis(it).first)
                    }
                    pickingStartDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingStartDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (pickingEndDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = draft.endDateMillis ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        draft = draft.copy(endDateMillis = CurrencyFormatter.dayBoundsMillis(it).second)
                    }
                    pickingEndDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingEndDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}
