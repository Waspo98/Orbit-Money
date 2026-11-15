package app.overbay.orbit.ui.accounts.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.util.LocalOrbitHaptics

private enum class PerkToggleVisualState { UNUSED, PARTIAL, FULL }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardPerkRow(
    title: String,
    subtitle: String?,
    valueCents: Cents,
    usedCents: Cents,
    daysUntilReset: Int?,
    onToggle: () -> Unit,
    onEditAmount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isFullyUsed = if (valueCents > 0L) usedCents >= valueCents else usedCents > 0L
    val isPartial = valueCents > 0L && usedCents > 0L && usedCents < valueCents
    val visualState = when {
        isFullyUsed -> PerkToggleVisualState.FULL
        isPartial -> PerkToggleVisualState.PARTIAL
        else -> PerkToggleVisualState.UNUSED
    }
    val partialFraction = if (valueCents > 0L) (usedCents.toFloat() / valueCents.toFloat()).coerceIn(0f, 1f) else 0f
    val remainingCents = (valueCents - usedCents).coerceAtLeast(0L)

    val titleColor by animateColorAsState(
        targetValue = if (isFullyUsed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "perkTitleColor"
    )
    val valueColor by animateColorAsState(
        targetValue = if (isFullyUsed) OrbitTheme.colors.income else MaterialTheme.colorScheme.primary,
        animationSpec = OrbitSprings.ColorEffectsFast,
        label = "perkValueColor"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconToggleButton(
                checked = isFullyUsed,
                onCheckedChange = { willBeFull ->
                    if (!isFullyUsed || willBeFull) haptics.confirm() else haptics.tick()
                    onToggle()
                },
                modifier = Modifier.size(48.dp)
            ) {
                AnimatedContent(
                    targetState = visualState,
                    transitionSpec = {
                        (scaleIn(OrbitSprings.FloatSpatialFast, initialScale = 0.72f) + fadeIn(OrbitSprings.FloatEffectsFast)) togetherWith
                            (scaleOut(OrbitSprings.FloatSpatialFast, targetScale = 0.72f) + fadeOut(OrbitSprings.FloatEffectsFast))
                    },
                    label = "perkCheckToggle"
                ) { toggleState ->
                    when (toggleState) {
                        PerkToggleVisualState.FULL -> Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Perk Used",
                            tint = OrbitTheme.colors.income,
                            modifier = Modifier.size(24.dp)
                        )
                        PerkToggleVisualState.PARTIAL -> PartialProgressRing(
                            fraction = partialFraction,
                            modifier = Modifier.size(22.dp)
                        )
                        PerkToggleVisualState.UNUSED -> Box(
                            modifier = Modifier
                                .size(22.dp)
                                .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.tick()
                        onEditAmount()
                    }
                    .padding(vertical = 2.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (isFullyUsed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!isFullyUsed && daysUntilReset != null && daysUntilReset <= 7) {
                        val urgencyTone = if (daysUntilReset <= 2) CardStatusTone.CRITICAL else CardStatusTone.WARNING
                        val urgencyText = if (daysUntilReset == 0) "Ends Today" else "${daysUntilReset}d Left"
                        CardStatusPill(text = urgencyText, tone = urgencyTone)
                    } else if (!isFullyUsed && daysUntilReset != null) {
                        Text(
                            text = "\u00B7 Resets ${CardFormat.formatDaysUntil(daysUntilReset)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (isPartial) {
                    Text(
                        text = "${CardFormat.formatAdaptiveCents(usedCents)} used \u00B7 ${CardFormat.formatAdaptiveCents(remainingCents)} left",
                        style = TabularNumStyle.copy(fontSize = 11.5.sp),
                        color = OrbitTheme.colors.warning
                    )
                    OrbitPillProgressBar(
                        percent = partialFraction,
                        height = 4.dp,
                        customFillColor = OrbitTheme.colors.warning
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (valueCents > 0L) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = MoneyUtils.formatCents(if (isPartial) remainingCents else valueCents),
                        style = TabularNumStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = valueColor
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.tick()
                                onEditAmount()
                            }
                    ) {
                        Text(
                            text = if (isPartial || isFullyUsed) "Edit" else "Log",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartialProgressRing(fraction: Float, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val arcColor = OrbitTheme.colors.warning
    Canvas(modifier = modifier) {
        val strokePx = 2.6.dp.toPx()
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
        drawCircle(color = trackColor, style = stroke)
        drawArc(
            color = arcColor,
            startAngle = -90f,
            sweepAngle = (360f * fraction).coerceAtLeast(24f),
            useCenter = false,
            style = stroke
        )
        drawCircle(color = arcColor, radius = 2.8.dp.toPx())
    }
}
