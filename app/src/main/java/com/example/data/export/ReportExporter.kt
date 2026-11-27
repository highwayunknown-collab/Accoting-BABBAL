package com.example.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.domain.model.AppPreferences
import com.example.domain.model.ReportData
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportExporter {

    fun exportCsv(
        report: ReportData,
        preferences: AppPreferences,
        outputStream: OutputStream
    ) {
        val sb = StringBuilder()
        // UTF-8 BOM so Excel recognizes ₹ properly
        sb.append('\uFEFF')
        sb.append("Date,Workspace,Type,Category,Account,To Account,Amount (${preferences.currencyCode}),Formatted Amount,Description\n")

        for (item in report.transactions) {
            val r = item.record
            val dateStr = CurrencyFormatter.formatDate(r.dateMillis, preferences.dateFormat)
            val wsStr = preferences.workspaceLabel(r.workspace)
            val amountDecimal = String.format(Locale.US, "%.2f", r.amountPaise / 100.0)
            val formattedAmt = CurrencyFormatter.formatPaise(r.amountPaise, preferences.currencySymbol, showDecimalsWhenZero = true)
            sb.append(csvCell(dateStr)).append(',')
                .append(csvCell(wsStr)).append(',')
                .append(csvCell(r.type.label)).append(',')
                .append(csvCell(item.categoryName)).append(',')
                .append(csvCell(item.accountName)).append(',')
                .append(csvCell(item.toAccountName.orEmpty())).append(',')
                .append(csvCell(amountDecimal)).append(',')
                .append(csvCell(formattedAmt)).append(',')
                .append(csvCell(r.description))
                .append('\n')
        }

        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun exportXlsx(
        report: ReportData,
        workspaceLabel: String,
        periodLabel: String,
        preferences: AppPreferences,
        outputStream: OutputStream
    ) {
        val sym = preferences.currencySymbol

        val summaryRows = listOf(
            listOf("BABBAL Finance Manager - Financial Report", ""),
            listOf("Workspace", workspaceLabel),
            listOf("Period", periodLabel),
            listOf("Total Income", CurrencyFormatter.formatPaise(report.totalIncomePaise, sym, true)),
            listOf("Total Expenses", CurrencyFormatter.formatPaise(report.totalExpensePaise, sym, true)),
            listOf("Net Savings", CurrencyFormatter.formatPaise(report.netSavingsPaise, sym, true)),
            listOf("Savings Rate", String.format(Locale.US, "%.1f%%", report.savingsPercentage)),
            listOf("Average Daily Expense", CurrencyFormatter.formatPaise(report.averageDailyExpensePaise, sym, true)),
            listOf(
                "Highest Spending Day",
                report.highestSpendingDayMillis?.let {
                    "${CurrencyFormatter.formatDate(it, preferences.dateFormat)} (${CurrencyFormatter.formatPaise(report.highestSpendingDayPaise, sym)})"
                } ?: "N/A"
            ),
            listOf(
                "Highest Expense Category",
                report.highestExpenseCategory?.let {
                    "${it.categoryName} (${CurrencyFormatter.formatPaise(it.amountPaise, sym)})"
                } ?: "N/A"
            )
        )

        val txRows = mutableListOf(
            listOf("Date", "Workspace", "Type", "Category", "Account", "To Account", "Amount (${preferences.currencyCode})", "Formatted", "Description")
        )
        for (item in report.transactions) {
            val r = item.record
            txRows.add(
                listOf(
                    CurrencyFormatter.formatDate(r.dateMillis, preferences.dateFormat),
                    preferences.workspaceLabel(r.workspace),
                    r.type.label,
                    item.categoryName,
                    item.accountName,
                    item.toAccountName.orEmpty(),
                    String.format(Locale.US, "%.2f", r.amountPaise / 100.0),
                    CurrencyFormatter.formatPaise(r.amountPaise, sym, true),
                    r.description
                )
            )
        }

        val breakdownRows = mutableListOf(
            listOf("Kind", "Category", "Amount", "Share %")
        )
        for (cat in report.expenseBreakdown) {
            breakdownRows.add(
                listOf(
                    "Expense",
                    cat.categoryName,
                    CurrencyFormatter.formatPaise(cat.amountPaise, sym, true),
                    String.format(Locale.US, "%.1f%%", cat.percentage)
                )
            )
        }
        for (cat in report.incomeBreakdown) {
            breakdownRows.add(
                listOf(
                    "Income",
                    cat.categoryName,
                    CurrencyFormatter.formatPaise(cat.amountPaise, sym, true),
                    String.format(Locale.US, "%.1f%%", cat.percentage)
                )
            )
        }

        ZipOutputStream(outputStream).use { zip ->
            fun putEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            putEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )

            putEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            putEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Summary" sheetId="1" r:id="rId1"/>
    <sheet name="Transactions" sheetId="2" r:id="rId2"/>
    <sheet name="Breakdown" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""
            )

            putEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
</Relationships>"""
            )

            putEntry("xl/worksheets/sheet1.xml", buildSheetXml(summaryRows))
            putEntry("xl/worksheets/sheet2.xml", buildSheetXml(txRows))
            putEntry("xl/worksheets/sheet3.xml", buildSheetXml(breakdownRows))
        }
    }

    private fun buildSheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIdx, cells ->
            val rNum = rowIdx + 1
            sb.append("<row r=\"").append(rNum).append("\">")
            cells.forEachIndexed { colIdx, raw ->
                val ref = colLetter(colIdx) + rNum
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                    .append(escapeXml(raw))
                    .append("</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun colLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun exportPdf(
        report: ReportData,
        workspaceLabel: String,
        periodLabel: String,
        preferences: AppPreferences,
        outputStream: OutputStream
    ) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val sym = preferences.currencySymbol

        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas: Canvas = page.canvas
        var y = 0f

        val headerBg = Paint().apply { color = Color.parseColor("#167B59") }
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#D6F2E6")
            textSize = 11f
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = Color.parseColor("#202B27")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#202B27")
            textSize = 10f
            isAntiAlias = true
        }
        val mutedPaint = Paint().apply {
            color = Color.parseColor("#5C6B64")
            textSize = 9.5f
            isAntiAlias = true
        }
        val incomePaint = Paint(bodyPaint).apply {
            color = Color.parseColor("#16A36A")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val expensePaint = Paint(bodyPaint).apply {
            color = Color.parseColor("#DC5656")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#D6E0DC")
            strokeWidth = 1f
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 12f
                canvas.drawText("BABBAL Finance Report - $workspaceLabel ($periodLabel) | Page $pageNum", margin, y, mutedPaint)
                y += 18f
                canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
                y += 16f
            }
        }

        // Top Header Band
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 76f, headerBg)
        canvas.drawText("BABBAL – Financial Report", margin, 34f, titlePaint)
        canvas.drawText("Workspace: $workspaceLabel  •  Period: $periodLabel", margin, 56f, subtitlePaint)
        y = 100f

        // Financial Summary
        canvas.drawText("Financial Summary", margin, y, sectionPaint)
        y += 18f
        val summaryLines = listOf(
            "Total Income" to CurrencyFormatter.formatPaise(report.totalIncomePaise, sym, true),
            "Total Expenses" to CurrencyFormatter.formatPaise(report.totalExpensePaise, sym, true),
            "Net Savings / Surplus" to "${CurrencyFormatter.formatPaise(report.netSavingsPaise, sym, true)} (${String.format(Locale.US, "%.1f%%", report.savingsPercentage)})",
            "Average Daily Expense" to CurrencyFormatter.formatPaise(report.averageDailyExpensePaise, sym, true),
            "Highest Expense Category" to (report.highestExpenseCategory?.let { "${it.categoryName} (${CurrencyFormatter.formatPaise(it.amountPaise, sym)})" } ?: "N/A")
        )
        for ((label, value) in summaryLines) {
            canvas.drawText(label, margin, y, mutedPaint)
            canvas.drawText(value, 240f, y, bodyPaint)
            y += 16f
        }
        y += 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 20f

        // Expense Breakdown
        if (report.expenseBreakdown.isNotEmpty()) {
            ensureSpace(40f)
            canvas.drawText("Expense Category Breakdown", margin, y, sectionPaint)
            y += 16f
            for (cat in report.expenseBreakdown.take(12)) {
                ensureSpace(18f)
                canvas.drawText(cat.categoryName.take(30), margin, y, bodyPaint)
                canvas.drawText(
                    "${CurrencyFormatter.formatPaise(cat.amountPaise, sym, true)} (${String.format(Locale.US, "%.1f%%", cat.percentage)})",
                    320f,
                    y,
                    expensePaint
                )
                y += 15f
            }
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 20f
        }

        // Transactions Ledger
        ensureSpace(40f)
        canvas.drawText("Transactions (${report.transactions.size})", margin, y, sectionPaint)
        y += 16f
        canvas.drawText("Date", margin, y, mutedPaint)
        canvas.drawText("Category / Notes", 110f, y, mutedPaint)
        canvas.drawText("Method", 335f, y, mutedPaint)
        canvas.drawText("Amount", 455f, y, mutedPaint)
        y += 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 14f

        for (item in report.transactions) {
            ensureSpace(20f)
            val r = item.record
            val dateStr = CurrencyFormatter.formatDate(r.dateMillis, preferences.dateFormat)
            val title = if (r.description.isNotBlank()) "${item.categoryName}: ${r.description}" else item.categoryName
            val amtStr = CurrencyFormatter.formatPaise(r.amountPaise, sym, true)
            val amtPaint = when (r.type) {
                com.example.domain.model.TransactionType.INCOME -> incomePaint
                com.example.domain.model.TransactionType.EXPENSE -> expensePaint
                com.example.domain.model.TransactionType.TRANSFER -> bodyPaint
            }
            canvas.drawText(dateStr, margin, y, bodyPaint)
            canvas.drawText(title.take(34), 110f, y, bodyPaint)
            canvas.drawText(item.accountName.take(16), 335f, y, mutedPaint)
            canvas.drawText(amtStr, 455f, y, amtPaint)
            y += 15f
        }

        doc.finishPage(page)
        doc.writeTo(outputStream)
        doc.close()
        outputStream.flush()
    }
}
