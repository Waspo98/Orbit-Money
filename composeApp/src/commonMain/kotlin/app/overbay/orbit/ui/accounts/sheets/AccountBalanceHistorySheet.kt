package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.BalanceRecordSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun AccountBalanceHistorySheet(
    account: Account,
    transactions: List<Transaction>,
    balanceRecords: List<BalanceRecord> = emptyList(),
    onAddRecord: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        AccountBalanceHistoryContent(
            account = account,
            transactions = transactions,
            balanceRecords = balanceRecords,
            onAddRecord = onAddRecord,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun AccountBalanceHistoryContent(
    account: Account,
    transactions: List<Transaction>,
    balanceRecords: List<BalanceRecord> = emptyList(),
    onAddRecord: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Balance History & Activity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${account.name} • Current ${MoneyUtils.formatCents(account.currentBalanceCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptics.tick()
                        onAddRecord()
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Record", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Close", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Balance Trend Sparkline if >= 2 snapshots
        if (balanceRecords.size >= 2) {
            BalanceTrendSparkline(
                records = balanceRecords,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "BALANCE SNAPSHOTS (${balanceRecords.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (balanceRecords.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Balance Snapshots Yet",
                        description = "Tap \"Add Record\" above to record your first dated snapshot for this account."
                    )
                }
            } else {
                itemsIndexed(balanceRecords, key = { _, rec -> rec.id }) { index, record ->
                    val older = balanceRecords.getOrNull(index + 1)
                    val deltaCents = older?.let { record.balanceCents - it.balanceCents }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = record.recordDate,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (record.source == BalanceRecordSource.SIMPLEFIN) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    ) {
                                        Text(
                                            text = if (record.source == BalanceRecordSource.SIMPLEFIN) "SimpleFIN" else "Manual",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (record.source == BalanceRecordSource.SIMPLEFIN) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                if (deltaCents != null) {
                                    val deltaColor = if (deltaCents > 0L) colors.income else if (deltaCents < 0L) colors.expense else MaterialTheme.colorScheme.outline
                                    Text(
                                        text = "Change: ${MoneyUtils.formatCents(deltaCents, signed = true)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = deltaColor
                                    )
                                } else {
                                    Text(
                                        text = "Initial Record",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Text(
                                text = MoneyUtils.formatCents(record.balanceCents),
                                style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "RECENT TRANSACTIONS (${transactions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (transactions.isEmpty()) {
                item {
                    Text(
                        text = "No recent transactions found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                itemsIndexed(transactions.take(20), key = { _, txn -> txn.id }) { _, txn ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = txn.effectiveMerchant,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = txn.effectiveDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        val isIncome = txn.effectiveAmountCents > 0L
                        Text(
                            text = MoneyUtils.formatCents(txn.effectiveAmountCents, signed = true),
                            style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            color = if (isIncome) OrbitColorTokens.Green else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun BalanceTrendSparkline(
    records: List<BalanceRecord>,
    modifier: Modifier = Modifier
) {
    val chronological = records.sortedBy { it.recordDate }
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(90.dp).padding(10.dp)) {
            if (chronological.size < 2) return@Canvas

            val minVal = chronological.minOf { it.balanceCents }
            val maxVal = chronological.maxOf { it.balanceCents }
            val range = if (maxVal == minVal) 1L else (maxVal - minVal)

            val w = size.width
            val h = size.height
            val padY = 6f
            val usableH = h - (padY * 2f)

            val points = chronological.mapIndexed { idx, rec ->
                val x = idx.toFloat() / (chronological.size - 1).toFloat() * w
                val normY = (rec.balanceCents - minVal).toFloat() / range.toFloat()
                val y = h - padY - (normY * usableH)
                x to y
            }

            val linePath = Path().apply {
                moveTo(points.first().first, points.first().second)
                for (i in 1 until points.size) {
                    lineTo(points[i].first, points[i].second)
                }
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(points.last().first, h)
                lineTo(points.first().first, h)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = 0f,
                    endY = h
                )
            )

            drawPath(
                path = linePath,
                color = primaryColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            points.forEach { (x, y) ->
                drawCircle(color = primaryColor, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
    }
}
