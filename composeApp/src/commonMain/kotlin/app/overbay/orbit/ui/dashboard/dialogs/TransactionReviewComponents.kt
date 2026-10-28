package app.overbay.orbit.ui.dashboard.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.domain.ReviewQueueCandidate
import app.overbay.orbit.domain.ReviewQueueEngine
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.transactions.components.RemoteLogoImage
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Empty state shown when all transactions are categorized (`allDone`) or when a 10-card stack completes (`batchComplete`).
 */
@Composable
internal fun ReviewEmptyStateContent(
    badgeText: String,
    title: String,
    subtitle: String,
    primaryButtonText: String,
    secondaryButtonText: String,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 360.dp)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                haptics.confirm()
                onPrimaryClick()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(text = primaryButtonText, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                haptics.tick()
                onSecondaryClick()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(text = secondaryButtonText, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Category picker view opened when the user swipes left ("No" / Change) on a review card.
 */
@Composable
internal fun CategoryReviewPickerContent(
    txn: ReviewQueueCandidate,
    categories: List<Category>,
    onChooseCategory: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val currentCategory = remember(categories, txn.categoryId) {
        categories.find { it.id == txn.categoryId }
    }
    val orderedCategories = remember(categories, txn.suggestedCategoryId) {
        categories.sortedWith(
            compareBy<Category> { it.id != txn.suggestedCategoryId }
                .thenBy { it.name.lowercase() }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                haptics.tick()
                onBack()
            },
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Back", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = txn.merchant,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Pick the right category",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Currently ${currentCategory?.name ?: txn.categoryName ?: "Uncategorized"}",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            orderedCategories.forEach { category ->
                val isCurrent = category.id == txn.categoryId
                val isSuggested = category.id == txn.suggestedCategoryId
                val catColor = parseReviewHexColor(category.color) ?: MaterialTheme.colorScheme.primary
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isCurrent) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            haptics.tick()
                            onChooseCategory(category.id)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = catColor.copy(alpha = 0.18f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = CategoryEmojiMapper.resolveEmoji(category.icon) ?: "$",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = catColor
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isSuggested) {
                                Text(
                                    text = "Usual category for this merchant",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (isCurrent) {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Inner content of the swipeable transaction review card (`ReviewTransactionCard` on `main`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewTransactionCardContent(
    txn: ReviewQueueCandidate,
    category: Category?,
    cardTone: String,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val catColor = parseReviewHexColor(category?.color ?: txn.categoryColor) ?: primary
    val borderColor = when (cardTone) {
        "yes" -> primary.copy(alpha = 0.65f)
        "no" -> colors.danger.copy(alpha = 0.65f)
        else -> colors.cardBorder
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 310.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Topline: short date & account name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = txn.dateDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (txn.accountName.isNotBlank()) {
                        Text(
                            text = txn.accountName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Merchant logo + name row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    val hasLogo = !txn.merchantLogoUrl.isNullOrBlank()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (hasLogo) Color.White else catColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (hasLogo) 8.dp else 0.dp)
                        ) {
                            RemoteLogoImage(
                                url = txn.merchantLogoUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                fallback = {
                                    Text(
                                        text = CategoryEmojiMapper.resolveEmoji(category?.icon ?: txn.categoryIcon) ?: "?",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = catColor
                                    )
                                }
                            )
                        }
                    }
                    Text(
                        text = txn.merchant,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Optional original description
                if (!txn.originalDescription.isNullOrBlank()) {
                    Text(
                        text = txn.originalDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Current category box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = catColor.copy(alpha = 0.14f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = CategoryEmojiMapper.resolveEmoji(category?.icon ?: txn.categoryIcon) ?: "?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = catColor
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Current category",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = category?.name ?: txn.categoryName ?: "Uncategorized",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Footer: formatted amount & review reason pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = txn.formattedAmount,
                        style = TabularNumStyle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (txn.isExpense) colors.expense else colors.income
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        txn.reviewReasons.take(3).forEach { reason ->
                            Surface(
                                shape = CircleShape,
                                color = primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = ReviewQueueEngine.reasonLabel(reason),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // YES / NO swipe choice badges
            if (cardTone == "yes") {
                SwipeChoiceBadge(
                    text = "YES",
                    color = primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(18.dp)
                )
            } else if (cardTone == "no") {
                SwipeChoiceBadge(
                    text = "NO",
                    color = colors.expense,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SwipeChoiceBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, color),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
        )
    }
}

internal fun makeReviewRuleDraft(
    txn: ReviewQueueCandidate,
    categories: List<Category>
): TransactionRule {
    val categoryId = txn.categoryId ?: txn.suggestedCategoryId ?: categories.firstOrNull()?.id
    val merchantValue = txn.merchant.ifBlank { txn.originalMerchant }
    return TransactionRule(
        id = 0L,
        name = "Categorize ${txn.merchant.ifBlank { "merchant" }}",
        priority = 10,
        conditions = listOf(
            RuleCondition(
                field = RuleConditionField.MERCHANT,
                operator = RuleOperator.CONTAINS,
                value = merchantValue
            )
        ),
        actions = if (categoryId != null) {
            listOf(
                RuleAction(
                    type = RuleActionType.CATEGORIZE,
                    value = categoryId.toString(),
                    targetCategoryId = categoryId
                )
            )
        } else {
            listOf(
                RuleAction(
                    type = RuleActionType.RENAME,
                    value = merchantValue,
                    textValue = merchantValue
                )
            )
        },
        enabled = true
    )
}

internal fun parseReviewHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank() || !hex.startsWith("#")) return null
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000 or colorInt)
            8 -> Color(colorInt)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
