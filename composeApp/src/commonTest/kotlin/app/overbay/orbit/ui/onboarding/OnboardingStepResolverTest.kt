package app.overbay.orbit.ui.onboarding

import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.ui.login.LocalSeedChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingStepResolverTest {

    @Test
    fun localFreshWithAct2AndTaxDeductionsIncludesFullGuidedPath() {
        val state = OnboardingUiState(
            selectedStorageMode = StorageMode.LOCAL,
            selectedLocalSeed = LocalSeedChoice.FRESH,
            enteredAct2 = true,
            enabledFeatures = OnboardingStepResolver.defaultEnabledFeatures() + "mha"
        )
        val steps = OnboardingStepResolver.resolveSteps(state)
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.APPEARANCE,
                OnboardingStep.STORAGE_MODE,
                OnboardingStep.LOCAL_WORKSPACE,
                OnboardingStep.DATA_CONNECT,
                OnboardingStep.PERSONALIZE_FORK,
                OnboardingStep.FEATURES,
                OnboardingStep.ADDITIONAL_ACCOUNTS,
                OnboardingStep.HOUSEHOLD,
                OnboardingStep.RETIREMENT,
                OnboardingStep.TAX_DEDUCTIONS,
                OnboardingStep.ALL_SET
            ),
            steps
        )
    }

    @Test
    fun featureTogglesPruneDisabledAct2Steps() {
        val state = OnboardingUiState(
            selectedStorageMode = StorageMode.LOCAL,
            selectedLocalSeed = LocalSeedChoice.FRESH,
            enteredAct2 = true,
            enabledFeatures = setOf("upcoming", "goals")
        )
        val steps = OnboardingStepResolver.resolveSteps(state)
        assertFalse(OnboardingStep.HOUSEHOLD in steps)
        assertFalse(OnboardingStep.RETIREMENT in steps)
        assertFalse(OnboardingStep.TAX_DEDUCTIONS in steps)
        assertTrue(OnboardingStep.FEATURES in steps)
        assertTrue(OnboardingStep.ADDITIONAL_ACCOUNTS in steps)
    }

    @Test
    fun selfHostedExistingServerSkipsDataConnectAndAct2() {
        val state = OnboardingUiState(
            selectedStorageMode = StorageMode.SELF_HOSTED,
            existingServerDetected = true
        )
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.APPEARANCE,
                OnboardingStep.STORAGE_MODE,
                OnboardingStep.SERVER_URL,
                OnboardingStep.SERVER_AUTH,
                OnboardingStep.ALL_SET
            ),
            OnboardingStepResolver.resolveSteps(state)
        )
    }

    @Test
    fun replayStartsAtAppearanceWithoutAuthStepsAndPreservesSkipVisibility() {
        val state = OnboardingUiState(
            isReplay = true,
            currentStep = OnboardingStep.APPEARANCE,
            enabledFeatures = setOf("household")
        )
        val steps = OnboardingStepResolver.resolveSteps(state)
        assertEquals(OnboardingStep.APPEARANCE, steps.first())
        assertFalse(OnboardingStep.STORAGE_MODE in steps)
        assertFalse(OnboardingStep.SERVER_AUTH in steps)
        assertTrue(OnboardingStep.HOUSEHOLD in steps)
        assertFalse(OnboardingStep.RETIREMENT in steps)
        assertTrue(OnboardingStepResolver.canShowTopSkip(state))
    }

    @Test
    fun topSkipHiddenBeforeStorageAndAuthAreSettled() {
        assertFalse(OnboardingStepResolver.canShowTopSkip(OnboardingUiState(currentStep = OnboardingStep.WELCOME)))
        assertFalse(OnboardingStepResolver.canShowTopSkip(OnboardingUiState(currentStep = OnboardingStep.STORAGE_MODE)))
        assertFalse(OnboardingStepResolver.canShowTopSkip(OnboardingUiState(currentStep = OnboardingStep.SERVER_AUTH)))
        assertTrue(OnboardingStepResolver.canShowTopSkip(OnboardingUiState(currentStep = OnboardingStep.DATA_CONNECT)))
        assertTrue(OnboardingStepResolver.canShowTopSkip(OnboardingUiState(currentStep = OnboardingStep.FEATURES)))
        assertFalse(OnboardingStepResolver.canShowTopSkip(OnboardingUiState(currentStep = OnboardingStep.ALL_SET)))
    }
}
