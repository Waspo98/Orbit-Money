package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Unified Credit Card detail modal/pane shared by Credit Card Tracker and Accounts.
 * Features header "Edit Card" action, billing/perks/strategy overview, and recent transactions.
 */
@Composable
fun CreditCardInspectorDetailContent(
    account: Account,
    transactions: List<Transaction>,
    householdMembers: List<HouseholdMember>,
    rewardsToolsEnabled: Boolean,
    onEditCard: () -> Unit,
    onToggleBenefit: (String) -> Unit,
    onOpenPerkUsage: (String) -> Unit,
    onSeeFullTransactions: () -> Unit,
    onClosePane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val profile = account.creditCardProfile ?: CreditCardProfile(accountId = account.id)
    val subProgress = remember(account, transactions) {
        CreditCardStrategyEngine.computeSubProgress(account, transactions)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "card_detail_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.cardName?.takeIf { it.isNotBlank() } ?: account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitleParts = listOfNotNull(
                        account.institution?.takeIf { it.isNotBlank() }
                            ?: profile.issuerName?.takeIf { it.isNotBlank() },
                        account.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { "•••• $it" }
                    )
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            text = subtitleParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = { haptics.tick(); onEditCard() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit Card",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = { haptics.tick(); onClosePane() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "card_detail_section") {
            CreditCardDetailSection(
                account = account,
                profile = profile,
                transactions = transactions,
                subProgress = subProgress,
                householdMembers = householdMembers,
                rewardsToolsEnabled = rewardsToolsEnabled,
                showBillingDetails = true,
                onToggleBenefit = onToggleBenefit,
                onOpenPerkUsage = onOpenPerkUsage
            )
        }

        item(key = "recent_txns_header") {
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT TRANSACTIONS (${transactions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { haptics.tick(); onSeeFullTransactions() },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Text(
                        text = "See Full Transactions",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (transactions.isEmpty()) {
            item(key = "recent_txns_empty") {
                Text(
                    text = "No transactions recorded for this card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(transactions.take(10), key = { it.id }) { txn ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OrbitCompactListRow(
                        title = txn.effectiveMerchant,
                        subtitle = txn.effectiveDate,
                        amount = MoneyUtils.formatCents(txn.effectiveAmountCents, signed = true),
                        amountColor = if (txn.effectiveAmountCents > 0L) colors.income else MaterialTheme.colorScheme.onSurface,
                        showTopDivider = false,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
            if (transactions.size > 5) {
                item(key = "see_full_txns_footer") {
                    OutlinedButton(
                        onClick = { haptics.tick(); onSeeFullTransactions() },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "See Full Transactions",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
