package app.overbay.orbit.ui.dashboard.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.dashboard.DashboardAction
import app.overbay.orbit.ui.dashboard.DashboardCardConfigItem
import app.overbay.orbit.ui.dashboard.DashboardCardType
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Modal bottom sheet allowing users to reorder dashboard cards (move up/down)
 * and toggle visibility with tactile switch haptics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCustomizeSheet(
    cards: List<DashboardCardConfigItem>,
    onAction: (DashboardAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    DashboardCustomizeSheet(
        cards = cards,
        onToggleVisibility = { onAction(DashboardAction.ToggleCardVisibility(it)) },
        onMoveUp = { onAction(DashboardAction.MoveCardUp(it)) },
        onMoveDown = { onAction(DashboardAction.MoveCardDown(it)) },
        onReset = { onAction(DashboardAction.ResetCardsToDefault) },
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCustomizeSheet(
    cards: List<DashboardCardConfigItem>,
    onToggleVisibility: (DashboardCardType) -> Unit,
    onMoveUp: (DashboardCardType) -> Unit,
    onMoveDown: (DashboardCardType) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Customize Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Show, hide, and reorder cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        haptics.tick()
                        onReset()
                    }
                ) {
                    Text(
                        text = "Reset to Default",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card Reorder & Visibility List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cards.forEachIndexed { index, config ->
                    CustomizeCardItem(
                        config = config,
                        isFirst = index == 0,
                        isLast = index == cards.lastIndex,
                        onToggle = {
                            haptics.tap()
                            onToggleVisibility(config.type)
                        },
                        onMoveUp = {
                            haptics.tick()
                            onMoveUp(config.type)
                        },
                        onMoveDown = {
                            haptics.tick()
                            onMoveDown(config.type)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Done CTA
            Button(
                onClick = {
                    haptics.confirm()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CustomizeCardItem(
    config: DashboardCardConfigItem,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder controls: Up / Down arrow buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Move Up",
                        tint = if (!isFirst) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(90f)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Move Down",
                        tint = if (!isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(90f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Title
            Text(
                text = config.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (config.isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )

            // Visibility Switch
            Switch(
                checked = config.isVisible,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
