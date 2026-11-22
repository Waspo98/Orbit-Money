package app.overbay.orbit.ui.onboarding

import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.ui.login.LocalSeedChoice

enum class OnboardingStep {
    WELCOME,
    APPEARANCE,
    STORAGE_MODE,
    LOCAL_WORKSPACE,
    SERVER_URL,
    SERVER_AUTH,
    DATA_CONNECT,
    PERSONALIZE_FORK,
    FEATURES,
    ADDITIONAL_ACCOUNTS,
    HOUSEHOLD,
    RETIREMENT,
    TAX_DEDUCTIONS,
    ALL_SET
}

data class OnboardingFeatureOption(
    val routeName: String,
    val title: String,
    val subtitle: String,
    val defaultEnabled: Boolean = true
)

object OnboardingStepResolver {

    val ALL_OPTIONAL_FEATURES: List<OnboardingFeatureOption> = listOf(
        OnboardingFeatureOption(
            routeName = "upcoming",
            title = "Upcoming Bills & Income",
            subtitle = "Track recurring bills, subscriptions, and paychecks on a calendar"
        ),
        OnboardingFeatureOption(
            routeName = "goals",
            title = "Savings Goals",
            subtitle = "Set targets for emergency funds, trips, or sinking funds"
        ),
        OnboardingFeatureOption(
            routeName = "spending_trends",
            title = "Spending Trends",
            subtitle = "See how your income and spending shift month over month"
        ),
        OnboardingFeatureOption(
            routeName = "net_worth",
            title = "Net Worth",
            subtitle = "Track total assets, debts, and net worth history over time"
        ),
        OnboardingFeatureOption(
            routeName = "card_perks",
            title = "Credit Card Tracker",
            subtitle = "Statement credits, rewards multipliers, bonuses, and Chase 5/24"
        ),
        OnboardingFeatureOption(
            routeName = "household",
            title = "Household",
            subtitle = "Household members, income profiles, and partner sharing"
        ),
        OnboardingFeatureOption(
            routeName = "retirement_calculator",
            title = "Retirement Calculator",
            subtitle = "Project your nest egg, Coast FIRE runway, and withdrawal plan"
        ),
        OnboardingFeatureOption(
            routeName = "housing_calculator",
            title = "Housing Calculator",
            subtitle = "Compare mortgages, home equity, and selling proceeds"
        ),
        OnboardingFeatureOption(
            routeName = "mha",
            title = "Tax-Deductible Expenses",
            subtitle = "Track deductible spending (general or Ministerial Housing Allowance)",
            defaultEnabled = false
        )
    )

    fun defaultEnabledFeatures(): Set<String> =
        ALL_OPTIONAL_FEATURES.filter { it.defaultEnabled }.map { it.routeName }.toSet()

    /**
     * Resolves the active ordered step path for the current branching decisions.
     * Drives both navigation progression and the spring-animated step indicator dots.
     */
    fun resolveSteps(state: OnboardingUiState): List<OnboardingStep> = buildList {
        if (state.isReplay) {
            add(OnboardingStep.APPEARANCE)
            add(OnboardingStep.DATA_CONNECT)
            add(OnboardingStep.FEATURES)
            add(OnboardingStep.ADDITIONAL_ACCOUNTS)
            if ("household" in state.enabledFeatures) add(OnboardingStep.HOUSEHOLD)
            if ("retirement_calculator" in state.enabledFeatures) add(OnboardingStep.RETIREMENT)
            if ("mha" in state.enabledFeatures) add(OnboardingStep.TAX_DEDUCTIONS)
            add(OnboardingStep.ALL_SET)
            return@buildList
        }

        add(OnboardingStep.WELCOME)
        add(OnboardingStep.APPEARANCE)
        add(OnboardingStep.STORAGE_MODE)

        when (state.selectedStorageMode) {
            StorageMode.LOCAL -> {
                add(OnboardingStep.LOCAL_WORKSPACE)
                if (state.selectedLocalSeed != LocalSeedChoice.FRESH) {
                    add(OnboardingStep.ALL_SET)
                    return@buildList
                }
            }
            StorageMode.SELF_HOSTED -> {
                add(OnboardingStep.SERVER_URL)
                add(OnboardingStep.SERVER_AUTH)
                if (state.existingServerDetected) {
                    add(OnboardingStep.ALL_SET)
                    return@buildList
                }
            }
        }

        add(OnboardingStep.DATA_CONNECT)
        add(OnboardingStep.PERSONALIZE_FORK)

        if (state.enteredAct2) {
            add(OnboardingStep.FEATURES)
            add(OnboardingStep.ADDITIONAL_ACCOUNTS)
            if ("household" in state.enabledFeatures) add(OnboardingStep.HOUSEHOLD)
            if ("retirement_calculator" in state.enabledFeatures) add(OnboardingStep.RETIREMENT)
            if ("mha" in state.enabledFeatures) add(OnboardingStep.TAX_DEDUCTIONS)
        }

        add(OnboardingStep.ALL_SET)
    }

    /**
     * Top-right Skip only appears once Storage Mode / Auth has been settled (or always on replay as Close).
     */
    fun canShowTopSkip(state: OnboardingUiState): Boolean {
        if (state.isReplay) return state.currentStep != OnboardingStep.ALL_SET
        return state.currentStep in setOf(
            OnboardingStep.DATA_CONNECT,
            OnboardingStep.PERSONALIZE_FORK,
            OnboardingStep.FEATURES,
            OnboardingStep.ADDITIONAL_ACCOUNTS,
            OnboardingStep.HOUSEHOLD,
            OnboardingStep.RETIREMENT,
            OnboardingStep.TAX_DEDUCTIONS
        )
    }

    fun isGateStep(step: OnboardingStep): Boolean = step in setOf(
        OnboardingStep.LOCAL_WORKSPACE,
        OnboardingStep.SERVER_URL,
        OnboardingStep.SERVER_AUTH,
        OnboardingStep.PERSONALIZE_FORK,
        OnboardingStep.ALL_SET
    )
}
