package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitAnimatedEntry
import app.overbay.orbit.ui.components.OrbitAnimatedEntryColumn
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitEntryKeyGenerator
import app.overbay.orbit.ui.components.OrbitHorizontalReveal
import app.overbay.orbit.ui.components.OrbitModalScaffold
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.markEntryRemoving
import app.overbay.orbit.ui.components.orbitFadeContentTransform
import app.overbay.orbit.ui.components.updateActiveEntry
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

data class SplitItemState(
    var amountCents: Cents,
    var categoryId: Long?
)

@Composable
fun SplitTransactionSheet(
    transaction: Transaction,
    categories: List<Category>,
    onSplitConfirmed: (parentId: Long, splits: List<Pair<Cents, Long?>>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isInlinePane: Boolean = false
) {
    if (isInlinePane) {
        SplitTransactionContent(
            transaction = transaction,
            categories = categories,
            onSplitConfirmed = onSplitConfirmed,
            onDismiss = onDismiss,
            modifier = modifier
        )
    } else {
        OrbitModalSheet(
            onDismissRequest = onDismiss,
            modifier = modifier
        ) {
            SplitTransactionContent(
                transaction = transaction,
                categories = categories,
                onSplitConfirmed = onSplitConfirmed,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SplitTransactionContent(
    transaction: Transaction,
    categories: List<Category>,
    onSplitConfirmed: (parentId: Long, splits: List<Pair<Cents, Long?>>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    val parentAbsAmount = abs(transaction.effectiveAmountCents)
    val isExpense = transaction.isExpense

    // Initialize with two split items (half each, or 0)
    val initialHalf = parentAbsAmount / 2L
    val entryKeyCounter = remember(transaction) { OrbitEntryKeyGenerator() }
    val splitItems = remember(transaction) {
        mutableStateListOf(
            OrbitAnimatedEntry(
                key = entryKeyCounter.nextKey(),
                value = SplitItemState(initialHalf, transaction.effectiveCategoryId)
            ),
            OrbitAnimatedEntry(
                key = entryKeyCounter.nextKey(),
                value = SplitItemState(parentAbsAmount - initialHalf, null)
            )
        )
    }

    val activeSplits = splitItems.filter { !it.isRemoving }
    val totalAllocated = activeSplits.sumOf { it.value.amountCents }
    val remaining = parentAbsAmount - totalAllocated
    val isBalanced = remaining == 0L && activeSplits.all { it.value.amountCents > 0L }

    OrbitModalScaffold(
        title = "Split Transaction",
        subtitle = "${transaction.effectiveMerchant} • Total: ${MoneyUtils.formatCents(parentAbsAmount)}",
        onDismiss = onDismiss,
        confirmText = "Save Split",
        confirmEnabled = isBalanced,
        onConfirm = {
            val splits = splitItems.filter { !it.isRemoving }.map { entry ->
                val split = entry.value
                val signed = if (isExpense) -split.amountCents else split.amountCents
                Pair(signed, split.categoryId)
            }
            onSplitConfirmed(transaction.id, splits)
        },
        bodyPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        bodySpacing = 0.dp,
        modifier = modifier
    ) {

        // Balance Indicator Banner
        val bannerBgColor by animateColorAsState(
            targetValue = if (isBalanced) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            },
            animationSpec = OrbitSprings.ColorEffectsFast
        )
        val bannerIconTint by animateColorAsState(
            targetValue = if (isBalanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            animationSpec = OrbitSprings.ColorEffectsFast
        )
        val bannerTextColor by animateColorAsState(
            targetValue = if (isBalanced) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            animationSpec = OrbitSprings.ColorEffectsFast
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bannerBgColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = isBalanced,
                    transitionSpec = { orbitFadeContentTransform() }
                ) { balanced ->
                    Icon(
                        imageVector = if (balanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = bannerIconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBalanced) {
                        "Balanced (100% Allocated)"
                    } else {
                        "Remaining to allocate: ${MoneyUtils.formatCents(remaining)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = bannerTextColor
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Split Line Items
        OrbitAnimatedEntryColumn(
            entries = splitItems,
            onRemoveFinished = { finishedKey ->
                splitItems.removeAll { it.key == finishedKey }
            },
            minActiveCount = 2,
            itemSpacing = 10.dp,
            bringIntoViewOnAdd = true
        ) { entry, displayIndex, canDelete ->
            SplitLineRow(
                index = displayIndex,
                item = entry.value,
                categories = categories,
                canDelete = canDelete,
                onAmountChanged = { newAmount ->
                    splitItems.updateActiveEntry(entry.key) { it.copy(amountCents = newAmount) }
                },
                onCategoryChanged = { newCatId ->
                    splitItems.updateActiveEntry(entry.key) { it.copy(categoryId = newCatId) }
                },
                onDelete = {
                    haptics.reject()
                    splitItems.markEntryRemoving(entry.key, minActiveCount = 2)
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // + Add Split Button (Title Case)
        OutlinedButton(
            onClick = {
                haptics.tick()
                val nextAmount = if (remaining > 0L) remaining else 0L
                splitItems.add(
                    OrbitAnimatedEntry(
                        key = entryKeyCounter.nextKey(),
                        value = SplitItemState(nextAmount, null),
                        animateEnter = true
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Split",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Split Line")
        }
    }
}

@Composable
private fun SplitLineRow(
    index: Int,
    item: SplitItemState,
    categories: List<Category>,
    canDelete: Boolean,
    onAmountChanged: (Cents) -> Unit,
    onCategoryChanged: (Long?) -> Unit,
    onDelete: () -> Unit
) {
    val selectedCategory = categories.find { it.id == item.categoryId }

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Split #${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OrbitHorizontalReveal(visible = canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Split",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(0.45f)) {
                    CurrencyTextField(
                        cents = item.amountCents,
                        onCentsChange = onAmountChanged,
                        label = "Amount"
                    )
                }

                OrbitDropdownField(
                    label = "Category",
                    selectedText = selectedCategory?.labelWithEmoji ?: "❓ Uncategorized",
                    options = categories,
                    optionLabel = { it.labelWithEmoji },
                    onOptionSelected = { onCategoryChanged(it.id) },
                    nullOptionLabel = "❓ Uncategorized",
                    onNullSelected = { onCategoryChanged(null) },
                    modifier = Modifier.weight(0.55f)
                )
            }
        }
    }
}
