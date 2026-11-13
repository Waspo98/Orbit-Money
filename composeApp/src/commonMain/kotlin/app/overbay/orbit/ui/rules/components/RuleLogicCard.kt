package app.overbay.orbit.ui.rules.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Connected IF -> THEN logic card grouping conditions and actions with compact pill selectors.
 */
@Composable
fun RuleLogicCard(
    conditions: List<RuleCondition>,
    actions: List<RuleAction>,
    accounts: List<Account>,
    categories: List<Category>,
    onAddCondition: () -> Unit,
    onConditionFieldChanged: (Int, RuleConditionField) -> Unit,
    onConditionChanged: (Int, RuleCondition) -> Unit,
    onDeleteCondition: (Int) -> Unit,
    onAddAction: () -> Unit,
    onActionTypeChanged: (Int, RuleActionType) -> Unit,
    onActionChanged: (Int, RuleAction) -> Unit,
    onDeleteAction: (Int) -> Unit,
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                conditions.forEachIndexed { index, condition ->
                    if (index > 0) {
                        InlineConnectorDivider(label = "and")
                    }
                    ConditionEditorRow(
                        condition = condition,
                        accounts = accounts,
                        categories = categories,
                        canDelete = conditions.size > 1,
                        onFieldChanged = { nextField -> onConditionFieldChanged(index, nextField) },
                        onConditionChanged = { updated -> onConditionChanged(index, updated) },
                        onDelete = {
                            haptics.tick()
                            onDeleteCondition(index)
                        }
                    )
                }
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions.forEachIndexed { index, action ->
                    if (index > 0) {
                        InlineConnectorDivider(label = "and")
                    }
                    ActionEditorRow(
                        action = action,
                        categories = categories,
                        canDelete = actions.size > 1,
                        onTypeChanged = { nextType -> onActionTypeChanged(index, nextType) },
                        onActionChanged = { updated -> onActionChanged(index, updated) },
                        onDelete = {
                            haptics.tick()
                            onDeleteAction(index)
                        }
                    )
                }
            }
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
