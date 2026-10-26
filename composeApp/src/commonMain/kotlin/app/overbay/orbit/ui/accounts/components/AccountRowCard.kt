package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AccountRowCard(
    account: Account,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onPerksClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isCredit = account.type == AccountType.CREDIT
    val isLiability = account.type.isLiability
    val isMortgage = account.type == AccountType.MORTGAGE

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Name, institution, masked last 4
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCredit) OrbitColorTokens.Purple.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCredit) Icons.Default.AccountBox else Icons.Default.Star,
                            contentDescription = null,
                            tint = if (isCredit) OrbitColorTokens.Purple else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            account.institution?.let { inst ->
                                Text(
                                    text = inst,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            account.accountNumberLast4?.let { last4 ->
                                Text(
                                    text = "•••• $last4",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                // Right: Balance & Chevron / More
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        val displayAmount = if (isMortgage && account.homeEquityCents != null) {
                            account.homeEquityCents!!
                        } else {
                            account.currentBalanceCents
                        }

                        val balanceColor = when {
                            isCredit || isLiability -> {
                                if (account.currentBalanceCents != 0L) OrbitColorTokens.Red
                                else MaterialTheme.colorScheme.onSurface
                            }
                            displayAmount >= 0L -> OrbitColorTokens.Green
                            else -> OrbitColorTokens.Red
                        }

                        Text(
                            text = MoneyUtils.formatCents(displayAmount),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = balanceColor
                        )

                        if (isMortgage && account.estimatedValueCents != null) {
                            Text(
                                text = "Home Equity",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else if (isCredit && account.creditCardProfile?.creditLimitCents != null) {
                            val limit = account.creditCardProfile.creditLimitCents
                            val ratio = account.creditCardProfile.creditUtilizationRatio(account.currentBalanceCents)
                            val pct = if (ratio != null) (ratio * 100.0).roundToInt() else null
                            Text(
                                text = if (pct != null) "$pct% of ${MoneyUtils.formatCents(limit)}" else "Limit ${MoneyUtils.formatCents(limit)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pct != null && pct > 30) OrbitColorTokens.Red else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (onEditClick != null || onPerksClick != null) {
                        IconButton(
                            onClick = { onEditClick?.invoke() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Optional Credit Perks mini pill strip
            if (isCredit && account.creditCardProfile != null) {
                val profile = account.creditCardProfile
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profile.rewardCategories.take(3).forEach { reward ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "${reward.multiplier}x ${reward.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    profile.annualFeeCents?.let { fee ->
                        if (fee > 0L) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = OrbitColorTokens.Purple.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${MoneyUtils.formatCents(fee)}/yr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OrbitColorTokens.Purple,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
