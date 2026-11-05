package app.overbay.orbit.ui.dashboard.dialogs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.domain.ReviewQueueCandidate
import app.overbay.orbit.domain.ReviewQueueEngine
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.transactions.components.RemoteLogoImage

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
    val catColor = (category?.color ?: txn.categoryColor).toComposeColor(primary)
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

                if (!txn.originalDescription.isNullOrBlank()) {
                    Text(
                        text = txn.originalDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = txn.formattedAmount,
                        style = TabularNumStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
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

            if (cardTone == "yes") {
                SwipeChoiceBadge("YES", primary, Modifier.align(Alignment.TopStart).padding(18.dp))
            } else if (cardTone == "no") {
                SwipeChoiceBadge("NO", colors.expense, Modifier.align(Alignment.TopEnd).padding(18.dp))
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
