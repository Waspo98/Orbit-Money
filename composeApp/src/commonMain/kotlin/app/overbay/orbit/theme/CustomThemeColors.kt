package app.overbay.orbit.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

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
    val canopyGlow: Color = Color(0x474DD0E1),
    val heroStatGood: Color = Color(0xFF8FF0C9),
    val heroStatWarn: Color = Color(0xFFFFE28A),
    val heroStatCaution: Color = Color(0xFFFFB3A8)
)

val LocalCustomThemeColors = staticCompositionLocalOf {
    emeraldCustomColors(isDark = false)
}

fun emeraldCustomColors(isDark: Boolean): CustomThemeColors = if (isDark) {
    CustomThemeColors(
        income = FinanceIncomeDark,
        onIncome = Color(0xFF0A1410),
        incomeContainer = FinanceIncomeContainerDark,
        onIncomeContainer = Color(0xFFA7F3D0),
        expense = FinanceExpenseDark,
        onExpense = Color(0xFF0A1410),
        expenseContainer = FinanceExpenseContainerDark,
        onExpenseContainer = Color(0xFFFECACA),
        danger = FinanceDangerDark,
        onDanger = Color(0xFF3F1212),
        dangerContainer = FinanceDangerContainerDark,
        onDangerContainer = Color(0xFFFCA5A5),
        warning = FinanceWarningDark,
        onWarning = Color(0xFF3F2A08),
        warningContainer = FinanceWarningContainerDark,
        onWarningContainer = Color(0xFFFDE68A),
        transfer = FinanceTransferDark,
        onTransfer = Color(0xFF0A1410),
        canopyStart = Color(0xFF04271D),
        canopyMid = Color(0xFF064E3B),
        canopyEnd = Color(0xFF084C36),
        onCanopy = Color(0xFFE8ECE6),
        cardBorder = Color(0xFF243330),
        chartPositive = FinanceIncomeDark,
        chartNegative = FinanceDangerDark,
        chartNeutral = FinanceTransferDark,
        chartGrid = Color(0x12FFFFFF)
    )
} else {
    CustomThemeColors(
        income = FinanceIncomeLight,
        onIncome = Color.White,
        incomeContainer = FinanceIncomeContainerLight,
        onIncomeContainer = Color(0xFF064E3B),
        expense = FinanceExpenseLight,
        onExpense = Color.White,
        expenseContainer = FinanceExpenseContainerLight,
        onExpenseContainer = Color(0xFF991B1B),
        danger = FinanceDangerLight,
        onDanger = Color.White,
        dangerContainer = FinanceDangerContainerLight,
        onDangerContainer = Color(0xFF991B1B),
        warning = FinanceWarningLight,
        onWarning = Color.White,
        warningContainer = FinanceWarningContainerLight,
        onWarningContainer = Color(0xFF713F12),
        transfer = FinanceTransferLight,
        onTransfer = Color.White,
        canopyStart = Color(0xFF052E24),
        canopyMid = Color(0xFF065F46),
        canopyEnd = Color(0xFF047857),
        onCanopy = Color.White,
        cardBorder = Color(0xFFE4E2D7),
        chartPositive = FinanceIncomeLight,
        chartNegative = FinanceDangerLight,
        chartNeutral = FinanceTransferLight,
        chartGrid = Color(0x0F000000)
    )
}

fun oceanSlateCustomColors(isDark: Boolean): CustomThemeColors {
    val base = emeraldCustomColors(isDark)
    return if (isDark) {
        base.copy(
            canopyStart = Color(0xFF002025),
            canopyEnd = Color(0xFF004F58),
            onCanopy = Color(0xFFDEE3E5)
        )
    } else {
        base.copy(
            canopyStart = Color(0xFF00363D),
            canopyEnd = Color(0xFF006874),
            onCanopy = Color.White
        )
    }
}

fun warmTerracottaCustomColors(isDark: Boolean): CustomThemeColors {
    val base = emeraldCustomColors(isDark)
    return if (isDark) {
        base.copy(
            canopyStart = Color(0xFF3E0A00),
            canopyEnd = Color(0xFF7A2912),
            onCanopy = Color(0xFFEDE0DD)
        )
    } else {
        base.copy(
            canopyStart = Color(0xFF5C1603),
            canopyEnd = Color(0xFFA43E23),
            onCanopy = Color.White
        )
    }
}

fun royalIndigoCustomColors(isDark: Boolean): CustomThemeColors {
    val base = emeraldCustomColors(isDark)
    return if (isDark) {
        base.copy(
            canopyStart = Color(0xFF081452),
            canopyEnd = Color(0xFF293CA0),
            onCanopy = Color(0xFFE3E1E9)
        )
    } else {
        base.copy(
            canopyStart = Color(0xFF0E2388),
            canopyEnd = Color(0xFF4355B9),
            onCanopy = Color.White
        )
    }
}

