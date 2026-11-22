package app.overbay.orbit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended Material 3 Expressive semantic colors designed specifically
 * for financial workflows, transaction provenance, and sticky canopy headers.
 */
@Immutable
data class CustomThemeColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val danger: Color = FinanceDangerLight,
    val onDanger: Color = Color.White,
    val dangerContainer: Color = FinanceDangerContainerLight,
    val onDangerContainer: Color = Color(0xFF991B1B),
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val transfer: Color,
    val onTransfer: Color,
    val canopyStart: Color,
    val canopyEnd: Color,
    val onCanopy: Color,
    val cardBorder: Color,
    val chartPositive: Color,
    val chartNegative: Color,
    val chartNeutral: Color,
    val chartGrid: Color,
    val canopyMid: Color = canopyEnd,
    val canopyGlow: Color = Color.Transparent,
    val canopyKicker: Color = onCanopy,
    val heroStatGood: Color = Color(0xFF8FF0C9),
    val heroStatWarn: Color = Color(0xFFFFE28A),
    val heroStatCaution: Color = Color(0xFFFFB3A8)
)

val LocalCustomThemeColors = staticCompositionLocalOf {
    emeraldCustomColors(isDark = false)
}

fun emeraldCustomColors(isDark: Boolean): CustomThemeColors =
    if (isDark) OrbitPresetPalettes.emeraldDark.customColors else OrbitPresetPalettes.emeraldLight.customColors
