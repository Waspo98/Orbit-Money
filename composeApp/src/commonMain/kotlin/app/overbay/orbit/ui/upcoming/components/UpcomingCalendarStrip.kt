package app.overbay.orbit.ui.upcoming.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun UpcomingCalendarStrip(
    selectedDate: String?,
    items: List<UpcomingItem>,
    onSelectDate: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val todayIso = LocalDateUtils.todayIsoDate()
    val todayParsed = LocalDateUtils.parseIsoDate(todayIso) ?: return

    // Next 14 days
    val dates = (0..13).map { dayOffset ->
        LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(todayParsed, dayOffset))
    }

    val itemDates = items.map { it.nextDate }.toSet()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dates.forEach { dateIso ->
            val isSelected = selectedDate == dateIso
            val isToday = dateIso == todayIso
            val hasItems = itemDates.contains(dateIso)
            val parsed = LocalDateUtils.parseIsoDate(dateIso)

            val dayOfWeek = parsed?.dayOfWeek?.name?.take(3) ?: ""
            val dayNum = parsed?.dayOfMonth?.toString() ?: ""

            Surface(
                onClick = {
                    haptics.tick()
                    onSelectDate(if (isSelected) null else dateIso)
                },
                shape = RoundedCornerShape(16.dp),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = if (isSelected || isToday) 1.5.dp else 1.dp,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    }
                ),
                modifier = Modifier.width(52.dp).height(74.dp)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = dayOfWeek,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = dayNum,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (hasItems) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                        )
                    } else {
                        Spacer(modifier = Modifier.size(5.dp))
                    }
                }
            }
        }
    }
}
