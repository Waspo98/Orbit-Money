package app.overbay.orbit.ui.components.charts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme

enum class DeltaTone {
    POSITIVE, // Gain, Under budget, Income (Emerald)
    NEGATIVE, // Loss, Over budget, Expense (Terracotta)
    NEUTRAL   // Informational / Gray
}

/**
 * Dynamic sticky header for interactive Canvas charts.
 * Seamlessly morphs between idle overview metrics and touch-scrubbed period readouts.
 */
@Composable
fun ChartScrubberHeader(
    title: String,
    formattedValue: String,
    modifier: Modifier = Modifier,
    deltaText: String? = null,
    deltaTone: DeltaTone = DeltaTone.NEUTRAL,
    subtitle: String? = null,
    isScrubbing: Boolean = false,
    scrubDateText: String? = null
) {
    val themeColors = OrbitTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 1. Top context row: Idle Title vs. Active Scrubbing Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = isScrubbing to scrubDateText,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "scrubberHeaderTitle"
            ) { (scrubbing, dateText) ->
                if (scrubbing && !dateText.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isScrubbing) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = "Scrubbing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Primary Hero Metric (Tabular Numerals)
        Text(
            text = formattedValue,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        // 3. Bottom Row: Delta pill & subtitle detail
        if (deltaText != null || subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (deltaText != null) {
                    val (pillBg, pillText) = when (deltaTone) {
                        DeltaTone.POSITIVE -> {
                            themeColors.income.copy(alpha = 0.15f) to themeColors.income
                        }
                        DeltaTone.NEGATIVE -> {
                            themeColors.expense.copy(alpha = 0.15f) to themeColors.expense
                        }
                        DeltaTone.NEUTRAL -> {
                            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = pillBg
                    ) {
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = pillText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
