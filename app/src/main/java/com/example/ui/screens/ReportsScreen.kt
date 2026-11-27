package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.export.CurrencyFormatter
import com.example.data.export.ReportExporter
import com.example.domain.model.AppPreferences
import com.example.domain.model.ReportData
import com.example.domain.model.ReportPeriodMode
import com.example.domain.model.WorkspaceFilter
import com.example.ui.ReportConfig
import com.example.ui.components.BabbalCard
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.MonthSelectorBar
import com.example.ui.components.WorkspaceSelectorRow
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    reportConfig: ReportConfig,
    reportData: ReportData,
    preferences: AppPreferences,
    onUpdateConfig: ((ReportConfig) -> ReportConfig) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sym = preferences.currencySymbol
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    val workspaceLabel = when (reportConfig.workspaceFilter) {
        WorkspaceFilter.ALL -> "All Finances"
        WorkspaceFilter.PERSONAL -> preferences.personalWorkspaceName
        WorkspaceFilter.GVVS -> preferences.businessWorkspaceName
    }

    val periodLabel = when (reportConfig.mode) {
        ReportPeriodMode.MONTHLY -> CurrencyFormatter.formatMonthYear(reportConfig.year, reportConfig.month)
        ReportPeriodMode.CALENDAR_YEAR -> "Jan–Dec ${reportConfig.year}"
        ReportPeriodMode.FINANCIAL_YEAR -> {
            val fyNext = (reportConfig.year + 1) % 100
            "FY ${reportConfig.year}–${fyNext.toString().padStart(2, '0')}"
        }
        ReportPeriodMode.CUSTOM -> "${CurrencyFormatter.formatDate(reportData.startMillis, preferences.dateFormat)} to ${CurrencyFormatter.formatDate(reportData.endMillis, preferences.dateFormat)}"
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.exportPdf(reportData, workspaceLabel, periodLabel, preferences, out)
                }
            }.onSuccess { onNotify("PDF report saved") }
                .onFailure { onNotify("Failed to save PDF: ${it.message}") }
        }
    }

    val createXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.exportXlsx(reportData, workspaceLabel, periodLabel, preferences, out)
                }
            }.onSuccess { onNotify("Excel (.xlsx) report saved") }
                .onFailure { onNotify("Failed to save Excel: ${it.message}") }
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.exportCsv(reportData, preferences, out)
                }
            }.onSuccess { onNotify("CSV report saved") }
                .onFailure { onNotify("Failed to save CSV: ${it.message}") }
        }
    }

    fun shareExport(kind: String) {
        runCatching {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safePeriod = periodLabel.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val (file, mime) = when (kind) {
                "pdf" -> File(exportsDir, "BABBAL_${safePeriod}.pdf") to "application/pdf"
                "xlsx" -> File(exportsDir, "BABBAL_${safePeriod}.xlsx") to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                else -> File(exportsDir, "BABBAL_${safePeriod}.csv") to "text/csv"
            }
            FileOutputStream(file).use { out ->
                when (kind) {
                    "pdf" -> ReportExporter.exportPdf(reportData, workspaceLabel, periodLabel, preferences, out)
                    "xlsx" -> ReportExporter.exportXlsx(reportData, workspaceLabel, periodLabel, preferences, out)
                    else -> ReportExporter.exportCsv(reportData, preferences, out)
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share BABBAL Report"))
        }.onFailure {
            onNotify("Share error: ${it.message}")
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            WorkspaceSelectorRow(
                selected = reportConfig.workspaceFilter,
                preferences = preferences,
                onSelect = { ws -> onUpdateConfig { it.copy(workspaceFilter = ws) } }
            )
        }

        // Report Period Mode Selector
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriodMode.entries.forEach { mode ->
                    FilterChip(
                        selected = reportConfig.mode == mode,
                        onClick = { onUpdateConfig { it.copy(mode = mode) } },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("report_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }

        // Period Navigation
        item {
            when (reportConfig.mode) {
                ReportPeriodMode.MONTHLY -> {
                    MonthSelectorBar(
                        year = reportConfig.year,
                        month = reportConfig.month,
                        onPrevious = {
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, reportConfig.year)
                                set(Calendar.MONTH, reportConfig.month - 1)
                                add(Calendar.MONTH, -1)
                            }
                            onUpdateConfig {
                                it.copy(year = cal.get(Calendar.YEAR), month = cal.get(Calendar.MONTH) + 1)
                            }
                        },
                        onNext = {
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, reportConfig.year)
                                set(Calendar.MONTH, reportConfig.month - 1)
                                add(Calendar.MONTH, 1)
                            }
                            onUpdateConfig {
                                it.copy(year = cal.get(Calendar.YEAR), month = cal.get(Calendar.MONTH) + 1)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ReportPeriodMode.CALENDAR_YEAR, ReportPeriodMode.FINANCIAL_YEAR -> {
                    BabbalCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onUpdateConfig { it.copy(year = it.year - 1) } }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Year")
                            }
                            Text(text = periodLabel, style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onUpdateConfig { it.copy(year = it.year + 1) } }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Year")
                            }
                        }
                    }
                }
                ReportPeriodMode.CUSTOM -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { pickingStart = true }, modifier = Modifier.weight(1f)) {
                            Text("From: ${CurrencyFormatter.formatDate(reportConfig.customStartMillis, preferences.dateFormat)}")
                        }
                        OutlinedButton(onClick = { pickingEnd = true }, modifier = Modifier.weight(1f)) {
                            Text("To: ${CurrencyFormatter.formatDate(reportConfig.customEndMillis, preferences.dateFormat)}")
                        }
                    }
                }
            }
        }

        // Export Actions Card (PDF / XLSX / CSV)
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text("Export & Share Report", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { createPdfLauncher.launch("BABBAL_Report_${reportConfig.year}.pdf") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF")
                    }
                    OutlinedButton(
                        onClick = { createXlsxLauncher.launch("BABBAL_Report_${reportConfig.year}.xlsx") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Excel")
                    }
                    OutlinedButton(
                        onClick = { createCsvLauncher.launch("BABBAL_Report_${reportConfig.year}.csv") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { shareExport("pdf") }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share PDF")
                    }
                    TextButton(onClick = { shareExport("xlsx") }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Excel")
                    }
                    TextButton(onClick = { shareExport("csv") }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share CSV")
                    }
                }
            }
        }

        // Report Key Metrics
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Report Summary • $periodLabel", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                ReportMetricRow("Total Income", CurrencyFormatter.formatPaise(reportData.totalIncomePaise, sym), IncomeGreen)
                ReportMetricRow("Total Expenses", CurrencyFormatter.formatPaise(reportData.totalExpensePaise, sym), ExpenseRed)
                ReportMetricRow(
                    "Net Savings",
                    "${CurrencyFormatter.formatPaise(reportData.netSavingsPaise, sym)} (${String.format(Locale.US, "%.1f%%", reportData.savingsPercentage)})",
                    if (reportData.netSavingsPaise >= 0) IncomeGreen else ExpenseRed
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ReportMetricRow("Avg Daily Expense", CurrencyFormatter.formatPaise(reportData.averageDailyExpensePaise, sym))
                ReportMetricRow(
                    "Highest Spending Day",
                    reportData.highestSpendingDayMillis?.let {
                        "${CurrencyFormatter.formatDate(it, preferences.dateFormat)} (${CurrencyFormatter.formatPaise(reportData.highestSpendingDayPaise, sym)})"
                    } ?: "N/A"
                )
                ReportMetricRow(
                    "Highest Expense Category",
                    reportData.highestExpenseCategory?.let {
                        "${it.categoryName} (${CurrencyFormatter.formatPaise(it.amountPaise, sym)})"
                    } ?: "N/A"
                )
            }
        }

        // Month-by-Month Comparison
        if (reportData.monthlyComparison.size > 1) {
            item {
                BabbalCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Month-by-Month Comparison", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    IncomeExpenseBarChart(
                        points = reportData.monthlyComparison,
                        currencySymbol = sym
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    reportData.monthlyComparison.forEach { pt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${pt.label} ${pt.year}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                "+${CurrencyFormatter.formatPaise(pt.incomePaise, sym)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = IncomeGreen,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "-${CurrencyFormatter.formatPaise(pt.expensePaise, sym)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpenseRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Payment Method Breakdown
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text("Payment Method Breakdown", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                if (reportData.paymentMethodBreakdown.isEmpty()) {
                    Text("No payment activity in this period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    reportData.paymentMethodBreakdown.forEach { pm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(pm.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.2f))
                            Text(
                                "In: ${CurrencyFormatter.formatPaise(pm.incomePaise, sym)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = IncomeGreen,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "Out: ${CurrencyFormatter.formatPaise(pm.expensePaise, sym)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpenseRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Expense Category Breakdown
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text("Expense Category Breakdown", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                CategoryDonutChart(
                    breakdown = reportData.expenseBreakdown,
                    totalAmountPaise = reportData.totalExpensePaise,
                    currencySymbol = sym
                )
                if (reportData.expenseBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    reportData.expenseBreakdown.forEach { cat ->
                        BreakdownListRow(cat.categoryName, cat.amountPaise, cat.percentage, Color(cat.colorHex), sym)
                    }
                }
            }
        }

        // Income Category Breakdown
        item {
            BabbalCard(modifier = Modifier.fillMaxWidth()) {
                Text("Income Category Breakdown", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                CategoryDonutChart(
                    breakdown = reportData.incomeBreakdown,
                    totalAmountPaise = reportData.totalIncomePaise,
                    currencySymbol = sym,
                    emptyText = "No income categories recorded"
                )
                if (reportData.incomeBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    reportData.incomeBreakdown.forEach { cat ->
                        BreakdownListRow(cat.categoryName, cat.amountPaise, cat.percentage, Color(cat.colorHex), sym)
                    }
                }
            }
        }
    }

    if (pickingStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = reportConfig.customStartMillis)
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onUpdateConfig { cfg -> cfg.copy(customStartMillis = it) } }
                    pickingStart = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingStart = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (pickingEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = reportConfig.customEndMillis)
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onUpdateConfig { cfg -> cfg.copy(customEndMillis = it) } }
                    pickingEnd = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingEnd = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ReportMetricRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

@Composable
private fun BreakdownListRow(
    name: String,
    amountPaise: Long,
    pct: Double,
    color: Color,
    sym: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = "${CurrencyFormatter.formatPaise(amountPaise, sym)} (${String.format(Locale.US, "%.1f%%", pct)})",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
