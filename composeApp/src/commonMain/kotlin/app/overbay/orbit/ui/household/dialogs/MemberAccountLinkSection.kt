package app.overbay.orbit.ui.household.dialogs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.HouseholdRetirementAccount
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.util.LocalOrbitHaptics

val RETIREMENT_ACCOUNT_KINDS = listOf(
    "401k" to "401(k)",
    "roth_401k" to "Roth 401(k)",
    "403b" to "403(b)",
    "457b" to "457(b)",
    "ira" to "Traditional IRA",
    "roth_ira" to "Roth IRA",
    "hsa" to "HSA",
    "brokerage" to "Taxable Brokerage",
    "pension" to "Pension",
    "other" to "Other"
)

@Composable
fun MemberAccountLinkSection(
    linkedAccounts: List<HouseholdRetirementAccount>,
    availableAccounts: List<Account>,
    onAddLink: (accountId: Long, kind: String) -> Unit,
    onUpdateLinkKind: (accountId: Long, newKind: String) -> Unit,
    onRemoveLink: (accountId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val eligibleAccounts = availableAccounts.filter {
        it.type == AccountType.INVESTMENT || it.type == AccountType.SAVINGS
    }
    val linkedAccountIds = linkedAccounts.map { it.accountId }.toSet()
    val unlinkedEligible = eligibleAccounts.filter { it.id !in linkedAccountIds }
    val accountMap = availableAccounts.associateBy { it.id }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Linked Retirement & Savings Accounts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Link investment and HSA accounts to attribute portfolio balances and savings rates to this member.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        if (linkedAccounts.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No linked accounts yet. Link an Investment or HSA account below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            linkedAccounts.forEach { link ->
                val account = accountMap[link.accountId]
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account?.name ?: "Account #${link.accountId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Current: ${MoneyUtils.formatCents(account?.currentBalanceCents ?: 0L)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.reject()
                                    onRemoveLink(link.accountId)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Unlink Account",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        val currentKindLabel = RETIREMENT_ACCOUNT_KINDS.find { it.first == link.accountKind }?.second ?: link.accountKind.uppercase()
                        OrbitDropdownField(
                            label = "Account Tax / Plan Kind",
                            selectedText = currentKindLabel,
                            options = RETIREMENT_ACCOUNT_KINDS,
                            optionLabel = { it.second },
                            onOptionSelected = { selected ->
                                haptics.tick()
                                onUpdateLinkKind(link.accountId, selected.first)
                            }
                        )
                    }
                }
            }
        }

        // Add Account Dropdown
        if (unlinkedEligible.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            OrbitDropdownField(
                label = "+ Link Investment / HSA Account",
                selectedText = "Select account to link...",
                options = unlinkedEligible,
                optionLabel = { "${it.name} (${it.type.displayName} - ${MoneyUtils.formatCents(it.currentBalanceCents)})" },
                onOptionSelected = { acct ->
                    haptics.tick()
                    val defaultKind = if (acct.name.contains("HSA", ignoreCase = true)) "hsa"
                    else if (acct.name.contains("Roth", ignoreCase = true)) "roth_ira"
                    else "401k"
                    onAddLink(acct.id, defaultKind)
                }
            )
        }
    }
}
