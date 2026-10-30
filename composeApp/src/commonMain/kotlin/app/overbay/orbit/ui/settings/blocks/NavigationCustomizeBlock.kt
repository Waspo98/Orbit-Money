package app.overbay.orbit.ui.settings.blocks

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitSectionCard

private data class NavCustomItem(val routeName: String, val title: String)

@Composable
fun NavigationCustomizeBlock(
    preferences: OrbitPreferences,
    onToggleRouteHidden: (String) -> Unit,
    onMoveRoute: (fromIndex: Int, toIndex: Int, currentList: List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultRoutes = remember {
        listOf(
            NavCustomItem("upcoming", "Upcoming"),
            NavCustomItem("goals", "Goals"),
            NavCustomItem("categories", "Categories"),
            NavCustomItem("rules", "Rules"),
            NavCustomItem("spending_trends", "Spending Trends"),
            NavCustomItem("net_worth", "Net Worth"),
            NavCustomItem("mha", "MHA Tracker"),
            NavCustomItem("housing_calculator", "Housing Calculator"),
            NavCustomItem("retirement_calculator", "Retirement Calculator"),
            NavCustomItem("household", "Household")
        )
    }

    val orderedRoutes = remember(preferences.moreRouteOrder) {
        if (preferences.moreRouteOrder.isNotEmpty()) {
            val orderMap = preferences.moreRouteOrder.withIndex().associate { it.value to it.index }
            defaultRoutes.sortedBy { orderMap[it.routeName] ?: 999 }
        } else {
            defaultRoutes
        }
    }

    val currentRouteStrings = remember(orderedRoutes) { orderedRoutes.map { it.routeName } }

    OrbitSectionCard(
        title = "Navigation & Feature Visibility",
        subtitle = "Toggle secondary tool visibility and customize ordering in More menu",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "MORE MENU DESTINATIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    orderedRoutes.forEachIndexed { index, item ->
                        val isVisible = !preferences.hiddenRouteNames.contains(item.routeName)

                        if (index > 0) {
                            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isVisible,
                                onCheckedChange = { onToggleRouteHidden(item.routeName) }
                            )

                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isVisible) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )

                            // Up button
                            IconButton(
                                onClick = { onMoveRoute(index, index - 1, currentRouteStrings) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Down button
                            IconButton(
                                onClick = { onMoveRoute(index, index + 1, currentRouteStrings) },
                                enabled = index < orderedRoutes.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    tint = if (index < orderedRoutes.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
