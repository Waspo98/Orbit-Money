package app.overbay.orbit.ui.transactions.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitScaleBadgeVisibility
import app.overbay.orbit.ui.components.orbitFadeContentTransform
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Compact single-row boolean flag pills + consolidated secondary workflow actions strip
 * replacing the tall multi-line switch group in [app.overbay.orbit.ui.transactions.sheets.TransactionEditorContent].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionOptionsBlock(
    isTransfer: Boolean,
    onTransferChange: (Boolean) -> Unit,
    mhaEligible: Boolean?,
    onMhaEligibleChange: (Boolean) -> Unit,
    isIgnored: Boolean = false,
    onIgnoredChange: (Boolean) -> Unit = {},
    onSplit: (() -> Unit)? = null,
    onCreateRule: (() -> Unit)? = null,
    onMarkRecurring: (() -> Unit)? = null,
    onRestoreOriginal: (() -> Unit)? = null,
    showMhaOption: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasTools = onSplit != null || onCreateRule != null || onMarkRecurring != null || onRestoreOriginal != null

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransactionFlagPill(
                label = "Transfer",
                icon = Icons.Default.Refresh,
                checked = isTransfer,
                onCheckedChange = onTransferChange
            )

            TransactionFlagPill(
                label = "Ignore Budget",
                icon = Icons.Default.Clear,
                checked = isIgnored,
                onCheckedChange = onIgnoredChange
            )

            OrbitScaleBadgeVisibility(visible = showMhaOption) {
                TransactionFlagPill(
                    label = "MHA Eligible",
                    icon = OrbitIcons.MhaTracker,
                    checked = mhaEligible == true,
                    onCheckedChange = onMhaEligibleChange
                )
            }
        }

        OrbitAccordionVisibility(visible = hasTools) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSplit != null) {
                        OrbitActionChip(
                            label = "Split",
                            leadingIcon = OrbitIcons.Split,
                            onClick = onSplit
                        )
                    }
                    if (onCreateRule != null) {
                        OrbitActionChip(
                            label = "Create Rule",
                            leadingIcon = OrbitIcons.Rules,
                            onClick = onCreateRule
                        )
                    }
                    if (onMarkRecurring != null) {
                        OrbitActionChip(
                            label = "Mark Recurring",
                            leadingIcon = OrbitIcons.Upcoming,
                            onClick = onMarkRecurring
                        )
                    }
                    OrbitScaleBadgeVisibility(visible = onRestoreOriginal != null) {
                        OrbitActionChip(
                            label = "Restore Original",
                            leadingIcon = Icons.Default.Refresh,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { onRestoreOriginal?.invoke() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionFlagPill(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
        },
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "txFlagPillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "txFlagPillFg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
        } else {
            OrbitTheme.colors.cardBorder
        },
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "txFlagPillBorder"
    )

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .orbitTactilePress()
            .clip(CircleShape)
            .clickable {
                haptics.tick()
                onCheckedChange(!checked)
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedContent(
                targetState = checked,
                transitionSpec = { orbitFadeContentTransform() },
                label = "txFlagPillIcon"
            ) { isChecked ->
                Icon(
                    imageVector = if (isChecked) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
