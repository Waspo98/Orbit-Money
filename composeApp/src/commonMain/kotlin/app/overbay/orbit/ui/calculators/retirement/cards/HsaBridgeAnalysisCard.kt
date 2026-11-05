package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HsaBridgeAnalysis
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun HsaBridgeAnalysisCard(
    hsaBridge: HsaBridgeAnalysis?,
    retirementAge: Double,
    annualSpendingCents: Cents,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "HSA Early-Retirement Bridge",
        subtitle = "Pre-age 65 distribution penalty guardrails",
        modifier = modifier
    ) {
        if (hsaBridge == null || hsaBridge.bridgeYears <= 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "No Early-Retirement Bridge Required",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your target retirement age (${retirementAge.toInt()}) is at or above 65. All HSA distributions for any purpose can be taken penalty-free starting at age 65.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            return@OrbitSectionCard
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status Header Badge Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (hsaBridge.isBridgeCovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (hsaBridge.isBridgeCovered) "Early Bridge Covered" else "Early Bridge Shortfall",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hsaBridge.isBridgeCovered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hsaBridge.isBridgeCovered) {
                                "Non-HSA portfolio covers ${hsaBridge.bridgeYears} bridge years before age 65 without incurring HSA non-medical withdrawal penalties."
                            } else {
                                "Non-HSA funds fall short of living expenses during the ${hsaBridge.bridgeYears}-year window before penalty-free HSA access at 65."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hsaBridge.isBridgeCovered) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hsaBridge.isBridgeCovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = if (hsaBridge.isBridgeCovered) "Covered" else "-${MoneyUtils.formatCents(hsaBridge.shortfallCents, showCents = false)} Short",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Key Metrics Breakdown
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BridgeMetricRow(
                        label = "Bridge Duration",
                        value = "${hsaBridge.bridgeYears} Years (Age ${retirementAge.toInt()} -> 65)"
                    )
                    BridgeMetricRow(
                        label = "Total Bridge Need",
                        value = MoneyUtils.formatCents(hsaBridge.bridgeNeedCents, showCents = false)
                    )
                    BridgeMetricRow(
                        label = "Projected Non-HSA at Retirement",
                        value = MoneyUtils.formatCents(hsaBridge.projectedNonHsaAtRetirementCents, showCents = false),
                        isEmphasized = true
                    )
                    BridgeMetricRow(
                        label = "Projected HSA at Retirement (Preserved)",
                        value = MoneyUtils.formatCents(hsaBridge.projectedHsaAtRetirementCents, showCents = false)
                    )
                }
            }

            // Rule Explainer
            Text(
                text = "Note: Under IRC Section 223, non-medical HSA distributions before age 65 incur ordinary income tax plus a 20% penalty. At age 65, the 20% penalty is eliminated, allowing penalty-free withdrawals for any purpose.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun BridgeMetricRow(
    label: String,
    value: String,
    isEmphasized: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isEmphasized) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isEmphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
