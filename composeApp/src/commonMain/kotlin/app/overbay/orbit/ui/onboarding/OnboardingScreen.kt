package app.overbay.orbit.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.onboarding.cards.DataSeedImportCard
import app.overbay.orbit.ui.onboarding.cards.ThemeStyleCard
import app.overbay.orbit.ui.onboarding.cards.WelcomeModeCard
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * First-run onboarding walkthrough screen.
 * Conforms strictly to the <= 6 parameter rule and < 350-line ceiling.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onPickBackupFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    OnboardingScreen(
        state = state,
        onIntent = viewModel::processIntent,
        onComplete = onComplete,
        onPickBackupFile = onPickBackupFile,
        modifier = modifier
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    onComplete: () -> Unit,
    onPickBackupFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) {
            onComplete()
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = statusBarTop, bottom = navBarBottom)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .align(Alignment.Center)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Title and Skip Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Orbit Money",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (state.currentStep != OnboardingStep.THEME_SETUP) {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            onIntent(OnboardingIntent.SkipOnboarding)
                        }
                    ) {
                        Text(
                            text = "Skip Setup",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Animated Step Card Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = state.currentStep,
                    transitionSpec = {
                        val isForward = targetState.ordinal > initialState.ordinal
                        if (isForward) {
                            slideInHorizontally(
                                animationSpec = OrbitSprings.spatialDefault(),
                                initialOffsetX = { it }
                            ) + fadeIn() togetherWith slideOutHorizontally(
                                animationSpec = OrbitSprings.spatialDefault(),
                                targetOffsetX = { -it }
                            ) + fadeOut()
                        } else {
                            slideInHorizontally(
                                animationSpec = OrbitSprings.spatialDefault(),
                                initialOffsetX = { -it }
                            ) + fadeIn() togetherWith slideOutHorizontally(
                                animationSpec = OrbitSprings.spatialDefault(),
                                targetOffsetX = { it }
                            ) + fadeOut()
                        }
                    },
                    label = "OnboardingStepCrossfade"
                ) { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeIntroCard()
                        OnboardingStep.STORAGE_MODE -> WelcomeModeCard(state, onIntent)
                        OnboardingStep.DATA_SEED -> DataSeedImportCard(state, onIntent, onPickBackupFile)
                        OnboardingStep.THEME_SETUP -> ThemeStyleCard(state, onIntent)
                    }
                }
            }

            // Footer: Back Button, Dot Indicators, Next/Finish CTA
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back IconButton
                    if (state.currentStep != OnboardingStep.WELCOME) {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                onIntent(OnboardingIntent.PreviousStep)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    // Stepper Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OnboardingStep.entries.forEach { step ->
                            val isCurrent = state.currentStep == step
                            val dotWidth by animateDpAsState(
                                targetValue = if (isCurrent) 24.dp else 8.dp,
                                animationSpec = OrbitSprings.spatialFast(),
                                label = "dotWidth"
                            )

                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(dotWidth)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                            )
                        }
                    }

                    // Spacer for balanced centering
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Primary Next / Finish Button
                val buttonText = when (state.currentStep) {
                    OnboardingStep.WELCOME -> "Get Started"
                    OnboardingStep.THEME_SETUP -> "Launch Orbit Money"
                    else -> "Next"
                }

                Button(
                    onClick = {
                        haptics.confirm()
                        onIntent(OnboardingIntent.NextStep)
                    },
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeIntroCard() {
    OrbitSectionCard(
        title = "Welcome to Orbit Money",
        subtitle = "Intelligent, private household finance"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "🪐",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }

            Text(
                text = "Take complete control of your wealth with offline-first privacy, mathematical precision, and tactile Material 3 Expressive motion.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IntroValueRow(icon = "🔒", title = "100% Private", desc = "Zero mandatory cloud accounts or tracking.")
                    IntroValueRow(icon = "⚡", title = "Tactile & Fast", desc = "Instant offline queries with 60fps spring physics.")
                    IntroValueRow(icon = "🎯", title = "Mathematically Exact", desc = "Integer-cents calculation precision for every budget.")
                }
            }
        }
    }
}

@Composable
private fun IntroValueRow(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
