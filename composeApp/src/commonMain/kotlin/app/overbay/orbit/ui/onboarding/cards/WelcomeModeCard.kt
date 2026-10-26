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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.SelectableListRow
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.ServerAuthStatus
import app.overbay.orbit.ui.onboarding.ServerConnectionStatus
import app.overbay.orbit.util.LocalOidcAuthLauncher
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Storage mode selection card in the Onboarding flow.
 * Allows choosing between 100% private Local SQLite storage or Self-Hosted Docker server.
 */
@Composable
fun WelcomeModeCard(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val oidcLauncher = LocalOidcAuthLauncher.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "Storage & Architecture",
        subtitle = "Choose how Orbit Money stores your financial data",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Local Device Mode Option
            SelectableListRow(
                title = "Local Device Storage",
                subtitle = "100% private, offline-first SQLite on this device. Zero cloud dependency.",
                selected = state.selectedStorageMode == StorageMode.LOCAL,
                onClick = {
                    haptics.tick()
                    onIntent(OnboardingIntent.SelectStorageMode(StorageMode.LOCAL))
                }
            )

            // Self-Hosted Server Mode Option
            SelectableListRow(
                title = "Self-Hosted Orbit Server",
                subtitle = "Connect to your home Docker instance for multi-device sync and backups.",
                selected = state.selectedStorageMode == StorageMode.SELF_HOSTED,
                onClick = {
                    haptics.tick()
                    onIntent(OnboardingIntent.SelectStorageMode(StorageMode.SELF_HOSTED))
                }
            )

            // Expandable Self-Hosted Connection Form
            AnimatedVisibility(
                visible = state.selectedStorageMode == StorageMode.SELF_HOSTED,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = { onIntent(OnboardingIntent.UpdateServerUrl(it)) },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://orbit.my-domain.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                haptics.tick()
                                onIntent(OnboardingIntent.TestServerConnection)
                            },
                            enabled = state.serverConnectionStatus !is ServerConnectionStatus.Checking,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (state.serverConnectionStatus is ServerConnectionStatus.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Test Connection")
                        }

                        when (val conn = state.serverConnectionStatus) {
                            is ServerConnectionStatus.Success -> {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = colors.incomeContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                                            text = "Connected",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onIncomeContainer
                                        )
                                    }
                                }
                            }
                            is ServerConnectionStatus.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = colors.expenseContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = colors.onExpenseContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Connection Failed",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onExpenseContainer
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    if (state.serverConnectionStatus is ServerConnectionStatus.Error) {
                        Text(
                            text = (state.serverConnectionStatus as ServerConnectionStatus.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.expense,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    if (state.oidcAuthEnabled) {
                        Button(
                            onClick = {
                                haptics.confirm()
                                onIntent(OnboardingIntent.PrepareOidcLogin)
                                oidcLauncher.launchOidcLogin(state.serverUrl, state.oidcLoginUrl)
                            },
                            enabled = state.serverUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(state.oidcLoginLabel)
                        }
                    }

                    if (state.localAuthEnabled) {
                        OutlinedTextField(
                            value = state.serverUsername,
                            onValueChange = {
                                onIntent(OnboardingIntent.UpdateCredentials(it, state.serverPassword))
                            },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = state.serverPassword,
                            onValueChange = {
                                onIntent(OnboardingIntent.UpdateCredentials(state.serverUsername, it))
                            },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { onIntent(OnboardingIntent.TogglePasswordVisibility) }) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Toggle Password Visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                haptics.confirm()
                                onIntent(OnboardingIntent.AuthenticateServer)
                            },
                            enabled = state.serverUrl.isNotBlank() && state.serverUsername.isNotBlank() && state.serverPassword.isNotBlank() && state.authStatus !is ServerAuthStatus.Authenticating,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (state.authStatus is ServerAuthStatus.Authenticating) "Signing In..." else "Sign In with Password")
                        }
                    }

                    when (val auth = state.authStatus) {
                        is ServerAuthStatus.Success -> {
                            Text(
                                text = "Signed in as ${auth.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.income,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        is ServerAuthStatus.Error -> {
                            Text(
                                text = auth.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.expense,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
