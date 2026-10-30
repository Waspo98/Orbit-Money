package app.overbay.orbit.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.dashboard.cards.EmptyWorkspaceSetupCard
import app.overbay.orbit.ui.dashboard.dialogs.DashboardCustomizeSheet
import app.overbay.orbit.ui.dashboard.dialogs.TransactionReviewSheet
import app.overbay.orbit.util.LocalDevicePosture

/**
 * Editorial Orbit Money Dashboard screen supporting all 16 customizable cards.
 * Conforms strictly to <= 6 parameter rule and < 400-line ceiling.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigate = onNavigate,
        modifier = modifier
    )
}

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    onNavigate: (OrbitNavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val posture = LocalDevicePosture.current
    val heroScrollState = rememberHeroScrollState()
    val scrollState = rememberScrollState()

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = navBarBottomPadding + 88.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(heroScrollState.nestedScrollConnection)
        ) {
            // Edge-to-edge curved green canopy hero with 2-axis morphing summary & controls bar
            MorphingPageHero(
                kicker = "FINANCIAL ORBIT",
                title = "Dashboard",
                subtitle = "${uiState.dateLabel} · ${uiState.greeting}",
                scrollState = heroScrollState,
                canopyBar = { _, isCompact, barHeight ->
                    val monthName = uiState.cashflow.monthLabel.ifBlank { "This Month" }
                    val netFormatted = MoneyUtils.formatCents(uiState.cashflow.netCents, showCents = false, signed = true)
                    CanopySurfacePill(
                        expandedText = "$monthName · Net $netFormatted",
                        compactText = "Net $netFormatted",
                        isCompact = isCompact,
                        leadingIcon = OrbitIcons.SpendingTrends,
                        onClick = { onNavigate(OrbitNavKey.SpendingTrends) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    if (uiState.uncategorized.count > 0) {
                        CanopyIconButton(
                            icon = OrbitIcons.Categories,
                            contentDescription = "Categorize Recent Transactions",
                            onClick = { onAction(DashboardAction.OpenCategorizeReview) },
                            buttonSize = barHeight,
                            badgeCount = uiState.uncategorized.count
                        )
                    }

                    CanopyIconButton(
                        icon = OrbitIcons.ViewSliders,
                        contentDescription = "Customize Dashboard",
                        onClick = { onAction(DashboardAction.OpenCustomizeSheet) },
                        buttonSize = barHeight,
                        isActive = uiState.customization.isCustomizing
                    )

                    CanopySettingsButton(
                        buttonSize = barHeight,
                        onClick = { onNavigate(OrbitNavKey.Settings) }
                    )
                }
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            } else {
                val visibleConfigs = uiState.customization.cardConfigs
                    .ifEmpty {
                        uiState.visibleCards.mapIndexed { idx, type ->
                            DashboardCardConfigItem(
                                type = type,
                                title = type.defaultTitle,
                                description = type.defaultDescription,
                                isVisible = true,
                                sortOrder = idx,
                                wideSpan = type.defaultWideSpan
                            )
                        }
                    }
                    .filter { config ->
                        config.isVisible && (if (config.type == DashboardCardType.MHA_SUMMARY) uiState.mhaEnabled else true)
                    }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 1440.dp)
                            .fillMaxWidth()
                            .padding(horizontal = if (posture.supportsTwoPane) 20.dp else 16.dp, vertical = 14.dp)
                    ) {
                        // First-time empty workspace guided setup card
                        if (uiState.isEmptyWorkspace) {
                            EmptyWorkspaceSetupCard(
                                onImportCsv = {
                                    onAction(DashboardAction.RequestOpenDataSettings)
                                    onNavigate(OrbitNavKey.Settings)
                                },
                                onConnectSimpleFin = {
                                    onAction(DashboardAction.RequestOpenDataSettings)
                                    onNavigate(OrbitNavKey.Settings)
                                },
                                onAddManualAccount = {
                                    onNavigate(OrbitNavKey.Accounts())
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (posture.supportsTwoPane) {
                            val gridRows = buildDashboardGridRows(visibleConfigs)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                gridRows.forEach { row ->
                                    if (row.isFullSpan) {
                                        DashboardCardItem(row.leftCard, uiState, onAction, onNavigate)
                                    } else {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Max),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            ) {
                                                DashboardCardItem(
                                                    cardType = row.leftCard,
                                                    uiState = uiState,
                                                    onAction = onAction,
                                                    onNavigate = onNavigate,
                                                    modifier = Modifier.fillMaxHeight()
                                                )
                                            }
                                            if (row.rightCard != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                ) {
                                                    DashboardCardItem(
                                                        cardType = row.rightCard,
                                                        uiState = uiState,
                                                        onAction = onAction,
                                                        onNavigate = onNavigate,
                                                        modifier = Modifier.fillMaxHeight()
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            visibleConfigs.forEachIndexed { index, config ->
                                DashboardCardItem(config.type, uiState, onAction, onNavigate)
                                if (index < visibleConfigs.lastIndex) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(bottomContentPadding))
                    }
                }
            }
        }

        // Customization Bottom Sheet
        if (uiState.customization.isCustomizing) {
            DashboardCustomizeSheet(
                cards = uiState.customization.cardConfigs,
                onAction = onAction,
                onDismiss = { onAction(DashboardAction.CloseCustomizeSheet) }
            )
        }

        if (uiState.isReviewModalOpen) {
            TransactionReviewSheet(state = uiState.categorizeReview, onAction = onAction)
        }
    }
}
