package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.calculators.retirement.LinkedRetirementAccountDetail
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.ui.components.orbitFadeContentTransform
import app.overbay.orbit.ui.components.charts.chartHorizontalScrub
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun RetirementHistoryTrajectoryCard(
    filteredPoints: List<RetirementHistoryPoint>,
    linkedAccounts: List<LinkedRetirementAccountDetail> = emptyList(),
    selectedHorizon: String,
    onSelectHorizon: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizons = listOf("YTD", "3Y", "5Y", "10Y", "All")
    val palette = retirementAccountPalette()
    val accountColorMap = remember(linkedAccounts, filteredPoints, palette) {
        val allIds = linkedAccounts.map { it.accountId } + filteredPoints.flatMap { pt -> pt.accounts.map { it.accountId } }
        retirementAccountColorMap(allIds, palette)
    }

    OrbitSectionCard(
        title = "Portfolio Balance History",
        subtitle = "Historical balance snapshots across connected retirement accounts",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OrbitSegmentedTabRow(
                items = horizons,
                selected = selectedHorizon,
                onSelect = onSelectHorizon,
                label = { it }
            )

            var lastNonEmptyPoints by remember { mutableStateOf(filteredPoints) }
            if (filteredPoints.isNotEmpty()) {
                lastNonEmptyPoints = filteredPoints
            }
            AnimatedContent(
                targetState = filteredPoints.isEmpty(),
                transitionSpec = { orbitFadeContentTransform() },
                label = "retirementHistoryEmpty"
            ) { isEmpty ->
                if (isEmpty || lastNonEmptyPoints.isEmpty()) {
                    EmptyStateCard(
                        title = "No Historical Snapshots",
                        description = "Historical investment balances will appear here as periodic balance snapshots accumulate."
                    )
                } else {
                    val activePoints = lastNonEmptyPoints
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val latestPoint = activePoints.last()
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
                                cornerRadius = 999.dp,
                                normalizeToFullWidth = true
                            )
                        }

                        InteractiveHistoryChart(
                            points = activePoints,
                            accountColorMap = accountColorMap,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Growth Summary Banner
                        val latestBalance = latestPoint.balanceCents
                        val startBalance = activePoints.first().balanceCents
                        val diffCents = latestBalance - startBalance
                        val diffPercent = if (startBalance > 0L) (diffCents.toDouble() / startBalance.toDouble()) * 100.0 else 0.0
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Latest Total Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = MoneyUtils.formatCents(latestBalance, showCents = false),
                                        style = TabularNumStyle.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$selectedHorizon Change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val isGain = diffCents >= 0L
                                    Text(
                                        text = "${if (isGain) "+" else ""}${MoneyUtils.formatCents(diffCents, showCents = false)} (${if (isGain) "+" else ""}${diffPercent.toInt()}%)",
                                        style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                        color = if (isGain) OrbitTheme.colors.income else OrbitTheme.colors.expense
                                    )
                                }
                            }
                        }

                        // O(N) indexed per-account changes over selected horizon
                        if (linkedAccounts.isNotEmpty() && activePoints.size >= 2) {
                            val firstById = remember(activePoints) { activePoints.first().accounts.associateBy { it.accountId } }
                            val lastById = remember(activePoints) { activePoints.last().accounts.associateBy { it.accountId } }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Account Growth Over $selectedHorizon",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                linkedAccounts.forEachIndexed { index, account ->
                                    val color = account.accountId?.let { accountColorMap[it] } ?: palette[index % palette.size]
                                    val startBal = account.accountId?.let { firstById[it]?.balanceCents } ?: 0L
                                    val endBal = account.accountId?.let { lastById[it]?.balanceCents } ?: account.balanceCents
                                    val changeCents = endBal - startBal
                                    val changePct = if (startBal > 0L) (changeCents.toDouble() / startBal.toDouble()) * 100.0 else 0.0
                                    val isGain = changeCents >= 0L

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
                                                Text(account.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            }
                                            Text(
                                                text = "${if (isGain) "+" else ""}${MoneyUtils.formatCompactCents(changeCents)} (${if (isGain) "+" else ""}${changePct.toInt()}%)",
                                                style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                                color = if (isGain) OrbitTheme.colors.income else OrbitTheme.colors.expense
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
    val textMeasurer = rememberTextMeasurer()

    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    val maxVal = remember(points) {
        val peak = points.maxOfOrNull { it.balanceCents } ?: 100_000_00L
        (peak.toDouble() * 1.1).toLong().coerceAtLeast(1L)
    }
    val maxValAnim = remember { Animatable(maxVal.toFloat()) }
    LaunchedEffect(maxVal) {
        maxValAnim.animateTo(maxVal.toFloat().coerceAtLeast(1f), OrbitSprings.FloatSpatialDefault)
    }
    val maxValState = maxValAnim.asState()

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = OrbitTheme.colors.cardBorder
    val tabularBase = TabularNumStyle
    val axisStyle = remember(tabularBase, onSurfaceVariant) { tabularBase.copy(fontSize = 9.5.sp, color = onSurfaceVariant) }
    val totalStroke = remember(density) { Stroke(width = with(density) { 2.5.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val seriesStroke = remember(density) { Stroke(width = with(density) { 1.5.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val acctPath = remember { Path() }
    val totalPath = remember { Path() }

    // Pre-index O(N) account balance arrays outside Canvas draw pass
    val preindexedSeries = remember(points) {
        val idSet = linkedSetOf<Long>()
        points.forEach { pt -> pt.accounts.forEach { idSet.add(it.accountId) } }
        idSet.map { acctId ->
            val values = LongArray(points.size) { -1L }
            points.forEachIndexed { idx, pt ->
                pt.accounts.forEach { acct ->
                    if (acct.accountId == acctId) values[idx] = acct.balanceCents
                }
            }
            acctId to values
        }
    }

    val activePt = scrubIndex?.let { points.getOrNull(it) }
    val readoutAlpha by animateFloatAsState(
        targetValue = if (activePt != null) 0.96f else 0.55f,
        animationSpec = OrbitSprings.FloatEffectsFast,
        label = "retirementHistoryReadoutAlpha"
    )

    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = readoutAlpha),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(32.dp)
        ) {
            AnimatedContent(
                targetState = activePt,
                contentKey = { it != null },
                transitionSpec = { fadeIn(OrbitSprings.FloatEffectsFast) togetherWith fadeOut(OrbitSprings.FloatEffectsFast) },
                contentAlignment = Alignment.CenterStart,
                label = "historyScrubReadout"
            ) { pt ->
                if (pt != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pt.month, style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                        Text(
                            text = "Total: ${MoneyUtils.formatCents(pt.balanceCents, showCents = false)}",
                            style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = primaryColor
                        )
                    }
                } else {
                    Text(
                        text = "Drag horizontally to inspect snapshots",
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
                .height(170.dp)
                .chartHorizontalScrub(
                    key = points,
                    clearOnRelease = true,
                    onFractionChanged = { fraction ->
                        val nextIdx = if (fraction != null && points.isNotEmpty()) {
                            (fraction * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                        } else null
                        if (nextIdx != null && nextIdx != scrubIndex) {
                            haptics.tick()
                        }
                        scrubIndex = nextIdx
                    }
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = points.size
                if (count < 2) return@Canvas
                val axisGutterPx = 46.dp.toPx()
                val topPadPx = 6.dp.toPx()
                val bottomPadPx = 4.dp.toPx()
                val plotW = (size.width - axisGutterPx).coerceAtLeast(1f)
                val plotH = (size.height - topPadPx - bottomPadPx).coerceAtLeast(1f)
                val denom = maxValState.value.toDouble().coerceAtLeast(1.0)

                fun idxToX(idx: Int): Float = (idx.toFloat() / (count - 1).toFloat()) * plotW
                fun balToY(bal: Cents): Float = (topPadPx + plotH - ((bal.toDouble() / denom) * plotH).toFloat()).coerceIn(topPadPx, topPadPx + plotH)

                listOf(1f to denom.toLong(), 0.5f to (denom.toLong() / 2L), 0f to 0L).forEach { (ratio, cents) ->
                    val y = topPadPx + plotH * (1f - ratio)
                    drawLine(color = gridColor.copy(alpha = 0.45f), start = Offset(0f, y), end = Offset(plotW, y), strokeWidth = 1.dp.toPx())
                    val label = textMeasurer.measure(MoneyUtils.formatCompactCents(cents), style = axisStyle, maxLines = 1)
                    drawText(label, topLeft = Offset(plotW + 6.dp.toPx(), (y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
                }

                preindexedSeries.forEach { (acctId, series) ->
                    val color = accountColorMap[acctId] ?: primaryColor
                    acctPath.rewind()
                    var started = false
                    for (idx in 0 until count) {
                        val b = series[idx]
                        if (b >= 0L) {
                            val x = idxToX(idx)
                            val y = balToY(b)
                            if (!started) { acctPath.moveTo(x, y); started = true } else acctPath.lineTo(x, y)
                        }
                    }
                    if (started) drawPath(acctPath, color = color.copy(alpha = 0.78f), style = seriesStroke)
                }

                totalPath.rewind()
                for (idx in 0 until count) {
                    val x = idxToX(idx)
                    val y = balToY(points[idx].balanceCents)
                    if (idx == 0) totalPath.moveTo(x, y) else totalPath.lineTo(x, y)
                }
                drawPath(totalPath, color = primaryColor, style = totalStroke)

                val activeIdx = scrubIndex
                if (activeIdx != null && activeIdx in points.indices) {
                    val sx = idxToX(activeIdx)
                    val sy = balToY(points[activeIdx].balanceCents)
                    drawLine(color = onSurfaceVariant.copy(alpha = 0.7f), start = Offset(sx, topPadPx), end = Offset(sx, topPadPx + plotH), strokeWidth = 1.dp.toPx())
                    drawCircle(color = primaryColor, radius = 5.dp.toPx(), center = Offset(sx, sy))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 46.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(points.firstOrNull()?.month ?: "", style = TabularNumStyle.copy(fontSize = 11.sp), color = onSurfaceVariant)
            Text(points.lastOrNull()?.month ?: "", style = TabularNumStyle.copy(fontSize = 11.sp), color = onSurfaceVariant)
        }
    }
}
