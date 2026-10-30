package app.overbay.orbit.ui.transactions.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

private data class FieldProvenanceInfo(
    val fieldKey: String,
    val label: String,
    val isEdited: Boolean,
    val source: String?,
    val originalText: String?
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionProvenanceCard(
    transaction: Transaction,
    onResetToOriginal: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onResetField: ((String) -> Unit)? = null,
    onViewRule: ((Long) -> Unit)? = null,
    onViewBudgetMonth: ((String) -> Unit)? = null,
    onMarkRecurring: (() -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val friendlyDate = LocalDateUtils.parseIsoDate(transaction.originalDate)
        ?.let { LocalDateUtils.formatDisplayDate(it) }
        ?: transaction.originalDate

    val fields = listOf(
        FieldProvenanceInfo(
            fieldKey = "merchant",
            label = "Merchant",
            isEdited = transaction.editedMerchant != null,
            source = transaction.editedMerchantSource,
            originalText = transaction.originalMerchant
        ),
        FieldProvenanceInfo(
            fieldKey = "category",
            label = "Category",
            isEdited = transaction.editedCategoryId != null,
            source = transaction.editedCategoryIdSource,
            originalText = null
        ),
        FieldProvenanceInfo(
            fieldKey = "amount",
            label = "Amount",
            isEdited = transaction.editedAmountCents != null,
            source = if (transaction.editedAmountCents != null) "user" else null,
            originalText = MoneyUtils.formatCents(transaction.originalAmountCents)
        ),
        FieldProvenanceInfo(
            fieldKey = "date",
            label = "Date",
            isEdited = transaction.editedDate != null,
            source = if (transaction.editedDate != null) "user" else null,
            originalText = friendlyDate
        ),
        FieldProvenanceInfo(
            fieldKey = "transfer",
            label = "Transfer",
            isEdited = transaction.editedIsTransfer != null,
            source = transaction.editedIsTransferSource,
            originalText = if (transaction.isTransfer) "Yes" else "No"
        ),
        FieldProvenanceInfo(
            fieldKey = "ignored",
            label = "Ignored",
            isEdited = transaction.editedIsIgnored != null,
            source = transaction.editedIsIgnoredSource,
            originalText = if (transaction.isIgnored) "Yes" else "No"
        ),
        FieldProvenanceInfo(
            fieldKey = "mha",
            label = "MHA",
            isEdited = transaction.editedMhaEligible != null,
            source = transaction.editedMhaEligibleSource,
            originalText = "Default"
        )
    )

    val editedFields = fields.filter { it.isEdited }
    val hasEdits = editedFields.isNotEmpty() || transaction.editSource == EditSource.USER

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Provenance",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Data Provenance",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (hasEdits) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptics.confirm()
                                onResetToOriginal(transaction.id)
                            }
                    ) {
                        Text(
                            text = "Reset All",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Original: ${transaction.originalMerchant} • $friendlyDate • ${MoneyUtils.formatCents(transaction.originalAmountCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Per-field provenance badges and individual reset chips
            if (editedFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    editedFields.forEach { item ->
                        val src = item.source?.lowercase() ?: "user"
                        val isRule = src.startsWith("rule:")
                        val ruleId = if (isRule) src.removePrefix("rule:").toLongOrNull() else null
                        val badgeLabel = when {
                            isRule && ruleId != null -> "${item.label}: Rule #$ruleId"
                            isRule -> "${item.label}: Rule"
                            src == "user" -> "${item.label}: User Edited"
                            else -> "${item.label}: $src"
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (isRule && ruleId != null && onViewRule != null) {
                                    Text(
                                        text = "View Rule",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable {
                                            haptics.tick()
                                            onViewRule(ruleId)
                                        }
                                    )
                                }

                                if (onResetField != null) {
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.clickable {
                                            haptics.tick()
                                            onResetField(item.fieldKey)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cross-module Quick Links (Budgets, Recurring)
            if (onViewBudgetMonth != null || onMarkRecurring != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onViewBudgetMonth != null) {
                        val month = transaction.effectiveDate.take(7)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.clickable {
                                haptics.tick()
                                onViewBudgetMonth(month)
                            }
                        ) {
                            Text(
                                text = "View Budgets for $month",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (onMarkRecurring != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.clickable {
                                haptics.tick()
                                onMarkRecurring()
                            }
                        ) {
                            Text(
                                text = "Mark as Recurring",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
