package app.overbay.orbit.ui.components.charts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.ProjectionPoint
import app.overbay.orbit.domain.RetirementScenarioModel
import app.overbay.orbit.theme.OrbitSprings
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Constant-height (`32.dp`) readout strip above [RetirementProjectionChart] that crossfades
 * between idle scenario legend pills and active touch-scrubbed age readouts with zero vertical layout shift.
 */
@Composable
internal fun RetirementChartReadoutBar(
    scrubbedAge: Double?,
    scenarios: Map<RetirementScenarioPreset, RetirementScenarioModel>,
    selectedPreset: RetirementScenarioPreset,
    presetColors: Map<RetirementScenarioPreset, Color>,
    modifier: Modifier = Modifier
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val containerAlpha by animateFloatAsState(
        targetValue = if (scrubbedAge != null) 1f else 0.55f,
        animationSpec = OrbitSprings.FloatEffectsFast,
        label = "retirementReadoutAlpha"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = containerAlpha),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .height(32.dp)
    ) {
        AnimatedContent(
            targetState = scrubbedAge,
            contentKey = { it != null },
            transitionSpec = {
                fadeIn(OrbitSprings.FloatEffectsFast) togetherWith
                    fadeOut(OrbitSprings.FloatEffectsFast)
            },
            contentAlignment = Alignment.CenterStart,
            label = "retirementScrubReadout"
        ) { activeAge ->
            if (activeAge != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AGE ${activeAge.roundToInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RetirementScenarioPreset.entries.forEach { p ->
                            val color = presetColors[p] ?: onSurfaceVariant
                            val bal = interpolateBalanceAtAge(scenarios[p]?.accumulationPoints ?: emptyList(), activeAge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .height(8.dp)
                                        .width(8.dp)
                                        .background(color, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = MoneyUtils.formatCompactCents(bal),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = if (p == selectedPreset) FontWeight.Bold else FontWeight.Normal,
                                    color = if (p == selectedPreset) color else onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Drag horizontally to inspect age",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RetirementScenarioPreset.entries.forEach { p ->
                            val color = presetColors[p] ?: onSurfaceVariant
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .height(7.dp)
                                        .width(7.dp)
                                        .background(color, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = p.name.take(4).lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (p == selectedPreset) FontWeight.Bold else FontWeight.Normal,
                                    color = if (p == selectedPreset) color else onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun interpolateBalanceAtAge(points: List<ProjectionPoint>, age: Double): Cents {
    if (points.isEmpty()) return 0L
    if (age <= points.first().age) return points.first().balanceCents
    if (age >= points.last().age) return points.last().balanceCents
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val next = points[i]
        if (age <= next.age) {
            val span = max(0.0001, next.age - prev.age)
            val ratio = (age - prev.age) / span
            return (prev.balanceCents.toDouble() + (next.balanceCents - prev.balanceCents).toDouble() * ratio).toLong()
        }
    }
    return points.last().balanceCents
}
