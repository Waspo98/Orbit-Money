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
                        text = "Your household financial orbit is ready to launch.",
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
                SetupCtaTile(
                    emoji = "📄",
                    title = "Import Rocket Money CSV",
                    description = "Fast migration of accounts, transactions, and categories.",
                    isPrimary = true,
                    onClick = {
                        haptics.confirm()
                        onImportCsv()
                    }
                )

                // CTA 2: Connect SimpleFIN Bank Sync
                SetupCtaTile(
                    emoji = "🏦",
                    title = "Connect SimpleFIN Bank Sync",
                    description = "Automatic, read-only sync with over 10,000 financial institutions.",
                    isPrimary = false,
                    onClick = {
                        haptics.confirm()
                        onConnectSimpleFin()
                    }
                )

                // CTA 3: Add Manual Account
                SetupCtaTile(
                    emoji = "✏️",
                    title = "Add Manual Account",
                    description = "Start fresh with custom checking, savings, or investment accounts.",
                    isPrimary = false,
                    onClick = {
                        haptics.confirm()
                        onAddManualAccount()
                    }
                )
            }
        }
    }
}

@Composable
private fun SetupCtaTile(
    emoji: String,
    title: String,
    description: String,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isPrimary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else OrbitTheme.colors.cardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
