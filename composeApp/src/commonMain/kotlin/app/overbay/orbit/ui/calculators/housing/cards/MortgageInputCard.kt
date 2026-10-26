package app.overbay.orbit.ui.calculators.housing.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.housing.HousingUiState
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun MortgageInputCard(
    uiState: HousingUiState,
    onSelectMortgage: (Long?) -> Unit,
    onSalePriceChange: (Cents?) -> Unit,
    onRemainingMortgageChange: (Cents?) -> Unit,
    onPurchasePriceChange: (Cents) -> Unit,
    onDownPaymentChange: (Cents) -> Unit,
    onInterestRateChange: (Double) -> Unit,
    onLoanTermChange: (Int) -> Unit,
    onPropertyTaxChange: (Cents) -> Unit,
    onInsuranceChange: (Cents) -> Unit,
    onHoaChange: (Cents) -> Unit,
    onUseNetProceeds: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedAccountMenu by remember { mutableStateOf(false) }
    val selectedAccount = uiState.availableMortgageAccounts.find { it.id == uiState.selectedMortgageAccountId }

    OrbitSectionCard(
        title = "Mortgage & Financing Scenario",
        subtitle = "Connect existing mortgage or configure home purchase parameters",
        modifier = modifier
    ) {
        // 1. Account Picker
        Text(
            text = "Current Property / Mortgage",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedAccountMenu = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = selectedAccount?.name ?: "Manual Estimate / No Linked Mortgage",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selectedAccount?.institution != null) {
                        Text(
                            text = selectedAccount.institution,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select mortgage",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = expandedAccountMenu,
                onDismissRequest = { expandedAccountMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Manual Estimate (No Account)") },
                    onClick = {
                        onSelectMortgage(null)
                        expandedAccountMenu = false
                    }
                )
                uiState.availableMortgageAccounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text("${account.name} (${MoneyUtils.formatCents(account.currentBalanceCents)})") },
                        onClick = {
                            onSelectMortgage(account.id)
                            expandedAccountMenu = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Selling Home Proceeds (If enabled / configured)
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Selling Existing Home (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CurrencyTextField(
                        cents = uiState.salePriceCents ?: 0L,
                        onCentsChange = { onSalePriceChange(if (it == 0L) null else it) },
                        label = "Sale Price",
                        modifier = Modifier.weight(1f)
                    )
                    CurrencyTextField(
                        cents = uiState.remainingMortgageCents ?: 0L,
                        onCentsChange = { onRemainingMortgageChange(if (it == 0L) null else it) },
                        label = "Mortgage Balance",
                        modifier = Modifier.weight(1f)
                    )
                }

                val proceeds = uiState.sellingProceeds
                if (proceeds != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Est. Net Proceeds: ${MoneyUtils.formatCents(proceeds.netProceedsCents)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "After ${MoneyUtils.formatCents(proceeds.totalSellingFeesCents)} fees & closing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            if (proceeds.netProceedsCents > 0L) {
                                Button(
                                    onClick = onUseNetProceeds,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Apply Down", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Purchase & Loan Inputs
        Text(
            text = "New Purchase Parameters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CurrencyTextField(
                cents = uiState.purchasePriceCents,
                onCentsChange = onPurchasePriceChange,
                label = "Purchase Price",
                modifier = Modifier.weight(1f)
            )
            val downPct = if (uiState.purchasePriceCents > 0L) {
                (uiState.downPaymentCents.toDouble() / uiState.purchasePriceCents.toDouble() * 100).toInt()
            } else 0
            CurrencyTextField(
                cents = uiState.downPaymentCents,
                onCentsChange = onDownPaymentChange,
                label = "Down Payment ($downPct%)",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.interestRatePercent.toString(),
                onValueChange = { str ->
                    val clean = str.toDoubleOrNull() ?: 0.0
                    onInterestRateChange(clean)
                },
                label = { Text("Interest Rate %") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Loan Term",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 20, 30).forEach { years ->
                        FilterChip(
                            selected = uiState.loanTermYears == years,
                            onClick = { onLoanTermChange(years) },
                            label = { Text("${years}y") }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Property taxes, insurance, HOA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CurrencyTextField(
                cents = uiState.annualPropertyTaxesCents,
                onCentsChange = onPropertyTaxChange,
                label = "Annual Taxes",
                modifier = Modifier.weight(1f)
            )
            CurrencyTextField(
                cents = uiState.annualHomeInsuranceCents,
                onCentsChange = onInsuranceChange,
                label = "Annual Insurance",
                modifier = Modifier.weight(1f)
            )
            CurrencyTextField(
                cents = uiState.monthlyHoaCents,
                onCentsChange = onHoaChange,
                label = "Monthly HOA",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
