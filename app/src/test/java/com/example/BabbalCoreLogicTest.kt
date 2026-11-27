package com.example

import com.example.data.crypto.InMemoryTestCryptoEngine
import com.example.data.export.CurrencyFormatter
import com.example.data.export.ReportExporter
import com.example.data.repository.AccountingCalculator
import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.AppPreferences
import com.example.domain.model.Category
import com.example.domain.model.SortOption
import com.example.domain.model.TransactionFilterState
import com.example.domain.model.TransactionRecord
import com.example.domain.model.TransactionType
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.zip.ZipInputStream

class BabbalCoreLogicTest {

    @Test
    fun `aes gcm string bytes and password encryption roundtrips cleanly`() {
        val crypto = InMemoryTestCryptoEngine()
        val secretNote = "GVVS Forest Produce Invoice #108 - ₹45,000"
        val enc = crypto.encryptString(secretNote)
        assertTrue(enc.startsWith("ENC1:"))
        assertFalse(enc.contains("GVVS"))
        assertEquals(secretNote, crypto.decryptString(enc))

        val payload = """{"workspace":"GVVS","amount":4500000}"""
        val pwdEnc = crypto.encryptWithPassword(payload, "BabbalVault@2026")
        assertTrue(pwdEnc.startsWith("BABBAL_ENC_V1:"))
        assertEquals(payload, crypto.decryptWithPassword(pwdEnc, "BabbalVault@2026"))

        val salt = crypto.generateSalt()
        val h1 = crypto.hashPin("2580", salt)
        val h2 = crypto.hashPin("2580", salt)
        assertEquals(h1, h2)
    }

    @Test
    fun `indian financial year report and csv xlsx exports work accurately`() {
        val bank = Account(id = 1L, name = "Bank Account", workspace = Workspace.GVVS, type = AccountType.BANK)
        val salesCat = Category(id = 10L, name = "Product Sales", workspace = Workspace.GVVS, type = TransactionType.INCOME)
        val rawCat = Category(id = 11L, name = "Raw Materials", workspace = Workspace.GVVS, type = TransactionType.EXPENSE)

        val aprDate = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 10, 12, 0, 0) }.timeInMillis
        val novDate = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 20, 12, 0, 0) }.timeInMillis

        val txs = listOf(
            TransactionRecord(
                id = 1L,
                workspace = Workspace.GVVS,
                type = TransactionType.INCOME,
                amountPaise = 2_000_000L, // ₹20,000
                dateMillis = aprDate,
                categoryId = 10L,
                accountId = 1L,
                description = "Mahua & Herbal batch"
            ),
            TransactionRecord(
                id = 2L,
                workspace = Workspace.GVVS,
                type = TransactionType.EXPENSE,
                amountPaise = 500_000L, // ₹5,000
                dateMillis = novDate,
                categoryId = 11L,
                accountId = 1L,
                description = "Packaging & raw herbs"
            )
        )

        val displays = AccountingCalculator.toTransactionDisplays(txs, listOf(salesCat, rawCat), listOf(bank))
        val fyStart = CurrencyFormatter.monthBoundsMillis(2026, 4).first
        val fyEnd = CurrencyFormatter.monthBoundsMillis(2027, 3).second

        val report = AccountingCalculator.computeReportData(WorkspaceFilter.GVVS, fyStart, fyEnd, displays)
        assertEquals(2_000_000L, report.totalIncomePaise)
        assertEquals(500_000L, report.totalExpensePaise)
        assertEquals(1_500_000L, report.netSavingsPaise)
        assertEquals(12, report.monthlyComparison.size)
        assertEquals("Raw Materials", report.highestExpenseCategory?.categoryName)

        val prefs = AppPreferences()
        val csvOut = ByteArrayOutputStream()
        ReportExporter.exportCsv(report, prefs, csvOut)
        val csvText = csvOut.toString(Charsets.UTF_8.name())
        assertTrue(csvText.contains("Mahua & Herbal batch"))
        assertTrue(csvText.contains("Packaging & raw herbs"))

        val xlsxOut = ByteArrayOutputStream()
        ReportExporter.exportXlsx(report, "GVVS Business", "FY 2026-27", prefs, xlsxOut)
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(xlsxOut.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zis.nextEntry
            }
        }
        assertTrue(entries.contains("[Content_Types].xml"))
        assertTrue(entries.contains("xl/workbook.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet1.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet2.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet3.xml"))
    }

    @Test
    fun `global search filter and sort orders work across descriptions and amounts`() {
        val cash = Account(id = 1L, name = "Cash", workspace = Workspace.PERSONAL, type = AccountType.CASH)
        val food = Category(id = 1L, name = "Food", workspace = Workspace.PERSONAL, type = TransactionType.EXPENSE)
        val t1 = TransactionRecord(id = 1L, workspace = Workspace.PERSONAL, type = TransactionType.EXPENSE, amountPaise = 25_000L, dateMillis = 1_000L, categoryId = 1L, accountId = 1L, description = "Groceries")
        val t2 = TransactionRecord(id = 2L, workspace = Workspace.PERSONAL, type = TransactionType.EXPENSE, amountPaise = 85_000L, dateMillis = 2_000L, categoryId = 1L, accountId = 1L, description = "Dinner party")
        val displays = AccountingCalculator.toTransactionDisplays(listOf(t1, t2), listOf(food), listOf(cash))

        val searched = AccountingCalculator.filterTransactions(
            displays,
            TransactionFilterState(searchQuery = "dinner")
        )
        assertEquals(1, searched.size)
        assertEquals(2L, searched.first().record.id)

        val sortedHigh = AccountingCalculator.filterTransactions(
            displays,
            TransactionFilterState(sortOption = SortOption.HIGHEST_AMOUNT)
        )
        assertEquals(2L, sortedHigh.first().record.id)
    }
}
