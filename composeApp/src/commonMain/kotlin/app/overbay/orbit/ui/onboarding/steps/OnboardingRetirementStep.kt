package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingRetirementStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val currentYear = remember { LocalDateUtils.todayLocalDate().year }
    val primaryBirthYear = remember(state.householdMembers) {
        state.householdMembers
            .firstOrNull { !it.role.equals("dependent", ignoreCase = true) && !it.birthDate.isNullOrBlank() }
            ?.birthDate
            ?.take(4)
            ?.toIntOrNull()
    }
    val countdownText = remember(state.retirementAge, primaryBirthYear, currentYear) {
        if (primaryBirthYear == null) {
            null
        } else {
            val targetYear = primaryBirthYear + state.retirementAge
            val yearsAway = (targetYear - currentYear).coerceAtLeast(0)
            if (yearsAway == 0) {
                "Target year $targetYear · Ready now"
            } else {
                "In $yearsAway year${if (yearsAway == 1) "" else "s"} ($targetYear)"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingHeroHeader(
            title = "When Do You Hope to Retire?",
            subtitle = "This sets the starting target on your Dashboard and Retirement Calculator—you can experiment freely later.",
            icon = OrbitIcons.RetirementCalculator
        )

        Spacer(modifier = Modifier.height(20.dp))

        OnboardingCardContainer {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TARGET RETIREMENT AGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Age ${state.retirementAge}",
                    style = TabularNumStyle.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (countdownText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = countdownText,
                            style = TabularNumStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Slider(
                value = state.retirementAge.toFloat(),
                onValueChange = { raw ->
                    val snapped = raw.roundToInt().coerceIn(40, 75)
                    if (snapped != state.retirementAge) {
                        haptics.tick()
                        onIntent(OnboardingIntent.UpdateRetirementAge(snapped))
                    }
                },
                valueRange = 40f..75f,
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(55 to "55 · Early", 60 to "60", 62 to "62", 65 to "65", 67 to "67").forEach { (age, label) ->
                    FilterChip(
                        selected = state.retirementAge == age,
                        onClick = {
                            haptics.tick()
                            onIntent(OnboardingIntent.UpdateRetirementAge(age))
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OnboardingCardContainer {
            Text(
                text = "Growth Assumption",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OrbitSegmentedTabRow(
                items = RetirementScenarioPreset.entries,
                selected = state.retirementPreset,
                onSelect = { onIntent(OnboardingIntent.UpdateRetirementPreset(it)) },
                label = { it.label }
            )
            val presetNote = when (state.retirementPreset) {
                RetirementScenarioPreset.CONSERVATIVE -> "5.0% annual return, 3.0% inflation — cautious, lower-volatility estimate."
                RetirementScenarioPreset.BALANCED -> "7.0% annual return, 2.5% inflation — typical diversified index portfolio."
                RetirementScenarioPreset.AGGRESSIVE -> "8.5% annual return, 2.25% inflation — equity-heavy long-term growth."
            }
            Text(
                text = presetNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}
