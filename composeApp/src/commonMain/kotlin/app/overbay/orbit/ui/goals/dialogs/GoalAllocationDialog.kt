package app.overbay.orbit.ui.goals.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun GoalAllocationDialog(
    goal: Goal,
    onConfirmDeposit: (goalId: Long, amountCents: Cents) -> Unit,
    onDismiss: () -> Unit
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss
    ) {
        GoalAllocationContent(
            goal = goal,
            onConfirmDeposit = onConfirmDeposit,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun GoalAllocationContent(
    goal: Goal,
    onConfirmDeposit: (goalId: Long, amountCents: Cents) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var depositCents by remember(goal) { mutableStateOf(5000L) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Deposit to ${goal.name}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))
        GoalAllocationFields(
            goal = goal,
            depositCents = depositCents,
            onDepositCentsChange = { depositCents = it }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    haptics.confirm()
                    onConfirmDeposit(goal.id, depositCents)
                },
                enabled = depositCents > 0L,
                modifier = Modifier.weight(1f)
            ) {
                Text("Deposit")
            }
        }
    }
}

@Composable
private fun GoalAllocationFields(
    goal: Goal,
    depositCents: Cents,
    onDepositCentsChange: (Cents) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Current: ${MoneyUtils.formatCents(goal.currentAmountCents, showCents = false)} of ${MoneyUtils.formatCents(goal.targetAmountCents, showCents = false)} (${MoneyUtils.formatCents(goal.remainingAmountCents, showCents = false)} remaining)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        CurrencyTextField(
            cents = depositCents,
            onCentsChange = onDepositCentsChange,
            label = "Deposit Amount"
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(2500L, 5000L, 10000L, 25000L).forEach { quickCents ->
                Surface(
                    onClick = {
                        haptics.tick()
                        onDepositCentsChange(quickCents)
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "+${MoneyUtils.formatCents(quickCents, showCents = false)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
