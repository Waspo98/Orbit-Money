package app.overbay.orbit.ui.budgets.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitModalScaffold
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun BudgetAmountEditorSheet(
    item: CategoryBudgetOverviewItem,
    threeMonthAvgCents: Cents,
    onSave: (categoryId: Long, amountCents: Cents, rollover: Boolean) -> Unit,
    onDelete: (budgetId: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        BudgetAmountEditorContent(
            item = item,
            threeMonthAvgCents = threeMonthAvgCents,
            onSave = onSave,
            onDelete = onDelete,
            onDismiss = onDismiss
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

    OrbitModalScaffold(
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = {
            haptics.confirm()
            onSave(item.category.id, amountCents, rollover)
        },
        modifier = modifier,
        title = if (item.amountCents > 0L) "Edit Monthly Budget" else "Set Monthly Budget",
        subtitle = "Allocated monthly envelope limit",
        headerTrailingActions = if (item.budgetId != null) {
            {
                OrbitActionChip(
                    label = "Delete",
                    onClick = {
                        haptics.reject()
                        onDelete(item.budgetId)
                    },
                    icon = Icons.Default.Delete,
                    isDestructive = true
                )
            }
        } else null,
        bodySpacing = 0.dp
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = categoryColor.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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

        Spacer(modifier = Modifier.height(14.dp))

        // Amount Input
        CurrencyTextField(
            cents = amountCents,
            onCentsChange = { amountCents = it },
            label = "Monthly Budget"
        )

        // 3-Month Average Helper Suggestion Pill
        if (threeMonthAvgCents > 0L) {
            Spacer(modifier = Modifier.height(6.dp))
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

        Spacer(modifier = Modifier.height(12.dp))

        // Rollover Option (rendered in its own soft 16dp tonal card)
        OrbitSwitchRow(
            title = "Rollover Unspent Room",
            subtitle = "Add remaining balance from previous month to this category's budget",
            checked = rollover,
            onCheckedChange = { rollover = it }
        )
    }
}
