package app.overbay.orbit.ui.onboarding.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.SelectableListRow
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.SeedChoice
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Data seed and workspace initialization card in the Onboarding flow.
 * Allows choosing demo data, starting fresh, or restoring existing backups.
 */
@Composable
fun DataSeedImportCard(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    onPickBackupFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "Initial Data & Household",
        subtitle = "Choose how to initialize your workspace",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SelectableListRow(
                title = "🎯 Load Demo Household (Recommended)",
                subtitle = "Explore with 9 realistic accounts, budgets, recurring bills, and transactions.",
                selected = state.selectedSeedChoice == SeedChoice.DEMO,
                onClick = {
                    haptics.tick()
                    onIntent(OnboardingIntent.SelectSeedChoice(SeedChoice.DEMO))
                }
            )

            SelectableListRow(
                title = "⚡ Start Fresh with Empty Accounts",
                subtitle = "Initialize clean accounts and default categories. Ready for manual entry or CSV.",
                selected = state.selectedSeedChoice == SeedChoice.FRESH,
                onClick = {
                    haptics.tick()
                    onIntent(OnboardingIntent.SelectSeedChoice(SeedChoice.FRESH))
                }
            )

            SelectableListRow(
                title = "💾 Restore Backup File",
                subtitle = "Restore all your data from an Orbit Money JSON backup export.",
                selected = state.selectedSeedChoice == SeedChoice.RESTORE_FILE,
                onClick = {
                    haptics.tick()
                    onIntent(OnboardingIntent.SelectSeedChoice(SeedChoice.RESTORE_FILE))
                }
            )

            SelectableListRow(
                title = "🛡️ Google Drive Cloud Backup",
                subtitle = "📲 Seamless device restore in Google Drive's hidden appDataFolder sandbox.",
                selected = state.selectedSeedChoice == SeedChoice.GOOGLE_DRIVE,
                onClick = {
                    haptics.tick()
                    onIntent(OnboardingIntent.SelectSeedChoice(SeedChoice.GOOGLE_DRIVE))
                }
            )

            // Expandable restore file section
            AnimatedVisibility(
                visible = state.selectedSeedChoice == SeedChoice.RESTORE_FILE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onPickBackupFile != null) {
                        Button(
                            onClick = {
                                haptics.tick()
                                onPickBackupFile()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Choose Backup File")
                        }
                    }

                    if (state.backupFileName != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = colors.incomeContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.onIncomeContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = state.backupFileName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onIncomeContainer
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = state.backupJsonContent ?: "",
                        onValueChange = {
                            onIntent(OnboardingIntent.SetBackupFile("Pasted JSON", it))
                        },
                        label = { Text("Or Paste Backup JSON") },
                        placeholder = { Text("{\"schemaVersion\": 1, ...}") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}
