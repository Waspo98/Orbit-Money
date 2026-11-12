package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.TransactionQuickAction
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.LocalOrbitSheetDismiss
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.OrbitModalFooter
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.ui.transactions.components.transactionQuickActionIcon
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Drag-and-drop customizer sheet for Transaction quick-action buttons and '...' overflow order.
 */
@Composable
fun TransactionActionsCustomizeSheet(
    pinnedIds: List<String>,
    orderIds: List<String>,
    mhaEnabled: Boolean,
    onSave: (pinnedIds: List<String>, orderIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    var draftOrder by remember(orderIds) {
        val indexMap = orderIds.withIndex().associate { it.value.lowercase() to it.index }
        val sorted = TransactionQuickAction.entries.sortedWith(
            compareBy<TransactionQuickAction> { indexMap[it.id] ?: (1000 + it.ordinal) }
                .thenBy { it.ordinal }
        )
        mutableStateOf(sorted)
    }

    var draftPinnedSet by remember(pinnedIds) {
        mutableStateOf(pinnedIds.map { it.lowercase() }.toSet())
    }

    val visibleActions = remember(draftOrder, mhaEnabled) {
        if (mhaEnabled) draftOrder else draftOrder.filter { it != TransactionQuickAction.MHA }
    }

    val reorderState = rememberDragReorderState(
        items = visibleActions,
        keySelector = { it.id },
        onMove = { fromVisible, toVisible ->
            if (fromVisible in visibleActions.indices && toVisible in visibleActions.indices) {
                val movingAction = visibleActions[fromVisible]
                val targetAction = visibleActions[toVisible]
                val mutable = draftOrder.toMutableList()
                val fullFrom = mutable.indexOf(movingAction)
                val fullTo = mutable.indexOf(targetAction)
                if (fullFrom >= 0 && fullTo >= 0) {
                    mutable.removeAt(fullFrom)
                    mutable.add(fullTo, movingAction)
                    draftOrder = mutable
                }
            }
        },
        itemSpacingDp = 1.dp
    )

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        val sheetDismiss = LocalOrbitSheetDismiss.current

        OrbitSheetHeader(
            title = "Customize Quick Actions",
            subtitle = "Checked actions appear as buttons; unchecked stay in the ••• menu. Drag to reorder.",
            onDismiss = onDismiss,
            trailingActions = {
                OrbitActionChip(
                    label = "Reset",
                    onClick = {
                        draftPinnedSet = OrbitPreferences.defaultTransactionPinnedActions().toSet()
                        draftOrder = TransactionQuickAction.entries.toList()
                    }
                )
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    visibleActions.forEachIndexed { index, action ->
                        key(action.id) {
                            val isPinned = action.id in draftPinnedSet
                            val isDestructive = action == TransactionQuickAction.DELETE

                            if (index > 0) {
                                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dragReorderItem(reorderState, action.id)
                                    .clickable {
                                        haptics.tick()
                                        draftPinnedSet = if (isPinned) {
                                            draftPinnedSet - action.id
                                        } else {
                                            draftPinnedSet + action.id
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OrbitDragHandle(state = reorderState, key = action.id)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Checkbox(
                                        checked = isPinned,
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        imageVector = transactionQuickActionIcon(action),
                                        contentDescription = null,
                                        tint = if (isDestructive) OrbitTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = action.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDestructive) OrbitTheme.colors.danger else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                OrbitBadge(
                                    text = if (isPinned) "Pinned" else "Menu",
                                    containerColor = if (isPinned) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    contentColor = if (isPinned) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        OrbitModalFooter(
            confirmText = "Save",
            onDismiss = onDismiss,
            onConfirm = {
                val orderedStrings = draftOrder.map { it.id }
                val pinnedOrdered = orderedStrings.filter { it in draftPinnedSet }
                sheetDismiss?.invoke { onSave(pinnedOrdered, orderedStrings) }
                    ?: onSave(pinnedOrdered, orderedStrings)
            }
        )
    }
}
