package app.overbay.orbit.ui.budgets.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetAmountEditorSheet(
    item: CategoryBudgetOverviewItem,
    threeMonthAvgCents: Cents,
    onSave: (categoryId: Long, amountCents: Cents, rollover: Boolean) -> Unit,
    onDelete: (budgetId: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        BudgetAmountEditorContent(
            item = item,
            threeMonthAvgCents = threeMonthAvgCents,
            onSave = onSave,
            onDelete = onDelete,
            onDismiss = onDismiss,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
fun BudgetAmountEditorContent(
    item: CategoryBudgetOverviewItem,
    threeMonthAvgCents: Cents,
    onSave: (categoryId: Long, amountCents: Cents, rollover: Boolean) -> Unit,
    onDelete: (budgetId: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    var amountCents by remember(item) { mutableStateOf(item.amountCents) }
    var rollover by remember(item) { mutableStateOf(item.rollover) }

    val categoryColor = try {
        val hex = item.category.color.trim().removePrefix("#")
        Color(hex.toLong(16) or 0xFF000000)
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
            // Header with Category Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (item.amountCents > 0L) "Edit Monthly Budget" else "Set Monthly Budget",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Allocated monthly envelope limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = categoryColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(categoryColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.category.labelWithEmoji,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Amount Input
            CurrencyTextField(
                cents = amountCents,
                onCentsChange = { amountCents = it },
                label = "Monthly Budget"
            )

            // 3-Month Average Helper Suggestion Pill
            if (threeMonthAvgCents > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            amountCents = threeMonthAvgCents
                        }
                    ) {
                        Text(
                            text = "Apply 3-Month Average (${MoneyUtils.formatCents(threeMonthAvgCents, showCents = false)})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rollover Option
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                OrbitSwitchRow(
                    title = "Rollover Unspent Room",
                    subtitle = "Add remaining balance from previous month to this category's budget",
                    checked = rollover,
                    onCheckedChange = { rollover = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Actions: Cancel & Save
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
                        onSave(item.category.id, amountCents, rollover)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }

            // Secondary: Delete Budget (if existing)
            if (item.budgetId != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        haptics.reject()
                        onDelete(item.budgetId)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Budget",
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text("Remove Budget")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
}
