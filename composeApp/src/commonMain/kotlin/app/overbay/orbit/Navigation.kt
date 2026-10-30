package app.overbay.orbit

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.*
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.ui.accounts.AccountsScreen
import app.overbay.orbit.ui.accounts.AccountsViewModel
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

@Composable
fun OrbitNavigation(
    repository: OrbitRepository? = null,
    initialKey: OrbitNavKey = OrbitNavKey.Dashboard,
    modifier: Modifier = Modifier
) {
    val backStack = remember(initialKey) { mutableStateListOf<OrbitNavKey>(initialKey) }
    val currentKey = backStack.lastOrNull() ?: OrbitNavKey.Dashboard
    val posture = LocalDevicePosture.current

    val repo = repository ?: remember { OrbitRepository(PlatformStorage()) }
    val preferences by repo.preferences.collectAsState()
    val isAuthenticated by repo.isAuthenticated.collectAsState()
    val loginViewModel = remember(repo) { LoginViewModel(repo) }
    val onboardingViewModel = remember(repo) { OnboardingViewModel(repo) }
    val dashboardViewModel = remember(repo) { DashboardViewModel(repo) }
    val transactionsViewModel = remember(repo) { TransactionsViewModel(repo) }
    val categoriesViewModel = remember(repo) { CategoriesViewModel(repo) }
    val rulesViewModel = remember(repo) { RulesViewModel(repo) }
    val budgetsViewModel = remember(repo) { BudgetsViewModel(repo) }
    val upcomingViewModel = remember(repo) { UpcomingViewModel(repo) }
    val goalsViewModel = remember(repo) { GoalsViewModel(repo) }
    val accountsViewModel = remember(repo) { AccountsViewModel(repo) }
    val netWorthViewModel = remember(repo) { NetWorthViewModel(repo) }
    val spendingTrendsViewModel = remember(repo) { SpendingTrendsViewModel(repo) }
    val mhaTrackerViewModel = remember(repo) { MhaTrackerViewModel(repo) }
    val housingViewModel = remember(repo) { HousingViewModel(repo) }
    val retirementViewModel = remember(repo) { RetirementViewModel(repo) }
    val householdViewModel = remember(repo) { HouseholdViewModel(repo) }
    val settingsViewModel = remember(repo) { SettingsViewModel(repo) }

    LaunchedEffect(preferences.storageMode, isAuthenticated, preferences.onboardingCompleted) {
        if (
            preferences.storageMode == StorageMode.SELF_HOSTED &&
            !isAuthenticated &&
            currentKey != OrbitNavKey.Login &&
            currentKey != OrbitNavKey.Onboarding
        ) {
            backStack.clear()
            backStack.add(OrbitNavKey.Login)
        } else if (
            preferences.storageMode == StorageMode.SELF_HOSTED &&
            isAuthenticated &&
            preferences.onboardingCompleted &&
            (currentKey == OrbitNavKey.Login || currentKey == OrbitNavKey.Onboarding)
        ) {
            backStack.clear()
            backStack.add(OrbitNavKey.Dashboard)
        }
    }

    LaunchedEffect(preferences.storageMode, isAuthenticated) {
        if (preferences.storageMode == StorageMode.SELF_HOSTED && isAuthenticated) {
            repo.refreshSimpleFinStatus()
        }
    }

    if (currentKey == OrbitNavKey.Login) {
        LoginScreen(
            viewModel = loginViewModel,
            onLoginSuccess = {
                backStack.clear()
                backStack.add(OrbitNavKey.Dashboard)
            },
            onUseOfflineStorage = {
                backStack.clear()
                if (preferences.onboardingCompleted) {
                    backStack.add(OrbitNavKey.Dashboard)
                } else {
                    backStack.add(OrbitNavKey.Onboarding)
                }
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (currentKey == OrbitNavKey.Onboarding) {
        OnboardingScreen(
            viewModel = onboardingViewModel,
            onComplete = {
                backStack.clear()
                backStack.add(OrbitNavKey.Dashboard)
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    BackHandler(enabled = backStack.size > 1) {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    val navigateTo: (OrbitNavKey) -> Unit = { destination ->
        if (destination is OrbitNavKey.Transactions) {
            val hasTransactionsDeepLink = destination.transactionId != null ||
                destination.accountId != null ||
                destination.categoryId != null ||
                destination.uncategorizedOnly ||
                destination.startDate != null ||
                destination.endDate != null ||
                destination.sortOption != null ||
                destination.openAddSheet ||
                !destination.includeTransfers ||
                !destination.includeIgnored
            if (hasTransactionsDeepLink) {
                transactionsViewModel.applyDeepLink(
                    transactionId = destination.transactionId,
                    accountId = destination.accountId,
                    categoryId = destination.categoryId,
                    uncategorizedOnly = destination.uncategorizedOnly,
                    startDate = destination.startDate,
                    endDate = destination.endDate,
                    sortOption = destination.sortOption,
                    openAddSheet = destination.openAddSheet,
                    includeTransfers = destination.includeTransfers,
                    includeIgnored = destination.includeIgnored
                )
            } else {
                transactionsViewModel.clearFilters()
                transactionsViewModel.dismissInspectorPane()
            }
        } else if (destination is OrbitNavKey.Accounts) {
            if (destination.accountId != null) {
                accountsViewModel.openAccountDeepLink(destination.accountId)
            } else if (currentKey is OrbitNavKey.Accounts) {
                accountsViewModel.dismissInspectorPane()
            }
        }

        if (destination != currentKey) {
            if (
                destination.primaryTabIndex != null &&
                destination.primaryTabIndex == currentKey.primaryTabIndex &&
                backStack.isNotEmpty()
            ) {
                backStack[backStack.lastIndex] = destination
            } else {
                backStack.add(destination)
            }
        }
    }
    val popBackOrDashboard: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else if (currentKey != OrbitNavKey.Dashboard) {
            backStack[0] = OrbitNavKey.Dashboard
        }
    }
    val canopyCoordinator = rememberCanopyTransitionCoordinator(currentKey.primaryTabIndex ?: currentKey)

    CompositionLocalProvider(LocalCanopyTransitionCoordinator provides canopyCoordinator) {
        AdaptiveNavigationShell(
            currentKey = currentKey,
            onNavigate = navigateTo,
            canNavigateBack = backStack.size > 1,
            onBack = popBackOrDashboard,
            mhaEnabled = preferences.mhaTrackerEnabled,
            hiddenRouteNames = preferences.hiddenRouteNames,
            moreRouteOrder = preferences.moreRouteOrder,
            modifier = modifier
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                GlobalStatusBanners(
                    repository = repo,
                    onNavigate = navigateTo
                )
                AnimatedContent(
                    targetState = currentKey,
                    transitionSpec = { orbitNavigationTransitionSpec() },
                    contentKey = { key -> key.primaryTabIndex ?: key },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            canopyCoordinator.containerRootX = coords.positionInRoot().x
                            canopyCoordinator.containerWidthPx = coords.size.width.toFloat()
                        },
                    label = "OrbitNavigationTransition"
                ) { key ->
                    CompositionLocalProvider(LocalCanopyScreenKeyId provides (key.primaryTabIndex ?: key)) {
                        when (key) {
                            OrbitNavKey.Dashboard -> DashboardScreen(dashboardViewModel, navigateTo, Modifier.fillMaxSize())
                            is OrbitNavKey.Transactions -> {
                                LaunchedEffect(key) {
                                    transactionsViewModel.applyDeepLink(
                                        transactionId = key.transactionId,
                                        accountId = key.accountId,
                                        categoryId = key.categoryId,
                                        uncategorizedOnly = key.uncategorizedOnly,
                                        startDate = key.startDate,
                                        endDate = key.endDate,
                                        sortOption = key.sortOption,
                                        openAddSheet = key.openAddSheet,
                                        includeTransfers = key.includeTransfers,
                                        includeIgnored = key.includeIgnored
                                    )
                                }
                                TransactionsScreen(transactionsViewModel, navigateTo, Modifier.fillMaxSize())
                            }
                            OrbitNavKey.Budgets -> BudgetsScreen(budgetsViewModel, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.Upcoming -> UpcomingScreen(upcomingViewModel, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            OrbitNavKey.Goals -> GoalsScreen(goalsViewModel, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            OrbitNavKey.Categories -> CategoriesScreen(categoriesViewModel, posture, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            OrbitNavKey.Rules -> RulesScreen(rulesViewModel, posture, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            is OrbitNavKey.Accounts -> AccountsScreen(accountsViewModel, key.accountId, navigateTo, Modifier.fillMaxSize())
                            OrbitNavKey.NetWorth -> NetWorthScreen(netWorthViewModel, posture, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            OrbitNavKey.SpendingTrends -> SpendingTrendsScreen(spendingTrendsViewModel, posture, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            OrbitNavKey.MhaTracker -> MhaTrackerScreen(mhaTrackerViewModel, posture, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            OrbitNavKey.HousingCalculator -> HousingCalculatorScreen(housingViewModel, posture, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            OrbitNavKey.RetirementCalculator -> RetirementCalculatorScreen(retirementViewModel, posture, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            OrbitNavKey.Household -> HouseholdScreen(householdViewModel, posture, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
                            OrbitNavKey.Settings -> SettingsScreen(settingsViewModel, posture, navigateTo, popBackOrDashboard, Modifier.fillMaxSize())
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
