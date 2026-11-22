package app.overbay.orbit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.*
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.accounts.AccountsScreen
import app.overbay.orbit.ui.accounts.AccountsViewModel
import app.overbay.orbit.ui.cardperks.CardPerksScreen
import app.overbay.orbit.ui.networth.NetWorthScreen
import app.overbay.orbit.ui.networth.NetWorthViewModel
import app.overbay.orbit.ui.budgets.BudgetsScreen
import app.overbay.orbit.ui.budgets.BudgetsViewModel
import app.overbay.orbit.ui.categories.CategoriesScreen
import app.overbay.orbit.ui.categories.CategoriesViewModel
import app.overbay.orbit.ui.components.AdaptiveNavigationShell
import app.overbay.orbit.ui.components.GlobalStatusBanners
import app.overbay.orbit.ui.components.LocalCanopyScreenKeyId
import app.overbay.orbit.ui.components.LocalCanopyTransitionCoordinator
import app.overbay.orbit.ui.components.LocalScreenScrollState
import app.overbay.orbit.ui.components.rememberCanopyTransitionCoordinator
import app.overbay.orbit.ui.dashboard.DashboardScreen
import app.overbay.orbit.ui.dashboard.DashboardViewModel
import app.overbay.orbit.ui.goals.GoalsScreen
import app.overbay.orbit.ui.goals.GoalsViewModel
import app.overbay.orbit.ui.login.LoginScreen
import app.overbay.orbit.ui.login.LoginViewModel
import app.overbay.orbit.ui.onboarding.OnboardingScreen
import app.overbay.orbit.ui.onboarding.OnboardingViewModel
import app.overbay.orbit.ui.rules.RulesScreen
import app.overbay.orbit.ui.rules.RulesViewModel
import app.overbay.orbit.ui.transactions.TransactionsScreen
import app.overbay.orbit.ui.transactions.TransactionsViewModel
import app.overbay.orbit.ui.upcoming.UpcomingScreen
import app.overbay.orbit.ui.upcoming.UpcomingViewModel
import app.overbay.orbit.ui.trends.SpendingTrendsScreen
import app.overbay.orbit.ui.trends.SpendingTrendsViewModel
import app.overbay.orbit.ui.mha.MhaTrackerScreen
import app.overbay.orbit.ui.mha.MhaTrackerViewModel
import app.overbay.orbit.ui.calculators.housing.HousingCalculatorScreen
import app.overbay.orbit.ui.calculators.housing.HousingViewModel
import app.overbay.orbit.ui.calculators.retirement.RetirementCalculatorScreen
import app.overbay.orbit.ui.calculators.retirement.RetirementViewModel
import app.overbay.orbit.ui.household.HouseholdScreen
import app.overbay.orbit.ui.household.HouseholdViewModel
import app.overbay.orbit.ui.settings.SettingsScreen
import app.overbay.orbit.ui.settings.SettingsViewModel
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.PlatformStorage

