package app.overbay.orbit.ui.accounts.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.CreditCardTemplate
import app.overbay.orbit.domain.CreditCardTemplates
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitTextField

val TEMPLATE_FILTER_CHIPS = listOf(
    "All",
    "Chase",
    "Amex",
    "Capital One",
    "Citi",
    "BofA",
    "Barclays",
    "Wells Fargo / Bilt",
    "U.S. Bank",
    "Business",
    "\$0 Fee"
)

fun filterCardTemplates(
    catalog: List<CreditCardTemplate>,
    query: String,
    categoryFilter: String = "All"
): List<CreditCardTemplate> {
    val q = query.trim().lowercase()
    return catalog.filter { tpl ->
        val matchesCategory = when (categoryFilter) {
            "All" -> true
            "Chase" -> tpl.issuerName.contains("Chase", ignoreCase = true)
            "Amex" -> tpl.issuerName.contains("American Express", ignoreCase = true) || tpl.network.equals("Amex", ignoreCase = true)
            "Capital One" -> tpl.issuerName.contains("Capital One", ignoreCase = true)
            "Citi" -> tpl.issuerName.contains("Citi", ignoreCase = true)
            "BofA" -> tpl.issuerName.contains("Bank of America", ignoreCase = true)
            "Barclays" -> tpl.issuerName.contains("Barclays", ignoreCase = true)
            "Wells Fargo / Bilt" -> tpl.issuerName.contains("Wells Fargo", ignoreCase = true) || tpl.issuerName.contains("Bilt", ignoreCase = true)
            "U.S. Bank" -> tpl.issuerName.contains("U.S. Bank", ignoreCase = true) || tpl.issuerName.contains("US Bank", ignoreCase = true)
            "Business" -> tpl.isBusinessCard
            "\$0 Fee" -> tpl.annualFeeCents == 0L
            else -> true
        }
        if (!matchesCategory) return@filter false
        if (q.isBlank()) return@filter true

        tpl.cardName.lowercase().contains(q) ||
            tpl.shortLabel.lowercase().contains(q) ||
            tpl.issuerName.lowercase().contains(q) ||
            tpl.network.lowercase().contains(q) ||
            (tpl.defaultSubRewardText?.lowercase()?.contains(q) == true)
    }
}

fun LazyListScope.cardTemplatePickerItems(
    catalog: List<CreditCardTemplate>,
    query: String,
    selectedFilter: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onSelectTemplate: (CreditCardTemplate) -> Unit,
    onSkip: () -> Unit
) {
    val filtered = filterCardTemplates(catalog, query, selectedFilter)

    item(key = "template_picker_header") {
        CardTemplatePickerHeader(
            query = query,
            selectedFilter = selectedFilter,
            totalMatching = filtered.size,
            onQueryChange = onQueryChange,
            onFilterChange = onFilterChange
        )
    }

    items(filtered, key = { "tpl_${it.id}" }) { tpl ->
        TemplateItemRow(
            template = tpl,
            onClick = { onSelectTemplate(tpl) }
        )
    }

    item(key = "template_picker_footer") {
        CardTemplatePickerFooter(
            asOf = catalog.firstOrNull()?.asOf ?: "2026-11",
            onSkip = onSkip
        )
    }
}

@Composable
fun CardTemplatePicker(
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectTemplate: (CreditCardTemplate) -> Unit,
    onSkip: () -> Unit,
    selectedFilter: String = "All",
    onFilterChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val catalog by CreditCardTemplates.catalogFlow.collectAsState()
    val filtered = remember(catalog, query, selectedFilter) {
        filterCardTemplates(catalog, query, selectedFilter)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CardTemplatePickerHeader(
            query = query,
            selectedFilter = selectedFilter,
            totalMatching = filtered.size,
            onQueryChange = onQueryChange,
            onFilterChange = onFilterChange
        )

        filtered.forEach { tpl ->
            TemplateItemRow(
                template = tpl,
                onClick = { onSelectTemplate(tpl) }
            )
        }

        CardTemplatePickerFooter(
            asOf = catalog.firstOrNull()?.asOf ?: "2026-11",
            onSkip = onSkip
        )
    }
}

@Composable
private fun CardTemplatePickerHeader(
    query: String,
    selectedFilter: String,
    totalMatching: Int,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OrbitTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search $totalMatching cards (e.g. Sapphire, Amex Gold, Venture X)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TEMPLATE_FILTER_CHIPS.forEach { chip ->
                FilterChip(
                    selected = selectedFilter == chip,
                    onClick = { onFilterChange(if (selectedFilter == chip && chip != "All") "All" else chip) },
                    label = { Text(chip, fontSize = 11.5.sp) }
                )
            }
        }
    }
}

@Composable
private fun CardTemplatePickerFooter(
    asOf: String,
    onSkip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedCard(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Custom Card", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Other Card / Enter Manually",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Set up custom billing cycle, benefits, and earning multipliers from scratch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = "Template perks and live sign-up bonuses as of ${LocalDateUtils.formatMonthLabel(asOf)}. Verify current terms with your card issuer.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TemplateItemRow(
    template: CreditCardTemplate,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardArtworkThumbnail(
                name = template.shortLabel,
                issuer = template.issuerName,
                artKey = template.imageUrl,
                artworkUrl = template.artworkUrl,
                modifier = Modifier.size(width = 62.dp, height = 39.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = template.cardName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (template.isElevatedSubOffer) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Elevated",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = buildString {
                        append("${template.issuerName} · ${template.network}")
                        if (template.isBusinessCard) append(" · Business")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                template.defaultSubRewardText?.takeIf { it.isNotBlank() }?.let { subText ->
                    val spendSuffix = template.defaultSubTargetSpendCents?.takeIf { it > 0L }?.let { spend ->
                        val mos = ((template.subSpendWindowDays ?: 90) / 30).coerceAtLeast(1)
                        " (${MoneyUtils.formatCents(spend, showCents = false)}/${mos}mo)"
                    }.orEmpty()
                    Text(
                        text = "SUB: $subText$spendSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        template.annualFeeCents <= 0L -> "\$0 Fee"
                        template.isAnnualFeeWaived -> "${MoneyUtils.formatCents(template.annualFeeCents, showCents = false)}/yr (1st \$0)"
                        else -> "${MoneyUtils.formatCents(template.annualFeeCents, showCents = false)}/yr"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${template.benefits.size} perks · ${template.rewardCategories.size} mults",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
