package app.overbay.orbit.ui.components.charts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.RetirementScenarioPreset
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.RetirementCalculatorEngine
import app.overbay.orbit.domain.ProjectionPoint
import app.overbay.orbit.domain.RetirementScenarioModel
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun rememberScenarioPresetColors(): Map<RetirementScenarioPreset, Color> {
    val conservative = OrbitTheme.colors.transfer
    val balanced = MaterialTheme.colorScheme.primary
    val aggressive = MaterialTheme.colorScheme.tertiary
    return remember(conservative, balanced, aggressive) {
        mapOf(
            RetirementScenarioPreset.CONSERVATIVE to conservative,
            RetirementScenarioPreset.BALANCED to balanced,
            RetirementScenarioPreset.AGGRESSIVE to aggressive
        )
    }
}

internal fun DrawScope.drawRetirementGridAndYAxis(
    textMeasurer: TextMeasurer,
    axisStyle: TextStyle,
    gridColor: Color,
    plotW: Float,
    topPadPx: Float,
    plotH: Float,
    maxBalance: Cents
) {
    val levels = listOf(1f to maxBalance, 0.5f to (maxBalance / 2L), 0f to 0L)
    levels.forEach { (ratio, cents) ->
        val y = topPadPx + plotH * (1f - ratio)
        drawLine(
            color = gridColor.copy(alpha = 0.5f),
            start = Offset(0f, y),
            end = Offset(plotW, y),
            strokeWidth = 1.dp.toPx()
        )
        val measured = textMeasurer.measure(MoneyUtils.formatCompactCents(cents), style = axisStyle, maxLines = 1)
        val labelY = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
        drawText(measured, topLeft = Offset(plotW + 6.dp.toPx(), labelY))
    }
}

/**
 * Constant-height (`44.dp`) interactive bar above [RetirementProjectionChart].
 * Displays selectable scenario pills (Conservative / Balanced / Aggressive with nominal rates)
 * when idle, and crossfades to age-scrubbed scenario values during horizontal chart touch.
 */
@Composable
internal fun RetirementChartReadoutBar(
    scrubbedAge: Double?,
    scenarios: Map<RetirementScenarioPreset, RetirementScenarioModel>,
    selectedPreset: RetirementScenarioPreset,
    presetColors: Map<RetirementScenarioPreset, Color>,
    onSelectPreset: (RetirementScenarioPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val containerAlpha by animateFloatAsState(
        targetValue = if (scrubbedAge != null) 0.96f else 0f,
        animationSpec = OrbitSprings.FloatEffectsFast,
        label = "retirementReadoutAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .height(44.dp)
    ) {
        AnimatedContent(
            targetState = scrubbedAge != null,
            transitionSpec = {
                fadeIn(OrbitSprings.FloatEffectsFast) togetherWith
                    fadeOut(OrbitSprings.FloatEffectsFast)
            },
            label = "retirementReadoutMode"
        ) { isScrubbing ->
            if (isScrubbing) {
                val activeAge = scrubbedAge ?: return@AnimatedContent
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = containerAlpha.coerceAtLeast(0.75f)),
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AGE ${activeAge.roundToInt()}",
                            style = TabularNumStyle.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RetirementScenarioPreset.entries.forEach { preset ->
                                val color = presetColors[preset] ?: onSurfaceVariant
                                val bal = interpolateBalanceAtAge(
                                    scenarios[preset]?.accumulationPoints ?: emptyList(),
                                    activeAge
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(color, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = MoneyUtils.formatCompactCents(bal),
                                        style = TabularNumStyle.copy(fontSize = 12.sp),
                                        fontWeight = if (preset == selectedPreset) FontWeight.Bold else FontWeight.Normal,
                                        color = if (preset == selectedPreset) color else onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RetirementScenarioPreset.entries.forEach { preset ->
                        val isSelected = preset == selectedPreset
                        val accent = presetColors[preset] ?: MaterialTheme.colorScheme.primary
                        val pillShape = RoundedCornerShape(12.dp)
                        Surface(
                            shape = pillShape,
                            color = if (isSelected) {
                                accent.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) accent.copy(alpha = 0.55f) else OrbitTheme.colors.cardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(pillShape)
                                .clickable {
                                    haptics.tick()
                                    onSelectPreset(preset)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(accent, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${RetirementCalculatorEngine.formatRatePercent(preset.nominalAnnualReturn)} nom",
                                    style = TabularNumStyle.copy(fontSize = 10.sp),
                                    color = if (isSelected) accent else onSurfaceVariant.copy(alpha = 0.78f),
                                    maxLines = 1
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
