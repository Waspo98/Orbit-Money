package app.overbay.orbit.ui.mha.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.overbay.orbit.ui.components.CanopyIconButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.PeriodNavigatorPill

@Composable
internal fun RowScope.MhaCanopyBar(
    selectedYear: Int,
    availableYears: List<Int>,
    canGoNext: Boolean,
    isCompact: Boolean,
    barHeight: Dp,
    showYearMenu: Boolean,
    onToggleYearMenu: (Boolean) -> Unit,
    onSelectYear: (Int) -> Unit,
    showConfigSheet: Boolean,
    onToggleConfigSheet: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        PeriodNavigatorPill(
            label = "Tax Year $selectedYear",
            compactLabel = "$selectedYear",
            isCompact = isCompact,
            onPrevious = { onSelectYear(selectedYear - 1) },
            onNext = { onSelectYear(selectedYear + 1) },
            canGoNext = canGoNext,
            onClickLabel = { onToggleYearMenu(true) },
            modifier = Modifier.fillMaxSize()
        )

        DropdownMenu(
            expanded = showYearMenu,
            onDismissRequest = { onToggleYearMenu(false) }
        ) {
            availableYears.forEach { yr ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "$yr",
                            fontWeight = if (yr == selectedYear) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelectYear(yr)
                        onToggleYearMenu(false)
                    }
                )
            }
        }
    }

    CanopyIconButton(
        icon = OrbitIcons.ViewSliders,
        contentDescription = "Configure MHA Limits",
        onClick = onToggleConfigSheet,
        buttonSize = barHeight,
        isActive = showConfigSheet
    )

    CanopySettingsButton(
        buttonSize = barHeight,
        onClick = onOpenSettings
    )
}
