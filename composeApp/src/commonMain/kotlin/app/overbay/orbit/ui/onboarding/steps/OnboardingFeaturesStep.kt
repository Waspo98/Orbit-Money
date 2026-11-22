package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingStepResolver
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun OnboardingFeaturesStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OnboardingHeroHeader(
            title = "What Do You Want to Track?",
            subtitle = "Toggle any of these tools on or off—you can always change this later in Settings.",
            icon = OrbitIcons.ViewSliders
        )

        Spacer(modifier = Modifier.height(4.dp))

        OnboardingStepResolver.ALL_OPTIONAL_FEATURES.forEach { feature ->
            val checked = feature.routeName in state.enabledFeatures
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (checked) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(
                    width = if (checked) 1.5.dp else 1.dp,
                    color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        haptics.tick()
                        onIntent(OnboardingIntent.ToggleFeature(feature.routeName))
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = featureIconFor(feature.routeName),
                                contentDescription = null,
                                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = feature.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = feature.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }

                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            haptics.tick()
                            onIntent(OnboardingIntent.ToggleFeature(feature.routeName))
                        }
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Core tools (Dashboard, Transactions, Budgets, Accounts, Categories, and Rules) stay on automatically. All 9 tools above can be toggled or reordered anytime under Settings → Turn On/Off App Features.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

private fun featureIconFor(routeName: String): ImageVector = when (routeName) {
    "card_perks" -> OrbitIcons.CardPerks
    "upcoming" -> OrbitIcons.Upcoming
    "goals" -> OrbitIcons.Goals
    "spending_trends" -> OrbitIcons.SpendingTrends
    "net_worth" -> OrbitIcons.NetWorth
    "mha" -> OrbitIcons.MhaTracker
    "housing_calculator" -> OrbitIcons.HousingCalculator
    "retirement_calculator" -> OrbitIcons.RetirementCalculator
    "household" -> OrbitIcons.Household
    else -> OrbitIcons.Dashboard
}
