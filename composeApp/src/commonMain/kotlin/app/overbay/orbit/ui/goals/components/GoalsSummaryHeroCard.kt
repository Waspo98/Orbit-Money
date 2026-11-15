package app.overbay.orbit.ui.goals.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStatTile
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.components.RollingTickerText
import app.overbay.orbit.ui.components.orbitTactilePress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt

/**
 * Active-goal-first headline card pairing overall progress with monthly savings pace
 * and a one-tap bridge into the Funding Accounts tab.
 */
@Composable
fun GoalsSummaryHeroCard(
    activeSavedCents: Cents,
    activeTargetCents: Cents,
    activeProgressRatio: Double,
    totalSavedCents: Cents,
    totalTargetCents: Cents,
    completedCount: Int,
    completedSavedCents: Cents,
    combinedMonthlyPaceCents: Cents,
    combinedMonthlyRequiredCents: Cents,
    totalUnallocatedCashCents: Cents,
    onOpenFundingAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasActiveTargets = activeTargetCents > 0L
    val displaySaved = if (hasActiveTargets) activeSavedCents else totalSavedCents
    val displayTarget = if (hasActiveTargets) activeTargetCents else totalTargetCents
    val displayRatio = if (hasActiveTargets) {
        activeProgressRatio
    } else if (totalTargetCents > 0L) {
        (totalSavedCents.toDouble() / totalTargetCents.toDouble()).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
    if (displayTarget <= 0L) return

    val colors = OrbitTheme.colors
    val pct = (displayRatio * 100).roundToInt()

    OrbitSectionCard(
        title = if (hasActiveTargets) "Active Targets" else "Completed Targets",
        headerBadge = if (hasActiveTargets && completedCount > 0) {
            {
                OrbitBadge(
                    text = "$completedCount reached · ${MoneyUtils.formatCents(completedSavedCents, showCents = false)}",
                    containerColor = colors.income.copy(alpha = 0.14f),
                    contentColor = colors.income
                )
            }
        } else null,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    RollingTickerCents(
                        cents = displaySaved,
                        showCents = false,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "of ${MoneyUtils.formatCents(displayTarget, showCents = false)} target",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RollingTickerText(
                    text = "$pct%",
                    style = TabularNumStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    color = if (hasActiveTargets) MaterialTheme.colorScheme.primary else colors.income
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OrbitPillProgressBar(
                percent = displayRatio.toFloat(),
                customFillColor = if (hasActiveTargets) MaterialTheme.colorScheme.primary else colors.income,
                height = 8.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val paceColor = when {
                    combinedMonthlyPaceCents > 0L -> colors.income
                    combinedMonthlyRequiredCents > 0L -> colors.warning
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val paceSubtitle = if (combinedMonthlyRequiredCents > 0L) {
                    "of ${MoneyUtils.formatCents(combinedMonthlyRequiredCents, showCents = false)}/mo needed"
                } else {
                    "Trailing monthly avg"
                }
                OrbitStatTile(
                    label = "Monthly Pace",
                    value = formatMonthlyPace(combinedMonthlyPaceCents),
                    subText = paceSubtitle,
                    valueColor = paceColor,
                    modifier = Modifier.weight(1f)
                )

                OrbitStatTile(
                    label = "Unallocated Cash",
                    value = MoneyUtils.formatCents(totalUnallocatedCashCents, showCents = false),
                    subText = "Manage funding →",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    subTextColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .orbitTactilePress()
                        .clickable { onOpenFundingAccounts() }
                )
            }
        }
    }
}
