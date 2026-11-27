package com.example.data.export

import com.example.domain.model.DateFormatOption
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object CurrencyFormatter {

    /**
     * Formats paise (1 INR = 100 paise) using the Indian numbering system (##,##,###.##).
     * For example: 123456789 paise -> ₹12,34,567.89 (or ₹12,34,567 if whole rupees and showDecimalsWhenZero = false).
     */
    fun formatPaise(
        paise: Long,
        symbol: String = "₹",
        showDecimalsWhenZero: Boolean = false,
        showPlusSign: Boolean = false
    ): String {
        val isNegative = paise < 0
        val absPaise = abs(paise)
        val whole = absPaise / 100L
        val cents = absPaise % 100L

        val groupedWhole = formatIndianGrouping(whole)
        val decimalPart = if (cents != 0L || showDecimalsWhenZero) {
            ".${cents.toString().padStart(2, '0')}"
        } else {
            ""
        }

        val prefix = when {
            isNegative -> "-$symbol"
            showPlusSign && paise > 0 -> "+$symbol"
            else -> symbol
        }
        return "$prefix$groupedWhole$decimalPart"
    }

    fun formatIndianGrouping(value: Long): String {
        val str = value.toString()
        if (str.length <= 3) return str
        val lastThree = str.takeLast(3)
        val remaining = str.dropLast(3)
        val sb = StringBuilder()
        var idx = remaining.length
        while (idx > 0) {
            val start = (idx - 2).coerceAtLeast(0)
            if (sb.isNotEmpty()) sb.insert(0, ',')
            sb.insert(0, remaining.substring(start, idx))
            idx = start
        }
        return "$sb,$lastThree"
    }

    fun parseRupeesToPaise(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace("₹", "").trim()
        if (cleaned.isEmpty()) return null
        val parsed = cleaned.toBigDecimalOrNull() ?: return null
        if (parsed < java.math.BigDecimal.ZERO) return null
        return parsed.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    }

    fun paiseToEditableString(paise: Long): String {
        if (paise == 0L) return ""
        val whole = paise / 100L
        val cents = abs(paise % 100L)
        return if (cents == 0L) {
            whole.toString()
        } else {
            "$whole.${cents.toString().padStart(2, '0')}"
        }
    }

    fun formatDate(
        millis: Long,
        option: DateFormatOption = DateFormatOption.DD_MM_YYYY
    ): String {
        val sdf = SimpleDateFormat(option.pattern, Locale.ENGLISH)
        return sdf.format(Date(millis))
    }

    fun formatMonthYear(year: Int, month: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(cal.time)
    }

    fun formatShortMonth(month: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("MMM", Locale.ENGLISH).format(cal.time)
    }

    fun monthBoundsMillis(year: Int, month: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return start.timeInMillis to end.timeInMillis
    }

    fun dayBoundsMillis(millis: Long): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return start.timeInMillis to end.timeInMillis
    }
}
