package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.SelectableListRow
import app.overbay.orbit.ui.login.LocalSeedChoice
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingErrorBanner
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalFileImportExportHelper
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun OnboardingLocalWorkspaceStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val fileHelper = LocalFileImportExportHelper.current
    val colors = OrbitTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingHeroHeader(
            title = "Set Up Your Local Workspace",
            subtitle = "Start clean, try sample data, or restore an existing Orbit backup.",
            icon = OrbitIcons.BackupArchive
        )

        Spacer(modifier = Modifier.height(20.dp))

        OnboardingCardContainer {
            SelectableListRow(
                title = "Start Fresh",
                subtitle = "Clean workspace with standard categories ready to go",
                subtitleMaxLines = 3,
                selected = state.selectedLocalSeed == LocalSeedChoice.FRESH,
                onClick = { onIntent(OnboardingIntent.SelectLocalSeed(LocalSeedChoice.FRESH)) }
            )

            SelectableListRow(
                title = "Explore Sample Data",
                subtitle = "Try out Orbit with realistic accounts, budgets, and transactions",
                subtitleMaxLines = 3,
                selected = state.selectedLocalSeed == LocalSeedChoice.DEMO,
                onClick = { onIntent(OnboardingIntent.SelectLocalSeed(LocalSeedChoice.DEMO)) }
            )

            SelectableListRow(
                title = "Restore Orbit Backup JSON",
                subtitle = "Bring over your accounts and history from another device",
                subtitleMaxLines = 3,
                selected = state.selectedLocalSeed == LocalSeedChoice.RESTORE_FILE,
                onClick = { onIntent(OnboardingIntent.SelectLocalSeed(LocalSeedChoice.RESTORE_FILE)) }
            )

            OrbitAccordionVisibility(visible = state.selectedLocalSeed == LocalSeedChoice.RESTORE_FILE) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.tick()
                            fileHelper.pickAndImportFile("application/json") { content ->
                                onIntent(OnboardingIntent.SetBackupFile("orbit-backup.orbit.json", content))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose Backup File")
                    }

                    OrbitAccordionVisibility(visible = state.backupFileName != null) {
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
                                    text = state.backupFileName.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onIncomeContainer
                                )
                            }
                        }
                    }

                    OrbitTextField(
                        value = state.backupJsonContent.orEmpty(),
                        onValueChange = { onIntent(OnboardingIntent.SetBackupFile("Pasted JSON", it)) },
                        label = { Text("Or Paste Backup JSON") },
                        placeholder = { Text("Paste exported Orbit backup text...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            OnboardingErrorBanner(message = state.authError)

            Button(
                onClick = {
                    haptics.confirm()
                    onIntent(OnboardingIntent.ConfirmLocalWorkspace)
                },
                enabled = !state.isSubmittingAuth,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (state.isSubmittingAuth) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                val label = when (state.selectedLocalSeed) {
                    LocalSeedChoice.FRESH -> "Continue"
                    LocalSeedChoice.DEMO -> "Load Sample Data"
                    LocalSeedChoice.RESTORE_FILE -> "Restore Backup"
                }
                Text(text = if (state.isSubmittingAuth) "Setting Up..." else label, fontWeight = FontWeight.Bold)
            }
        }
    }
}
