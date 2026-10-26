package app.overbay.orbit.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.HeroStatItem
import app.overbay.orbit.ui.components.HeroStatTone
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitPageActionRow
import app.overbay.orbit.ui.components.OrbitPrimaryPillButton
import app.overbay.orbit.ui.components.OrbitSecondaryPillButton
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.rules.components.RuleItemCard
import app.overbay.orbit.ui.rules.sheets.RuleEditorSheet

@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState(maxHeight = 200.dp, minHeight = 64.dp)

    Scaffold(
        modifier = modifier.fillMaxSize()
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
                // Curved Green Canopy Hero
                MorphingPageHero(
                    kicker = "AUTOMATION",
                    title = "Rules",
                    subtitle = "${state.rules.size} automated categorization rules",
                    canNavigateBack = true,
                    onBack = { onNavigate(OrbitNavKey.Dashboard) },
                    scrollState = heroScrollState,
                    onPrimaryAddClick = { viewModel.openAddRule() },
                    stats = listOf(
                        HeroStatItem("TOTAL RULES", "${state.rules.size}", HeroStatTone.DEFAULT),
                        HeroStatItem("ACTIVE", "${state.enabledRulesCount}", HeroStatTone.GOOD),
                        HeroStatItem("INACTIVE", "${state.rules.size - state.enabledRulesCount}", HeroStatTone.DEFAULT)
                    )
                )

                // Action Row Directly Below Hero
                OrbitPageActionRow {
                    OrbitPrimaryPillButton(
                        text = "+ New Rule",
                        onClick = { viewModel.openAddRule() }
                    )
                    OrbitSecondaryPillButton(
                        text = if (state.isApplyingRules) "Running..." else "Run Rules",
                        icon = Icons.Default.PlayArrow,
                        enabled = !state.isApplyingRules && state.rules.isNotEmpty(),
                        onClick = { viewModel.runRulesNow() }
                    )
                }

                // Scrollable Content
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = state.rules,
                            key = { it.id }
                        ) { rule ->
                            RuleItemCard(
                                rule = rule,
                                categoryMap = state.categoryMap,
                                onToggleEnabled = { enabled -> viewModel.toggleRuleEnabled(rule.id, enabled) },
                                onEdit = { viewModel.openEditRule(rule) },
                                onDelete = { viewModel.requestDeleteRule(rule.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showEditorSheet) {
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
