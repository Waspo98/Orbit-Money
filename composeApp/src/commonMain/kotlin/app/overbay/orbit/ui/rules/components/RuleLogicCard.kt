package app.overbay.orbit.ui.rules.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.delay

/**
 * Stable-keyed wrapper for an editable rule condition or action so rows preserve focus and can
 * complete their entrance and exit transitions cleanly.
 */
data class RuleEditorEntry<T>(
    val key: Long,
    val value: T,
    val animateEnter: Boolean = false,
    val isRemoving: Boolean = false
)

/**
 * Connected IF -> THEN logic card grouping conditions and actions with compact pill selectors.
 */
@Composable
fun RuleLogicCard(
    conditions: List<RuleEditorEntry<RuleCondition>>,
    actions: List<RuleEditorEntry<RuleAction>>,
    accounts: List<Account>,
    categories: List<Category>,
    onAddCondition: () -> Unit,
    onConditionFieldChanged: (Long, RuleConditionField) -> Unit,
    onConditionChanged: (Long, RuleCondition) -> Unit,
    onDeleteCondition: (Long) -> Unit,
    onRemoveConditionFinished: (Long) -> Unit,
    onAddAction: () -> Unit,
    onActionTypeChanged: (Long, RuleActionType) -> Unit,
    onActionChanged: (Long, RuleAction) -> Unit,
    onDeleteAction: (Long) -> Unit,
    onRemoveActionFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            RuleSectionHeaderRow(
                label = "IF THE TRANSACTION…",
                labelColor = MaterialTheme.colorScheme.primary,
                addLabel = "Add Condition",
                chipContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                chipContentColor = MaterialTheme.colorScheme.primary,
                chipBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                onAddClick = {
                    haptics.tick()
                    onAddCondition()
                }
            )

            AnimatedRuleEntryList(
                entries = conditions,
                onDelete = onDeleteCondition,
                onRemoveFinished = onRemoveConditionFinished
            ) { entry, canDelete, onDeleteClick ->
                ConditionEditorRow(
                    condition = entry.value,
                    accounts = accounts,
                    categories = categories,
                    canDelete = canDelete,
                    onFieldChanged = { nextField -> onConditionFieldChanged(entry.key, nextField) },
                    onConditionChanged = { updated -> onConditionChanged(entry.key, updated) },
                    onDelete = onDeleteClick
                )
            }

            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

            RuleSectionHeaderRow(
                label = "THEN…",
                labelColor = MaterialTheme.colorScheme.secondary,
                addLabel = "Add Action",
                chipContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                chipContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                chipBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                onAddClick = {
                    haptics.tick()
                    onAddAction()
                }
            )

            AnimatedRuleEntryList(
                entries = actions,
                onDelete = onDeleteAction,
                onRemoveFinished = onRemoveActionFinished
            ) { entry, canDelete, onDeleteClick ->
                ActionEditorRow(
                    action = entry.value,
                    categories = categories,
                    canDelete = canDelete,
                    onTypeChanged = { nextType -> onActionTypeChanged(entry.key, nextType) },
                    onActionChanged = { updated -> onActionChanged(entry.key, updated) },
                    onDelete = onDeleteClick
                )
            }
        }
    }
}

@Composable
private fun <T> AnimatedRuleEntryList(
    entries: List<RuleEditorEntry<T>>,
    onDelete: (Long) -> Unit,
    onRemoveFinished: (Long) -> Unit,
    rowContent: @Composable (entry: RuleEditorEntry<T>, canDelete: Boolean, onDeleteClick: () -> Unit) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val activeIndicesByKey = remember(entries) {
        var activeIndex = 0
        buildMap {
            entries.forEach { entry ->
                if (!entry.isRemoving) {
                    put(entry.key, activeIndex++)
                }
            }
        }
    }
    val activeCount = activeIndicesByKey.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
    ) {
        entries.forEach { entry ->
            val activeIndex = activeIndicesByKey[entry.key]
            val canDelete = activeCount > 1 && !entry.isRemoving
            key(entry.key) {
                AnimatedRuleEntryContainer(
                    entry = entry,
                    activeIndex = activeIndex,
                    onRemoveFinished = onRemoveFinished
                ) {
                    rowContent(entry, canDelete) {
                        if (canDelete) {
                            haptics.tick()
                            onDelete(entry.key)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> AnimatedRuleEntryContainer(
    entry: RuleEditorEntry<T>,
    activeIndex: Int?,
    onRemoveFinished: (Long) -> Unit,
    content: @Composable () -> Unit
) {
    val visibleState = remember { MutableTransitionState(!entry.animateEnter) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var hasBroughtIntoView by remember { mutableStateOf(false) }

    // Latch the previous connector state during exit so the whole row+divider unit collapses together.
    var showConnector by remember { mutableStateOf((activeIndex ?: 0) > 0) }
    if (!entry.isRemoving && activeIndex != null) {
        showConnector = activeIndex > 0
    }

    LaunchedEffect(entry.isRemoving) {
        visibleState.targetState = !entry.isRemoving
    }

    LaunchedEffect(
        entry.isRemoving,
        visibleState.isIdle,
        visibleState.currentState,
        visibleState.targetState
    ) {
        if (entry.isRemoving && visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) {
            onRemoveFinished(entry.key)
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = expandVertically(
            animationSpec = OrbitSprings.IntSizeSpatialDefault,
            expandFrom = Alignment.Top
        ) + scaleIn(
            animationSpec = OrbitSprings.FloatSpatialDefault,
            initialScale = 0.96f,
            transformOrigin = TransformOrigin(0.5f, 0f)
        ) + fadeIn(
            animationSpec = OrbitSprings.FloatEffectsFast
        ),
        exit = shrinkVertically(
            animationSpec = OrbitSprings.IntSizeSpatialDefault,
            shrinkTowards = Alignment.Top
        ) + scaleOut(
            animationSpec = OrbitSprings.FloatSpatialDefault,
            targetScale = 0.96f,
            transformOrigin = TransformOrigin(0.5f, 0f)
        ) + fadeOut(
            animationSpec = OrbitSprings.FloatEffectsFast
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (entry.animateEnter && !entry.isRemoving && !hasBroughtIntoView) {
            LaunchedEffect(entry.key) {
                delay(180)
                bringIntoViewRequester.bringIntoView()
                hasBroughtIntoView = true
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
        ) {
            OrbitAccordionVisibility(visible = showConnector) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    InlineConnectorDivider(label = "and")
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            content()
        }
    }
}

@Composable
private fun RuleSectionHeaderRow(
    label: String,
    labelColor: Color,
    addLabel: String,
    chipContainerColor: Color,
    chipContentColor: Color,
    chipBorderColor: Color,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = labelColor
        )

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = chipContainerColor,
            border = BorderStroke(1.dp, chipBorderColor),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onAddClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = addLabel,
                    tint = chipContentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = addLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = chipContentColor
                )
            }
        }
    }
}

@Composable
private fun InlineConnectorDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrbitBadge(
            text = label,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = OrbitTheme.colors.cardBorder,
            modifier = Modifier.weight(1f)
        )
    }
}
