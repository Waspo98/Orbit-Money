package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.accounts.components.AccountClosureAlertBanner
import app.overbay.orbit.ui.accounts.components.AccountInspectorHeader
import app.overbay.orbit.ui.accounts.components.AccountRecentTransactionsSection

/**
 * Unified Credit Card detail modal/pane shared by Credit Card Tracker and Accounts.
 * Features sticky header with "Edit Card" + optional `•••` overflow menu, billing/perks/strategy
 * overview, and grouped Recent Activity card.
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
    modifier: Modifier = Modifier,
    categoryMap: Map<Long, Category> = emptyMap(),
    hasClosureAlert: Boolean = false,
    onReviewClosure: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onAddBalanceRecord: (() -> Unit)? = null,
    onSnapshotReminder: (() -> Unit)? = null,
    onMerge: (() -> Unit)? = null,
    onUnlinkSimpleFin: (() -> Unit)? = null,
    onToggleArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val profile = account.creditCardProfile ?: CreditCardProfile(accountId = account.id)
    val subProgress = remember(account, transactions) {
        CreditCardStrategyEngine.computeSubProgress(account, transactions)
    }
    val cardTitle = profile.cardName?.takeIf { it.isNotBlank() } ?: account.name
    val subtitle = remember(account, profile) {
        listOfNotNull(
            account.institution?.takeIf { it.isNotBlank() }
                ?: profile.issuerName?.takeIf { it.isNotBlank() },
            account.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { "•••• $it" }
        ).joinToString(" • ").takeIf { it.isNotBlank() }
    }
    val showClosureBanner = (hasClosureAlert || !account.simplefinMissingSince.isNullOrBlank()) && onReviewClosure != null

    Column(modifier = modifier.fillMaxWidth()) {
        AccountInspectorHeader(
            accountName = cardTitle,
            title = cardTitle,
            subtitle = subtitle,
            editLabel = "Edit Card",
            isArchived = account.isArchived,
            onEdit = onEditCard,
            onClose = onClosePane,
            onHistory = onHistory,
            onAddBalanceRecord = onAddBalanceRecord,
            onSnapshotReminder = onSnapshotReminder,
            onMerge = onMerge,
            onUnlinkSimpleFin = onUnlinkSimpleFin,
            onToggleArchive = onToggleArchive,
            onDelete = onDelete
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showClosureBanner) {
                item(key = "card_closure_alert") {
                    Box(
                        modifier = Modifier.animateItem(
                            fadeInSpec = OrbitSprings.FloatEffectsDefault,
                            placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                            fadeOutSpec = OrbitSprings.FloatEffectsFast
                        )
                    ) {
                        AccountClosureAlertBanner(
                            account = account,
                            hasClosureAlert = hasClosureAlert,
                            onReviewClosure = onReviewClosure!!
                        )
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

            item(key = "card_recent_txns") {
                AccountRecentTransactionsSection(
                    transactions = transactions,
                    categoryMap = categoryMap,
                    onSeeTransactions = onSeeFullTransactions
                )
            }
        }
    }
}