internal class OrbitViewModelHolder(repo: OrbitRepository) {
    val loginViewModel by lazy { LoginViewModel(repo) }
    val onboardingViewModel by lazy { OnboardingViewModel(repo) }
    val dashboardViewModel by lazy { DashboardViewModel(repo) }
    val transactionsViewModel by lazy { TransactionsViewModel(repo) }
    val categoriesViewModel by lazy { CategoriesViewModel(repo) }
    val rulesViewModel by lazy { RulesViewModel(repo) }
    val budgetsViewModel by lazy { BudgetsViewModel(repo) }
    val upcomingViewModel by lazy { UpcomingViewModel(repo) }
    val goalsViewModel by lazy { GoalsViewModel(repo) }
    val accountsViewModel by lazy { AccountsViewModel(repo) }
    val netWorthViewModel by lazy { NetWorthViewModel(repo) }
    val spendingTrendsViewModel by lazy { SpendingTrendsViewModel(repo) }
    val mhaTrackerViewModel by lazy { MhaTrackerViewModel(repo) }
    val housingViewModel by lazy { HousingViewModel(repo) }
    val retirementViewModel by lazy { RetirementViewModel(repo) }
    val householdViewModel by lazy { HouseholdViewModel(repo) }
    val settingsViewModel by lazy { SettingsViewModel(repo) }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OrbitNavigation(
    repository: OrbitRepository? = null,
    initialKey: OrbitNavKey = OrbitNavKey.Dashboard,
    modifier: Modifier = Modifier
) {
    val backStackController = remember(initialKey) { OrbitBackStackController(initialKey) }
    val currentEntry = backStackController.currentEntry
    val currentKey = currentEntry.key
    val posture = LocalDevicePosture.current

    val repo = repository ?: remember { OrbitRepository(PlatformStorage()) }
    val preferences by repo.preferences.collectAsState()
    val isAuthenticated by repo.isAuthenticated.collectAsState()
    // Instantiate ViewModels lazily per visited route so cold-starting onto Dashboard
    // only constructs DashboardViewModel instead of all 17 feature ViewModels at once.
    val viewModels = remember(repo) { OrbitViewModelHolder(repo) }
    val navCoordinator = remember(backStackController, viewModels) {
        OrbitNavigationCoordinator(backStackController, viewModels)
    }

    LaunchedEffect(initialKey) {
        navCoordinator.applyInitialKey(initialKey)
    }

    LaunchedEffect(preferences.storageMode, isAuthenticated, preferences.onboardingCompleted) {
        if (
            preferences.storageMode == StorageMode.SELF_HOSTED &&
            !isAuthenticated &&
            currentKey != OrbitNavKey.Login &&
            currentKey !is OrbitNavKey.Onboarding
        ) {
            backStackController.resetTo(OrbitNavKey.Login)
        } else if (
            preferences.storageMode == StorageMode.SELF_HOSTED &&
            isAuthenticated &&
            preferences.onboardingCompleted &&
            currentKey == OrbitNavKey.Login
        ) {
            backStackController.resetTo(OrbitNavKey.Dashboard)
        }
    }

    LaunchedEffect(preferences.storageMode, isAuthenticated) {
        if (preferences.storageMode == StorageMode.SELF_HOSTED && isAuthenticated) {
            repo.refreshSimpleFinStatus()
        }
    }

    val isAuthRoute = currentKey == OrbitNavKey.Login || currentKey is OrbitNavKey.Onboarding
    var lastAuthKey by remember(initialKey) {
        mutableStateOf<OrbitNavKey>(
            if (initialKey == OrbitNavKey.Login || initialKey is OrbitNavKey.Onboarding) initialKey else OrbitNavKey.Login
        )
    }
    if (isAuthRoute) {
        lastAuthKey = currentKey
    }
    var lastShellEntry by remember(initialKey) {
        mutableStateOf(
            if (initialKey == OrbitNavKey.Login || initialKey is OrbitNavKey.Onboarding) {
                OrbitBackStackEntry(id = 0L, key = OrbitNavKey.Dashboard)
            } else {
                OrbitBackStackEntry(id = 0L, key = initialKey)
            }
        )
    }
    if (!isAuthRoute) {
        lastShellEntry = currentEntry
    }

    BackHandler(enabled = !isAuthRoute && backStackController.canPopBack) {
        navCoordinator.popBack()
    }

    val navigateTo: (OrbitNavKey) -> Unit = { navCoordinator.navigate(it, fromTab = false) }
    val navigateFromTab: (OrbitNavKey) -> Unit = { navCoordinator.navigate(it, fromTab = true) }

    AnimatedContent(
        targetState = isAuthRoute,
        transitionSpec = {
            (fadeIn(OrbitSprings.FloatEffectsDefault) +
                scaleIn(initialScale = 0.98f, animationSpec = OrbitSprings.FloatSpatialDefault)) togetherWith
                fadeOut(OrbitSprings.FloatEffectsFast)
        },
        modifier = modifier.fillMaxSize(),
        label = "authShellTransition"
    ) { showAuth ->
        if (showAuth) {
            val activeAuthKey = if (isAuthRoute) currentKey else lastAuthKey
            val onboardingKey = activeAuthKey as? OrbitNavKey.Onboarding
            if (onboardingKey != null) {
                LaunchedEffect(onboardingKey) {
                    viewModels.onboardingViewModel.beginSession(isReplay = onboardingKey.isReplay)
                }
                OnboardingScreen(
                    viewModel = viewModels.onboardingViewModel,
                    posture = posture,
                    onFinish = {
                        if (onboardingKey.isReplay && backStackController.canPopBack) {
                            navCoordinator.popBack()
                        } else {
                            backStackController.resetTo(OrbitNavKey.Dashboard)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LoginScreen(
                    viewModel = viewModels.loginViewModel,
                    onLoginSuccess = { backStackController.resetTo(OrbitNavKey.Dashboard) },
                    onUseOfflineStorage = { backStackController.resetTo(OrbitNavKey.Dashboard) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            val activeShellEntry = if (!isAuthRoute) currentEntry else lastShellEntry
            val navTransition = updateTransition(targetState = activeShellEntry, label = "OrbitNavigationTransition")
            val canopyCoordinator = rememberCanopyTransitionCoordinator(
                navTransition.targetState.key.primaryTabIndex ?: navTransition.targetState.key
            )

            CompositionLocalProvider(LocalCanopyTransitionCoordinator provides canopyCoordinator) {
                AdaptiveNavigationShell(
                    currentKey = activeShellEntry.key,
                    onNavigate = navigateTo,
                    onTabSelected = navigateFromTab,
                    mhaEnabled = preferences.mhaTrackerEnabled,
                    hiddenRouteNames = preferences.hiddenRouteNames,
                    moreRouteOrder = preferences.moreRouteOrder,
                    modifier = Modifier.fillMaxSize()
                ) {
            Column(modifier = Modifier.fillMaxSize()) {
                GlobalStatusBanners(
                    repository = repo,
                    onNavigate = navigateTo
                )
                navTransition.AnimatedContent(
                    transitionSpec = {
                        buildOrbitTransitionTransform(
                            resolveOrbitTransitionDirection(initialState.key, targetState.key)
                        )
                    },
                    contentKey = { entry -> entry.id },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val rx = coords.positionInRoot().x
                            val rw = coords.size.width.toFloat()
                            if (kotlin.math.abs(rx - canopyCoordinator.containerRootX) > 0.5f) canopyCoordinator.containerRootX = rx
                            if (kotlin.math.abs(rw - canopyCoordinator.containerWidthPx) > 0.5f) canopyCoordinator.containerWidthPx = rw

                        }
                ) { entry ->
                    val key = entry.key
                    CompositionLocalProvider(
                        LocalCanopyScreenKeyId provides (key.primaryTabIndex ?: key),
                        LocalScreenScrollState provides backStackController.scrollStateFor(entry.id)
                    ) {
                        when (key) {
                            OrbitNavKey.Dashboard -> DashboardScreen(
                                viewModel = viewModels.dashboardViewModel,
                                onNavigate = navigateTo,
                                modifier = Modifier.fillMaxSize()
                            )
                            is OrbitNavKey.Transactions -> TransactionsScreen(
                                viewModel = viewModels.transactionsViewModel,
                                onNavigate = navigateTo,
                                modifier = Modifier.fillMaxSize()
                            )
                            OrbitNavKey.Budgets -> BudgetsScreen(viewModels.budgetsViewModel, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.Upcoming -> UpcomingScreen(viewModels.upcomingViewModel, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.Goals -> GoalsScreen(viewModels.goalsViewModel, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.Categories -> CategoriesScreen(viewModels.categoriesViewModel, posture, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.Rules -> RulesScreen(viewModels.rulesViewModel, posture, navigateTo, Modifier.fillMaxSize())
                            is OrbitNavKey.Accounts -> AccountsScreen(viewModels.accountsViewModel, key.accountId, navigateTo, Modifier.fillMaxSize())
                            is OrbitNavKey.CardPerks -> CardPerksScreen(viewModels.accountsViewModel, key.accountId, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.NetWorth -> NetWorthScreen(viewModels.netWorthViewModel, posture, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.SpendingTrends -> SpendingTrendsScreen(viewModels.spendingTrendsViewModel, posture, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.MhaTracker -> MhaTrackerScreen(viewModels.mhaTrackerViewModel, posture, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.HousingCalculator -> HousingCalculatorScreen(viewModels.housingViewModel, posture, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.RetirementCalculator -> RetirementCalculatorScreen(viewModels.retirementViewModel, posture, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.Household -> HouseholdScreen(viewModels.householdViewModel, posture, navigateTo, Modifier.fillMaxSize())
                            is OrbitNavKey.Settings -> SettingsScreen(viewModels.settingsViewModel, posture, navigateTo, Modifier.fillMaxSize())
                            else -> {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Orbit Money", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = key.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = "Mode: ${posture.mode.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
            }
        }
    }
}
