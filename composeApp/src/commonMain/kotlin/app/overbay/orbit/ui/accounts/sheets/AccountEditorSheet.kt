package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
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
    var accountNumberLast4 by remember(account) { mutableStateOf(account.accountNumberLast4 ?: "") }
    var balanceCents by remember(account) { mutableStateOf(account.currentBalanceCents) }
    var estimatedValueCents by remember(account) { mutableStateOf(account.estimatedValueCents ?: 0L) }
    var mhaEligible by remember(account) { mutableStateOf(account.mhaDefaultEligible) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (account.id == 0L) "Add New Account" else "Edit Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (account.id != 0L && onDelete != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            haptics.reject()
                            onDelete(account.id)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Account",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Account Name
        OrbitTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Account Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Institution Name
        OrbitTextField(
            value = institution,
            onValueChange = { institution = it },
            label = { Text("Financial Institution (e.g. Chase, Vanguard)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 2-Column Bento Row: Account Type + Last 4 Digits
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
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
                onValueChange = { if (it.length <= 4) accountNumberLast4 = it.filter { ch -> ch.isDigit() } },
                label = { Text("Last 4 Digits") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(0.42f)
            )
        }

        // Current Balance
        CurrencyTextField(
            cents = balanceCents,
            onCentsChange = { balanceCents = it },
            label = "Current Balance",
            allowNegative = true,
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

        Spacer(modifier = Modifier.height(4.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Save Account", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
