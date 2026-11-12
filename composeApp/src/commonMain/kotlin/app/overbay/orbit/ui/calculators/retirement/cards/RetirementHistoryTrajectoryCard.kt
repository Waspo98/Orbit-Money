package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.calculators.retirement.LinkedRetirementAccountDetail
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.charts.chartHorizontalScrub
import app.overbay.orbit.ui.components.charts.rememberSmoothScrubPosition
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
                    OrbitStackedSegmentBar(
                        segments = accountBalances.mapIndexed { index, acct ->
                            OrbitBarSegment(
                                key = acct.accountId,
                                fraction = max(0.02f, acct.balanceCents.toFloat() / totalAccountBal.toFloat()),
                                color = accountColorMap[acct.accountId] ?: palette[index % palette.size]
                            )
                        },
                        height = 8.dp,
                        cornerRadius = 4.dp,
                        normalizeToFullWidth = true
                    )
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
        (peak.toDouble() * 1.1).toLong().coerceAtLeast(1L)
    }
    val animatedMaxVal by animateFloatAsState(
        targetValue = maxVal.toFloat(),
        animationSpec = OrbitSprings.FloatSpatialDefault,
        label = "historyChartMaxScale"
    )

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

    val activePt = scrubIndex?.let { points.getOrNull(it) }
    val scrubNormTarget = scrubIndex?.takeIf { it in points.indices && points.size >= 2 }?.let { idx ->
        val xNorm = idx.toFloat() / (points.size - 1).toFloat()
        val yNorm = (1f - (points[idx].balanceCents.toFloat() / animatedMaxVal.coerceAtLeast(1f))).coerceIn(0f, 1f)
        Offset(xNorm, yNorm)
    }
    val smoothScrubNorm = rememberSmoothScrubPosition(scrubNormTarget)

    Column(modifier = modifier) {
        // Constant-height readout bar (zero vertical layout shift on touch)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (activePt != null) 1f else 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .height(30.dp)
        ) {
            AnimatedContent(
                targetState = activePt,
                contentKey = { it != null },
                transitionSpec = {
                    fadeIn(OrbitSprings.FloatEffectsFast) togetherWith
                        fadeOut(OrbitSprings.FloatEffectsFast)
                },
                contentAlignment = Alignment.CenterStart,
                label = "historyScrubReadout"
            ) { pt ->
                if (pt != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pt.month, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Total: ${MoneyUtils.formatCents(pt.balanceCents, showCents = false)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                } else {
                    Text(
                        text = "Drag horizontally to inspect monthly balances",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .chartHorizontalScrub(
                    key = points,
                    clearOnRelease = true,
                    onFractionChanged = { fraction ->
                        scrubIndex = if (fraction != null && points.isNotEmpty()) {
                            (fraction * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                        } else null
                    }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val count = points.size
                if (count < 2) return@Canvas
                val denom = animatedMaxVal.toDouble().coerceAtLeast(1.0)

                fun idxToX(idx: Int): Float = (idx.toFloat() / (count - 1).toFloat()) * w
                fun balToY(bal: Cents): Float = (h - ((bal.toDouble() / denom) * h).toFloat()).coerceIn(0f, h)

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

                // 3. Smoothly gliding scrub line & circle
                if (smoothScrubNorm != null) {
                    val sx = smoothScrubNorm.x * w
                    val sy = smoothScrubNorm.y * h
                    drawLine(color = onSurfaceVariant.copy(alpha = 0.6f), start = Offset(sx, 0f), end = Offset(sx, h), strokeWidth = 1.5f)
                    drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = Offset(sx, sy))
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
