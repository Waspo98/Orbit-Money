package app.overbay.orbit.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.login.components.ConfirmedServerPill
import app.overbay.orbit.ui.login.components.LoginBrandLockup
import app.overbay.orbit.ui.login.components.LoginErrorBanner
import app.overbay.orbit.ui.login.components.LoginLocalSetupStep
import app.overbay.orbit.ui.login.components.LoginModeSelectionStep
import app.overbay.orbit.ui.login.components.LoginServerUrlStep
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalOidcAuthLauncher
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PlatformRuntime

/**
 * Centered morphing Onboarding & Login Screen with M3E physics springs.
 * Progressively morphs from Mode Selection (Local vs Self-Hosted) ->
 * Server URL confirmation (`https://` prefilled) -> Credentials / OIDC,
 * or Local Workspace initialization.
 * Root Login screen composable.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onUseOfflineStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flowState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = viewModel.currentUiState.let { snap ->
        flowState.copy(
            username = snap.username,
            password = snap.password,
            customServerInput = snap.customServerInput,
            backupJsonContent = snap.backupJsonContent
        )
    }

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

    BackHandler(enabled = state.step != LoginStep.MODE_SELECT) {
        when (state.step) {
            LoginStep.LOCAL_SETUP -> onIntent(LoginIntent.BackToModeSelect)
            LoginStep.SERVER_URL -> {
                if (state.serverUrl.isNotBlank()) {
                    onIntent(LoginIntent.SetShowServerConfig(false))
                } else {
                    onIntent(LoginIntent.BackToModeSelect)
                }
            }
            LoginStep.SERVER_AUTH -> onIntent(LoginIntent.SetShowServerConfig(true))
            LoginStep.MODE_SELECT -> {}
        }
    }

    val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val secondaryGlow = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
    val bgColor = MaterialTheme.colorScheme.background

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
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                LoginBrandLockup(
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = {
                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(OrbitSprings.IntOffsetSpatialDefault) { dir * it / 8 } +
                            fadeIn(OrbitSprings.FloatEffectsDefault))
                            .togetherWith(
                                slideOutHorizontally(OrbitSprings.IntOffsetSpatialDefault) { -dir * it / 8 } +
                                    fadeOut(OrbitSprings.FloatEffectsFast)
                            )
                            .using(
                                SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ -> OrbitSprings.IntSizeSpatialDefault }
                                )
                            )
                    },
                    label = "LoginStepMorph"
                ) { step ->
                    when (step) {
                        LoginStep.MODE_SELECT -> LoginModeSelectionStep(onIntent = onIntent)
                        LoginStep.LOCAL_SETUP -> LoginLocalSetupStep(state = state, onIntent = onIntent)
                        LoginStep.SERVER_URL -> LoginServerUrlStep(state = state, onIntent = onIntent)
                        LoginStep.SERVER_AUTH -> LoginServerAuthStep(state = state, onIntent = onIntent)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginServerAuthStep(
    state: LoginUiState,
    onIntent: (LoginIntent) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val oidcLauncher = LocalOidcAuthLauncher.current

    Column(modifier = Modifier.fillMaxWidth()) {
        ConfirmedServerPill(
            serverUrl = state.serverUrl,
            onChangeClick = { onIntent(LoginIntent.SetShowServerConfig(true)) }
        )

        if (state.localEnabled) {
            OrbitTextField(
                value = state.username,
                onValueChange = { onIntent(LoginIntent.UpdateUsername(it)) },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OrbitTextField(
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
                onIntent(LoginIntent.SelectLocalMode)
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
