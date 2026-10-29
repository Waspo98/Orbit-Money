package app.overbay.orbit.ui.rules.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle

private const val MAX_PREVIEW_ROWS = 50

@Composable
fun RuleMatchPreviewWindow(
    allConditionsValid: Boolean,
    matchCount: Int,
    matchingTransactions: List<Transaction>,
    accounts: List<Account>,
    categories: List<Category>,
    sourceTransactionId: Long? = null,
    modifier: Modifier = Modifier
) {
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val displayedTransactions = remember(matchingTransactions) {
        matchingTransactions.take(MAX_PREVIEW_ROWS)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Window Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Matching Transactions",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MATCHING TRANSACTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (allConditionsValid) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "$matchCount match${if (matchCount == 1) "" else "es"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            // Window Body
            when {
                !allConditionsValid -> {
                    Text(
                        text = "Fill in conditions above to preview matching transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                displayedTransactions.isEmpty() -> {
                    Text(
                        text = if (matchCount > 0) {
                            "Matches $matchCount existing transaction${if (matchCount == 1) "" else "s"}."
                        } else {
                            "No existing transactions match these conditions."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 216.dp),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = displayedTransactions,
                            key = { it.id }
                        ) { txn ->
                            RulePreviewTransactionRow(
                                transaction = txn,
                                accountName = accountMap[txn.accountId]?.name ?: txn.accountName,
                                categoryLabel = txn.effectiveCategoryId?.let { categoryMap[it]?.labelWithEmoji },
                                isSource = sourceTransactionId != null && txn.id == sourceTransactionId
                            )
                        }

                        if (matchingTransactions.size > MAX_PREVIEW_ROWS) {
                            item(key = "preview_overflow_footer") {
                                Text(
                                    text = "Showing latest $MAX_PREVIEW_ROWS of ${matchingTransactions.size} matching transactions",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulePreviewTransactionRow(
    transaction: Transaction,
    accountName: String,
    categoryLabel: String?,
    isSource: Boolean
) {
    val colors = OrbitTheme.colors
    val borderColor = if (isSource) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        colors.cardBorder
    }

    val metaText = remember(transaction.effectiveDate, accountName, categoryLabel, transaction.originalMerchant, transaction.effectiveMerchant) {
        buildList {
            add(transaction.effectiveDate)
            if (accountName.isNotBlank()) add(accountName)
            if (!categoryLabel.isNullOrBlank()) add(categoryLabel)
            if (transaction.originalMerchant.isNotBlank() && transaction.originalMerchant != transaction.effectiveMerchant) {
                add("Orig: ${transaction.originalMerchant}")
            }
        }.joinToString(" • ")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = transaction.effectiveMerchant.ifBlank { transaction.originalMerchant },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSource) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = MoneyUtils.formatCents(transaction.effectiveAmountCents, signed = true),
                style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = if (transaction.effectiveAmountCents > 0L) colors.income else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
