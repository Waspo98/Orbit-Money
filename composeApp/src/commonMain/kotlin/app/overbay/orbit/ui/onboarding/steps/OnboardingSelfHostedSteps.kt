package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.login.components.ConfirmedServerPill
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingErrorBanner
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOidcAuthLauncher
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PlatformRuntime

private const val ORBIT_GITHUB_URL = "https://github.com/Waspo98/Orbit-Money"
private const val DOCKER_QUICK_START =
    "docker run -d --name orbit-money -p 5008:5008 \\\n" +
    "  -v orbit-money-data:/app/data \\\n" +
    "  -e ADMIN_PASSWORD=change-me \\\n" +
    "  ghcr.io/waspo98/orbit-money:latest"

@Composable
fun OnboardingServerUrlStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var copiedSnippet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingHeroHeader(
            title = "Connect Your Orbit Server",
            subtitle = "Enter the address of your self-hosted Orbit Money instance.",
            icon = OrbitIcons.CloudSync
        )

        Spacer(modifier = Modifier.height(20.dp))

        OnboardingCardContainer {
            OrbitTextField(
                value = state.serverInput,
                onValueChange = { onIntent(OnboardingIntent.UpdateServerInput(it)) },
                label = { Text("Server Address") },
                placeholder = { Text("https://orbit.yourdomain.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    haptics.confirm()
                    onIntent(OnboardingIntent.TestAndConnectServer)
                }),
                modifier = Modifier.fillMaxWidth()
            )

            OnboardingErrorBanner(message = state.serverError)

            Button(
                onClick = {
                    haptics.confirm()
                    onIntent(OnboardingIntent.TestAndConnectServer)
                },
                enabled = !state.isTestingServer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (state.isTestingServer) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (state.isTestingServer) "Connecting..." else "Connect to Server",
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.tick()
                        onIntent(OnboardingIntent.ToggleDockerHelp(!state.dockerHelpExpanded))
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have a server running yet?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (state.dockerHelpExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OrbitAccordionVisibility(visible = state.dockerHelpExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Spin up Orbit Money anywhere Docker runs in under a minute. It stores everything in a local SQLite volume on your machine:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = DOCKER_QUICK_START,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptics.confirm()
                                clipboard.setText(AnnotatedString(DOCKER_QUICK_START))
                                copiedSnippet = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (copiedSnippet) "Copied!" else "Copy Command", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                uriHandler.openUri(ORBIT_GITHUB_URL)
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("GitHub Guide", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingServerAuthStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val oidcLauncher = LocalOidcAuthLauncher.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingHeroHeader(
            title = "Sign In to Your Server",
            subtitle = "If your server is already set up, we'll bring everything straight over.",
            icon = OrbitIcons.Accounts
        )

        Spacer(modifier = Modifier.height(18.dp))

        OnboardingCardContainer {
            ConfirmedServerPill(
                serverUrl = state.serverUrl,
                onChangeClick = { onIntent(OnboardingIntent.Back) }
            )

            if (state.localAuthEnabled) {
                OrbitTextField(
                    value = state.username,
                    onValueChange = { onIntent(OnboardingIntent.UpdateUsername(it)) },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OrbitTextField(
                    value = state.password,
                    onValueChange = { onIntent(OnboardingIntent.UpdatePassword(it)) },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { haptics.tick(); onIntent(OnboardingIntent.TogglePasswordVisibility) }) {
                            Text(if (state.isPasswordVisible) "Hide" else "Show", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        haptics.confirm()
                        onIntent(OnboardingIntent.SubmitPasswordLogin)
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                OnboardingErrorBanner(message = state.authError)

                Button(
                    onClick = {
                        haptics.confirm()
                        onIntent(OnboardingIntent.SubmitPasswordLogin)
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
                    Text(if (state.isSubmittingAuth) "Signing In..." else "Sign In", fontWeight = FontWeight.Bold)
                }
            } else {
                OnboardingErrorBanner(message = state.authError)
            }

            if (state.oidcEnabled && state.localAuthEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text("OR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
            }

            if (state.oidcEnabled) {
                val launchOidc = {
                    haptics.confirm()
                    onIntent(OnboardingIntent.PrepareOidcLogin)
                    if (PlatformRuntime.isWeb || state.serverUrl.isNotBlank()) {
                        oidcLauncher.launchOidcLogin(state.serverUrl, state.oidcLoginUrl)
                    }
                }
                if (!state.localAuthEnabled) {
                    Button(
                        onClick = launchOidc,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(state.oidcLoginLabel, fontWeight = FontWeight.Bold)
                    }
                } else {
                    FilledTonalButton(
                        onClick = launchOidc,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(state.oidcLoginLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (state.sampleDataEnabled) {
                TextButton(
                    onClick = {
                        haptics.tick()
                        onIntent(OnboardingIntent.SubmitServerSampleData)
                    },
                    enabled = !state.isSampleLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (state.isSampleLoading) "Loading sample data..." else "Try sample data on this server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
