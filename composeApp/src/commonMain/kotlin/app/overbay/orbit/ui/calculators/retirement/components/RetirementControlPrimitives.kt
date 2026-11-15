package app.overbay.orbit.ui.calculators.retirement.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.calculators.retirement.RetirementAmountField
import app.overbay.orbit.ui.components.OrbitCompactCurrencyField
import app.overbay.orbit.ui.components.OrbitModalScaffold
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Tickless capsule-track Material 3 slider with haptic feedback when quantized step changes
 * and zero caterpillar dots or trailing stop indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitSmoothSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragStateChanged: ((Boolean) -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val interactionSource = remember { MutableInteractionSource() }
    val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-5f)
    val fraction = ((clamped - valueRange.start) / span).coerceIn(0f, 1f)

    Slider(
        value = clamped,
        onValueChange = { raw ->
            onDragStateChanged?.invoke(true)
            onValueChange(raw)
        },
        onValueChangeFinished = {
            onDragStateChanged?.invoke(false)
        },
        valueRange = valueRange,
        steps = 0,
        interactionSource = interactionSource,
        thumb = {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(26.dp)
                    .background(accentColor, RoundedCornerShape(999.dp))
            )
        },
        track = {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            ) {
                val corner = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(
                    color = trackColor,
                    size = Size(size.width, size.height),
                    cornerRadius = corner
                )
                val fillW = (size.width * fraction).coerceIn(0f, size.width)
                if (fillW > 0f) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.88f),
                        size = Size(fillW, size.height),
                        cornerRadius = corner
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Formatted tabular currency pill that opens [QuickAmountSheet] on tap so scrolling pages never
 * accidentally summon the soft keyboard inside a vertical feed.
 */
@Composable
fun TappableAmountPill(
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val haptics = LocalOrbitHaptics.current
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = valueText,
                style = TabularNumStyle.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor
            )
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Amount",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAmountSheet(
    field: RetirementAmountField,
    initialCents: Cents,
    connectedBalanceCents: Cents,
    plannedMonthlySavingsCents: Cents,
    grossIncomeAnnualCents: Cents,
    onApply: (Cents) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    var draftCents by remember(field, initialCents) { mutableLongStateOf(initialCents) }

    OrbitModalSheet(onDismissRequest = onDismiss) {
        OrbitModalScaffold(
            title = field.title,
            subtitle = field.subtitle,
            confirmText = "Apply",
            dismissText = "Cancel",
            onDismiss = onDismiss,
            onConfirm = { onApply(draftCents) }
        ) {
            OrbitCompactCurrencyField(
                cents = draftCents,
                onCentsChange = { draftCents = max(0L, it) },
                inlineLabel = if (field.unitSuffix.isNotEmpty()) field.unitSuffix else null,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (field) {
                    RetirementAmountField.MONTHLY_SAVINGS -> {
                        QuickNudgeChip("-$250") { draftCents = max(0L, draftCents - 250_00L) }
                        QuickNudgeChip("+$250") { draftCents += 250_00L }
                        QuickNudgeChip("+$500") { draftCents += 500_00L }
                        if (plannedMonthlySavingsCents > 0L) {
                            QuickNudgeChip("Household (${MoneyUtils.formatCompactCents(plannedMonthlySavingsCents)})") {
                                haptics.confirm()
                                onResetDefault()
                                onDismiss()
                            }
                        }
                    }

                    RetirementAmountField.ANNUAL_SPENDING -> {
                        if (grossIncomeAnnualCents > 0L) {
                            val pct55 = (grossIncomeAnnualCents.toDouble() * 0.55).roundToLong()
                            val pct80 = (grossIncomeAnnualCents.toDouble() * 0.80).roundToLong()
                            QuickNudgeChip("55% Income (${MoneyUtils.formatCompactCents(pct55)})") { draftCents = pct55 }
                            QuickNudgeChip("80% Income (${MoneyUtils.formatCompactCents(pct80)})") { draftCents = pct80 }
                        }
                        QuickNudgeChip("-$5K") { draftCents = max(0L, draftCents - 5_000_00L) }
                        QuickNudgeChip("+$5K") { draftCents += 5_000_00L }
                    }

                    RetirementAmountField.STARTING_BALANCE -> {
                        QuickNudgeChip("+$10K") { draftCents += 10_000_00L }
                        QuickNudgeChip("+$50K") { draftCents += 50_000_00L }
                        QuickNudgeChip("Use Connected (${MoneyUtils.formatCompactCents(connectedBalanceCents)})") {
                            haptics.confirm()
                            onResetDefault()
                            onDismiss()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun AgeStepperChip(
    isIncrement: Boolean,
    description: String,
    onClick: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val glyphColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable {
                haptics.tick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier.size(30.dp)
        ) {
            Canvas(modifier = Modifier.size(30.dp)) {
                val strokePx = 1.8.dp.toPx()
                val arm = 4.5.dp.toPx()
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(
                    color = glyphColor,
                    start = Offset(cx - arm, cy),
                    end = Offset(cx + arm, cy),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
                if (isIncrement) {
                    drawLine(
                        color = glyphColor,
                        start = Offset(cx, cy - arm),
                        end = Offset(cx, cy + arm),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
internal fun BenchmarkSnapChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else OrbitTheme.colors.cardBorder
        ),
        modifier = modifier
            .clip(shape)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickNudgeChip(
    label: String,
    onClick: () -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TabularNumStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
