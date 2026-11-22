package app.overbay.orbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.ResolvedOrbitPalette
import app.overbay.orbit.theme.TabularNumStyle

/**
 * Reusable live mini-Orbit preview card demonstrating the full resolved palette:
 * 3-stop canopy header (`canopyStart..canopyEnd`, `canopyKicker`, `onCanopy`),
 * page canvas (`background`), card surface (`surface`), hairline (`cardBorder`),
 * primary accent links/progress, theme-tinted font inks (`onSurface`, `onSurfaceVariant`),
 * and harmonized financial badge (`incomeContainer`, `onIncomeContainer`).
 */
@Composable
fun LiveThemePreviewCard(
    palette: ResolvedOrbitPalette,
    modifier: Modifier = Modifier
) {
    val scheme = palette.colorScheme
    val custom = palette.customColors
    val spec = OrbitSprings.ColorEffectsDefault

    val canvasBg by animateColorAsState(scheme.background, spec, label = "previewCanvasBg")
    val cardSurface by animateColorAsState(scheme.surface, spec, label = "previewCardSurface")
    val trackColor by animateColorAsState(scheme.surfaceContainerHigh, spec, label = "previewTrack")
    val borderColor by animateColorAsState(custom.cardBorder, spec, label = "previewBorder")
    val canopyStart by animateColorAsState(custom.canopyStart, spec, label = "previewCanopyStart")
    val canopyMid by animateColorAsState(custom.canopyMid, spec, label = "previewCanopyMid")
    val canopyEnd by animateColorAsState(custom.canopyEnd, spec, label = "previewCanopyEnd")
    val canopyKicker by animateColorAsState(custom.canopyKicker, spec, label = "previewCanopyKicker")
    val onCanopy by animateColorAsState(custom.onCanopy, spec, label = "previewOnCanopy")
    val primaryColor by animateColorAsState(scheme.primary, spec, label = "previewPrimary")
    val onSurface by animateColorAsState(scheme.onSurface, spec, label = "previewOnSurface")
    val onSurfaceVariant by animateColorAsState(scheme.onSurfaceVariant, spec, label = "previewOnSurfaceVar")
    val incomeContainer by animateColorAsState(custom.incomeContainer, spec, label = "previewIncomeCont")
    val onIncomeContainer by animateColorAsState(custom.onIncomeContainer, spec, label = "previewOnIncomeCont")

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = canvasBg,
        shadowElevation = 2.dp,
        border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.42f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Canopy header strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(canopyStart, canopyMid, canopyEnd)))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "THIS MONTH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = canopyKicker,
                            letterSpacing = 0.9.sp
                        )
                        RollingTickerCents(
                            cents = 142_050L,
                            style = TabularNumStyle.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold),
                            color = onCanopy
                        )
                    }
                    OrbitBadge(
                        text = "+24% saved",
                        containerColor = incomeContainer,
                        contentColor = onIncomeContainer
                    )
                }
            }

            // 2. Card resting on tinted page canvas
            Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardSurface,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Budget",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Manage",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryColor
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Groceries & Dining",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = onSurfaceVariant
                            )
                            Text(
                                text = "\$540 / \$800",
                                style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = onSurfaceVariant
                            )
                        }

                        OrbitPillProgressBar(
                            percent = 0.675f,
                            customFillColor = primaryColor,
                            trackColor = trackColor,
                            height = 7.dp
                        )

                        HorizontalDivider(thickness = 1.dp, color = borderColor)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Everyday Checking",
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "\$6,420.00",
                                style = TabularNumStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
