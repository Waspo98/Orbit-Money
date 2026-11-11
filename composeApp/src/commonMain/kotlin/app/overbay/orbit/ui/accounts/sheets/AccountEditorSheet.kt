package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalFooter
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun AccountEditorSheet(
    account: Account,
    onSave: (Account) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onUnlinkSimpleFin: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    showMhaOption: Boolean = false,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        AccountEditorContent(
            account = account,
            onSave = onSave,
            onDelete = onDelete,
            onUnlinkSimpleFin = onUnlinkSimpleFin,
            onDismiss = onDismiss,
            showMhaOption = showMhaOption
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorContent(
    account: Account,
    onSave: (Account) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onUnlinkSimpleFin: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    showMhaOption: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var showUnlinkConfirm by remember { mutableStateOf(false) }

    var name by remember(account) { mutableStateOf(account.name) }
    var institution by remember(account) { mutableStateOf(account.institution ?: "") }
    var selectedType by remember(account) { mutableStateOf(account.type) }
    var accountNumberLast4 by remember(account) { mutableStateOf(account.accountNumberLast4 ?: "") }
    var balanceCents by remember(account) { mutableStateOf(account.currentBalanceCents) }
    var estimatedValueCents by remember(account) { mutableStateOf(account.estimatedValueCents ?: 0L) }
    var mhaEligible by remember(account) { mutableStateOf(account.mhaDefaultEligible) }
    val billingState = remember(account) { AccountBillingFieldsState(account.creditCardProfile, account.id) }

    Column(modifier = modifier.fillMaxWidth()) {
        OrbitSheetHeader(
            title = if (account.id == 0L) "Add New Account" else "Edit Account",
            onDismiss = onDismiss,
            trailingActions = {
                if (account.id != 0L && onDelete != null) {
                    OrbitActionChip(
                        label = "Delete",
                        leadingIcon = Icons.Default.Delete,
                        isDestructive = true,
                        onClick = {
                            haptics.reject()
                            onDelete(account.id)
                        }
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!account.simplefinAccountId.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
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
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = OrbitColorTokens.Green,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Linked to SimpleFIN",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Synced via SimpleFIN Bridge",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        if (onUnlinkSimpleFin != null) {
                            OrbitActionChip(
                                label = "Unlink",
                                onClick = { showUnlinkConfirm = true }
                            )
                        }
                    }
                }
            }

            OrbitSectionCard(
                title = "Account Details",
                subtitle = "Institution, account category & current balance",
                showHeaderDivider = false
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrbitTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Account Name") },
                        placeholder = { Text("e.g. Total Checking, Joint Savings") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OrbitTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = { Text("Financial Institution") },
                        placeholder = { Text("e.g. Chase, Vanguard, Fidelity") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OrbitDropdownField(
                            label = "Account Type",
                            selectedText = selectedType.displayName,
                            options = AccountType.entries,
                            optionLabel = { it.displayName },
                            onOptionSelected = { selectedType = it },
                            modifier = Modifier.weight(0.58f)
                        )

                        OrbitTextField(
                            value = accountNumberLast4,
                            onValueChange = { accountNumberLast4 = it.filter(Char::isDigit).take(4) },
                            label = { Text("Last 4 Digits") },
                            placeholder = { Text("1234") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.42f)
                        )
                    }

                    CurrencyTextField(
                        cents = balanceCents,
                        onCentsChange = { balanceCents = it },
                        label = "Current Balance",
                        allowNegative = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedType == AccountType.MORTGAGE || selectedType == AccountType.OTHER) {
                        CurrencyTextField(
                            cents = estimatedValueCents,
                            onCentsChange = { estimatedValueCents = it },
                            label = "Estimated Property / Asset Value",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (selectedType == AccountType.CREDIT) {
                AccountEditorBillingSection(state = billingState)
            }

            if (showMhaOption) {
                OrbitSwitchRow(
                    title = "MHA Expense Eligible",
                    subtitle = "Treat transactions from this account as household-eligible by default",
                    checked = mhaEligible,
                    onCheckedChange = { mhaEligible = it }
                )
            }
        }

        OrbitModalFooter(
            confirmText = if (account.id == 0L) "Add Account" else "Save Account",
            confirmEnabled = name.isNotBlank(),
            onDismiss = onDismiss,
            onConfirm = {
                val estCents = if (estimatedValueCents > 0L) estimatedValueCents else null
                val updatedProfile = if (selectedType == AccountType.CREDIT) {
                    billingState.toUpdatedProfile(
                        existing = account.creditCardProfile,
                        accountName = name,
                        institution = institution
                    )
                } else account.creditCardProfile

                val updated = account.copy(
                    name = name.trim(),
                    institution = institution.trim().ifBlank { null },
                    type = selectedType,
                    currentBalanceCents = balanceCents,
                    estimatedValueCents = estCents,
                    accountNumberLast4 = accountNumberLast4.trim().ifBlank { null },
                    mhaDefaultEligible = mhaEligible,
                    creditCardProfile = updatedProfile
                )
                onSave(updated)
            }
        )
    }

    if (showUnlinkConfirm && onUnlinkSimpleFin != null) {
        OrbitConfirmationDialog(
            title = "Unlink SimpleFIN?",
            message = "Unlinking will turn this account into a manual account. Existing transactions and balances will not be lost.",
            confirmText = "Unlink",
            onConfirm = {
                showUnlinkConfirm = false
                onUnlinkSimpleFin()
            },
            onDismiss = { showUnlinkConfirm = false }
        )
    }
}
