package app.overbay.orbit.ui.upcoming.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.CardCycleStatus
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Section card listing credit cards with upcoming payment due dates within the next 35 days.
 * Rendered at the top of the Upcoming list. Tapping a row navigates to the card in Accounts.
 */
@Composable
fun CardPaymentsDueCard(
    cardPayments: List<CardCycleStatus>,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cardPayments.isEmpty()) return
    val haptics = LocalOrbitHaptics.current

    OrbitSectionCard(
        title = "Credit Card Payments",
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            cardPayments.forEachIndexed { index, card ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = OrbitTheme.colors.cardBorder,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                val dueStr = card.dueDate?.let {
                    "Due " + LocalDateUtils.formatRelativeOrDisplayDate(it)
                } ?: "Due date pending"

                val amountText = if (card.estimatedStatementBalanceCents != null) {
                    "Est. " + MoneyUtils.formatCents(card.estimatedStatementBalanceCents)
                } else {
                    "Owed " + MoneyUtils.formatCents(card.owedNowCents)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.tick()
                            onNavigate(OrbitNavKey.Accounts(card.accountId))
                        }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = card.cardTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!card.holderLabel.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                OrbitBadge(
                                    text = card.holderLabel,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dueStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = amountText,
                        style = TabularNumStyle.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = OrbitTheme.colors.expense
                    )
                }
            }
        }
    }
}
