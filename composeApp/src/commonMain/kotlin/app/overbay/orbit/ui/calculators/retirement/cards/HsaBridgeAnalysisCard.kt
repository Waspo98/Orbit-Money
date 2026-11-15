package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HsaBridgeAnalysis
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.orbitMorphContentTransform

@Composable
fun HsaBridgeAnalysisCard(
    hsaBridge: HsaBridgeAnalysis?,
    retirementAge: Double,
    modifier: Modifier = Modifier
) {
    var lastActiveBridge by remember { mutableStateOf(hsaBridge) }
    if (hsaBridge != null && hsaBridge.bridgeYears > 0) {
        lastActiveBridge = hsaBridge
    }

    val isNoBridgeNeeded = retirementAge >= 65.0 || hsaBridge == null || hsaBridge.bridgeYears <= 0

    AnimatedContent(
        targetState = isNoBridgeNeeded,
        transitionSpec = { orbitMorphContentTransform(clip = false) },
        modifier = modifier,
        label = "hsaBridgeMode"
    ) { noBridge ->
        if (noBridge) {
            // Compact info banner instead of an oversized empty illustration card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = OrbitTheme.colors.income,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No Pre-65 HSA Bridge Needed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Retiring at ${retirementAge.toInt()} avoids early non-medical HSA distribution penalties.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val activeBridge = lastActiveBridge ?: return@AnimatedContent
            OrbitSectionCard(
                title = "HSA Early-Retirement Bridge",
                subtitle = "Pre-age 65 distribution penalty guardrails"
            ) {
                val bannerContainerColor by animateColorAsState(
                    targetValue = if (activeBridge.isBridgeCovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    animationSpec = OrbitSprings.ColorEffectsFast,
                    label = "hsaBannerContainer"
                )
                val bannerTitleColor by animateColorAsState(
                    targetValue = if (activeBridge.isBridgeCovered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    animationSpec = OrbitSprings.ColorEffectsFast,
                    label = "hsaBannerTitle"
                )
                val badgeContainerColor by animateColorAsState(
                    targetValue = if (activeBridge.isBridgeCovered) OrbitTheme.colors.income else OrbitTheme.colors.expense,
                    animationSpec = OrbitSprings.ColorEffectsFast,
                    label = "hsaBadgeContainer"
                )

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bannerContainerColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = if (activeBridge.isBridgeCovered) "Early Bridge Covered" else "Early Bridge Shortfall",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = bannerTitleColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (activeBridge.isBridgeCovered) {
                                        "Non-HSA portfolio covers ${activeBridge.bridgeYears} bridge years before age 65."
                                    } else {
                                        "Non-HSA funds fall short during the ${activeBridge.bridgeYears}-year pre-65 window."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = bannerTitleColor.copy(alpha = 0.85f)
                                )
                            }

                            OrbitBadge(
                                text = if (activeBridge.isBridgeCovered) "Covered" else "-${MoneyUtils.formatCompactCents(activeBridge.shortfallCents)} Short",
                                containerColor = badgeContainerColor,
                                contentColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OrbitMiniStatRow(
                                label = "Bridge Window",
                                value = "${activeBridge.bridgeYears} Yrs (${retirementAge.toInt()} -> 65)"
                            )
                            OrbitMiniStatRow(
                                label = "Total Bridge Need",
                                value = MoneyUtils.formatCents(activeBridge.bridgeNeedCents, showCents = false),
                                showTopDivider = true
                            )
                            OrbitMiniStatRow(
                                label = "Projected Non-HSA",
                                value = MoneyUtils.formatCents(activeBridge.projectedNonHsaAtRetirementCents, showCents = false),
                                valueColor = OrbitTheme.colors.income,
                                showTopDivider = true
                            )
                            OrbitMiniStatRow(
                                label = "Preserved HSA",
                                value = MoneyUtils.formatCents(activeBridge.projectedHsaAtRetirementCents, showCents = false),
                                showTopDivider = true
                            )
                        }
                    }

                    Text(
                        text = "Under IRC Section 223, non-medical HSA withdrawals before age 65 incur ordinary tax plus a 20% penalty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
