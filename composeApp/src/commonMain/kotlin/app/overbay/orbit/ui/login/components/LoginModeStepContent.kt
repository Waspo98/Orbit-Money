package app.overbay.orbit.ui.login.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitHorizontalReveal
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.SelectableListRow
import app.overbay.orbit.ui.login.LocalSeedChoice
import app.overbay.orbit.ui.login.LoginIntent
import app.overbay.orbit.ui.login.LoginUiState
import app.overbay.orbit.util.LocalFileImportExportHelper
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun LoginModeSelectionStep(
    onIntent: (LoginIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Choose how you want to use Orbit Money",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        SelectableListRow(
            title = "Local",
            subtitle = "Private offline storage right on this device",
            subtitleMaxLines = 3,
            leadingIcon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = OrbitIcons.Dashboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            sidePrimary = "→",
            onClick = { onIntent(LoginIntent.SelectLocalMode) }
        )

        SelectableListRow(
            title = "Self-Hosted",
            subtitle = "Connect to your Orbit Money server",
            subtitleMaxLines = 3,
            leadingIcon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = OrbitIcons.Accounts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            sidePrimary = "→",
            onClick = { onIntent(LoginIntent.SelectSelfHostedMode) }
        )
    }
}

@Composable
fun LoginLocalSetupStep(
    state: LoginUiState,
    onIntent: (LoginIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val fileHelper = LocalFileImportExportHelper.current
    val colors = OrbitTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Choose how to initialize your local workspace",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        SelectableListRow(
            title = "Start Fresh",
            subtitle = "Clean workspace with standard categories",
            subtitleMaxLines = 3,
            selected = state.selectedLocalSeed == LocalSeedChoice.FRESH,
            onClick = { onIntent(LoginIntent.SelectLocalSeed(LocalSeedChoice.FRESH)) }
        )

        SelectableListRow(
            title = "Sample Data",
            subtitle = "Explore with realistic accounts and transactions",
            subtitleMaxLines = 3,
            selected = state.selectedLocalSeed == LocalSeedChoice.DEMO,
            onClick = { onIntent(LoginIntent.SelectLocalSeed(LocalSeedChoice.DEMO)) }
        )

        SelectableListRow(
            title = "Restore Backup JSON",
            subtitle = "Import an existing Orbit Money backup export",
            subtitleMaxLines = 3,
            selected = state.selectedLocalSeed == LocalSeedChoice.RESTORE_FILE,
            onClick = { onIntent(LoginIntent.SelectLocalSeed(LocalSeedChoice.RESTORE_FILE)) }
        )

        OrbitAccordionVisibility(
            visible = state.selectedLocalSeed == LocalSeedChoice.RESTORE_FILE
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        fileHelper.pickAndImportFile("application/json") { content ->
                            onIntent(LoginIntent.SetBackupFile("orbit-backup.orbit.json", content))
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choose Backup File")
                }

                val lastBackupFileName = remember { mutableStateOf("") }
                if (state.backupFileName != null) {
                    lastBackupFileName.value = state.backupFileName
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
                                text = lastBackupFileName.value,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.onIncomeContainer
                            )
                        }
                    }
                }

                OrbitTextField(
                    value = state.backupJsonContent.orEmpty(),
                    onValueChange = { onIntent(LoginIntent.SetBackupFile("Pasted JSON", it)) },
                    label = { Text("Or Paste Backup Data") },
                    placeholder = { Text("Paste exported backup text here...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        LoginErrorBanner(errorMessage = state.errorMessage)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptics.tick()
                    onIntent(LoginIntent.BackToModeSelect)
                },
                enabled = !state.isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Back")
            }

            Button(
                onClick = {
                    haptics.confirm()
                    onIntent(LoginIntent.ConfirmLocalSetup)
                },
                enabled = !state.isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                OrbitHorizontalReveal(visible = state.isSubmitting, expandFrom = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
                Text(
                    text = if (state.isSubmitting) "Starting..." else "Continue",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
