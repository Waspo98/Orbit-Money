package app.overbay.orbit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.ui.accounts.AccountsScreen
import app.overbay.orbit.ui.accounts.AccountsViewModel
import app.overbay.orbit.ui.networth.NetWorthScreen
import app.overbay.orbit.ui.networth.NetWorthViewModel
import app.overbay.orbit.ui.budgets.BudgetsScreen
import app.overbay.orbit.ui.budgets.BudgetsViewModel
import app.overbay.orbit.ui.categories.CategoriesScreen
import app.overbay.orbit.ui.categories.CategoriesViewModel
import app.overbay.orbit.ui.components.AdaptiveNavigationShell
import app.overbay.orbit.ui.dashboard.DashboardScreen
import app.overbay.orbit.ui.dashboard.DashboardViewModel
import app.overbay.orbit.ui.goals.GoalsScreen
import app.overbay.orbit.ui.goals.GoalsViewModel
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
    var showMoreFromHeader by remember { mutableStateOf(false) }
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

    AdaptiveNavigationShell(
        currentKey = currentKey,
        onNavigate = { key ->
            if (key != currentKey) {
                backStack.add(key)
            }
        },
        canNavigateBack = backStack.size > 1,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        },
        mhaEnabled = preferences.mhaTrackerEnabled,
        externalShowMore = showMoreFromHeader,
        onDismissExternalMore = { showMoreFromHeader = false },
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = currentKey,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize(),
            label = "OrbitNavigationCrossfade"
        ) { key ->
            when (key) {
                OrbitNavKey.Dashboard -> {
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is OrbitNavKey.Transactions -> {
                    if (key.transactionId != null) {
                        transactionsViewModel.selectTransactionForInspect(key.transactionId)
                    }
                    TransactionsScreen(
                        viewModel = transactionsViewModel,
                        onOpenDrawer = { showMoreFromHeader = true },
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.Budgets -> {
                    BudgetsScreen(
                        viewModel = budgetsViewModel,
                        onOpenDrawer = { showMoreFromHeader = true },
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.Upcoming -> {
                    UpcomingScreen(
                        viewModel = upcomingViewModel,
                        onOpenDrawer = { showMoreFromHeader = true },
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.Goals -> {
                    GoalsScreen(
                        viewModel = goalsViewModel,
                        onOpenDrawer = { showMoreFromHeader = true },
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.Categories -> {
                    CategoriesScreen(
                        viewModel = categoriesViewModel,
                        onOpenDrawer = { showMoreFromHeader = true },
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.Rules -> {
                    RulesScreen(
                        viewModel = rulesViewModel,
                        onOpenDrawer = { showMoreFromHeader = true },
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is OrbitNavKey.Accounts -> {
                    AccountsScreen(
                        viewModel = accountsViewModel,
                        initialAccountId = key.accountId,
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.NetWorth -> {
                    NetWorthScreen(
                        viewModel = netWorthViewModel,
                        onOpenDrawer = { showMoreFromHeader = true },
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.SpendingTrends -> {
                    SpendingTrendsScreen(
                        viewModel = spendingTrendsViewModel,
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.MhaTracker -> {
                    MhaTrackerScreen(
                        viewModel = mhaTrackerViewModel,
                        onOpenDrawer = { showMoreFromHeader = true },
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.HousingCalculator -> {
                    HousingCalculatorScreen(
                        viewModel = housingViewModel,
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.RetirementCalculator -> {
                    RetirementCalculatorScreen(
                        viewModel = retirementViewModel,
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.Household -> {
                    HouseholdScreen(
                        viewModel = householdViewModel,
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                OrbitNavKey.Settings -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigate = { destination ->
                            if (destination != currentKey) {
                                backStack.add(destination)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Orbit Money",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = key.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Mode: ${posture.mode.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
