package app.overbay.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

private data class DestinationItem(
    val key: OrbitNavKey,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

/**
 * Categorized M3E bottom sheet displaying Orbit Money's secondary navigation destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreDestinationsSheet(
    currentKey: OrbitNavKey,
    onNavigate: (OrbitNavKey) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    mhaEnabled: Boolean = false
) {
    val haptics = LocalOrbitHaptics.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val planningGroup = listOf(
        DestinationItem(OrbitNavKey.Upcoming, "Upcoming", "Bills, subscriptions & recurring paychecks", OrbitIcons.Upcoming),
        DestinationItem(OrbitNavKey.Goals, "Goals", "Target savings & sinking funds", OrbitIcons.Goals),
        DestinationItem(OrbitNavKey.Categories, "Categories", "Budget structures & monthly caps", OrbitIcons.Categories),
        DestinationItem(OrbitNavKey.Rules, "Rules", "Automated merchant categorizations", OrbitIcons.Rules)
    )

    val insightsGroup = buildList {
        add(DestinationItem(OrbitNavKey.SpendingTrends, "Spending Trends", "Income vs expense breakdown & velocity", OrbitIcons.SpendingTrends))
        add(DestinationItem(OrbitNavKey.NetWorth, "Net Worth", "Historical assets, liabilities & trajectory", OrbitIcons.NetWorth))
        if (mhaEnabled) {
            add(DestinationItem(OrbitNavKey.MhaTracker, "MHA Tracker", "Ministerial housing allowance & tax tracking", OrbitIcons.MhaTracker))
        }
    }

    val calculatorsGroup = listOf(
        DestinationItem(OrbitNavKey.HousingCalculator, "Housing Calculator", "Mortgage, rent vs buy & affordability", OrbitIcons.HousingCalculator),
        DestinationItem(OrbitNavKey.RetirementCalculator, "Retirement Calculator", "Coast FIRE, nest egg & drawdowns", OrbitIcons.RetirementCalculator)
    )

    val systemGroup = listOf(
        DestinationItem(OrbitNavKey.Household, "Household", "Member profiles & account sharing", OrbitIcons.Household),
        DestinationItem(OrbitNavKey.Settings, "Settings", "Data storage, Google Drive & theme presets", OrbitIcons.Settings)
    )

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "More Destinations",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            DestinationSection("Planning & Tracking", planningGroup, currentKey, onNavigate, onDismiss, haptics::tick)
            Spacer(modifier = Modifier.height(16.dp))

            DestinationSection("Insights & Analytics", insightsGroup, currentKey, onNavigate, onDismiss, haptics::tick)
            Spacer(modifier = Modifier.height(16.dp))

            DestinationSection("Calculators & Projections", calculatorsGroup, currentKey, onNavigate, onDismiss, haptics::tick)
            Spacer(modifier = Modifier.height(16.dp))

            DestinationSection("Household & System", systemGroup, currentKey, onNavigate, onDismiss, haptics::tick)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DestinationSection(
    sectionTitle: String,
    items: List<DestinationItem>,
    currentKey: OrbitNavKey,
    onNavigate: (OrbitNavKey) -> Unit,
    onDismiss: () -> Unit,
    onTick: () -> Unit
) {
    Text(
        text = sectionTitle.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = OrbitTheme.colors.cardBorder
                    )
                }
                val isActive = currentKey == item.key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            onTick()
                            onNavigate(item.key)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
