package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSheetHeader

@Composable
fun AccountBalanceHistorySheet(
    account: Account,
    transactions: List<Transaction> = emptyList(),
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
    transactions: List<Transaction> = emptyList(),
    balanceRecords: List<BalanceRecord> = emptyList(),
    onAddRecord: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OrbitSheetHeader(
            title = "Balance History",
            subtitle = "${account.name} • Current ${MoneyUtils.formatCents(account.currentBalanceCents)}",
            onNavigateBack = onNavigateBack,
            onDismiss = onDismiss,
            trailingActions = {
                OrbitActionChip(
                    label = "Add Record",
                    leadingIcon = Icons.Default.Add,
                    onClick = onAddRecord
                )
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            AccountInlineBalanceHistorySection(
                balanceRecords = balanceRecords,
                onAddRecord = onAddRecord,
                showHeaderAction = false
            )
        }
    }
}

/**
 * Reusable Balance History sparkline + grouped snapshot list used inline on the Account Detail
 * `Balance History` tab as well as the Credit Card Balance History sub-mode.
 */
@Composable
fun AccountInlineBalanceHistorySection(
    balanceRecords: List<BalanceRecord>,
    onAddRecord: () -> Unit,
    modifier: Modifier = Modifier,
    showHeaderAction: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (balanceRecords.size >= 2) {
            BalanceTrendSparkline(
                records = balanceRecords,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )
        }

        OrbitSectionCard(
            title = "Balance Snapshots",
            subtitle = if (balanceRecords.isNotEmpty()) "${balanceRecords.size} recorded entries" else null,
            action = if (showHeaderAction) {
                {
                    OrbitActionChip(
                        label = "Add Record",
                        leadingIcon = Icons.Default.Add,
                        onClick = onAddRecord
                    )
                }
            } else null,
            showHeaderDivider = balanceRecords.isNotEmpty(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (balanceRecords.isEmpty()) {
                EmptyStateCard(
                    title = "No Balance History Yet",
                    description = "Tap \"Add Record\" above to record your first balance snapshot for this account."
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    balanceRecords.forEachIndexed { index, record ->
                        val older = balanceRecords.getOrNull(index + 1)
                        val deltaCents = older?.let { record.balanceCents - it.balanceCents }
                        val subtitleText = if (deltaCents != null) {
                            "Change: ${MoneyUtils.formatCents(deltaCents, signed = true)}"
                        } else {
                            "Initial Record"
                        }
                        val friendlyDate = remember(record.recordDate) {
                            LocalDateUtils.parseIsoDate(record.recordDate)
                                ?.let { LocalDateUtils.formatDisplayDate(it) }
                                ?: record.recordDate
                        }

                        OrbitCompactListRow(
                            title = friendlyDate,
                            subtitle = subtitleText,
                            showTopDivider = index > 0,
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OrbitBadge(
                                        text = if (record.source == BalanceRecordSource.SIMPLEFIN) "SimpleFIN" else "Manual",
                                        containerColor = if (record.source == BalanceRecordSource.SIMPLEFIN) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        contentColor = if (record.source == BalanceRecordSource.SIMPLEFIN) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Text(
                                        text = MoneyUtils.formatCents(record.balanceCents),
                                        style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        )
                    }
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
    val chronological = remember(records) { records.sortedBy { it.recordDate } }
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(96.dp).padding(12.dp)) {
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
                    colors = listOf(primaryColor.copy(alpha = 0.24f), Color.Transparent),
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
                drawCircle(color = primaryColor, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}
