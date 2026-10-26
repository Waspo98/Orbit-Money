package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

data class SplitItemState(
    var amountCents: Cents,
    var categoryId: Long?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTransactionSheet(
    transaction: Transaction,
    categories: List<Category>,
    onSplitConfirmed: (parentId: Long, splits: List<Pair<Cents, Long?>>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val parentAbsAmount = abs(transaction.effectiveAmountCents)
    val isExpense = transaction.isExpense

    // Initialize with two split items (half each, or 0)
    val initialHalf = parentAbsAmount / 2L
    val splitItems = remember(transaction) {
        mutableStateListOf(
            SplitItemState(initialHalf, transaction.effectiveCategoryId),
            SplitItemState(parentAbsAmount - initialHalf, null)
        )
    }

    val totalAllocated = splitItems.sumOf { it.amountCents }
    val remaining = parentAbsAmount - totalAllocated
    val isBalanced = remaining == 0L && splitItems.all { it.amountCents > 0L }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Split Transaction",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${transaction.effectiveMerchant} • Total: ${MoneyUtils.formatCents(parentAbsAmount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Balance Indicator Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isBalanced) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBalanced) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = if (isBalanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBalanced) {
                            "Balanced (100% Allocated)"
                        } else {
                            "Remaining to allocate: ${MoneyUtils.formatCents(remaining)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBalanced) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Split Line Items
            splitItems.forEachIndexed { index, item ->
                SplitLineRow(
                    index = index,
                    item = item,
                    categories = categories,
                    canDelete = splitItems.size > 2,
                    onAmountChanged = { newAmount ->
                        splitItems[index] = item.copy(amountCents = newAmount)
                    },
                    onCategoryChanged = { newCatId ->
                        splitItems[index] = item.copy(categoryId = newCatId)
                    },
                    onDelete = {
                        haptics.reject()
                        splitItems.removeAt(index)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // + Add Split Button (Title Case)
            OutlinedButton(
                onClick = {
                    haptics.tick()
                    val nextAmount = if (remaining > 0L) remaining else 0L
                    splitItems.add(SplitItemState(nextAmount, null))
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

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons (Title Case)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        haptics.confirm()
                        val splits = splitItems.map { split ->
                            val signed = if (isExpense) -split.amountCents else split.amountCents
                            Pair(signed, split.categoryId)
                        }
                        onSplitConfirmed(transaction.id, splits)
                    },
                    enabled = isBalanced,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Split")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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

                if (canDelete) {
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
                    selectedText = selectedCategory?.name ?: "Uncategorized",
                    options = categories,
                    optionLabel = { it.name },
                    onOptionSelected = { onCategoryChanged(it.id) },
                    nullOptionLabel = "Uncategorized",
                    onNullSelected = { onCategoryChanged(null) },
                    modifier = Modifier.weight(0.55f)
                )
            }
        }
    }
}
