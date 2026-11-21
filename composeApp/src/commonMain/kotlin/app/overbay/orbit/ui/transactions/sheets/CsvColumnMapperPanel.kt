package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.CsvColumnMapping
import app.overbay.orbit.domain.CsvImportFormat
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Reusable CSV format badge + interactive column mapping panel shared by
 * [CsvImportSheet] and onboarding's Data Connection step.
 */
@Composable
fun CsvColumnMapperPanel(
    detectedFormat: CsvImportFormat,
    headers: List<String>,
    mapping: CsvColumnMapping,
    sampleRows: List<Map<String, String>>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onMappingChange: (CsvColumnMapping) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val useSplitInflowOutflow = mapping.amountColumn.isNullOrBlank() &&
        (!mapping.inflowColumn.isNullOrBlank() || !mapping.outflowColumn.isNullOrBlank())

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.tick()
                        onExpandedChange(!expanded)
                    }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    OrbitBadge(
                        text = if (detectedFormat == CsvImportFormat.GENERIC) "Custom CSV" else "Detected: ${detectedFormat.displayName}",
                        containerColor = if (detectedFormat == CsvImportFormat.GENERIC) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (detectedFormat == CsvImportFormat.GENERIC) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Text(
                        text = if (expanded) "Hide columns" else "Adjust columns",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            OrbitAccordionVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColumnMappingDropdown(
                        label = "Date Column *",
                        selected = mapping.dateColumn,
                        options = headers,
                        allowNone = false,
                        onSelect = { onMappingChange(mapping.copy(dateColumn = it.orEmpty())) }
                    )
                    ColumnMappingDropdown(
                        label = "Merchant / Description *",
                        selected = mapping.merchantColumn,
                        options = headers,
                        allowNone = false,
                        onSelect = { onMappingChange(mapping.copy(merchantColumn = it.orEmpty())) }
                    )

                    if (!useSplitInflowOutflow) {
                        ColumnMappingDropdown(
                            label = "Amount Column *",
                            selected = mapping.amountColumn,
                            options = headers,
                            allowNone = true,
                            noneLabel = "Use separate Inflow/Outflow columns",
                            onSelect = { chosen ->
                                if (chosen == null) {
                                    onMappingChange(
                                        mapping.copy(
                                            amountColumn = null,
                                            inflowColumn = mapping.inflowColumn ?: headers.firstOrNull(),
                                            outflowColumn = mapping.outflowColumn ?: headers.getOrNull(1)
                                        )
                                    )
                                } else {
                                    onMappingChange(mapping.copy(amountColumn = chosen, inflowColumn = null, outflowColumn = null))
                                }
                            }
                        )
                    } else {
                        ColumnMappingDropdown(
                            label = "Inflow / Credit Column *",
                            selected = mapping.inflowColumn,
                            options = headers,
                            allowNone = true,
                            noneLabel = "Switch to single Amount column",
                            onSelect = { chosen ->
                                if (chosen == null) {
                                    onMappingChange(mapping.copy(amountColumn = headers.firstOrNull(), inflowColumn = null, outflowColumn = null))
                                } else {
                                    onMappingChange(mapping.copy(inflowColumn = chosen))
                                }
                            }
                        )
                        ColumnMappingDropdown(
                            label = "Outflow / Debit Column *",
                            selected = mapping.outflowColumn,
                            options = headers,
                            allowNone = false,
                            onSelect = { onMappingChange(mapping.copy(outflowColumn = it)) }
                        )
                    }

                    ColumnMappingDropdown(
                        label = "Category Column (Optional)",
                        selected = mapping.categoryColumn,
                        options = headers,
                        allowNone = true,
                        onSelect = { onMappingChange(mapping.copy(categoryColumn = it)) }
                    )
                    ColumnMappingDropdown(
                        label = "Account Name Column (Optional)",
                        selected = mapping.accountNameColumn,
                        options = headers,
                        allowNone = true,
                        onSelect = { onMappingChange(mapping.copy(accountNameColumn = it)) }
                    )

                    OrbitSwitchRow(
                        title = "Flip +/- Amount Signs",
                        subtitle = "Turn on if expenses show up as positive numbers in your file",
                        checked = mapping.invertSign,
                        onCheckedChange = { onMappingChange(mapping.copy(invertSign = it)) },
                        showContainer = false
                    )

                    if (sampleRows.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SAMPLE ROW PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        sampleRows.take(2).forEach { row ->
                            val dateVal = row[mapping.dateColumn].orEmpty()
                            val merchVal = row[mapping.merchantColumn].orEmpty()
                            val amtVal = mapping.amountColumn?.let { row[it] }
                                ?: listOfNotNull(
                                    mapping.inflowColumn?.let { row[it]?.takeIf { v -> v.isNotBlank() }?.let { v -> "+$v" } },
                                    mapping.outflowColumn?.let { row[it]?.takeIf { v -> v.isNotBlank() }?.let { v -> "-$v" } }
                                ).joinToString(" / ")
                            Text(
                                text = "$dateVal · $merchVal · $amtVal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnMappingDropdown(
    label: String,
    selected: String?,
    options: List<String>,
    allowNone: Boolean,
    noneLabel: String = "None",
    onSelect: (String?) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    haptics.tick()
                    menuOpen = true
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selected?.takeIf { it.isNotBlank() } ?: noneLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            if (allowNone) {
                DropdownMenuItem(
                    text = { Text(noneLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        menuOpen = false
                        onSelect(null)
                    }
                )
            }
            options.forEach { header ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = header,
                            fontWeight = if (header == selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onSelect(header)
                    }
                )
            }
        }
    }
}
