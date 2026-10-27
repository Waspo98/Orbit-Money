package app.overbay.orbit.ui.transactions.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial 3-line transaction row featuring 40dp avatar, 3-line metadata,
 * circular 3-dots button, and inline accordion detail drawer.
 * Strictly under the 400-line ceiling.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SwipeableTransactionRow(
    transaction: Transaction,
    category: Category?,
    accountName: String?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onSplit: () -> Unit,
    onCreateRule: () -> Unit,
    onMarkRecurring: () -> Unit,
    onToggleTransfer: () -> Unit,
    onToggleMha: () -> Unit,
    onToggleIgnore: () -> Unit,
    onDelete: () -> Unit,
    onResetMerchant: () -> Unit,
    onResetCategory: () -> Unit,
    modifier: Modifier = Modifier,
    isInspected: Boolean = false,
    onFindLogo: (() -> Unit)? = null,
    onUseCategoryIcon: (() -> Unit)? = null,
    onReportLogoStatus: ((String, String) -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    val catColor = parseHexColor(category?.color) ?: MaterialTheme.colorScheme.primary
    val rowBgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isInspected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surface
    }
    val rowBorderColor = if (isInspected || isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        OrbitTheme.colors.cardBorder
    }

    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        color = rowBgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isInspected) 1.5.dp else 1.dp, rowBorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main 3-line summary row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { haptics.tick(); onClick() },
                        onLongClick = { haptics.confirm(); onLongClick() }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // 40dp Avatar / Vendor Logo Mark
                TransactionMerchantMark(
                    transaction = transaction,
                    categoryIcon = category?.icon,
                    categoryColor = catColor,
                    size = 40.dp,
                    cornerRadius = 10.dp,
                    onFindLogo = onFindLogo,
                    onUseCategoryIcon = onUseCategoryIcon,
                    onReportLogoStatus = onReportLogoStatus
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 3-Line Center Metadata
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    // Line 1: Bold Merchant + Badges
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.effectiveMerchant,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (transaction.effectiveIsIgnored) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (transaction.effectiveIsIgnored) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (transaction.editSource == EditSource.USER) {
                            Text("Edited", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp))
                        }
                        if (transaction.isPending) {
                            Text("Pending", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = colors.warning, modifier = Modifier.padding(start = 6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Line 2: Date • Category
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(transaction.effectiveDate, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" • ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = category?.name ?: if (transaction.effectiveIsTransfer) "Transfer" else "Uncategorized",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = catColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Line 3: Account Name
                    Text(
                        text = accountName ?: transaction.accountName.ifBlank { "Account" },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Right Column: Amount + Circular 3-Dots Button
                Column(horizontalAlignment = Alignment.End) {
                    val amountColor = when {
                        transaction.effectiveIsTransfer -> MaterialTheme.colorScheme.secondary
                        transaction.isIncome -> colors.income
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = MoneyUtils.formatCents(transaction.effectiveAmountCents, signed = true),
                        style = TabularNumStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = amountColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 28dp circular bordered 3-dots button
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            modifier = Modifier.size(28.dp).clickable { haptics.tick(); menuExpanded = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = OrbitIcons.MoreHoriz,
                                    contentDescription = "Actions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                            DropdownMenuItem(text = { Text("Split") }, onClick = { menuExpanded = false; onSplit() })
                            DropdownMenuItem(text = { Text("Create rule") }, onClick = { menuExpanded = false; onCreateRule() })
                            DropdownMenuItem(text = { Text("Mark as recurring") }, onClick = { menuExpanded = false; onMarkRecurring() })
                            DropdownMenuItem(
                                text = { Text(if (transaction.effectiveIsTransfer) "Unmark transfer" else "Mark as transfer") },
                                onClick = { menuExpanded = false; onToggleTransfer() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (transaction.effectiveMhaEligible == true) "Remove MHA" else "MHA eligible") },
                                onClick = { menuExpanded = false; onToggleMha() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (transaction.effectiveIsIgnored) "Unignore" else "Ignore") },
                                onClick = { menuExpanded = false; onToggleIgnore() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Delete", color = colors.danger) }, onClick = { menuExpanded = false; onDelete() })
                        }
                    }
                }
            }

            // Inline Accordion Detail Panel
            AnimatedVisibility(visible = isExpanded) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 2-Column Metadata Grid
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                MetaItem(label = "DATE", value = transaction.effectiveDate)
                                Spacer(modifier = Modifier.height(6.dp))
                                MetaItem(label = "ACCOUNT", value = accountName ?: transaction.accountName)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MetaItem(label = "MERCHANT", value = transaction.effectiveMerchant)
                                    if (transaction.editedMerchant != null) {
                                        Text("Reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onResetMerchant() }.padding(start = 6.dp))
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MetaItem(label = "CATEGORY", value = category?.name ?: "Uncategorized")
                                    if (transaction.editedCategoryId != null) {
                                        Text("Reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onResetCategory() }.padding(start = 6.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                MetaItem(label = "ORIGINAL DESCRIPTION", value = transaction.originalDescription ?: transaction.originalMerchant, isMono = true)
                                Spacer(modifier = Modifier.height(6.dp))
                                MetaItem(label = "NOTES", value = transaction.notes ?: "None")
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Compact Pill Action Buttons Row
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompactPillAction(label = "Edit", onClick = onEdit)
                            CompactPillAction(label = "Split", onClick = onSplit)
                            CompactPillAction(label = "Create rule", onClick = onCreateRule)
                            CompactPillAction(label = "Recurring", onClick = onMarkRecurring)
                            CompactPillAction(label = if (transaction.effectiveIsTransfer) "Unmark Transfer" else "Transfer", onClick = onToggleTransfer)
                            CompactPillAction(label = if (transaction.effectiveIsIgnored) "Unignore" else "Ignore", onClick = onToggleIgnore)
                            CompactPillAction(label = "Delete", isDestructive = true, onClick = onDelete)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaItem(label: String, value: String, isMono: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isMono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactPillAction(
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val haptics = LocalOrbitHaptics.current
    OutlinedButton(
        onClick = { haptics.tick(); onClick() },
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        border = BorderStroke(1.dp, if (isDestructive) OrbitTheme.colors.danger else OrbitTheme.colors.cardBorder),
        modifier = Modifier.height(30.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) OrbitTheme.colors.danger else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank() || !hex.startsWith("#")) return null
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or colorInt)
        } else if (clean.length == 8) {
            Color(colorInt)
        } else null
    } catch (_: Exception) {
        null
    }
}
