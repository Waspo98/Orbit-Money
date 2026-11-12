package app.overbay.orbit.ui.transactions.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Clean 3-line transaction row featuring 40dp avatar, metadata summary,
 * vertically centered tabular amount, and expandable inline provenance/actions drawer.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    modifier: Modifier = Modifier,
    originalCategoryLabel: String? = null,
    mhaEnabled: Boolean = false,
    pinnedActions: List<String> = OrbitPreferences.defaultTransactionPinnedActions(),
    actionOrder: List<String> = OrbitPreferences.defaultTransactionActionOrder(),
    isInspected: Boolean = false,
    onFindLogo: (() -> Unit)? = null,
    onUseCategoryIcon: (() -> Unit)? = null,
    onReportLogoStatus: ((String, String) -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val catColor = category?.color.toComposeColor(MaterialTheme.colorScheme.primary)
    val rowBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            isInspected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "txnRowBg"
    )
    val rowBorderColor by animateColorAsState(
        targetValue = if (isInspected || isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        } else {
            OrbitTheme.colors.cardBorder
        },
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "txnRowBorder"
    )

    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        color = rowBgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isInspected) 1.5.dp else 1.dp, rowBorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = expandHorizontally(OrbitSprings.spatialFast()) + fadeIn(OrbitSprings.FloatEffectsFast),
                    exit = shrinkHorizontally(OrbitSprings.spatialFast()) + fadeOut(OrbitSprings.FloatEffectsFast)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }

                TransactionMerchantMark(
                    transaction = transaction,
                    categoryIcon = category?.displayEmoji,
                    categoryColor = catColor,
                    size = 40.dp,
                    cornerRadius = 10.dp,
                    onFindLogo = onFindLogo,
                    onUseCategoryIcon = onUseCategoryIcon,
                    onReportLogoStatus = onReportLogoStatus
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
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
                        if (transaction.isPending) {
                            Text("Pending", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = colors.warning, modifier = Modifier.padding(start = 6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(transaction.effectiveDate, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" • ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = category?.labelWithEmoji ?: if (transaction.effectiveIsTransfer) "🔄 Transfer" else "❓ Uncategorized",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = catColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

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
            }

            OrbitAccordionVisibility(visible = isExpanded) {
                TransactionAccordionContent(
                    transaction = transaction,
                    category = category,
                    categoryColor = catColor,
                    originalCategoryLabel = originalCategoryLabel,
                    accountName = accountName,
                    mhaEnabled = mhaEnabled,
                    pinnedIds = pinnedActions,
                    orderIds = actionOrder,
                    onOpenCategoryPicker = onOpenCategoryPicker,
                    onEdit = onEdit,
                    onSplit = onSplit,
                    onCreateRule = onCreateRule,
                    onMarkRecurring = onMarkRecurring,
                    onToggleTransfer = onToggleTransfer,
                    onToggleMha = onToggleMha,
                    onToggleIgnore = onToggleIgnore,
                    onDelete = onDelete,
                    onCustomizeActions = onCustomizeActions,
                    onResetField = onResetField,
                    onResetAll = onResetAll,
                    onViewRule = onViewRule
                )
            }
        }
    }
}
