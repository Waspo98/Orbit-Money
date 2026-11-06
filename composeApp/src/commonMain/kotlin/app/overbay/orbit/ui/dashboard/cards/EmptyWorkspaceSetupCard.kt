package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.SelectableListRow
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Friendly first-time guided setup card rendered when accounts and transactions are both empty.
 */
@Composable
fun EmptyWorkspaceSetupCard(
    onImportCsv: () -> Unit,
    onConnectSimpleFin: () -> Unit,
    onAddManualAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "🚀", fontSize = 28.sp)
                Column {
                    Text(
                        text = "Welcome to Orbit Money",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Let's set up your household finances.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Choose how you'd like to get started:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // CTA 1: Import Rocket Money CSV
                SelectableListRow(
                    title = "Import Rocket Money CSV",
                    subtitle = "Fast migration of accounts, transactions, and categories.",
                    isSelected = true,
                    onClick = {
                        haptics.confirm()
                        onImportCsv()
                    },
                    leadingContent = { Text(text = "📄", fontSize = 24.sp) }
                )

                // CTA 2: Connect SimpleFIN Bank Sync
                SelectableListRow(
                    title = "Connect SimpleFIN Bank Sync",
                    subtitle = "Automatic, read-only sync with over 10,000 financial institutions.",
                    isSelected = false,
                    onClick = {
                        haptics.confirm()
                        onConnectSimpleFin()
                    },
                    leadingContent = { Text(text = "🏦", fontSize = 24.sp) }
                )

                // CTA 3: Add Manual Account
                SelectableListRow(
                    title = "Add Manual Account",
                    subtitle = "Start fresh with custom checking, savings, or investment accounts.",
                    isSelected = false,
                    onClick = {
                        haptics.confirm()
                        onAddManualAccount()
                    },
                    leadingContent = { Text(text = "✏️", fontSize = 24.sp) }
                )
            }
        }
    }
}
