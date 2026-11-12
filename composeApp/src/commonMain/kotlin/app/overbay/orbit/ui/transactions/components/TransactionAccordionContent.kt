package app.overbay.orbit.ui.transactions.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Expanded inline accordion body for [SwipeableTransactionRow] showing 2-column provenance metadata,
 * 1-tap Category chip, active status/edit pills, and customizable quick-action strip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionAccordionContent(
    transaction: Transaction,
    category: Category?,
    categoryColor: Color,
    originalCategoryLabel: String?,
    accountName: String?,
    mhaEnabled: Boolean,
    pinnedIds: List<String>,
    orderIds: List<String>,
    onOpenCategoryPicker: () -> Unit,
    onEdit: () -> Unit,
    onSplit: () -> Unit,
    onCreateRule: () -> Unit,
    onMarkRecurring: () -> Unit,
    onToggleTransfer: () -> Unit,
    onToggleMha: () -> Unit,
    onToggleIgnore: () -> Unit,
    onDelete: () -> Unit,
    onCustomizeActions: () -> Unit,
    onResetField: (String) -> Unit,
    onResetAll: () -> Unit,
    onViewRule: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    val dateProv = parseFieldProvenance(transaction.editedDate != null, "user")
    val merchantProv = parseFieldProvenance(transaction.editedMerchant != null, transaction.editedMerchantSource)
    val categoryEdited = transaction.editedCategoryIdSource != null || transaction.editedCategoryId != null
    val categoryProv = parseFieldProvenance(categoryEdited, transaction.editedCategoryIdSource)
    val amountProv = parseFieldProvenance(transaction.editedAmountCents != null, "user")
    val transferProv = parseFieldProvenance(transaction.editedIsTransfer != null, transaction.editedIsTransferSource)
    val mhaProv = parseFieldProvenance(transaction.editedMhaEligible != null, transaction.editedMhaEligibleSource)
    val ignoreProv = parseFieldProvenance(transaction.editedIsIgnored != null, transaction.editedIsIgnoredSource)

    val hasManualEdits = transaction.editSource == EditSource.USER || listOf(
        dateProv, merchantProv, categoryProv, amountProv, transferProv, mhaProv, ignoreProv
    ).any { it is FieldEditProvenance.Manual }

    val showTransferPill = transaction.effectiveIsTransfer || transferProv != FieldEditProvenance.None
    val showMhaPill = mhaEnabled && transaction.effectiveMhaEligible == true
    val showIgnorePill = transaction.effectiveIsIgnored || ignoreProv != FieldEditProvenance.None
    val showStatusStrip = amountProv != FieldEditProvenance.None ||
        showTransferPill || showMhaPill || showIgnorePill || hasManualEdits

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccordionMetaField(
                        label = "Date",
                        value = transaction.effectiveDate,
                        provenance = dateProv,
                        originalSubtext = if (dateProv != FieldEditProvenance.None) {
                            "Originally: ${transaction.originalDate}"
                        } else null,
                        onReset = { onResetField("date") },
                        onViewRule = onViewRule
                    )
                    AccordionMetaField(
                        label = "Account",
                        value = (accountName ?: transaction.accountName).ifBlank { "Account" }
                    )
                    AccordionMetaField(
                        label = "Merchant",
                        value = transaction.effectiveMerchant,
                        provenance = merchantProv,
                        originalSubtext = if (merchantProv != FieldEditProvenance.None && transaction.originalMerchant.isNotBlank()) {
                            "Originally: ${transaction.originalMerchant}"
                        } else null,
                        onReset = { onResetField("merchant") },
                        onViewRule = onViewRule
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        FieldLabelWithProvenance(
                            label = "Category",
                            provenance = categoryProv,
                            onReset = { onResetField("category") },
                            onViewRule = onViewRule
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        TransactionCategoryChip(
                            categoryLabel = category?.labelWithEmoji
                                ?: if (transaction.effectiveIsTransfer) "🔄 Transfer" else "❓ Uncategorized",
                            categoryColor = categoryColor,
                            onClick = onOpenCategoryPicker
                        )
                        if (categoryProv != FieldEditProvenance.None) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Originally: ${originalCategoryLabel ?: "Uncategorized"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    AccordionMetaField(
                        label = "Bank Statement",
                        value = transaction.originalDescription ?: transaction.originalMerchant,
                        isMono = true
                    )
                    AccordionMetaField(
                        label = "Notes",
                        value = transaction.notes?.takeIf { it.isNotBlank() } ?: "None"
                    )
                }
            }

            if (showStatusStrip) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (amountProv != FieldEditProvenance.None) {
                        StatusProvenancePill(
                            text = "Amount (Orig: ${MoneyUtils.formatCents(transaction.originalAmountCents, signed = true)})",
                            provenance = amountProv,
                            onReset = { onResetField("amount") },
                            onViewRule = onViewRule
                        )
                    }
                    if (showTransferPill) {
                        StatusProvenancePill(
                            text = if (transaction.effectiveIsTransfer) "Transfer" else "Not Transfer",
                            provenance = transferProv,
                            onReset = { onResetField("transfer") },
                            onViewRule = onViewRule
                        )
                    }
                    if (showMhaPill) {
                        StatusProvenancePill(
                            text = "MHA Eligible",
                            provenance = mhaProv,
                            onReset = { onResetField("mha") },
                            onViewRule = onViewRule
                        )
                    }
                    if (showIgnorePill) {
                        StatusProvenancePill(
                            text = if (transaction.effectiveIsIgnored) "Ignored" else "Included",
                            provenance = ignoreProv,
                            onReset = { onResetField("ignored") },
                            onViewRule = onViewRule
                        )
                    }
                    if (hasManualEdits) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.tick()
                                    onResetAll()
                                }
                        ) {
                            Text(
                                text = "Restore All",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TransactionQuickActionStrip(
                transaction = transaction,
                pinnedIds = pinnedIds,
                orderIds = orderIds,
                mhaEnabled = mhaEnabled,
                onEdit = onEdit,
                onSplit = onSplit,
                onCreateRule = onCreateRule,
                onMarkRecurring = onMarkRecurring,
                onToggleTransfer = onToggleTransfer,
                onToggleMha = onToggleMha,
                onToggleIgnore = onToggleIgnore,
                onDelete = onDelete,
                onCustomizeActions = onCustomizeActions
            )
        }
    }
}

@Composable
private fun AccordionMetaField(
    label: String,
    value: String,
    isMono: Boolean = false,
    provenance: FieldEditProvenance = FieldEditProvenance.None,
    originalSubtext: String? = null,
    onReset: () -> Unit = {},
    onViewRule: (Long) -> Unit = {}
) {
    Column {
        FieldLabelWithProvenance(label = label, provenance = provenance, onReset = onReset, onViewRule = onViewRule)
        Text(
            text = value,
            style = if (isMono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (originalSubtext != null) {
            Text(
                text = originalSubtext,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
