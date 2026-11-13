package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.AccountClosureCandidate
import app.overbay.orbit.domain.AccountClosureResolution
import app.overbay.orbit.domain.ClosureReason
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitModalFooterFrame
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Prompts the user when SimpleFIN stops reporting an account, a loan reaches $0,
 * or a large payoff credit matches the remaining loan principal.
 */
@Composable
fun AccountClosurePromptDialog(
    candidate: AccountClosureCandidate,
    selectedResolution: AccountClosureResolution,
    isResolving: Boolean,
    errorMessage: String?,
    onSelectResolution: (AccountClosureResolution) -> Unit,
    onConfirm: () -> Unit,
    onDismissPrompt: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val account = candidate.account
    val institutionLabel = account.institution?.takeIf { it.isNotBlank() } ?: "Your bank"
    val propertyLabel = candidate.propertyTitle ?: account.name

    val reasonExplanation = when (candidate.reason) {
        ClosureReason.MISSING_FROM_SIMPLEFIN ->
            "$institutionLabel stopped reporting \"${account.name}\" during SimpleFIN sync, which usually happens after an account is paid off or closed."
        ClosureReason.BALANCE_REACHED_ZERO ->
            "\"${account.name}\" now has a $0.00 balance."
        ClosureReason.LARGE_PAYOFF_CREDIT -> {
            val txn = candidate.payoffTransaction
            val amtText = txn?.let { MoneyUtils.formatCents(it.effectiveAmountCents) } ?: "a payoff credit"
            val merchantText = txn?.effectiveMerchant?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            val dateText = txn?.effectiveDate?.take(10)?.let { " on $it" } ?: ""
            "Orbit spotted a +$amtText payment$merchantText$dateText that nearly matched the ${MoneyUtils.formatCents(account.currentBalanceCents)} balance on \"${account.name}\"."
        }
    }

    OrbitModalSheet(
        onDismissRequest = { if (!isResolving) onSnooze() },
        modifier = modifier,
        footer = {
            OrbitModalFooterFrame(errorBannerText = errorMessage) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { haptics.confirm(); onConfirm() },
                        enabled = !isResolving,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Confirm", fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { haptics.tick(); onSnooze() },
                            enabled = !isResolving,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Ask Later", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { haptics.tick(); onDismissPrompt() },
                            enabled = !isResolving,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Still Open", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Did you recently pay off or close this account?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(account.name)
                            account.institution?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = reasonExplanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (candidate.hasPropertyContext) {
                val estFormatted = candidate.estimatedPropertyValueCents?.let { MoneyUtils.formatCents(it) }
                Text(
                    text = if (estFormatted != null) {
                        "Choose what happened so $propertyLabel ($estFormatted est. value) stays accurate in Real Estate & Net Worth:"
                    } else {
                        "Choose what happened so your Real Estate equity and Net Worth stay accurate:"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ClosureChoiceCard(
                    icon = OrbitIcons.NetWorth,
                    title = "Sold Property",
                    subtitle = "Archive property & loan so sold home equity is removed from Net Worth (all transactions are kept).",
                    enabled = !isResolving,
                    selected = selectedResolution == AccountClosureResolution.SOLD_PROPERTY,
                    onClick = {
                        haptics.tick()
                        onSelectResolution(AccountClosureResolution.SOLD_PROPERTY)
                    }
                )

                ClosureChoiceCard(
                    icon = Icons.Default.Home,
                    title = "Paid Off — Still Own Home",
                    subtitle = "Keep \"$propertyLabel\" active in Real Estate at full property value and archive this paid-off loan.",
                    enabled = !isResolving,
                    selected = selectedResolution == AccountClosureResolution.PAID_OFF_KEEP_PROPERTY,
                    onClick = {
                        haptics.tick()
                        onSelectResolution(AccountClosureResolution.PAID_OFF_KEEP_PROPERTY)
                    }
                )

                ClosureChoiceCard(
                    icon = Icons.Default.Refresh,
                    title = "Refinanced Loan",
                    subtitle = "Keep \"$propertyLabel\" active so you can link your new lender's mortgage, and archive this closed loan.",
                    enabled = !isResolving,
                    selected = selectedResolution == AccountClosureResolution.REFINANCED_KEEP_PROPERTY,
                    onClick = {
                        haptics.tick()
                        onSelectResolution(AccountClosureResolution.REFINANCED_KEEP_PROPERTY)
                    }
                )
            } else {
                ClosureChoiceCard(
                    icon = OrbitIcons.Accounts,
                    title = "Yes — Closed or Paid Off",
                    subtitle = "Archive \"${account.name}\" so it leaves active balances while preserving all historical transactions.",
                    enabled = !isResolving,
                    selected = selectedResolution == AccountClosureResolution.SOLD_OR_CLOSED,
                    onClick = {
                        haptics.tick()
                        onSelectResolution(AccountClosureResolution.SOLD_OR_CLOSED)
                    }
                )
            }
        }
    }
}

@Composable
private fun ClosureChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "closureCardContainer"
    )
    val border by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder,
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "closureCardBorder"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .orbitTactilePress(enabled = enabled)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
