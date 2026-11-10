package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.SubProgressSummary
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.components.OrbitIcons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditCardPerksPanel(
    profile: CreditCardProfile,
    subProgress: SubProgressSummary? = null,
    onEditPerks: () -> Unit,
    onToggleBenefit: ((benefitIndex: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val subEligibility = remember(profile) { CreditCardStrategyEngine.computeSubEligibility(profile) }
    val netFeeCents = remember(profile) { CreditCardStrategyEngine.computeCardEffectiveAnnualFeeCents(profile) }
    val strategy = profile.strategy

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header with Stylized Card-Art Badge & Network Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!profile.network.isNullOrBlank()) {
                            Text(
                                text = profile.network.uppercase().take(4),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = OrbitIcons.NetWorth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = profile.cardName ?: "Credit Card Strategy & Perks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!profile.network.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Text(
                                        text = profile.network,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        profile.issuerName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onEditPerks) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Card Profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Strategy Badges: Player, Opened Date, 5/24 Status, SUB Cooldown
            if (!strategy.cardholder.isNullOrBlank() || strategy.openedDate != null || strategy.isBusinessCard || subEligibility != null) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    strategy.cardholder?.takeIf { it.isNotBlank() }?.let { holder ->
                        StrategyPillBadge(text = holder, isAccent = true)
                    }
                    if (strategy.isBusinessCard) {
                        StrategyPillBadge(text = "Business • 5/24 Exempt")
                    } else if (strategy.openedDate != null) {
                        val opened = LocalDateUtils.parseIsoDate(strategy.openedDate)
                        val dropOff = opened?.let { LocalDateUtils.addMonthsClamped(it, 24) }
                        val today = LocalDateUtils.todayLocalDate()
                        if (dropOff != null && dropOff > today) {
                            StrategyPillBadge(text = "5/24 until ${LocalDateUtils.formatIsoDate(dropOff)}")
                        }
                    }
                    strategy.openedDate?.let { opened ->
                        StrategyPillBadge(text = "Opened $opened")
                    }
                    subEligibility?.let { elig ->
                        StrategyPillBadge(
                            text = elig.statusLabel,
                            isPositive = elig.isEligibleNow
                        )
                    }
                }
            }

            // Active SUB Spend Tracker Card
            if (subProgress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                SubTrackerMiniCard(sub = subProgress)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Annual fee & cycle status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profile.annualFeeCents?.let { fee ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OrbitColorTokens.Purple.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Annual Fee",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrbitColorTokens.Purple
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = MoneyUtils.formatCents(fee),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OrbitColorTokens.Purple
                            )
                            val netText = if (netFeeCents < fee) {
                                "Net ${MoneyUtils.formatCents(netFeeCents)} after perks"
                            } else {
                                profile.annualFeePostDate?.let { "Renews $it" } ?: "No credits used yet"
                            }
                            Text(
                                text = netText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (netFeeCents <= 0L) OrbitColorTokens.Green else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                profile.creditLimitCents?.let { limit ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Credit Line",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = MoneyUtils.formatCents(limit),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (profile.statementClosingDay != null) {
                                Text(
                                    text = "Closes day ${profile.statementClosingDay}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Authorized users chips if present
            if (profile.authorizedUsers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Authorized Users: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        profile.authorizedUsers.forEach { user ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = user,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Reward Multipliers
            if (profile.rewardCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "REWARD MULTIPLIERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profile.rewardCategories.forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                val multText = if (cat.multiplier == cat.multiplier.toLong().toDouble()) {
                                    "${cat.multiplier.toLong()}x"
                                } else {
                                    "${cat.multiplier}x"
                                }
                                Text(
                                    text = multText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = cat.label.ifBlank { cat.category },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Cadence-Aware Statement Credits Checklist
            if (profile.benefits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "STATEMENT CREDITS & PERKS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile.benefits.forEachIndexed { index, benefit ->
                        CardBenefitChecklistRow(
                            benefit = benefit,
                            onToggle = { onToggleBenefit?.invoke(index) }
                        )
                    }
                }
            }

            profile.notes?.takeIf { it.isNotBlank() }?.let { notesText ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = notesText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
