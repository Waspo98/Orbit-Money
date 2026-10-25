package app.overbay.orbit.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.rules.components.RuleItemCard
import app.overbay.orbit.ui.rules.sheets.RuleEditorSheet
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val haptics = LocalOrbitHaptics.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.tick()
                    viewModel.openAddRule()
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Rule")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Canopy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Rules Engine",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.enabledRulesCount} active of ${state.rules.size} rules",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        haptics.confirm()
                        viewModel.runRulesNow()
                    },
                    enabled = !state.isApplyingRules && state.rules.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isApplyingRules) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run Rules",
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                        Text("Run Rules Now")
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        title = "No Rules Configured",
                        description = "Create automated rules to rename merchants, assign categories, or flag transfers automatically.",
                        actionButtonText = "Add First Rule",
                        onActionClick = { viewModel.openAddRule() }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
            message = "Are you sure you want to permanently delete this rule?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { viewModel.deleteRule(ruleId) },
            onDismiss = { viewModel.dismissDeleteRule() }
        )
    }
}