fun monochromeInkCustomColors(isDark: Boolean): CustomThemeColors {
    val base = emeraldCustomColors(isDark)
    return if (isDark) {
        base.copy(
            canopyStart = Color(0xFF0A0A0C),
            canopyEnd = Color(0xFF1E1E22),
            onCanopy = Color(0xFFE3E2E6)
        )
    } else {
        base.copy(
            canopyStart = Color(0xFF141416),
            canopyEnd = Color(0xFF2E3033),
            onCanopy = Color.White
        )
    }
}

fun dynamicCustomColors(scheme: ColorScheme, isDark: Boolean): CustomThemeColors {
    val base = emeraldCustomColors(isDark)
    return base.copy(
        canopyStart = if (isDark) scheme.surfaceContainerHigh else scheme.primary,
        canopyEnd = if (isDark) scheme.surfaceContainer else scheme.primaryContainer,
        onCanopy = if (isDark) scheme.onSurface else scheme.onPrimary
    )
}

// =============================================================================
// Custom Theme Builder Presets & Scheme Generator
// =============================================================================
@Serializable
data class CustomThemePreset(
    val id: String = "custom_theme",
    val name: String = "Custom Theme",
    val isDark: Boolean = false,
    val backgroundColorHex: String = "#F7F9F7",
    val textColorHex: String = "#191C1A",
    val surfaceColorHex: String = "#FFFFFF",
    val accentColorHex: String? = "#0B6E4F"
)

fun createCustomColorScheme(
    preset: CustomThemePreset,
    fallbackAccent: Color = EmeraldLightPrimary
): ColorScheme {
    val bg = preset.backgroundColorHex.toComposeColor(if (preset.isDark) Color(0xFF0D1310) else Color(0xFFF7F9F7))
    val text = preset.textColorHex.toComposeColor(if (preset.isDark) Color(0xFFE1E3DF) else Color(0xFF191C1A))
    val surface = preset.surfaceColorHex.toComposeColor(if (preset.isDark) Color(0xFF151E19) else Color(0xFFFFFFFF))
    val accent = preset.accentColorHex.toComposeColor(fallbackAccent)

    val accentLum = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    val onAccent = if (accentLum > 0.5f) Color(0xFF121212) else Color.White

    val surfaceContainer = if (preset.isDark) blendColors(surface, Color.White, 0.05f) else blendColors(surface, Color.Black, 0.035f)
    val surfaceContainerLow = if (preset.isDark) blendColors(surface, Color.Black, 0.05f) else blendColors(surface, Color.White, 0.04f)
    val surfaceContainerHigh = if (preset.isDark) blendColors(surface, Color.White, 0.10f) else blendColors(surface, Color.Black, 0.07f)
    val outline = if (preset.isDark) blendColors(surface, Color.White, 0.18f) else blendColors(surface, Color.Black, 0.15f)

    return if (preset.isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.22f),
            onPrimaryContainer = accent,
            secondary = surfaceContainerHigh,
            onSecondary = text,
            secondaryContainer = accent.copy(alpha = 0.22f),
            onSecondaryContainer = accent,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            outline = outline,
            surfaceContainer = surfaceContainer,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerHigh = surfaceContainerHigh
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.16f),
            onPrimaryContainer = accent,
            secondary = surfaceContainerHigh,
            onSecondary = text,
            secondaryContainer = accent.copy(alpha = 0.16f),
            onSecondaryContainer = accent,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            outline = outline,
            surfaceContainer = surfaceContainer,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerHigh = surfaceContainerHigh
        )
    }
}

fun createCustomThemeColors(preset: CustomThemePreset): CustomThemeColors {
    val base = emeraldCustomColors(preset.isDark)
    val accent = preset.accentColorHex.toComposeColor(EmeraldLightPrimary)
    return base.copy(
        canopyStart = if (preset.isDark) blendColors(accent, Color.Black, 0.4f) else accent,
        canopyEnd = if (preset.isDark) blendColors(accent, Color.Black, 0.2f) else blendColors(accent, Color.White, 0.15f)
    )
}

fun blendColors(base: Color, overlay: Color, amount: Float): Color {
    val r = base.red + (overlay.red - base.red) * amount
    val g = base.green + (overlay.green - base.green) * amount
    val b = base.blue + (overlay.blue - base.blue) * amount
    return Color(red = r.coerceIn(0f, 1f), green = g.coerceIn(0f, 1f), blue = b.coerceIn(0f, 1f), alpha = 1f)
}
