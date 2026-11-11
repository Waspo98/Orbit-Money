package app.overbay.orbit.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max

data class SpendingBarItem(
    val label: String,                     // e.g. "Apr" or "Food"
    val expenseCents: Cents,               // Primary spending amount
    val incomeCents: Cents? = null,        // Optional income (for dual-bar flow mode)
    val budgetCents: Cents? = null         // Optional budget ceiling line
)

/**
 * Interactive monthly bar chart supporting grouped flow (Income vs Expenses) or category trends.
 * Features rounded bar caps, budget threshold reference line, and touch selection with haptics.
 */
@Composable
fun SpendingTrendsBarChart(
    items: List<SpendingBarItem>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    expenseColor: Color = MaterialTheme.colorScheme.error,
    incomeColor: Color = MaterialTheme.colorScheme.primary,
    barColor: Color? = null,
    onItemSelected: ((Int?, SpendingBarItem?) -> Unit)? = null
) {
    if (items.isEmpty()) return

    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var lastHapticIndex by remember { mutableStateOf<Int?>(null) }

    // Pre-allocated drawing objects (Zero allocation in Canvas onDraw)
    val cornerRadius = remember { CornerRadius(10f, 10f) }
    val budgetStroke = remember(density) {
        Stroke(
            width = with(density) { 1.5.dp.toPx() },
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )
    }
    val gridStroke = remember(density) {
        Stroke(
            width = with(density) { 1.dp.toPx() },
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
    }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val budgetLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
    val budgetPillColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

    val scaleTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        color = onSurfaceVariant.copy(alpha = 0.65f)
    )
    val budgetTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onTertiaryContainer
    )

    // Calculate maximum scale
    val maxValue = remember(items) {
        var highest = 1L
        for (item in items) {
            highest = max(highest, item.expenseCents)
            if (item.incomeCents != null) highest = max(highest, item.incomeCents)
            if (item.budgetCents != null) highest = max(highest, item.budgetCents)
        }
        (highest * 1.15).toLong() // 15% top headroom
    }

    LaunchedEffect(selectedIndex) {
        val idx = selectedIndex
        if (idx != null && idx != lastHapticIndex) {
            haptics.tick()
            lastHapticIndex = idx
            onItemSelected?.invoke(idx, items.getOrNull(idx))
        } else if (idx == null) {
            lastHapticIndex = null
            onItemSelected?.invoke(null, null)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(items) {
                    detectTapGestures(
                        onTap = { offset ->
                            val n = items.size
                            if (n > 0) {
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                val idx = (fraction * n).toInt().coerceIn(0, n - 1)
                                selectedIndex = if (selectedIndex == idx) null else idx
                            }
                        }
                    )
                }
                .pointerInput(items) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val n = items.size
                            if (n > 0) {
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                selectedIndex = (fraction * n).toInt().coerceIn(0, n - 1)
                            }
                        },
                        onDragEnd = { /* keep selection so user can tap inspect month */ },
                        onDragCancel = { /* keep selection */ },
                        onDrag = { change, _ ->
                            val n = items.size
                            if (n > 0) {
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                selectedIndex = (fraction * n).toInt().coerceIn(0, n - 1)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val n = items.size
                val slotWidth = w / n.toFloat()

                // 1. Draw Subtle Gridlines at 25%, 50%, 75%, 100% of maxValue
                val gridFractions = floatArrayOf(0.25f, 0.50f, 0.75f, 1.0f)
                for (fraction in gridFractions) {
                    val value = (fraction * maxValue).toLong()
                    val gridY = (h - ((value.toDouble() / maxValue.toDouble()) * h).toFloat()).coerceIn(4f, h - 4f)
                    drawLine(
                        color = outlineColor.copy(alpha = 0.25f),
                        start = Offset(0f, gridY),
                        end = Offset(w, gridY),
                        strokeWidth = gridStroke.width,
                        pathEffect = gridStroke.pathEffect
                    )
                    val scaleText = MoneyUtils.formatCompactCents(value)
                    val textLayout = textMeasurer.measure(text = scaleText, style = scaleTextStyle)
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(4f, (gridY - textLayout.size.height - 2f).coerceAtLeast(2f))
                    )
                }

                // 2. Draw Baseline
                drawLine(outlineColor, Offset(0f, h - 2f), Offset(w, h - 2f), strokeWidth = 2f)

                // 3. Draw Budget Reference Line and Pill
                val budgetCents = items.firstNotNullOfOrNull { it.budgetCents?.takeIf { b -> b > 0L } }
                if (budgetCents != null && budgetCents > 0L) {
                    val budgetY = h - ((budgetCents.toDouble() / maxValue.toDouble()) * h).toFloat()
                    drawLine(
                        color = budgetLineColor,
                        start = Offset(0f, budgetY),
                        end = Offset(w, budgetY),
                        strokeWidth = budgetStroke.width,
                        pathEffect = budgetStroke.pathEffect
                    )

                    val budgetLabel = "Budget ${MoneyUtils.formatCompactCents(budgetCents)}"
                    val budgetLayout = textMeasurer.measure(text = budgetLabel, style = budgetTextStyle)
                    val pillW = budgetLayout.size.width + 12f
                    val pillH = budgetLayout.size.height + 4f
                    val pillX = w - pillW - 6f
                    val pillY = (budgetY - pillH / 2f).coerceIn(2f, h - pillH - 2f)
                    drawRoundRect(
                        color = budgetPillColor,
                        topLeft = Offset(pillX, pillY),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawText(
                        textLayoutResult = budgetLayout,
                        topLeft = Offset(pillX + 6f, pillY + 2f)
                    )
                }

                // 4. Draw Bar Clusters
                for (i in 0 until n) {
                    val item = items[i]
                    val isSelected = selectedIndex == i
                    val hasActiveSelection = selectedIndex != null

                    val slotLeft = i * slotWidth
                    val clusterWidth = slotWidth * 0.72f
                    val clusterOffset = slotLeft + (slotWidth - clusterWidth) / 2f

                    // Selection highlight background behind cluster
                    if (isSelected) {
                        drawRoundRect(
                            color = selectionBgColor,
                            topLeft = Offset(slotLeft + 2f, 4f),
                            size = Size(slotWidth - 4f, h - 4f),
                            cornerRadius = cornerRadius
                        )
                    }

                    val alpha = if (hasActiveSelection && !isSelected) 0.40f else 1.0f

                    if (item.incomeCents != null) {
                        // Dual Bar Mode (Income & Expense side-by-side)
                        val barWidth = (clusterWidth - 4f) / 2f

                        // Income Bar (Left)
                        val incomeH = ((item.incomeCents.toDouble() / maxValue.toDouble()) * h).toFloat().coerceAtLeast(4f)
                        drawRoundRect(
                            color = incomeColor.copy(alpha = alpha),
                            topLeft = Offset(clusterOffset, h - incomeH),
                            size = Size(barWidth, incomeH),
                            cornerRadius = cornerRadius
                        )

                        // Expense Bar (Right)
                        val expenseH = ((item.expenseCents.toDouble() / maxValue.toDouble()) * h).toFloat().coerceAtLeast(4f)
                        drawRoundRect(
                            color = expenseColor.copy(alpha = alpha),
                            topLeft = Offset(clusterOffset + barWidth + 4f, h - expenseH),
                            size = Size(barWidth, expenseH),
                            cornerRadius = cornerRadius
                        )
                    } else {
                        // Single Bar Mode (Category Spending)
                        val baseBarColor = barColor ?: expenseColor
                        val isOverBudget = item.budgetCents != null && item.budgetCents > 0L && item.expenseCents > item.budgetCents
                        val colorToUse = if (isOverBudget) expenseColor else baseBarColor

                        val barH = ((item.expenseCents.toDouble() / maxValue.toDouble()) * h).toFloat().coerceAtLeast(4f)
                        drawRoundRect(
                            color = colorToUse.copy(alpha = alpha),
                            topLeft = Offset(clusterOffset, h - barH),
                            size = Size(clusterWidth, barH),
                            cornerRadius = cornerRadius
                        )
                    }
                }
            }
        }

        // 5. X-Axis Month / Category Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            for (i in items.indices) {
                val isSelected = selectedIndex == i
                Text(
                    text = items[i].label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6. Dual-Bar Legend (Income / Expenses)
        if (items.any { it.incomeCents != null }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(label = "Income", color = incomeColor)
                Spacer(modifier = Modifier.width(16.dp))
                LegendDot(label = "Expenses", color = expenseColor)
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
