package app.overbay.orbit.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CanopyActionPillButton
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.HeroScrollState
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.components.rememberOrbitLazyListState
import app.overbay.orbit.ui.settings.components.SettingsDetailHeader
import app.overbay.orbit.ui.settings.components.SettingsDialogsHost
import app.overbay.orbit.ui.settings.components.settingsIndexItems
import app.overbay.orbit.ui.settings.sections.settingsSectionItems
import app.overbay.orbit.ui.transactions.components.TransactionsSearchBar
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.PlatformRuntime

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
            val isShowingPhoneSubpage = !isTwoPane && uiState.activeSection != null
            val activeDetailSection = uiState.activeSection ?: SettingsSection.APPEARANCE

            var previousSection by remember { mutableStateOf(uiState.activeSection) }
            LaunchedEffect(uiState.activeSection, isTwoPane) {
                if (previousSection != uiState.activeSection) {
                    previousSection = uiState.activeSection
                    if (!isTwoPane) heroScrollState.expand()
                }
            }

            BackHandler(enabled = uiState.isSearchActive) {
                viewModel.setSearchQuery("")
                viewModel.setSearchActive(false)
            }
            BackHandler(enabled = !uiState.isSearchActive && isShowingPhoneSubpage) {
                viewModel.openSection(null)
            }

            val onSignOutClick = { viewModel.showDialog(SettingsDialogState.ConfirmSignOut) }
            val onClearSearch = {
                viewModel.setSearchQuery("")
                viewModel.setSearchActive(false)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(heroScrollState.nestedScrollConnection)
            ) {
                SettingsHeroHeader(
                    uiState = uiState,
                    isShowingPhoneSubpage = isShowingPhoneSubpage,
                    activeDetailSection = activeDetailSection,
                    heroScrollState = heroScrollState,
                    viewModel = viewModel
                )

                if (isTwoPane) {
                    OrbitMorphingSplitLayout(
                        isDetailOpen = true,
                        onDismissDetail = {},
                        masterWeightWhenOpen = 0.38f,
                        handleBackPress = false,
                        modifier = Modifier.fillMaxSize(),
                        masterContent = {
                            LazyColumn(
                                state = rememberOrbitLazyListState(slotKey = "settings_master_index"),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                settingsIndexItems(uiState, activeDetailSection, viewModel::openSection, onSignOutClick, onClearSearch)
                            }
                        },
                        detailContent = {
                            Column(modifier = Modifier.fillMaxSize()) {
                                SettingsDetailHeader(section = activeDetailSection)
                                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                                LazyColumn(
                                    state = rememberOrbitLazyListState(slotKey = "settings_detail_${activeDetailSection.name}"),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    settingsSectionItems(activeDetailSection, uiState, viewModel, onNavigate)
                                }
                            }
                        }
                    )
                } else {
                    AnimatedContent(
                        targetState = uiState.activeSection,
                        transitionSpec = {
                            val dir = if (targetState != null && initialState == null) 1 else -1
                            (slideInHorizontally(OrbitSprings.IntOffsetSpatialDefault) { dir * it / 4 } +
                                fadeIn(OrbitSprings.FloatEffectsDefault)) togetherWith
                                (slideOutHorizontally(OrbitSprings.IntOffsetSpatialDefault) { -dir * it / 4 } +
                                    fadeOut(OrbitSprings.FloatEffectsFast))
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "settingsPhoneSectionTransition"
                    ) { section ->
                        LazyColumn(
                            state = rememberOrbitLazyListState(slotKey = section?.name ?: "settings_phone_index"),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (section == null) {
                                settingsIndexItems(uiState, null, viewModel::openSection, onSignOutClick, onClearSearch)
                            } else {
                                settingsSectionItems(section, uiState, viewModel, onNavigate)
                            }
                        }
                    }
                }
            }
        }
    }

    SettingsDialogsHost(uiState = uiState, viewModel = viewModel, onNavigate = onNavigate)
}

@Composable
private fun SettingsHeroHeader(
    uiState: SettingsUiState,
    isShowingPhoneSubpage: Boolean,
    activeDetailSection: SettingsSection,
    heroScrollState: HeroScrollState,
    viewModel: SettingsViewModel
) {
    val rootSubtitle = when {
        uiState.isSyncing -> "Syncing workspace..."
        uiState.storageMode == StorageMode.SELF_HOSTED -> "Self-Hosted Server · Active"
        else -> "On This Device · Private"
    }
    MorphingPageHero(
        kicker = null,
        title = if (isShowingPhoneSubpage) activeDetailSection.title else "Settings",
        collapsedTitle = if (isShowingPhoneSubpage) activeDetailSection.shortTitle else null,
        subtitle = if (isShowingPhoneSubpage) activeDetailSection.subtitle else rootSubtitle,
        scrollState = heroScrollState,
        actions = {
            if (isShowingPhoneSubpage && PlatformRuntime.isWeb) {
                CanopyActionPillButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Settings",
                    compactLabel = "Back",
                    isCompact = heroScrollState.collapseFraction > 0.45f,
                    buttonSize = 36.dp,
                    onClick = { viewModel.openSection(null) }
                )
            }
        },
        canopyBar = if (isShowingPhoneSubpage) {
            null
        } else {
            { _, isCompact, barHeight ->
                TransactionsSearchBar(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    isSearchActive = uiState.isSearchActive,
                    onSearchActiveChange = viewModel::setSearchActive,
                    isCompact = isCompact,
                    placeholderText = if (isCompact) "Search..." else "Search settings",
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                if (uiState.storageMode == StorageMode.SELF_HOSTED || uiState.preferences.googleDriveSyncEnabled) {
                    CanopyIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Sync Now",
                        onClick = viewModel::triggerSync,
                        buttonSize = barHeight,
                        isActive = uiState.isSyncing,
                        enabled = !uiState.isSyncing
                    )
                }
            }
        }
    )
}
