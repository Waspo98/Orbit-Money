package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.LocalOrbitSheetDismiss
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.transactions.components.TransactionMerchantMark
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Tonal Balance Hero Card for standard & property Account Detail views.
 * Displays kicker label, type & sync provenance badges, animated odometer balance,
 * manual `Log Balance` shortcut, and Mortgage Home Equity readout when applicable.
 */
@Composable
fun AccountBalanceHeroCard(
    account: Account,
    modifier: Modifier = Modifier,
    onAddBalanceRecord: (() -> Unit)? = null
) {
    val colors = OrbitTheme.colors
    val isLiability = account.type.isLiability
    val headlineCents = if (account.isProperty) {
        account.estimatedValueCents ?: account.currentBalanceCents
    } else {
        account.currentBalanceCents
    }
    val balanceColor = when {
        account.isProperty -> colors.income
        isLiability -> if (account.currentBalanceCents != 0L) colors.expense else MaterialTheme.colorScheme.onSurface
        headlineCents >= 0L -> colors.income
        else -> colors.expense
    }
    val isSyncedHealthy = !account.simplefinAccountId.isNullOrBlank() && account.simplefinMissingSince.isNullOrBlank()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (account.isProperty) "ESTIMATED VALUE" else "CURRENT BALANCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrbitBadge(
                        text = if (account.isProperty) "Property" else account.type.displayName,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (isSyncedHealthy) {
                        OrbitBadge(
                            text = "● Synced",
                            containerColor = colors.incomeContainer,
                            contentColor = colors.onIncomeContainer
                        )
                    } else if (account.isManual && !account.isProperty) {
                        OrbitBadge(
                            text = "Manual",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (account.isArchived) {
                        OrbitBadge(
                            text = "Archived",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RollingTickerCents(
                    cents = headlineCents,
                    style = TabularNumStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    color = balanceColor
                )

                if (onAddBalanceRecord != null) {
                    OrbitActionChip(
                        label = "Log Balance",
                        leadingIcon = Icons.Default.Add,
                        onClick = onAddBalanceRecord
                    )
                }
            }

            if (account.type == AccountType.MORTGAGE && account.homeEquityCents != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OrbitMiniStatRow(
                    label = "Home Equity",
                    detail = "Est. Property ${MoneyUtils.formatCents(account.estimatedValueCents ?: 0L)}",
                    value = MoneyUtils.formatCents(account.homeEquityCents!!),
                    valueColor = if (account.homeEquityCents!! >= 0L) colors.income else colors.expense,
                    showTopDivider = true
                )
            }
        }
    }
}

/**
 * Actionable warning banner shown only when an account is missing from SimpleFIN sync
 * or flagged as a payoff/closure candidate.
 */
@Composable
fun AccountClosureAlertBanner(
    account: Account,
    hasClosureAlert: Boolean,
    onReviewClosure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val missingFromSync = !account.simplefinMissingSince.isNullOrBlank()
    if (!hasClosureAlert && !missingFromSync) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (missingFromSync) "No Longer Reported by SimpleFIN" else "Possible Payoff Detected",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Did you recently pay off or close this account?",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OrbitActionChip(
                label = "Review",
                onClick = onReviewClosure
            )
        }
    }
}

/**
 * Grouped Recent Activity section card shared by standard Account Detail and Credit Card Detail views.
 * Shows up to 15 recent transactions with merchant/category marks, friendly dates, hairline dividers,
 * and direct navigation to the filtered Transactions feed.
 */
@Composable
fun AccountRecentTransactionsSection(
    transactions: List<Transaction>,
    categoryMap: Map<Long, Category>,
    onSeeTransactions: () -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int = 15
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val sheetDismiss = LocalOrbitSheetDismiss.current
    val navigateToAll: () -> Unit = {
        sheetDismiss?.invoke(onSeeTransactions) ?: onSeeTransactions()
    }

    OrbitSectionCard(
        title = "Recent Activity",
        subtitle = if (transactions.isNotEmpty()) "${transactions.size} total transactions" else null,
        actionText = if (transactions.isNotEmpty()) "See All →" else null,
        onActionClick = if (transactions.isNotEmpty()) navigateToAll else null,
        showHeaderDivider = transactions.isNotEmpty(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        modifier = modifier
    ) {
        if (transactions.isEmpty()) {
            Text(
                text = "No transactions recorded for this account yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            val preview = remember(transactions, maxItems) { transactions.take(maxItems) }
            Column(modifier = Modifier.fillMaxWidth()) {
                preview.forEachIndexed { index, txn ->
                    val cat = txn.effectiveCategoryId?.let { categoryMap[it] }
                    val catColor = cat?.color.toComposeColor(MaterialTheme.colorScheme.primary)
                    val friendlyDate = remember(txn.effectiveDate) {
                        LocalDateUtils.parseIsoDate(txn.effectiveDate)
                            ?.let { LocalDateUtils.formatRelativeOrDisplayDate(it) }
                            ?: txn.effectiveDate
                    }
                    val subtitleText = when {
                        txn.effectiveIsTransfer -> "$friendlyDate • Transfer"
                        cat != null -> "$friendlyDate • ${cat.name}"
                        else -> friendlyDate
                    }
                    val amountColor = when {
                        txn.effectiveIsTransfer -> MaterialTheme.colorScheme.secondary
                        txn.effectiveAmountCents > 0L -> colors.income
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    OrbitCompactListRow(
                        title = txn.effectiveMerchant,
                        subtitle = subtitleText,
                        amount = MoneyUtils.formatCents(txn.effectiveAmountCents, signed = true),
                        amountColor = amountColor,
                        showTopDivider = index > 0,
                        leadingContent = {
                            TransactionMerchantMark(
                                transaction = txn,
                                categoryIcon = cat?.displayEmoji,
                                categoryColor = catColor,
                                size = 34.dp,
                                cornerRadius = 9.dp
                            )
                        }
                    )
                }

                if (transactions.size > maxItems) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            navigateToAll()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "See All ${transactions.size} Transactions",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
