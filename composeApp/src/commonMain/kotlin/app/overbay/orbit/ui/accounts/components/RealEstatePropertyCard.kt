package app.overbay.orbit.ui.accounts.components

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.RealEstatePropertyEngine
import app.overbay.orbit.domain.RealEstatePropertyUnit
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Option C Real Estate Property Card: headlines the property's Net Equity while
 * nesting the Estimated Property Value asset row and all linked Mortgage / HELOC
 * liability rows underneath so every number matches its detail sheet without whiplash.
 */
@Composable
fun RealEstatePropertyCard(
    unit: RealEstatePropertyUnit,
    selectedAccountId: Long?,
    closureCandidateIds: Set<Long>,
    onInspectAccount: (Account) -> Unit,
    onEditAccount: (Account) -> Unit,
    onReviewClosure: (Account) -> Unit,
    onSeeTransactions: (Account) -> Unit,
    onFindLogo: (Account) -> Unit,
    onReportLogoStatus: (String, String) -> Unit,
    onHistoryClick: (Account) -> Unit,
    onMergeClick: (Account) -> Unit,
    onToggleArchive: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val equityPct = (unit.equityFraction * 100f).roundToInt()
    val equityColor = if (unit.netEquityCents >= 0L) colors.income else colors.expense

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Property Headline Row (Title + Equity Bar + Net Equity)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.tick()
                        if (unit.anchorAccount.isProperty) onInspectAccount(unit.anchorAccount)
                        else onEditAccount(unit.anchorAccount)
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = OrbitIcons.HousingCalculator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = unit.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(unit.equityFraction.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(equityColor)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (unit.isPaidOff) "Owned Free & Clear" else "$equityPct% equity",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = if (unit.isPaidOff) colors.income else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MoneyUtils.formatCents(unit.netEquityCents),
                        style = TabularNumStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = equityColor
                    )
                    Text(
                        text = "Net Equity",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)

            // 2. Nested Sub-Row 1: Estimated Property Value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { haptics.tick(); onEditAccount(unit.anchorAccount) }
                    .padding(start = 26.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Estimated Property Value",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Manual valuation • Tap to update",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = MoneyUtils.formatCents(unit.estimatedValueCents),
                        style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Property Value",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 3. Nested Sub-Rows 2..N: Linked Mortgages / HELOCs
            if (unit.linkedLoans.isEmpty()) {
                HorizontalDivider(thickness = 1.dp, color = colors.cardBorder.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "No active mortgage linked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "$0.00 owed",
                        style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                unit.linkedLoans.forEach { loan ->
                    HorizontalDivider(thickness = 1.dp, color = colors.cardBorder.copy(alpha = 0.7f))
                    LinkedPropertyLoanSubRow(
                        loan = loan,
                        lenderLabel = RealEstatePropertyEngine.loanDisplayLabel(loan, unit),
                        isInspected = selectedAccountId == loan.id,
                        hasClosureAlert = loan.id in closureCandidateIds,
                        onInspect = { onInspectAccount(loan) },
                        onReviewClosure = { onReviewClosure(loan) },
                        onSeeTransactions = { onSeeTransactions(loan) },
                        onEdit = { onEditAccount(loan) },
                        onFindLogo = { onFindLogo(loan) },
                        onReportLogoStatus = onReportLogoStatus,
                        onHistory = { onHistoryClick(loan) },
                        onMerge = { onMergeClick(loan) },
                        onToggleArchive = { onToggleArchive(loan) },
                        onDelete = { onDeleteAccount(loan) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkedPropertyLoanSubRow(
    loan: Account,
    lenderLabel: String,
    isInspected: Boolean,
    hasClosureAlert: Boolean,
    onInspect: () -> Unit,
    onReviewClosure: () -> Unit,
    onSeeTransactions: () -> Unit,
    onEdit: () -> Unit,
    onFindLogo: () -> Unit,
    onReportLogoStatus: (String, String) -> Unit,
    onHistory: () -> Unit,
    onMerge: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }
    val rowBg = if (isInspected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)

    Surface(color = rowBg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { haptics.tick(); onInspect() }
                .padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountLeadingVisual(account = loan, onReportLogoStatus = onReportLogoStatus)
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lenderLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AccountBadge(text = loan.type.displayName)
                    loan.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { last4 ->
                        Text(
                            text = "•••• $last4",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                    if (hasClosureAlert) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .clickable { haptics.tick(); onReviewClosure() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Paid Off / Closed?",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = MoneyUtils.formatCents(loan.currentBalanceCents),
                style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = if (loan.currentBalanceCents != 0L) colors.expense else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))

            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(30.dp)
                        .clickable { haptics.tick(); menuExpanded = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = OrbitIcons.MoreHoriz,
                            contentDescription = "Loan Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Account Details") }, onClick = { menuExpanded = false; onInspect() })
                    if (hasClosureAlert) {
                        DropdownMenuItem(text = { Text("Review Payoff / Closure") }, onClick = { menuExpanded = false; onReviewClosure() })
                    }
                    DropdownMenuItem(text = { Text("See Transactions") }, onClick = { menuExpanded = false; onSeeTransactions() })
                    DropdownMenuItem(text = { Text("Edit Loan") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Find Bank Logo") }, onClick = { menuExpanded = false; onFindLogo() })
                    DropdownMenuItem(text = { Text("Balance History") }, onClick = { menuExpanded = false; onHistory() })
                    DropdownMenuItem(text = { Text("Merge Account") }, onClick = { menuExpanded = false; onMerge() })
                    DropdownMenuItem(
                        text = { Text(if (loan.isArchived) "Unarchive" else "Archive") },
                        onClick = { menuExpanded = false; onToggleArchive() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = colors.danger) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}
