package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun AccountEditorSheet(
    account: Account,
    onSave: (Account) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit,
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
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorContent(
    account: Account,
    onSave: (Account) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    var name by remember(account) { mutableStateOf(account.name) }
    var institution by remember(account) { mutableStateOf(account.institution ?: "") }
    var selectedType by remember(account) { mutableStateOf(account.type) }
    var typeDropdownExpanded by remember(account) { mutableStateOf(false) }
    var accountNumberLast4 by remember(account) { mutableStateOf(account.accountNumberLast4 ?: "") }
    var balanceCents by remember(account) { mutableStateOf(account.currentBalanceCents) }
    var estimatedValueCents by remember(account) { mutableStateOf(account.estimatedValueCents ?: 0L) }
    var mhaEligible by remember(account) { mutableStateOf(account.mhaDefaultEligible) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
            Text(
                text = if (account.id == 0L) "Add New Account" else "Edit Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Account Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Account Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Institution Name
            OutlinedTextField(
                value = institution,
                onValueChange = { institution = it },
                label = { Text("Financial Institution (e.g. Chase, Vanguard)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Account Type Selector
            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    AccountType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                selectedType = type
                                typeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Current Balance
            CurrencyTextField(
                cents = balanceCents,
                onCentsChange = { balanceCents = it },
                label = "Current Balance",
                allowNegative = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Last 4 Digits
            OutlinedTextField(
                value = accountNumberLast4,
                onValueChange = { if (it.length <= 4) accountNumberLast4 = it },
                label = { Text("Last 4 Digits (Optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Estimated Property Value (for mortgage or real estate)
            if (selectedType == AccountType.MORTGAGE || selectedType == AccountType.OTHER) {
                CurrencyTextField(
                    cents = estimatedValueCents,
                    onCentsChange = { estimatedValueCents = it },
                    label = "Estimated Property / Asset Value",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // MHA Default Eligible Switch
            OrbitSwitchRow(
                title = "MHA Expense Eligible",
                subtitle = "Treat transactions from this account as household-eligible by default",
                checked = mhaEligible,
                onCheckedChange = { mhaEligible = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        haptics.confirm()
                        val estCents = if (estimatedValueCents > 0L) estimatedValueCents else null

                        val updated = account.copy(
                            name = name.trim(),
                            institution = institution.trim().ifBlank { null },
                            type = selectedType,
                            currentBalanceCents = balanceCents,
                            estimatedValueCents = estCents,
                            accountNumberLast4 = accountNumberLast4.trim().ifBlank { null },
                            mhaDefaultEligible = mhaEligible
                        )
                        onSave(updated)
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Account")
                }
            }

            // Delete Account Option
            if (account.id != 0L && onDelete != null) {
                TextButton(
                    onClick = {
                        haptics.reject()
                        onDelete(account.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Delete Account",
                        color = OrbitColorTokens.Red,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
}
