package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingErrorBanner
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.ui.transactions.sheets.CsvColumnMapperPanel
import app.overbay.orbit.util.LocalFileImportExportHelper
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun OnboardingDataConnectStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val fileHelper = LocalFileImportExportHelper.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OnboardingHeroHeader(
            title = "Bring In Your Finances",
            subtitle = "Import past history from another app, connect automatic bank sync, or do both.",
            icon = OrbitIcons.BankLink
        )

        // Card 1: CSV Import (Rocket Money, Mint, Monarch, YNAB, or any bank CSV)
        OnboardingCardContainer {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Import Past History (CSV)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OrbitBadge(text = "Optional")
            }

            Text(
                text = "Moving over from Rocket Money, Mint, Monarch, YNAB, or a bank export? Drop in a CSV and Orbit will detect the columns automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            if (state.csvImportedCount > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = OrbitTheme.colors.incomeContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OrbitTheme.colors.income, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Imported ${state.csvImportedCount} transactions${state.csvImportedFormatName?.let { " ($it)" }.orEmpty()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OrbitTheme.colors.onIncomeContainer
                            )
                            if (state.csvLatestDateIso != null) {
                                Text(
                                    text = "History through ${state.csvLatestDateIso}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OrbitTheme.colors.onIncomeContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        fileHelper.pickAndImportFile("text/csv") { content ->
                            onIntent(OnboardingIntent.LoadCsvText("import.csv", content))
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.csvImportedCount > 0) "Import Another CSV" else "Choose CSV File")
                }
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onIntent(OnboardingIntent.ToggleCsvPaste(!state.csvPasteExpanded))
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
                ) {
                    Text(if (state.csvPasteExpanded) "Hide Paste" else "Paste CSV")
                }
            }

            OrbitAccordionVisibility(visible = state.csvPasteExpanded) {
                OrbitTextField(
                    value = state.csvRawText,
                    onValueChange = { onIntent(OnboardingIntent.LoadCsvText("pasted.csv", it)) },
                    label = { Text("Paste CSV Text") },
                    placeholder = { Text("Date, Merchant, Amount...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val inspected = state.csvInspection
            val mapping = state.csvMapping
            if (inspected != null && mapping != null) {
                CsvColumnMapperPanel(
                    detectedFormat = inspected.detectedFormat,
                    headers = inspected.headers,
                    mapping = mapping,
                    sampleRows = inspected.sampleRows,
                    expanded = state.csvMapperExpanded,
                    onExpandedChange = { onIntent(OnboardingIntent.ToggleCsvMapper(it)) },
                    onMappingChange = { onIntent(OnboardingIntent.UpdateCsvMapping(it)) }
                )
            }

            val preview = state.csvPreview
            if (preview != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Ready: ${preview.transactions.size} transactions" +
                                if (preview.accounts.isNotEmpty()) " across ${preview.accounts.size} account(s)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (preview.earliestTransactionDateIso != null && preview.latestTransactionDateIso != null) {
                            Text(
                                text = "${preview.earliestTransactionDateIso} → ${preview.latestTransactionDateIso}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        haptics.confirm()
                        onIntent(OnboardingIntent.ConfirmCsvImport)
                    },
                    enabled = !state.isImportingCsv && preview.transactions.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (state.isImportingCsv) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (state.isImportingCsv) "Importing..." else "Import ${preview.transactions.size} Transactions", fontWeight = FontWeight.Bold)
                }
            }

            OnboardingErrorBanner(message = state.csvError)
        }

        // Card 2: SimpleFIN Bridge
        OnboardingSimpleFinCard(state = state, onIntent = onIntent)

        if (!state.simpleFinConnected && state.csvImportedCount == 0) {
            TextButton(onClick = { haptics.tick(); onIntent(OnboardingIntent.Next) }) {
                Text(
                    text = "I'll add accounts manually instead",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}
