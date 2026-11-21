package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.ui.components.OrbitBadge
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
    onRunSetupGuide: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
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
                if (onRunSetupGuide != null) {
                    SelectableListRow(
                        title = "Run Guided Setup",
                        subtitle = "Walk through syncing, CSV import, accounts, household, and tools.",
                        isSelected = false,
                        onClick = {
                            haptics.confirm()
                            onRunSetupGuide()
                        },
                        leadingContent = { Text(text = "✨", fontSize = 24.sp) },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OrbitBadge(
                                    text = "Guided",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }

                SelectableListRow(
                    title = "Import Transactions CSV",
                    subtitle = "Rocket Money, Mint, Monarch, YNAB, or any bank CSV export.",
                    isSelected = false,
                    onClick = {
                        haptics.confirm()
                        onImportCsv()
                    },
                    leadingContent = { Text(text = "📄", fontSize = 24.sp) },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OrbitBadge(
                                text = "Recommended",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                SelectableListRow(
                    title = "Connect SimpleFIN Bank Sync",
                    subtitle = "Automatic, read-only sync with over 10,000 financial institutions.",
                    isSelected = false,
                    onClick = {
                        haptics.confirm()
                        onConnectSimpleFin()
                    },
                    leadingContent = { Text(text = "🏦", fontSize = 24.sp) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                SelectableListRow(
                    title = "Add Manual Account",
                    subtitle = "Start fresh with custom checking, savings, or investment accounts.",
                    isSelected = false,
                    onClick = {
                        haptics.confirm()
                        onAddManualAccount()
                    },
                    leadingContent = { Text(text = "✏️", fontSize = 24.sp) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}
