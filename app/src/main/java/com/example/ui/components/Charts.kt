package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.export.CurrencyFormatter
import com.example.domain.model.CategoryBreakdown
import com.example.domain.model.DailyActivityPoint
import com.example.domain.model.MonthlyComparisonPoint
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import java.util.Locale
import kotlin.math.max

@Composable
fun IncomeExpenseBarChart(
    points: List<MonthlyComparisonPoint>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxVal = max(1L, points.maxOfOrNull { max(it.incomePaise, it.expensePaise) } ?: 1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendDot(color = IncomeGreen, label = "Income")
            ChartLegendDot(color = ExpenseRed, label = "Expenses")
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (points.all { it.incomePaise == 0L && it.expensePaise == 0L }) {
            ChartEmptyPlaceholder("No income or expense activity in this period")
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                val w = size.width
                val h = size.height
                val bottomPad = 8f
                val chartH = h - bottomPad

                // Horizontal reference lines
                for (i in 0..3) {
                    val yPos = chartH * (i / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, yPos),
                        end = Offset(w, yPos),
                        strokeWidth = 1.5f
                    )
                }

                val groupCount = points.size.coerceAtLeast(1)
                val groupWidth = w / groupCount
                val barWidth = (groupWidth * 0.28f).coerceIn(8f, 26f)
                val gap = (barWidth * 0.25f).coerceAtLeast(3f)

                points.forEachIndexed { idx, pt ->
                    val centerX = groupWidth * idx + groupWidth / 2f
                    val incH = (pt.incomePaise.toFloat() / maxVal.toFloat()) * (chartH * 0.9f)
                    val expH = (pt.expensePaise.toFloat() / maxVal.toFloat()) * (chartH * 0.9f)

                    if (pt.incomePaise > 0L) {
                        val minVisH = incH.coerceAtLeast(4f)
                        drawRoundRect(
                            color = IncomeGreen,
                            topLeft = Offset(centerX - barWidth - gap / 2f, chartH - minVisH),
                            size = Size(barWidth, minVisH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                    if (pt.expensePaise > 0L) {
                        val minVisH = expH.coerceAtLeast(4f)
                        drawRoundRect(
                            color = ExpenseRed,
                            topLeft = Offset(centerX + gap / 2f, chartH - minVisH),
                            size = Size(barWidth, minVisH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                points.forEach { pt ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pt.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            val latest = points.lastOrNull()
            if (latest != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${latest.label}: In ${CurrencyFormatter.formatPaise(latest.incomePaise, currencySymbol)}  •  Out ${CurrencyFormatter.formatPaise(latest.expensePaise, currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    breakdown: List<CategoryBreakdown>,
    totalAmountPaise: Long,
    currencySymbol: String,
    emptyText: String = "No expense categories recorded",
    modifier: Modifier = Modifier
) {
    if (breakdown.isEmpty() || totalAmountPaise <= 0L) {
        ChartEmptyPlaceholder(emptyText, modifier = modifier)
        return
    }

    val topItems = breakdown.take(6)
    val othersAmount = breakdown.drop(6).sumOf { it.amountPaise }
    val displayList = if (othersAmount > 0L) {
        topItems + CategoryBreakdown(
            categoryId = -1L,
            categoryName = "Other Categories",
            amountPaise = othersAmount,
            percentage = (othersAmount.toDouble() / totalAmountPaise.toDouble()) * 100.0,
            colorHex = 0xFF64748B
        )
    } else {
        topItems
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(136.dp)) {
                val strokeWidth = size.minDimension * 0.20f
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                var startAngle = -90f
                displayList.forEach { item ->
                    val sweep = ((item.amountPaise.toFloat() / totalAmountPaise.toFloat()) * 360f)
                        .coerceAtLeast(2f)
                    drawArc(
                        color = Color(item.colorHex),
                        startAngle = startAngle,
                        sweepAngle = (sweep - 1.5f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatPaise(totalAmountPaise, currencySymbol),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayList.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(item.colorHex))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.US, "%.0f%%", item.percentage),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlyTrendLineChart(
    points: List<MonthlyComparisonPoint>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2 || points.all { it.expensePaise == 0L && it.incomePaise == 0L }) {
        ChartEmptyPlaceholder("Not enough trend data yet", modifier = modifier)
        return
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxVal = max(1L, points.maxOfOrNull { max(it.incomePaise, it.expensePaise) } ?: 1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChartLegendDot(color = ExpenseRed, label = "Spending Trend")
            ChartLegendDot(color = IncomeGreen, label = "Income Trend")
        }
        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val w = size.width
            val h = size.height
            val chartH = h - 12f
            val stepX = w / (points.size - 1).coerceAtLeast(1)

            for (i in 0..2) {
                val y = chartH * (i / 2f)
                drawLine(color = gridColor, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
            }

            fun buildOffsets(selector: (MonthlyComparisonPoint) -> Long): List<Offset> {
                return points.mapIndexed { idx, pt ->
                    val ratio = selector(pt).toFloat() / maxVal.toFloat()
                    Offset(
                        x = idx * stepX,
                        y = (chartH - ratio * (chartH * 0.88f)).coerceIn(8f, chartH)
                    )
                }
            }

            val expenseOffsets = buildOffsets { it.expensePaise }
            val incomeOffsets = buildOffsets { it.incomePaise }

            // Area fill under spending curve
            val areaPath = Path().apply {
                moveTo(expenseOffsets.first().x, chartH)
                expenseOffsets.forEach { lineTo(it.x, it.y) }
                lineTo(expenseOffsets.last().x, chartH)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(ExpenseRed.copy(alpha = 0.22f), Color.Transparent)
                )
            )

            // Income line
            val incomePath = Path().apply {
                moveTo(incomeOffsets.first().x, incomeOffsets.first().y)
                incomeOffsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(incomePath, color = IncomeGreen, style = Stroke(width = 4f, cap = StrokeCap.Round))

            // Expense line
            val expensePath = Path().apply {
                moveTo(expenseOffsets.first().x, expenseOffsets.first().y)
                expenseOffsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(expensePath, color = ExpenseRed, style = Stroke(width = 4.5f, cap = StrokeCap.Round))

            expenseOffsets.forEach { pt ->
                drawCircle(color = ExpenseRed, radius = 6f, center = pt)
            }
            incomeOffsets.forEach { pt ->
                drawCircle(color = IncomeGreen, radius = 5f, center = pt)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { pt ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = pt.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DailyActivityChart(
    points: List<DailyActivityPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty() || points.all { it.incomePaise == 0L && it.expensePaise == 0L }) {
        ChartEmptyPlaceholder("No daily activity recorded for this month", modifier = modifier)
        return
    }

    val maxVal = max(1L, points.maxOfOrNull { max(it.incomePaise, it.expensePaise) } ?: 1L)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val w = size.width
            val h = size.height
            val slotW = w / points.size.coerceAtLeast(1)
            val barW = (slotW * 0.56f).coerceIn(3f, 14f)

            points.forEachIndexed { idx, pt ->
                val x = idx * slotW + (slotW - barW) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 4f),
                    size = Size(barW, h - 8f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                val dominant = max(pt.incomePaise, pt.expensePaise)
                if (dominant > 0L) {
                    val barH = ((dominant.toFloat() / maxVal.toFloat()) * (h - 10f)).coerceAtLeast(6f)
                    val barColor = if (pt.expensePaise >= pt.incomePaise) ExpenseRed else IncomeGreen
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, h - 4f - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Day 1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Day ${points.size / 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Day ${points.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartEmptyPlaceholder(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
