package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.retirement.LinkedRetirementAccountDetail
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun RetirementHistoryTrajectoryCard(
    historyPoints: List<RetirementHistoryPoint>,
    linkedAccounts: List<LinkedRetirementAccountDetail> = emptyList(),
    selectedHorizon: String,
    onSelectHorizon: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val horizons = listOf("YTD", "3Y", "5Y", "10Y", "All")
    val currentYear = LocalDateUtils.todayLocalDate().year.toString()

    val filteredPoints = when (selectedHorizon) {
        "YTD" -> historyPoints.filter { it.month.startsWith(currentYear) }.ifEmpty { historyPoints.takeLast(12) }
        "3Y" -> historyPoints.takeLast(36)
        "5Y" -> historyPoints.takeLast(60)
        "10Y" -> historyPoints.takeLast(120)
        else -> historyPoints
    }

    val latestBalance = filteredPoints.lastOrNull()?.balanceCents ?: 0L
    val startBalance = filteredPoints.firstOrNull()?.balanceCents ?: 0L
    val diffCents = latestBalance - startBalance
    val diffPercent = if (startBalance > 0L) (diffCents.toDouble() / startBalance.toDouble()) * 100.0 else 0.0
    val palette = retirementAccountPalette()
    val accountColorMap = remember(linkedAccounts, filteredPoints, palette) {
        val allIds = linkedAccounts.map { it.accountId } + filteredPoints.flatMap { pt -> pt.accounts.map { it.accountId } }
        retirementAccountColorMap(allIds, palette)
    }

    OrbitSectionCard(
        title = "Retirement Portfolio History",
        subtitle = "Multi-account historical growth trajectory across connected accounts",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Horizon Selection Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                horizons.forEach { horizon ->
                    FilterChip(
                        selected = selectedHorizon == horizon,
                        onClick = {
                            haptics.tick()
                            onSelectHorizon(horizon)
                        },
                        label = { Text(horizon, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (filteredPoints.isEmpty()) {
                EmptyStateCard(
                    title = "No Historical Snapshots",
                    description = "Historical investment balances will appear here as periodic balance snapshots accumulate."
                )
            } else {
                // Proportional Account Mix Stacked Bar above chart
                val latestPoint = filteredPoints.last()
                val accountBalances = latestPoint.accounts
                val totalAccountBal = accountBalances.sumOf { it.balanceCents }

                if (totalAccountBal > 0L && accountBalances.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        accountBalances.forEachIndexed { index, acct ->
                            val weight = max(0.02f, acct.balanceCents.toFloat() / totalAccountBal.toFloat())
                            val color = accountColorMap[acct.accountId] ?: palette[index % palette.size]
                            Box(modifier = Modifier.weight(weight).height(8.dp).background(color))
                        }
                    }
                }

                // Interactive Multi-Series Canvas Line Chart
                InteractiveHistoryChart(
                    points = filteredPoints,
                    accountColorMap = accountColorMap,
                    modifier = Modifier.fillMaxWidth()
                )

                // Growth Summary Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Latest Total Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(MoneyUtils.formatCents(latestBalance, showCents = false), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$selectedHorizon Change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            val isGain = diffCents >= 0L
                            Text(
                                text = "${if (isGain) "+" else ""}${MoneyUtils.formatCents(diffCents, showCents = false)} (${if (isGain) "+" else ""}${diffPercent.toInt()}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isGain) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Per-Account Change Over Selected Horizon
                if (linkedAccounts.isNotEmpty() && filteredPoints.size >= 2) {
                    val firstPt = filteredPoints.first()
                    val lastPt = filteredPoints.last()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Account Growth Over $selectedHorizon",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        linkedAccounts.forEachIndexed { index, account ->
                            val color = account.accountId?.let { accountColorMap[it] } ?: palette[index % palette.size]
                            val startBal = firstPt.accounts.find { it.accountId == account.accountId }?.balanceCents ?: 0L
                            val endBal = lastPt.accounts.find { it.accountId == account.accountId }?.balanceCents ?: account.balanceCents
                            val changeCents = endBal - startBal
                            val changePct = if (startBal > 0L) (changeCents.toDouble() / startBal.toDouble()) * 100.0 else 0.0
                            val isGain = changeCents >= 0L

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.height(10.dp).width(10.dp).background(color, RoundedCornerShape(2.dp)))
                                        Text(account.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    }
                                    Text(
                                        text = "${if (isGain) "+" else ""}${MoneyUtils.formatCompactCents(changeCents)} (${if (isGain) "+" else ""}${changePct.toInt()}%)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isGain) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveHistoryChart(
    points: List<RetirementHistoryPoint>,
    accountColorMap: Map<Long, Color>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptics = LocalOrbitHaptics.current
    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    var lastHapticIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(scrubIndex) {
        val idx = scrubIndex
        if (idx != null && idx != lastHapticIndex) {
            haptics.tick()
            lastHapticIndex = idx
        } else if (idx == null) {
            lastHapticIndex = null
        }
    }

    val maxVal = remember(points) {
        val peak = points.maxOfOrNull { it.balanceCents } ?: 100_000_00L
        (peak.toDouble() * 1.1).toLong()
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val totalStroke = remember(density) {
        Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val seriesStroke = remember(density) {
        Stroke(width = with(density) { 1.5.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val acctPath = remember { Path() }
    val totalPath = remember { Path() }
    val allAccountIds = remember(points) { points.flatMap { it.accounts }.mapNotNull { it.accountId }.distinct() }

    Column(modifier = modifier) {
        // Scrubber Tooltip Row
        val activePt = scrubIndex?.let { points.getOrNull(it) }
        if (activePt != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(activePt.month, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Total: ${MoneyUtils.formatCents(activePt.balanceCents, showCents = false)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = primaryColor)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(points.size) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (points.isNotEmpty()) {
                                scrubIndex = ((offset.x / size.width) * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                            }
                        },
                        onDragEnd = { scrubIndex = null },
                        onDragCancel = { scrubIndex = null },
                        onDrag = { change, _ ->
                            if (points.isNotEmpty()) {
                                scrubIndex = ((change.position.x / size.width) * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val count = points.size
                if (count < 2) return@Canvas

                fun idxToX(idx: Int): Float = (idx.toFloat() / (count - 1).toFloat()) * w
                fun balToY(bal: Cents): Float = h - ((bal.toDouble() / maxVal.toDouble()) * h).toFloat()

                // 1. Draw individual colored lines for each linked account
                allAccountIds.forEach { acctId ->
                    val color = accountColorMap[acctId] ?: primaryColor
                    acctPath.rewind()
                    var started = false

                    points.forEachIndexed { idx, pt ->
                        val b = pt.accounts.find { it.accountId == acctId }?.balanceCents
                        if (b != null) {
                            val x = idxToX(idx)
                            val y = balToY(b)
                            if (!started) { acctPath.moveTo(x, y); started = true } else acctPath.lineTo(x, y)
                        }
                    }
                    if (started) {
                        drawPath(acctPath, color = color.copy(alpha = 0.8f), style = seriesStroke)
                    }
                }

                // 2. Draw Total Savings line (solid primary)
                totalPath.rewind()
                points.forEachIndexed { idx, pt ->
                    val x = idxToX(idx)
                    val y = balToY(pt.balanceCents)
                    if (idx == 0) totalPath.moveTo(x, y) else totalPath.lineTo(x, y)
                }
                drawPath(totalPath, color = primaryColor, style = totalStroke)

                // 3. Scrub line & circle
                val sIdx = scrubIndex
                if (sIdx != null && sIdx in 0 until count) {
                    val sx = idxToX(sIdx)
                    val pt = points[sIdx]
                    val sy = balToY(pt.balanceCents)
                    drawLine(color = onSurfaceVariant.copy(alpha = 0.6f), start = Offset(sx, 0f), end = Offset(sx, h), strokeWidth = 1.5f)
                    drawCircle(color = primaryColor, radius = with(density) { 5.dp.toPx() }, center = Offset(sx, sy))
                }
            }
        }

        // X-Axis bounds
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(points.firstOrNull()?.month ?: "", style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
            Text(points.lastOrNull()?.month ?: "", style = MaterialTheme.typography.labelSmall, color = onSurfaceVariant)
        }
    }
}
