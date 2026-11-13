package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import app.overbay.orbit.ui.components.LocalOrbitSheetDismiss
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitHorizontalReveal
import app.overbay.orbit.ui.components.OrbitModalFooter
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

private enum class EditorAccountKind(val label: String, val accountType: AccountType, val isProperty: Boolean = false) {
    CHECKING("Checking", AccountType.CHECKING),
    SAVINGS("Savings", AccountType.SAVINGS),
    CREDIT("Credit Card", AccountType.CREDIT),
    INVESTMENT("Investment", AccountType.INVESTMENT),
    PROPERTY("Real Estate Property", AccountType.OTHER, isProperty = true),
    MORTGAGE("Mortgage", AccountType.MORTGAGE),
    LOAN("Loan", AccountType.LOAN),
    CASH("Cash", AccountType.CASH),
    OTHER("Other Asset", AccountType.OTHER);

    companion object {
        fun fromAccount(account: Account): EditorAccountKind {
            if (account.isProperty) return PROPERTY
            return entries.firstOrNull { !it.isProperty && it.accountType == account.type } ?: OTHER
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorContent(
    account: Account,
    availablePropertyAnchors: List<Account> = emptyList(),
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
    var selectedKind by remember(account) { mutableStateOf(EditorAccountKind.fromAccount(account)) }
    val selectedType = selectedKind.accountType
    var accountNumberLast4 by remember(account) { mutableStateOf(account.accountNumberLast4 ?: "") }
    var balanceCents by remember(account) { mutableStateOf(account.currentBalanceCents) }
    var estimatedValueCents by remember(account) { mutableStateOf(account.estimatedValueCents ?: 0L) }
    var propertyName by remember(account) { mutableStateOf(account.propertyName ?: "") }
    var linkedPropertyId by remember(account) { mutableStateOf(account.linkedPropertyAccountId) }
    var mhaEligible by remember(account) { mutableStateOf(account.mhaDefaultEligible) }
    val billingState = remember(account) { AccountBillingFieldsState(account.creditCardProfile, account.id) }
    val eligibleProperties = remember(availablePropertyAnchors, account.id) {
        availablePropertyAnchors.filter { it.id != 0L && it.id != account.id }
    }

    val isInsideSheet = LocalOrbitSheetDismiss.current != null

    Column(
        modifier = if (isInsideSheet) modifier.fillMaxWidth() else modifier.fillMaxSize()
    ) {
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
                .weight(1f, fill = !isInsideSheet)
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

                    OrbitAccordionVisibility(visible = !selectedKind.isProperty) {
                        OrbitTextField(
                            value = institution,
                            onValueChange = { institution = it },
                            label = { Text("Financial Institution") },
                            placeholder = { Text("e.g. Chase, Vanguard, Fidelity") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OrbitDropdownField(
                            label = "Account Type",
                            selectedText = selectedKind.label,
                            options = EditorAccountKind.entries,
                            optionLabel = { it.label },
                            onOptionSelected = {
                                selectedKind = it
                                if (!it.isProperty && it.accountType !in setOf(AccountType.MORTGAGE, AccountType.LOAN)) {
                                    linkedPropertyId = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        OrbitHorizontalReveal(visible = !selectedKind.isProperty) {
                            Row {
                                Spacer(modifier = Modifier.width(10.dp))
                                OrbitTextField(
                                    value = accountNumberLast4,
                                    onValueChange = { accountNumberLast4 = it.filter(Char::isDigit).take(4) },
                                    label = { Text("Last 4 Digits") },
                                    placeholder = { Text("1234") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.width(128.dp)
                                )
                            }
                        }
                    }

                    OrbitAccordionVisibility(visible = !selectedKind.isProperty) {
                        CurrencyTextField(
                            cents = balanceCents,
                            onCentsChange = { balanceCents = it },
                            label = "Current Balance",
                            allowNegative = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OrbitAccordionVisibility(visible = (selectedType == AccountType.MORTGAGE || selectedType == AccountType.LOAN) && eligibleProperties.isNotEmpty()) {
                        val linkOptions = listOf<Long?>(null) + eligibleProperties.map { it.id }
                        val selectedProp = eligibleProperties.find { it.id == linkedPropertyId }
                        OrbitDropdownField(
                            label = "Linked Property (Optional)",
                            selectedText = selectedProp?.let { it.propertyName?.takeIf(String::isNotBlank) ?: it.name }
                                ?: "None (Standalone)",
                            options = linkOptions,
                            optionLabel = { id ->
                                if (id == null) "None (Standalone)"
                                else eligibleProperties.find { it.id == id }
                                    ?.let { it.propertyName?.takeIf(String::isNotBlank) ?: it.name }
                                    ?: "Property"
                            },
                            onOptionSelected = { linkedPropertyId = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OrbitAccordionVisibility(visible = selectedKind.isProperty || ((selectedType == AccountType.MORTGAGE || selectedType == AccountType.OTHER) && linkedPropertyId == null)) {
                        CurrencyTextField(
                            cents = estimatedValueCents,
                            onCentsChange = { estimatedValueCents = it },
                            label = if (selectedKind.isProperty || selectedType == AccountType.MORTGAGE) "Estimated Property Value" else "Estimated Property / Asset Value",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OrbitAccordionVisibility(visible = selectedType == AccountType.MORTGAGE && linkedPropertyId == null && estimatedValueCents > 0L) {
                        OrbitTextField(
                            value = propertyName,
                            onValueChange = { propertyName = it },
                            label = { Text("Property Display Name (Optional)") },
                            placeholder = { Text(name.ifBlank { "e.g. 2104 Doubletree Lane" }) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            OrbitAccordionVisibility(visible = selectedType == AccountType.CREDIT) {
                AccountEditorBillingSection(state = billingState)
            }

            OrbitAccordionVisibility(visible = showMhaOption && !selectedKind.isProperty) {
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
            confirmEnabled = name.isNotBlank() && (!selectedKind.isProperty || estimatedValueCents > 0L) && (selectedType != AccountType.CREDIT || billingState.isValid),
            errorBannerText = if (selectedType == AccountType.CREDIT) billingState.firstErrorMessage else null,
            onDismiss = onDismiss,
            onConfirm = {
                val resolvedLinkId = if (selectedType in setOf(AccountType.MORTGAGE, AccountType.LOAN) && !selectedKind.isProperty) {
                    linkedPropertyId
                } else null
                val estCents = if (resolvedLinkId == null && estimatedValueCents > 0L) estimatedValueCents else null
                val updatedProfile = if (selectedType == AccountType.CREDIT) {
                    billingState.toUpdatedProfile(
                        existing = account.creditCardProfile,
                        accountName = name,
                        institution = institution
                    )
                } else account.creditCardProfile

                val updated = account.copy(
                    name = name.trim(),
                    institution = if (selectedKind.isProperty) null else institution.trim().ifBlank { null },
                    type = selectedType,
                    currentBalanceCents = if (selectedKind.isProperty) 0L else balanceCents,
                    estimatedValueCents = estCents,
                    isProperty = selectedKind.isProperty,
                    propertyName = if (!selectedKind.isProperty && estCents != null) propertyName.trim().ifBlank { null } else null,
                    linkedPropertyAccountId = resolvedLinkId,
                    accountNumberLast4 = if (selectedKind.isProperty) null else accountNumberLast4.trim().ifBlank { null },
                    mhaDefaultEligible = if (selectedKind.isProperty) false else mhaEligible,
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
