package app.overbay.orbit.ui.mha.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.MhaYearTaxRecord
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.components.OrbitTextField
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun MhaYearRateSection(
    selectedYear: Int,
    currentYear: Int,
    activeYearRecord: MhaYearTaxRecord?,
    pinCustomRate: Boolean,
    onTogglePinCustomRate: (Boolean) -> Unit,
    customEffectiveRateText: String,
    onCustomEffectiveRateChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHistorical = selectedYear < currentYear
    val colors = OrbitTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (activeYearRecord != null && !isHistorical && !pinCustomRate) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "$selectedYear Effective Tax Rate Recorded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatTwoDecimals(activeYearRecord.effectiveRatePercent)}% saved automatically for $selectedYear history",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        OrbitSwitchRow(
            title = if (isHistorical) "Saved $selectedYear Effective Rate" else "Lock Custom $selectedYear Rate",
            subtitle = when {
                pinCustomRate -> "Using stored $selectedYear effective rate instead of live brackets"
                isHistorical -> "Currently estimated from live profile — enable to store your $selectedYear rate"
                else -> "Auto-calculating live rate; enable to lock a fixed effective rate for $selectedYear"
            },
            checked = pinCustomRate,
            onCheckedChange = onTogglePinCustomRate
        )

        OrbitAccordionVisibility(visible = pinCustomRate) {
            OrbitTextField(
                value = customEffectiveRateText,
                onValueChange = onCustomEffectiveRateChange,
                label = { Text("$selectedYear Effective Tax Rate (%)") },
                suffixText = "%",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

internal fun formatTwoDecimals(value: Double): String {
    val hundredths = (value * 100.0).roundToLong()
    val whole = hundredths / 100
    val frac = abs(hundredths % 100)
    return when {
        frac == 0L -> "$whole"
        frac % 10L == 0L -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
}
