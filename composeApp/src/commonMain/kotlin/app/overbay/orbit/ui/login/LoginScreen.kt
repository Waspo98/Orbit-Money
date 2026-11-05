package app.overbay.orbit.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.ui.login.components.LoginBrandLockup
import app.overbay.orbit.ui.login.components.LoginErrorBanner
import app.overbay.orbit.ui.login.components.LoginServerConfigBox
import app.overbay.orbit.util.LocalOidcAuthLauncher
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PlatformRuntime

/**
 * Immich-inspired centered Login Screen with OIDC / OAuth, Local Password,
 * Server Endpoint Switcher, Sample Data, and Offline Local Storage fallback.
 * Conforms strictly to the <= 6 parameter rule and < 400-line ceiling.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onUseOfflineStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LoginScreen(
        state = state,
        onIntent = viewModel::processIntent,
        onLoginSuccess = onLoginSuccess,
        onUseOfflineStorage = onUseOfflineStorage,
        modifier = modifier
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onIntent: (LoginIntent) -> Unit,
    onLoginSuccess: () -> Unit,
    onUseOfflineStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val oidcLauncher = LocalOidcAuthLauncher.current

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            onIntent(LoginIntent.ClearNavigationFlags)
            onLoginSuccess()
        }
    }

    LaunchedEffect(state.switchedToLocalMode) {
        if (state.switchedToLocalMode) {
            onIntent(LoginIntent.ClearNavigationFlags)
            onUseOfflineStorage()
        }
    }

    val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val secondaryGlow = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
    val bgColor = MaterialTheme.colorScheme.background

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                val radius1 = size.maxDimension * 0.55f
                val radius2 = size.maxDimension * 0.65f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.12f),
                        radius = radius1
                    ),
                    radius = radius1,
                    center = Offset(size.width * 0.2f, size.height * 0.12f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.88f),
                        radius = radius2
                    ),
                    radius = radius2,
                    center = Offset(size.width * 0.82f, size.height * 0.88f)
                )
            }
            .padding(top = statusBarTop, bottom = navBarBottom)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 36.dp)
            ) {
                LoginBrandLockup(
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                LoginServerConfigBox(
                    state = state,
                    onIntent = onIntent
                )

                if (state.localEnabled) {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { onIntent(LoginIntent.UpdateUsername(it)) },
                        label = { Text("Username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { onIntent(LoginIntent.UpdatePassword(it)) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (state.isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    haptics.tick()
                                    onIntent(LoginIntent.TogglePasswordVisibility)
                                }
                            ) {
                                Text(
                                    text = if (state.isPasswordVisible) "Hide" else "Show",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                haptics.confirm()
                                onIntent(LoginIntent.SubmitPasswordLogin)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LoginErrorBanner(errorMessage = state.errorMessage)

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            haptics.confirm()
                            onIntent(LoginIntent.SubmitPasswordLogin)
                        },
                        enabled = !state.isSubmitting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (state.isSubmitting) "Signing In..." else "Sign In",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    LoginErrorBanner(errorMessage = state.errorMessage)
                }

                if (state.oidcEnabled && state.localEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                }

                if (state.oidcEnabled) {
                    val onOidcClick = {
                        haptics.confirm()
                        onIntent(LoginIntent.PrepareOidcLogin)
                        if (PlatformRuntime.isWeb || state.serverUrl.isNotBlank()) {
                            oidcLauncher.launchOidcLogin(state.serverUrl, state.oidcLoginUrl)
                        }
                    }

                    if (!state.localEnabled) {
                        Button(
                            onClick = onOidcClick,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = state.oidcLoginLabel,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onOidcClick,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = state.oidcLoginLabel,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                if (state.sampleDataEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            haptics.tick()
                            onIntent(LoginIntent.SubmitSampleDataLogin)
                        },
                        enabled = !state.isSampleLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (state.isSampleLoading) "Loading sample data..." else "Continue with sample data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        haptics.tick()
                        onIntent(LoginIntent.UseOfflineLocalStorage)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Use Offline Local Storage Instead",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
