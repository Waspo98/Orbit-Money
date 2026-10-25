package app.overbay.orbit.ui.components.charts

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import kotlin.math.min

/**
 * Tactile circular / open horseshoe sweep arc gauge for Budget pacing and safe-to-spend remainder.
 * Features animated spring transitions, warning overbudget states, and center tabular readout.
 */
@Composable
fun BudgetRingGauge(
    spentCents: Cents,
    budgetCents: Cents,
    modifier: Modifier = Modifier,
    gaugeSize: Dp = 220.dp,
    ringThickness: Dp = 16.dp,
    safeSpendDailyCents: Cents? = null,
    paceStatus: BudgetPaceStatus = BudgetPaceStatus.ON_TRACK
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { ringThickness.toPx() }

    val rawRatio = if (budgetCents > 0L) spentCents.toDouble() / budgetCents.toDouble() else 0.0
    val isOverBudget = rawRatio > 1.0

    // Animated sweep arc progress with spring physics
    val animatedProgress by animateFloatAsState(
        targetValue = rawRatio.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "budgetGaugeSweep"
    )

    // Pre-allocated Stroke instances (Zero allocation in Canvas onDraw)
    val trackStroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }
    val progressStroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx * 1.08f, cap = StrokeCap.Round)
    }

    val themeColors = OrbitTheme.colors
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val statusColor = when {
        isOverBudget || paceStatus == BudgetPaceStatus.OVER_BUDGET -> themeColors.expense
        paceStatus == BudgetPaceStatus.WATCH || rawRatio >= 0.85 -> themeColors.warning
        else -> themeColors.income
    }

    // Horseshoe Geometry: 260 degree arc, starting at 140 degrees
    val startAngle = 140f
    val totalSweep = 260f

    Box(
        modifier = modifier.size(gaugeSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val d = min(size.width, size.height)
            val padding = strokeWidthPx / 2f + 4f
            val arcSize = Size(d - padding * 2, d - padding * 2)
            val topLeft = Offset((size.width - arcSize.width) / 2f, (size.height - arcSize.height) / 2f)

            // 1. Draw Horseshoe Background Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = trackStroke
            )

            // 2. Draw Active Progress Sweep Arc
            val sweep = animatedProgress * totalSweep
            if (sweep > 0.5f) {
                drawArc(
                    color = statusColor,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = progressStroke
                )
            }
        }

        // 3. Center Metric Readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            val remainingCents = budgetCents - spentCents
            val heroValueText = if (isOverBudget) {
                MoneyUtils.formatCents(-remainingCents, showCents = false)
            } else {
                MoneyUtils.formatCents(remainingCents, showCents = false)
            }

            Text(
                text = if (isOverBudget) "OVER BUDGET" else "LEFT TO SPEND",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = heroValueText,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = if (isOverBudget) statusColor else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Sub-pill: Pace or Daily Safe Spend
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusColor.copy(alpha = 0.14f)
            ) {
                val pillText = when {
                    isOverBudget -> "${(rawRatio * 100).toInt()}% Used"
                    safeSpendDailyCents != null && safeSpendDailyCents > 0L -> {
                        "${MoneyUtils.formatCents(safeSpendDailyCents, showCents = false)}/day safe"
                    }
                    else -> "${(rawRatio * 100).toInt()}% Spent"
                }

                Text(
                    text = pillText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
