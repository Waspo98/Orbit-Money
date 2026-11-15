package app.overbay.orbit.ui.calculators.retirement.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.calculators.retirement.RetirementAccountResolver
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.calculators.retirement.cards.retirementAccountColorMap
import app.overbay.orbit.ui.calculators.retirement.cards.retirementAccountPalette
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitModalScaffold
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max

/**
 * Modal checklist allowing the user to select exactly which accounts count toward
 * retirement projection balances, HSA bridge splits, and history charts.
 */
@Composable
fun IncludedAccountsSheet(
    uiState: RetirementUiState,
    onApply: (List<Long>?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val householdMetaById = remember(uiState.householdMembers, uiState.accountById) {
        val map = mutableMapOf<Long, Pair<String, String>>()
        uiState.householdMembers.forEach { member ->
            member.retirementAccounts.forEach { link ->
                val acct = uiState.accountById[link.accountId]
                val kind = RetirementAccountResolver.classifyPlanKind(link.accountKind.ifBlank { acct?.name ?: "" })
                map[link.accountId] = member.name to kind
            }
        }
        map
    }

    val candidateAccounts = remember(
        uiState.allAccounts,
        uiState.includedAccountIds,
        uiState.defaultIncludedAccountIds,
        householdMetaById
    ) {
        uiState.allAccounts.filter { acct ->
            !acct.isArchived && (
                acct.type.isAsset ||
                    acct.id in uiState.includedAccountIds ||
                    acct.id in uiState.defaultIncludedAccountIds ||
                    acct.id in householdMetaById
                )
        }
    }

    val groups = remember(candidateAccounts, householdMetaById) {
        val investments = mutableListOf<Account>()
        val cashSavings = mutableListOf<Account>()
        val otherAssets = mutableListOf<Account>()
        candidateAccounts.forEach { acct ->
            when {
                acct.type == AccountType.INVESTMENT ||
                    acct.id in householdMetaById ||
                    isRetirementKeyword(acct.name) -> investments.add(acct)
                acct.type == AccountType.SAVINGS ||
                    acct.type == AccountType.CHECKING ||
                    acct.type == AccountType.CASH -> cashSavings.add(acct)
                else -> otherAssets.add(acct)
            }
        }
        buildList {
            if (investments.isNotEmpty()) add("Investments & Retirement" to investments)
            if (cashSavings.isNotEmpty()) add("Savings & Cash" to cashSavings)
            if (otherAssets.isNotEmpty()) add("Other Assets" to otherAssets)
        }
    }

    var selectedIds by remember(uiState.showIncludedAccountsSheet, uiState.includedAccountIds) {
        mutableStateOf(uiState.includedAccountIds)
    }
    var preferAutoMode by remember(uiState.showIncludedAccountsSheet, uiState.hasCustomAccountSelection) {
        mutableStateOf(!uiState.hasCustomAccountSelection)
    }

    val draftTotalCents = remember(selectedIds, candidateAccounts) {
        candidateAccounts.filter { it.id in selectedIds }.sumOf { max(0L, it.currentBalanceCents) }
    }
    val palette = retirementAccountPalette()
    val colorMap = remember(candidateAccounts, palette) {
        retirementAccountColorMap(candidateAccounts.map { it.id }, palette)
    }

    OrbitModalSheet(onDismissRequest = onDismiss, modifier = modifier) {
        OrbitModalScaffold(
            title = "Included Accounts",
            subtitle = "Choose which accounts count toward retirement projections",
            confirmText = "Apply",
            dismissText = "Cancel",
            onDismiss = onDismiss,
            onConfirm = {
                onApply(if (preferAutoMode) null else selectedIds.sorted())
            },
            bodySpacing = 14.dp
        ) {
            // Live Selection Summary Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (preferAutoMode) "Auto Default (${selectedIds.size} of ${candidateAccounts.size})" else "Custom Selection (${selectedIds.size} of ${candidateAccounts.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = MoneyUtils.formatCents(draftTotalCents, showCents = false),
                            style = TabularNumStyle.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!preferAutoMode) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                selectedIds = uiState.defaultIncludedAccountIds
                                preferAutoMode = true
                            },
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Reset Auto", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        OrbitBadge(
                            text = "Auto Linked",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                EmptyStateCard(
                    title = "No Eligible Asset Accounts",
                    description = "Add an Investment or Savings account to include balances in retirement projections."
                )
            } else {
                groups.forEach { (groupTitle, accts) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = groupTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        accts.forEachIndexed { idx, acct ->
                            val isChecked = acct.id in selectedIds
                            val meta = householdMetaById[acct.id]
                            val kindLabel = meta?.second ?: RetirementAccountResolver.classifyPlanKind(acct.name).let {
                                if (it == "Other" && acct.type != AccountType.OTHER) acct.type.displayName else it
                            }
                            val accentColor = colorMap[acct.id] ?: palette[idx % palette.size]
                            AccountToggleRow(
                                account = acct,
                                ownerName = meta?.first,
                                planKind = kindLabel,
                                isChecked = isChecked,
                                accentColor = accentColor,
                                onToggle = {
                                    haptics.tick()
                                    preferAutoMode = false
                                    selectedIds = if (isChecked) selectedIds - acct.id else selectedIds + acct.id
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountToggleRow(
    account: Account,
    ownerName: String?,
    planKind: String,
    isChecked: Boolean,
    accentColor: Color,
    onToggle: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else OrbitTheme.colors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(30.dp)
                    .background(
                        color = if (isChecked) accentColor else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(999.dp)
                    )
            )
            Checkbox(
                checked = isChecked,
                onCheckedChange = null,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = listOfNotNull(ownerName, account.institution, planKind)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" • ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = MoneyUtils.formatCents(max(0L, account.currentBalanceCents), showCents = false),
                style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun isRetirementKeyword(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("401") || n.contains("403") || n.contains("457") ||
        n.contains("ira") || n.contains("roth") || n.contains("hsa") ||
        n.contains("retire") || n.contains("pension") || n.contains("tsp")
}
