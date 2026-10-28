package app.overbay.orbit.ui.upcoming.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.upcoming.UpcomingUiState
import app.overbay.orbit.ui.upcoming.UpcomingViewModel
import app.overbay.orbit.ui.upcoming.sheets.RecurringItemEditorContent
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

private enum class UpcomingInspectorSubMode { DETAIL, EDIT }

/**
 * Right-hand morphing inspector pane for Upcoming recurring schedules on unfolded phones, tablets, and desktops.
 * Shows the read-only schedule summary by default when tapped, and morphs smoothly into the editor form.
 * Strictly under the 400-line ceiling.
 */
@Composable
fun UpcomingDetailInspectorPane(
    state: UpcomingUiState,
    viewModel: UpcomingViewModel,
    onClosePane: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var cachedItem by remember { mutableStateOf<UpcomingItem?>(null) }
    val activeItem = state.inspectingItem
    if (activeItem != null) {
        cachedItem = activeItem
    }
    val inspecting = activeItem ?: cachedItem

    val subMode = if (state.showEditorSheet) UpcomingInspectorSubMode.EDIT else UpcomingInspectorSubMode.DETAIL

    BackHandler(enabled = subMode == UpcomingInspectorSubMode.EDIT && state.inspectingItemId != null) {
        haptics.tick()
        viewModel.closeEditorSheet()
    }

    AnimatedContent(
        targetState = subMode,
        transitionSpec = { fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault()) },
        modifier = modifier.fillMaxSize(),
        label = "upcomingInspectorSubModeTransition"
    ) { mode ->
        when (mode) {
            UpcomingInspectorSubMode.EDIT -> {
                RecurringItemEditorContent(
                    item = state.editingItem ?: inspecting,
                    categories = state.categories,
                    accounts = state.accounts,
                    onSave = { viewModel.saveUpcoming(it) },
                    onDelete = { viewModel.deleteUpcoming(it) },
                    onDismiss = {
                        if (state.inspectingItemId != null) viewModel.closeEditorSheet() else onClosePane()
                    }
                )
            }
            UpcomingInspectorSubMode.DETAIL -> {
                if (inspecting != null) {
                    val cat = inspecting.categoryId?.let { state.categoryMap[it] }
                    val acct = inspecting.accountId?.let { state.accountMap[it] }
                    UpcomingReadOnlyDetailContent(
                        item = inspecting,
                        category = cat,
                        account = acct,
                        onClose = onClosePane,
                        onEdit = { viewModel.openEditUpcoming(inspecting) },
                        onMarkPaid = { viewModel.openReconcileDialog(inspecting) },
                        onDelete = { viewModel.deleteUpcoming(inspecting.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpcomingReadOnlyDetailContent(
    item: UpcomingItem,
    category: Category?,
    account: Account?,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val isExpense = item.direction.lowercase() != "income"
    val amountColor = if (isExpense) colors.expense else colors.income
    val catColor = try {
        val hex = category?.color?.trim()?.removePrefix("#") ?: "9E9E9E"
        Color(hex.toLong(16) or 0xFF000000)
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category?.displayEmoji ?: "📅",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { item.merchant ?: "Recurring Item" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                val prefix = if (isExpense) "-" else "+"
                Text(
                    text = "$prefix${MoneyUtils.formatCents(abs(item.amountCents))}",
                    style = TabularNumStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = amountColor
                )
            }
            IconButton(onClick = { haptics.tick(); onClose() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UpcomingMetaField(label = "NEXT DUE DATE", value = item.nextDate)
                UpcomingMetaField(
                    label = "FREQUENCY",
                    value = item.frequencyType.name.lowercase().replaceFirstChar { it.uppercase() }
                )
                UpcomingMetaField(
                    label = "DIRECTION",
                    value = if (isExpense) "Bill / Expense" else "Income"
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UpcomingMetaField(
                    label = "CATEGORY",
                    value = category?.labelWithEmoji ?: "None / Unassigned"
                )
                UpcomingMetaField(
                    label = "PAYMENT ACCOUNT",
                    value = account?.name ?: "None / Unassigned"
                )
                UpcomingMetaField(
                    label = "STATUS",
                    value = item.status.name.lowercase().replaceFirstChar { it.uppercase() }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UpcomingPillAction(label = "Edit Schedule", onClick = onEdit)
            UpcomingPillAction(
                label = if (isExpense) "Mark Paid" else "Mark Received",
                onClick = onMarkPaid
            )
            UpcomingPillAction(label = "Delete", isDestructive = true, onClick = onDelete)
        }
    }
}

@Composable
private fun UpcomingMetaField(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UpcomingPillAction(label: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    val haptics = LocalOrbitHaptics.current
    OutlinedButton(
        onClick = { haptics.tick(); onClick() },
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, if (isDestructive) OrbitTheme.colors.danger else OrbitTheme.colors.cardBorder),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) OrbitTheme.colors.danger else MaterialTheme.colorScheme.onSurface
        )
    }
}
