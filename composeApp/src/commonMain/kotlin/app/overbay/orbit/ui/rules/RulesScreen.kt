package app.overbay.orbit.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
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
    val heroScrollState = rememberHeroScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                    onBack = { onNavigate(OrbitNavKey.Dashboard) },
                    scrollState = heroScrollState,
                    actions = {
                        if (state.rules.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.18f),
                                modifier = Modifier.clickable(
                                    enabled = !state.isApplyingRules
                                ) { viewModel.runRulesNow() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Run Rules",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (state.isApplyingRules) "Running..." else "Run Rules",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                )

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
