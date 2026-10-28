package app.overbay.orbit.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.components.CanopyActionPillButton
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.rules.components.RuleDetailInspectorPane
import app.overbay.orbit.ui.rules.components.RuleItemCard
import app.overbay.orbit.ui.rules.sheets.RuleEditorSheet
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onBack: () -> Unit = { onNavigate(OrbitNavKey.Dashboard) },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isDetailOpen = isTwoPane && (state.inspectingRule != null || state.showEditorSheet)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (!state.isLoading) {
                    OrbitFloatingActionButton(
                        visible = heroScrollState.isFabVisible,
                        onClick = { viewModel.openAddRule() },
                        contentDescription = "New Rule"
                    )
                }
            }
        ) { paddingValues ->
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .nestedScroll(heroScrollState.nestedScrollConnection)
                ) {
                    MorphingPageHero(
                        kicker = "AUTOMATION",
                        title = "Rules",
                        subtitle = "${state.rules.size} automated categorization rules",
                        canNavigateBack = true,
                        onBack = onBack,
                        scrollState = heroScrollState,
                        canopyBar = { _, isCompact, barHeight ->
                            CanopySurfacePill(
                                expandedText = "${state.rules.size} Automated Rules",
                                compactText = "${state.rules.size} Rules",
                                isCompact = isCompact,
                                leadingIcon = OrbitIcons.Rules,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            if (state.rules.isNotEmpty()) {
                                CanopyActionPillButton(
                                    icon = Icons.Default.PlayArrow,
                                    label = if (state.isApplyingRules) "Running..." else "Run Rules",
                                    compactLabel = if (state.isApplyingRules) "Running" else "Run",
                                    isCompact = isCompact,
                                    isActive = state.isApplyingRules,
                                    enabled = !state.isApplyingRules,
                                    buttonSize = barHeight,
                                    onClick = { viewModel.runRulesNow() }
                                )
                            }

                            CanopyIconButton(
                                icon = Icons.Default.Add,
                                contentDescription = "New Rule",
                                onClick = { viewModel.openAddRule() },
                                buttonSize = barHeight
                            )

                            CanopySettingsButton(
                                buttonSize = barHeight,
                                onClick = { onNavigate(OrbitNavKey.Settings) }
                            )
                        }
                    )

                    OrbitMorphingSplitLayout(
                        isDetailOpen = isDetailOpen,
                        onDismissDetail = { viewModel.dismissInspectorPane() },
                        masterWeightWhenOpen = 0.56f,
                        modifier = Modifier.fillMaxSize(),
                        masterContent = {
                            if (state.rules.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyStateCard(
                                        title = "No Rules Configured",
                                        description = "Create automated rules to rename merchants, assign categories, or flag transfers automatically.",
                                        actionButtonText = "+ Add First Rule",
                                        onActionClick = { viewModel.openAddRule() }
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = state.rules,
                                        key = { it.id }
                                    ) { rule ->
                                        RuleItemCard(
                                            rule = rule,
                                            categoryMap = state.categoryMap,
                                            isInspected = isTwoPane && state.inspectingRuleId == rule.id,
                                            onClick = if (isTwoPane) {
                                                { viewModel.selectRuleForInspect(rule.id) }
                                            } else {
                                                null
                                            },
                                            onToggleEnabled = { enabled -> viewModel.toggleRuleEnabled(rule.id, enabled) },
                                            onEdit = { viewModel.openEditRule(rule) },
                                            onDelete = { viewModel.requestDeleteRule(rule.id) }
                                        )
                                    }
                                }
                            }
                        },
                        detailContent = {
                            RuleDetailInspectorPane(
                                state = state,
                                onToggleEnabled = { id, enabled -> viewModel.toggleRuleEnabled(id, enabled) },
                                onOpenEdit = { rule -> viewModel.openEditRule(rule) },
                                onSaveRule = { rule -> viewModel.saveRule(rule) },
                                onCloseEdit = { viewModel.closeEditorSheet() },
                                onDeleteRule = { id -> viewModel.requestDeleteRule(id) },
                                onRunRulesNow = { viewModel.runRulesNow() },
                                onPreviewCount = { conditions -> viewModel.previewMatchCount(conditions) },
                                onDismissPane = { viewModel.dismissInspectorPane() }
                            )
                        }
                    )
                }
            }
        }

        if (!isTwoPane && state.showEditorSheet) {
            RuleEditorSheet(
                rule = state.editingRule,
                categories = state.categories,
                accounts = state.accounts,
                onSave = { rule -> viewModel.saveRule(rule) },
                onPreviewCount = { conditions -> viewModel.previewMatchCount(conditions) },
                onDismiss = { viewModel.closeEditorSheet() }
            )
        }

        if (state.deleteConfirmRuleId != null) {
            val ruleId = state.deleteConfirmRuleId!!
            OrbitConfirmationDialog(
                title = "Delete Rule?",
                message = "Are you sure you want to delete this rule? It will no longer process future transactions automatically.",
                confirmText = "Delete",
                isDestructive = true,
                onConfirm = { viewModel.deleteRule(ruleId) },
                onDismiss = { viewModel.dismissDeleteRule() }
            )
        }
    }
}
