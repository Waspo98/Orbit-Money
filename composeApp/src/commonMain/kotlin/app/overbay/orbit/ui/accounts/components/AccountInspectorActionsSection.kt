package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial actions section for Account Detail view.
 * Encapsulates action pills (Edit, Perks, Balance History, Snapshot Reminder,
 * Merge, Transactions, Archive, Delete) and SimpleFIN sync link status.
 * Strictly under the 390-line ceiling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountInspectorActionsSection(
    account: Account,
    onEdit: () -> Unit,
    onPerks: () -> Unit,
    onHistory: () -> Unit,
    onSnapshotReminder: () -> Unit,
    onMerge: () -> Unit,
    onSeeTransactions: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onUnlinkSimpleFin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isCredit = account.type == AccountType.CREDIT
    var showUnlinkConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // SimpleFIN link status banner if linked
        if (!account.simplefinAccountId.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = OrbitColorTokens.Green,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Linked to SimpleFIN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Automatically syncing balances & activity",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    if (onUnlinkSimpleFin != null) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                showUnlinkConfirm = true
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "Unlink",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Action pills row
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccountPillButton(
                label = "Edit Account",
                onClick = onEdit
            )
            if (isCredit) {
                AccountPillButton(
                    label = "Card Perks",
                    onClick = onPerks
                )
            }
            AccountPillButton(
                label = "Balance History",
                onClick = onHistory
            )
            AccountPillButton(
                label = "Snapshot Reminder",
                onClick = onSnapshotReminder
            )
            AccountPillButton(
                label = "Merge",
                onClick = onMerge
            )
            AccountPillButton(
                label = "See Transactions",
                onClick = onSeeTransactions
            )
            AccountPillButton(
                label = if (account.isArchived) "Unarchive" else "Archive",
                onClick = onToggleArchive
            )
            AccountPillButton(
                label = "Delete",
                isDestructive = true,
                onClick = onDelete
            )
        }
    }

    if (showUnlinkConfirm && onUnlinkSimpleFin != null) {
        OrbitConfirmationDialog(
            title = "Unlink SimpleFIN?",
            message = "Unlinking \"${account.name}\" from SimpleFIN will convert it to a manual account. Existing transactions and balance history will be preserved.",
            confirmText = "Unlink",
            isDestructive = false,
            onConfirm = {
                showUnlinkConfirm = false
                onUnlinkSimpleFin()
            },
            onDismiss = { showUnlinkConfirm = false }
        )
    }
}

@Composable
private fun AccountPillButton(
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    OutlinedButton(
        onClick = {
            haptics.tick()
            onClick()
        },
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        border = BorderStroke(
            1.dp,
            if (isDestructive) OrbitTheme.colors.danger else OrbitTheme.colors.cardBorder
        ),
        modifier = modifier.height(34.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) OrbitTheme.colors.danger else MaterialTheme.colorScheme.onSurface
        )
    }
}
