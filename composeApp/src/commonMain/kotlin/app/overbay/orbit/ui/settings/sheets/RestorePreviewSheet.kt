package app.overbay.orbit.ui.settings.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.BackupPreviewStats
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitStatTile
import app.overbay.orbit.ui.settings.components.SettingsCalloutBanner

/**
 * Modal bottom sheet inspecting backup snapshot counts before replacing workspace data.
 */
@Composable
fun RestorePreviewSheet(
    stats: BackupPreviewStats,
    isRestoring: Boolean = false,
    errorMessage: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        confirmText = "Restore Workspace",
        onConfirm = onConfirm,
        confirmEnabled = !isRestoring,
        isSaving = isRestoring,
        errorBannerText = errorMessage,
        modifier = modifier,
        title = "Workspace Restore Preview",
        subtitle = "Review the contents of this backup before replacing local workspace data.",
        bodySpacing = 14.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewStatTile("Accounts", stats.accountCount, Modifier.weight(1f))
                PreviewStatTile("Transactions", stats.transactionCount, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewStatTile("Categories", stats.categoryCount, Modifier.weight(1f))
                PreviewStatTile("Budgets", stats.budgetCount, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewStatTile("Goals", stats.goalCount, Modifier.weight(1f))
                PreviewStatTile("Rules", stats.ruleCount, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewStatTile("Upcoming", stats.upcomingCount, Modifier.weight(1f))
                PreviewStatTile("Members", stats.memberCount, Modifier.weight(1f))
            }
        }

        SettingsCalloutBanner(
            icon = OrbitIcons.InfoCircle,
            text = "SimpleFIN bank connection credentials are not included in backups and must be reconnected after restoring."
        )

        if (stats.exportedAt.isNotBlank()) {
            Text(
                text = "Backup Created: ${stats.exportedAt}",
                style = TabularNumStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreviewStatTile(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    OrbitStatTile(
        label = label,
        value = count.toString(),
        valueColor = MaterialTheme.colorScheme.primary,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    )
}
