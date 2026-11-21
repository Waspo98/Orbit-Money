package app.overbay.orbit.ui.mha.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitExpandableChevron
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchGroupCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.data.model.TaxTrackingMode
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.LocalTaxTrackingMode

@Composable
fun MhaAutoDefaultsSection(
    accounts: List<Account>,
    categories: List<Category>,
    onToggleAccountDefault: (Long, Boolean) -> Unit,
    onToggleCategoryDefault: (Long, Boolean) -> Unit,
    onToggleCategoryIgnoreDefault: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isGeneral = LocalTaxTrackingMode.current == TaxTrackingMode.GENERAL
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AutoIncludeAccountsCard(
            accounts = accounts,
            isGeneral = isGeneral,
            onToggle = onToggleAccountDefault
        )

        CategoryDefaultsCard(
            title = "Auto-Include Categories",
            subtitle = if (isGeneral) "Categories that count as tax-deductible by default" else "Categories that qualify under Section 107",
            sectionLabel = if (isGeneral) "Deductible Categories" else "Housing & Utility Categories",
            emptyTitle = "No Categories Selected",
            emptyDesc = if (isGeneral) {
                "Mark categories like Charity, Medical/HSA, or Business Expenses to auto-include transactions."
            } else {
                "Mark categories like Mortgage, Rent, Utilities, or Maintenance to auto-include transactions."
            },
            isIgnoredMode = false,
            isGeneral = isGeneral,
            categories = categories,
            onToggle = onToggleCategoryDefault
        )

        CategoryDefaultsCard(
            title = "Auto-Ignore Categories",
            subtitle = if (isGeneral) "Explicitly exclude non-deductible expenses even on designated accounts" else "Explicitly exclude non-housing expenditures even on designated accounts",
            sectionLabel = if (isGeneral) "Excluded Categories" else "Exempt / Non-Housing Categories",
            emptyTitle = "No Ignored Categories",
            emptyDesc = if (isGeneral) {
                "Categories tagged here will never count as tax-deductible expenses."
            } else {
                "Categories tagged here will never count as MHA expenses."
            },
            isIgnoredMode = true,
            isGeneral = isGeneral,
            categories = categories,
            onToggle = onToggleCategoryIgnoreDefault
        )
    }
}

@Composable
private fun AutoIncludeAccountsCard(
    accounts: List<Account>,
    isGeneral: Boolean,
    onToggle: (Long, Boolean) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var isExpanded by remember { mutableStateOf(true) }
    var showSelectedOnly by remember { mutableStateOf(false) }

    val eligibleAccounts = accounts.filter { it.mhaDefaultEligible }
    val displayAccounts = if (showSelectedOnly) eligibleAccounts else accounts

    OrbitSectionCard(
        title = "Auto-Include Accounts",
        subtitle = if (isGeneral) {
            "Expenses from these accounts default to tax-deductible (${eligibleAccounts.size} active)"
        } else {
            "Expenses from these accounts default to MHA eligible (${eligibleAccounts.size} active)"
        },
        action = {
            IconButton(onClick = {
                haptics.tick()
                isExpanded = !isExpanded
            }) {
                OrbitExpandableChevron(
                    expanded = isExpanded,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
        }
    ) {
        OrbitAccordionVisibility(visible = isExpanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isGeneral) "Designated Deductible Accounts" else "Designated Housing Accounts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = showSelectedOnly,
                        onClick = {
                            haptics.tick()
                            showSelectedOnly = !showSelectedOnly
                        },
                        label = { Text(if (showSelectedOnly) "Selected Only" else "Show All") },
                        leadingIcon = if (showSelectedOnly) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (displayAccounts.isEmpty()) {
                    EmptyStateCard(
                        title = "No Accounts Available",
                        description = "Add a checking, savings, or credit card account first to configure automatic housing allowance defaults."
                    )
                } else {
                    OrbitSwitchGroupCard {
                        displayAccounts.forEachIndexed { index, account ->
                            OrbitSwitchRow(
                                title = account.name,
                                subtitle = "${account.type.displayName} • ${account.institution ?: "Manual"} • Balance: ${MoneyUtils.formatCents(account.currentBalanceCents)}",
                                checked = account.mhaDefaultEligible,
                                onCheckedChange = { checked -> onToggle(account.id, checked) },
                                showContainer = false
                            )
                            if (index < displayAccounts.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDefaultsCard(
    title: String,
    subtitle: String,
    sectionLabel: String,
    emptyTitle: String,
    emptyDesc: String,
    isIgnoredMode: Boolean,
    isGeneral: Boolean,
    categories: List<Category>,
    onToggle: (Long, Boolean) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var isExpanded by remember { mutableStateOf(true) }
    var showSelectedOnly by remember { mutableStateOf(false) }

    val nonTransfer = categories.filter { !it.isTransfer }
    val filtered = if (isIgnoredMode) nonTransfer.filter { it.mhaDefaultIgnored } else nonTransfer.filter { it.mhaDefaultEligible }
    val display = if (showSelectedOnly) filtered else nonTransfer

    OrbitSectionCard(
        title = title,
        subtitle = "$subtitle (${filtered.size} active)",
        action = {
            IconButton(onClick = {
                haptics.tick()
                isExpanded = !isExpanded
            }) {
                OrbitExpandableChevron(
                    expanded = isExpanded,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
        }
    ) {
        OrbitAccordionVisibility(visible = isExpanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sectionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = showSelectedOnly,
                        onClick = {
                            haptics.tick()
                            showSelectedOnly = !showSelectedOnly
                        },
                        label = { Text(if (showSelectedOnly) "Selected Only" else "Show All") },
                        leadingIcon = if (showSelectedOnly) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (display.isEmpty()) {
                    EmptyStateCard(
                        title = emptyTitle,
                        description = emptyDesc
                    )
                } else {
                    OrbitSwitchGroupCard {
                        display.forEachIndexed { index, cat ->
                            val isChecked = if (isIgnoredMode) cat.mhaDefaultIgnored else cat.mhaDefaultEligible
                            val subText = if (isIgnoredMode) {
                                if (isChecked) {
                                    if (isGeneral) "Blocked from tax deductions" else "Blocked from MHA eligibility"
                                } else "Normal eligibility evaluation"
                            } else {
                                if (isChecked) {
                                    if (isGeneral) "Auto-included in Tax Deductions" else "Auto-included in MHA"
                                } else "Standard category"
                            }

                            OrbitSwitchRow(
                                title = cat.labelWithEmoji,
                                subtitle = subText,
                                checked = isChecked,
                                onCheckedChange = { checked -> onToggle(cat.id, checked) },
                                showContainer = false
                            )
                            if (index < display.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
