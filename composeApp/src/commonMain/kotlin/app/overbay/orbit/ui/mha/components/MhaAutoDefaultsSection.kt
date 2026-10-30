package app.overbay.orbit.ui.mha.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun MhaAutoDefaultsSection(
    accounts: List<Account>,
    categories: List<Category>,
    onToggleAccountDefault: (Long, Boolean) -> Unit,
    onToggleCategoryDefault: (Long, Boolean) -> Unit,
    onToggleCategoryIgnoreDefault: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AutoIncludeAccountsCard(
            accounts = accounts,
            onToggle = onToggleAccountDefault
        )

        CategoryDefaultsCard(
            title = "Auto-Include Categories",
            subtitle = "Categories that qualify under Section 107",
            sectionLabel = "Housing & Utility Categories",
            emptyTitle = "No Categories Selected",
            emptyDesc = "Mark categories like Mortgage, Rent, Utilities, or Maintenance to auto-include transactions.",
            isIgnoredMode = false,
            categories = categories,
            onToggle = onToggleCategoryDefault
        )

        CategoryDefaultsCard(
            title = "Auto-Ignore Categories",
            subtitle = "Explicitly exclude non-housing expenditures even on designated accounts",
            sectionLabel = "Exempt / Non-Housing Categories",
            emptyTitle = "No Ignored Categories",
            emptyDesc = "Categories tagged here will never count as MHA expenses.",
            isIgnoredMode = true,
            categories = categories,
            onToggle = onToggleCategoryIgnoreDefault
        )
    }
}

@Composable
private fun AutoIncludeAccountsCard(
    accounts: List<Account>,
    onToggle: (Long, Boolean) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var isExpanded by remember { mutableStateOf(true) }
    var showSelectedOnly by remember { mutableStateOf(false) }

    val eligibleAccounts = accounts.filter { it.mhaDefaultEligible }
    val displayAccounts = if (showSelectedOnly) eligibleAccounts else accounts

    OrbitSectionCard(
        title = "Auto-Include Accounts",
        subtitle = "Expenses from these accounts default to MHA eligible (${eligibleAccounts.size} active)",
        action = {
            IconButton(onClick = {
                haptics.tick()
                isExpanded = !isExpanded
            }) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
        }
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = androidx.compose.animation.expandVertically(spring(stiffness = Spring.StiffnessLow)),
            exit = androidx.compose.animation.shrinkVertically(spring(stiffness = Spring.StiffnessLow))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Designated Housing Accounts",
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
                        title = "No Accounts Selected",
                        description = "Enable any account below to automatically mark all its outflows as eligible housing expenses."
                    )
                } else {
                    displayAccounts.forEachIndexed { index, account ->
                        DefaultToggleRow(
                            title = account.name,
                            subtitle = "${account.type.displayName} • ${account.institution ?: "Manual"}",
                            trailingInfo = MoneyUtils.formatCents(account.currentBalanceCents),
                            isChecked = account.mhaDefaultEligible,
                            onCheckedChange = { checked ->
                                haptics.tick()
                                onToggle(account.id, checked)
                            }
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

@Composable
private fun CategoryDefaultsCard(
    title: String,
    subtitle: String,
    sectionLabel: String,
    emptyTitle: String,
    emptyDesc: String,
    isIgnoredMode: Boolean,
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
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
        }
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = androidx.compose.animation.expandVertically(spring(stiffness = Spring.StiffnessLow)),
            exit = androidx.compose.animation.shrinkVertically(spring(stiffness = Spring.StiffnessLow))
        ) {
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
                    display.forEachIndexed { index, cat ->
                        val isChecked = if (isIgnoredMode) cat.mhaDefaultIgnored else cat.mhaDefaultEligible
                        val subText = if (isIgnoredMode) {
                            if (isChecked) "Blocked from MHA eligibility" else "Normal eligibility evaluation"
                        } else {
                            if (isChecked) "Auto-included in MHA" else "Standard category"
                        }

                        DefaultToggleRow(
                            title = cat.labelWithEmoji,
                            subtitle = subText,
                            trailingInfo = null,
                            isChecked = isChecked,
                            onCheckedChange = { checked ->
                                haptics.tick()
                                onToggle(cat.id, checked)
                            }
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

@Composable
private fun DefaultToggleRow(
    title: String,
    subtitle: String,
    trailingInfo: String?,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (trailingInfo != null) {
                    Text(
                        text = "Balance: $trailingInfo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
