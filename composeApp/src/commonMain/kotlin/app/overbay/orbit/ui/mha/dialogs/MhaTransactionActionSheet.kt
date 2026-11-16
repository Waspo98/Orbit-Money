package app.overbay.orbit.ui.mha.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.mha.MhaTransactionItem
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@Composable
fun MhaTransactionActionSheet(
    item: MhaTransactionItem,
    onDismiss: () -> Unit,
    onToggleEligible: () -> Unit,
    onResetOverride: () -> Unit,
    onToggleIgnore: () -> Unit,
    onOpenTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val parsedDate = LocalDateUtils.parseIsoDate(item.effectiveDate)
    val displayDate = parsedDate?.let { LocalDateUtils.formatDisplayDate(it) } ?: item.effectiveDate

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OrbitSheetHeader(
                title = item.merchant,
                subtitle = "$displayDate · ${item.category?.labelWithEmoji ?: "❓ Uncategorized"}",
                onDismiss = onDismiss
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = MoneyUtils.formatCents(abs(item.amountCents)),
                            style = TabularNumStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                            color = if (item.isEligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        OrbitBadge(
                            text = if (item.isEligible) "Counted in MHA" else "Excluded",
                            containerColor = if (item.isEligible) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (item.isEligible) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    if (item.eligibilityReason.isNotBlank()) {
                        Text(
                            text = item.eligibilityReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item.account?.name?.takeIf { it.isNotBlank() }?.let { acctName ->
                        OrbitMiniStatRow(
                            label = "Account",
                            value = acctName,
                            showTopDivider = true
                        )
                    }

                    item.transaction.notes?.takeIf { it.isNotBlank() }?.let { note ->
                        OrbitMiniStatRow(
                            label = "Notes",
                            value = note,
                            showTopDivider = true
                        )
                    }
                }
            }

            Button(
                onClick = {
                    haptics.confirm()
                    onToggleEligible()
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = if (item.isEligible) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = if (item.isEligible) "Exclude From MHA" else "Count as MHA Eligible",
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (item.isExplicitOverride) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onResetOverride()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset to Auto-Default")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onToggleIgnore()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(if (item.transaction.effectiveIsIgnored) "Un-Ignore" else "Ignore")
                }

                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onDismiss()
                        onOpenTransaction()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Details")
                }
            }
        }
    }
}
