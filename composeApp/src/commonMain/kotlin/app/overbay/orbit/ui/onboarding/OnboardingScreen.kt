package app.overbay.orbit.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.components.orbitTopSystemBarPadding
import app.overbay.orbit.ui.onboarding.components.OnboardingStepDots
import app.overbay.orbit.ui.onboarding.steps.OnboardingAdditionalAccountsStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingAllSetStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingAppearanceStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingDataConnectStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingFeaturesStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingHouseholdStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingLocalWorkspaceStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingPersonalizeForkStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingRetirementStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingServerAuthStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingServerUrlStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingStorageModeStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingTaxDeductionsStep
import app.overbay.orbit.ui.onboarding.steps.OnboardingWelcomeStep
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics
import orbitmoney.composeapp.generated.resources.Res
import orbitmoney.composeapp.generated.resources.orbit_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit,
    posture: DevicePosture = LocalDevicePosture.current,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(
        uiState = state,
        onIntent = viewModel::processIntent,
        posture = posture,
        onFinish = onFinish,
        modifier = modifier
    )
}

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    posture: DevicePosture,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onIntent(OnboardingIntent.ClearCompletedFlag)
            onFinish()
        }
    }

    BackHandler(enabled = uiState.canGoBack) {
        haptics.tick()
        onIntent(OnboardingIntent.Back)
    }

    val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val secondaryGlow = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f)
    val bgColor = MaterialTheme.colorScheme.background
    val steps = uiState.resolvedSteps
    val isLastStep = uiState.currentStep == OnboardingStep.ALL_SET
    val showNextArrow = !isLastStep && !OnboardingStepResolver.isGateStep(uiState.currentStep)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                val r1 = size.maxDimension * 0.55f
                val r2 = size.maxDimension * 0.65f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.10f),
                        radius = r1
                    ),
                    radius = r1,
                    center = Offset(size.width * 0.18f, size.height * 0.10f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.84f, size.height * 0.90f),
                        radius = r2
                    ),
                    radius = r2,
                    center = Offset(size.width * 0.84f, size.height * 0.90f)
                )
            }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .orbitTopSystemBarPadding()
                        .height(56.dp)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.orbit_logo),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "Orbit Money",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    TextButton(
                        onClick = {
                            haptics.tick()
                            onIntent(OnboardingIntent.SkipToDashboard)
                        },
                        enabled = uiState.showTopSkip,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.alpha(if (uiState.showTopSkip) 1f else 0f)
                    ) {
                        Text(
                            text = if (uiState.isReplay) "Close" else "Skip Setup",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.canGoBack) {
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    onIntent(OnboardingIntent.Back)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }

                        OnboardingStepDots(
                            stepCount = steps.size,
                            currentIndex = uiState.currentStepIndex
                        )

                        if (showNextArrow) {
                            FilledTonalIconButton(
                                onClick = {
                                    haptics.confirm()
                                    onIntent(OnboardingIntent.Next)
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize().imePadding()
        ) { innerPadding ->
            val maxContentWidth = if (uiState.currentStep == OnboardingStep.APPEARANCE && posture.supportsTwoPane) {
                900.dp
            } else {
                560.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = uiState.currentStep,
                    transitionSpec = {
                        val dir = if (uiState.transitionDirection >= 0) 1 else -1
                        (slideInHorizontally(OrbitSprings.IntOffsetSpatialDefault) { dir * it / 5 } +
                            fadeIn(OrbitSprings.FloatEffectsDefault)) togetherWith
                            (slideOutHorizontally(OrbitSprings.IntOffsetSpatialDefault) { -dir * it / 5 } +
                                fadeOut(OrbitSprings.FloatEffectsFast))
                    },
                    modifier = Modifier.fillMaxSize().widthIn(max = maxContentWidth),
                    label = "OnboardingStepTransition"
                ) { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> OnboardingWelcomeStep(uiState, onIntent)
                        OnboardingStep.APPEARANCE -> OnboardingAppearanceStep(uiState, posture, onIntent)
                        OnboardingStep.STORAGE_MODE -> OnboardingStorageModeStep(uiState, onIntent)
                        OnboardingStep.LOCAL_WORKSPACE -> OnboardingLocalWorkspaceStep(uiState, onIntent)
                        OnboardingStep.SERVER_URL -> OnboardingServerUrlStep(uiState, onIntent)
                        OnboardingStep.SERVER_AUTH -> OnboardingServerAuthStep(uiState, onIntent)
                        OnboardingStep.DATA_CONNECT -> OnboardingDataConnectStep(uiState, onIntent)
                        OnboardingStep.PERSONALIZE_FORK -> OnboardingPersonalizeForkStep(uiState, onIntent)
                        OnboardingStep.FEATURES -> OnboardingFeaturesStep(uiState, onIntent)
                        OnboardingStep.ADDITIONAL_ACCOUNTS -> OnboardingAdditionalAccountsStep(uiState, onIntent)
                        OnboardingStep.HOUSEHOLD -> OnboardingHouseholdStep(uiState, onIntent)
                        OnboardingStep.RETIREMENT -> OnboardingRetirementStep(uiState, onIntent)
                        OnboardingStep.TAX_DEDUCTIONS -> OnboardingTaxDeductionsStep(uiState, onIntent)
                        OnboardingStep.ALL_SET -> OnboardingAllSetStep(uiState, onIntent)
                    }
                }
            }
        }
    }
}
