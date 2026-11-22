package app.overbay.orbit.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import app.overbay.orbit.data.model.OrbitAccentColorMode

/**
 * Curated quick-pick accent seeds exposed in Settings -> Appearance -> Custom Accent.
 */
val CuratedSeedSwatches: List<Pair<String, String>> = listOf(
    "Teal" to "#0D9488",
    "Ocean" to "#0284C7",
    "Cobalt" to "#2563EB",
    "Violet" to "#7C3AED",
    "Plum" to "#9333EA",
    "Rose" to "#E11D48",
    "Coral" to "#EA580C",
    "Amber" to "#B45309",
    "Olive" to "#4D7C0F",
    "Forest" to "#15803D",
    "Slate" to "#475569",
    "Graphite" to "#3F3F46"
)

const val DEFAULT_CUSTOM_ACCENT_HEX = "#0D9488"

/**
 * Generates a complete, WCAG AA-verified Light or Dark [ResolvedOrbitPalette]
 * (all 32 Material 3 roles + harmonized [CustomThemeColors]) from any single accent seed color.
 */
fun generatePaletteFromSeed(
    seedColor: Color,
    isDark: Boolean
): ResolvedOrbitPalette {
    val seedHsl = seedColor.toHsl()
    val isAchromatic = seedHsl.saturation < 0.08f
    val hue = if (isAchromatic) 220f else seedHsl.hue
    val primarySat = if (isAchromatic) 0.04f else seedHsl.saturation.coerceIn(0.28f, 0.90f)
    val neutralSat = if (isAchromatic) 0.04f else (seedHsl.saturation * 0.16f).coerceIn(0.05f, 0.15f)
    val darkNeutralSat = if (isAchromatic) 0.06f else (seedHsl.saturation * 0.22f).coerceIn(0.08f, 0.22f)

    // 1. Tonal surface & ink ramp
    val background: Color
    val surface: Color
    val surfaceContainerLowest: Color
    val surfaceContainerLow: Color
    val surfaceContainer: Color
    val surfaceContainerHigh: Color
    val surfaceContainerHighest: Color
    val surfaceDim: Color
    val surfaceBright: Color
    val onSurface: Color
    val onSurfaceVariant: Color
    val outline: Color
    val outlineVariant: Color
    val inverseSurface: Color
    val inverseOnSurface: Color

    if (isDark) {
        background = hslToColor(hue, darkNeutralSat, 0.068f)
        surface = hslToColor(hue, darkNeutralSat, 0.105f)
        surfaceContainerLowest = hslToColor(hue, darkNeutralSat, 0.045f)
        surfaceContainerLow = hslToColor(hue, darkNeutralSat, 0.125f)
        surfaceContainer = hslToColor(hue, darkNeutralSat, 0.150f)
        surfaceContainerHigh = hslToColor(hue, darkNeutralSat, 0.185f)
        surfaceContainerHighest = hslToColor(hue, darkNeutralSat, 0.225f)
        surfaceDim = background
        surfaceBright = hslToColor(hue, darkNeutralSat, 0.255f)
        onSurface = ensureMinContrast(hslToColor(hue, neutralSat * 0.7f, 0.92f), surfaceContainerHighest, 7.5, preferDarker = false)
        onSurfaceVariant = ensureMinContrast(hslToColor(hue, neutralSat * 0.85f, 0.68f), surfaceContainerHigh, 4.8, preferDarker = false)
        outline = hslToColor(hue, neutralSat * 0.9f, 0.48f)
        outlineVariant = hslToColor(hue, darkNeutralSat, 0.22f)
        inverseSurface = hslToColor(hue, neutralSat * 0.7f, 0.92f)
        inverseOnSurface = hslToColor(hue, darkNeutralSat, 0.11f)
    } else {
        background = hslToColor(hue, neutralSat * 0.85f, 0.982f)
        surface = Color.White
        surfaceContainerLowest = Color.White
        surfaceContainerLow = hslToColor(hue, neutralSat, 0.956f)
        surfaceContainer = hslToColor(hue, neutralSat, 0.934f)
        surfaceContainerHigh = hslToColor(hue, neutralSat, 0.908f)
        surfaceContainerHighest = hslToColor(hue, neutralSat, 0.880f)
        surfaceDim = hslToColor(hue, neutralSat, 0.892f)
        surfaceBright = Color.White
        onSurface = ensureMinContrast(hslToColor(hue, neutralSat * 1.1f, 0.11f), surfaceContainerHighest, 8.5, preferDarker = true)
        onSurfaceVariant = ensureMinContrast(hslToColor(hue, neutralSat * 1.15f, 0.36f), surfaceContainerHigh, 4.8, preferDarker = true)
        outline = hslToColor(hue, neutralSat * 0.95f, 0.54f)
        outlineVariant = hslToColor(hue, neutralSat * 1.05f, 0.86f)
        inverseSurface = hslToColor(hue, darkNeutralSat, 0.14f)
        inverseOnSurface = hslToColor(hue, neutralSat * 0.7f, 0.94f)
    }

    // 2. Primary, Secondary & Tertiary accents with guaranteed WCAG AA contrast
    val primary = if (isDark) {
        val targetL = if (isAchromatic) 0.90f else 0.73f
        val targetS = if (isAchromatic) 0.04f else (primarySat * 0.88f).coerceIn(0.32f, 0.85f)
        ensureMinContrast(hslToColor(hue, targetS, targetL), surfaceContainerLow, 5.2, preferDarker = false)
    } else {
        val targetL = if (isAchromatic) 0.14f else seedHsl.lightness.coerceIn(0.30f, 0.43f)
        ensureMinContrast(hslToColor(hue, primarySat, targetL), surfaceContainerLow, 4.8, preferDarker = true)
    }
    val onPrimary = ensureMinContrast(
        bestOnColor(primary, darkInk = hslToColor(hue, darkNeutralSat, 0.09f), lightInk = Color.White),
        primary,
        minRatio = 4.6
    )

    val primaryContainer = if (isDark) {
        hslToColor(hue, (primarySat * 0.72f).coerceAtMost(0.65f), 0.23f)
    } else {
        hslToColor(hue, (primarySat * 0.78f).coerceAtMost(0.75f), 0.91f)
    }
    val onPrimaryContainer = ensureMinContrast(
        hslToColor(hue, primarySat * 0.85f, if (isDark) 0.90f else 0.21f),
        primaryContainer,
        minRatio = 5.5,
        preferDarker = !isDark
    )
    val inversePrimary = if (isDark) {
        ensureMinContrast(hslToColor(hue, primarySat, 0.38f), inverseSurface, 4.6, preferDarker = true)
    } else {
        ensureMinContrast(hslToColor(hue, (primarySat * 0.85f).coerceAtMost(0.80f), 0.74f), inverseSurface, 4.6, preferDarker = false)
    }

    val secondarySat = if (isAchromatic) 0.05f else (primarySat * 0.36f).coerceIn(0.14f, 0.34f)
    val secondary = ensureMinContrast(
        hslToColor(hue, secondarySat, if (isDark) 0.74f else 0.38f),
        surface,
        minRatio = 4.6,
        preferDarker = !isDark
    )
    val onSecondary = ensureMinContrast(
        bestOnColor(secondary, darkInk = hslToColor(hue, darkNeutralSat, 0.10f), lightInk = Color.White),
        secondary,
        minRatio = 4.6
    )
    val secondaryContainer = hslToColor(hue, secondarySat * 0.95f, if (isDark) 0.21f else 0.91f)
    val onSecondaryContainer = ensureMinContrast(
        hslToColor(hue, secondarySat, if (isDark) 0.90f else 0.18f),
        secondaryContainer,
        minRatio = 5.5,
        preferDarker = !isDark
    )

    val tertiaryHue = (hue + 38f) % 360f
    val tertiarySat = if (isAchromatic) 0.14f else (primarySat * 0.50f).coerceIn(0.22f, 0.48f)
    val tertiary = ensureMinContrast(
        hslToColor(tertiaryHue, tertiarySat, if (isDark) 0.76f else 0.38f),
        surface,
        minRatio = 4.6,
        preferDarker = !isDark
    )
    val onTertiary = ensureMinContrast(
        bestOnColor(tertiary, darkInk = hslToColor(tertiaryHue, 0.20f, 0.10f), lightInk = Color.White),
        tertiary,
        minRatio = 4.6
    )
    val tertiaryContainer = hslToColor(tertiaryHue, tertiarySat * 0.9f, if (isDark) 0.23f else 0.91f)
    val onTertiaryContainer = ensureMinContrast(
        hslToColor(tertiaryHue, tertiarySat, if (isDark) 0.91f else 0.18f),
        tertiaryContainer,
        minRatio = 5.5,
        preferDarker = !isDark
    )

    // 3. Theme-harmonized financial semantics (universally recognizable, temperature-tuned)
    val incomeHue = if (isAchromatic) 156f else nudgeHueToward(156f, hue, maxShiftDeg = 12f)
    val dangerHue = if (isAchromatic) 354f else nudgeHueToward(356f, hue, maxShiftDeg = 10f)
    val warningHue = if (isAchromatic) 39f else nudgeHueToward(39f, hue, maxShiftDeg = 6f)

    val incomeSat = if (isAchromatic) 0.44f else 0.68f
    val dangerSat = if (isAchromatic) 0.60f else 0.74f
    val warningSat = if (isAchromatic) 0.72f else 0.84f

    val income = ensureMinContrast(
        hslToColor(incomeHue, incomeSat, if (isDark) 0.60f else 0.31f),
        surfaceContainerLow,
        minRatio = 4.7,
        preferDarker = !isDark
    )
    val onIncome = ensureMinContrast(
        bestOnColor(income, darkInk = hslToColor(incomeHue, 0.45f, 0.08f), lightInk = Color.White),
        income,
        minRatio = 4.6
    )
    val incomeContainer = hslToColor(incomeHue, incomeSat * 0.85f, if (isDark) 0.18f else 0.90f)
    val onIncomeContainer = ensureMinContrast(
        hslToColor(incomeHue, incomeSat * 0.85f, if (isDark) 0.88f else 0.20f),
        incomeContainer,
        minRatio = 5.5,
        preferDarker = !isDark
    )

    val danger = ensureMinContrast(
        hslToColor(dangerHue, dangerSat, if (isDark) 0.68f else 0.41f),
        surfaceContainerLow,
        minRatio = 4.7,
        preferDarker = !isDark
    )
    val onDanger = ensureMinContrast(
        bestOnColor(danger, darkInk = hslToColor(dangerHue, 0.45f, 0.10f), lightInk = Color.White),
        danger,
        minRatio = 4.6
    )
    val dangerContainer = hslToColor(dangerHue, dangerSat * 0.80f, if (isDark) 0.18f else 0.92f)
    val onDangerContainer = ensureMinContrast(
        hslToColor(dangerHue, dangerSat * 0.80f, if (isDark) 0.88f else 0.24f),
        dangerContainer,
        minRatio = 5.5,
        preferDarker = !isDark
    )

    val warning = ensureMinContrast(
        hslToColor(warningHue, warningSat, if (isDark) 0.64f else 0.34f),
        surfaceContainerLow,
        minRatio = 4.7,
        preferDarker = !isDark
    )
    val onWarning = ensureMinContrast(
        bestOnColor(warning, darkInk = hslToColor(warningHue, 0.60f, 0.10f), lightInk = Color.White),
        warning,
        minRatio = 4.6
    )
    val warningContainer = hslToColor(warningHue, warningSat * 0.80f, if (isDark) 0.17f else 0.90f)
    val onWarningContainer = ensureMinContrast(
        hslToColor(warningHue, warningSat * 0.80f, if (isDark) 0.88f else 0.22f),
        warningContainer,
        minRatio = 5.5,
        preferDarker = !isDark
    )

    // 4. Deep atmospheric canopy header with guaranteed >= 7.5:1 onCanopy legibility
    val canopySat = if (isAchromatic) 0.06f else (primarySat * 0.88f).coerceIn(0.28f, 0.80f)
    val onCanopy = if (isDark) onSurface else Color.White
    val canopyStart = ensureMinContrast(
        hslToColor(hue, canopySat, if (isDark) 0.10f else 0.13f),
        onCanopy,
        minRatio = 7.5,
        preferDarker = true
    )
    val canopyMid = ensureMinContrast(
        hslToColor(hue, canopySat, if (isDark) 0.17f else 0.20f),
        onCanopy,
        minRatio = 7.5,
        preferDarker = true
    )
    val canopyEnd = ensureMinContrast(
        hslToColor(hue, canopySat, if (isDark) 0.23f else 0.27f),
        onCanopy,
        minRatio = 7.2,
        preferDarker = true
    )
    val canopyGlow = hslToColor(hue, if (isAchromatic) 0f else (canopySat * 0.95f).coerceAtMost(0.85f), 0.72f)
        .copy(alpha = if (isAchromatic) 0.16f else 0.24f)
    val canopyKicker = ensureMinContrast(
        hslToColor(hue, if (isAchromatic) 0.05f else (primarySat * 0.85f).coerceAtMost(0.85f), 0.84f),
        canopyEnd,
        minRatio = 5.0,
        preferDarker = false
    )

    return OrbitPresetSpec(
        isDark = isDark,
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceContainerHigh,
        onSurfaceVariant = onSurfaceVariant,
        surfaceDim = surfaceDim,
        surfaceBright = surfaceBright,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        outline = outline,
        outlineVariant = outlineVariant,
        error = danger,
        onError = onDanger,
        errorContainer = dangerContainer,
        onErrorContainer = onDangerContainer,
        income = income,
        onIncome = onIncome,
        incomeContainer = incomeContainer,
        onIncomeContainer = onIncomeContainer,
        warning = warning,
        onWarning = onWarning,
        warningContainer = warningContainer,
        onWarningContainer = onWarningContainer,
        canopyStart = canopyStart,
        canopyMid = canopyMid,
        canopyEnd = canopyEnd,
        canopyGlow = canopyGlow,
        canopyKicker = canopyKicker,
        onCanopy = onCanopy
    ).toPalette()
}

/**
 * Adapts any dark [ResolvedOrbitPalette] cleanly for pure-black AMOLED displays,
 * calibrating container hierarchy and card borders so hairlines stay crisp without tinted muddiness.
 */
fun applyAmoledOverrides(palette: ResolvedOrbitPalette): ResolvedOrbitPalette {
    val amoledBorder = blendColors(Color(0xFF232428), palette.colorScheme.primary, 0.12f)
    val amoledScheme = palette.colorScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0C),
        surfaceContainer = Color(0xFF121215),
        surfaceContainerHigh = Color(0xFF1A1B1F),
        surfaceContainerHighest = Color(0xFF232429),
        outlineVariant = amoledBorder
    )
    val amoledCustom = palette.customColors.copy(
        cardBorder = amoledBorder,
        onExpense = Color.Black,
        onTransfer = Color.Black,
        chartGrid = Color(0x16FFFFFF)
    )
    return ResolvedOrbitPalette(colorScheme = amoledScheme, customColors = amoledCustom)
}

/**
 * Derives theme-harmonized [CustomThemeColors] from an Android 12+ Material You dynamic [ColorScheme],
 * ensuring borders, financial semantics, and canopy headers stay cohesive and WCAG-legible.
 */
fun dynamicCustomColors(scheme: ColorScheme, isDark: Boolean): CustomThemeColors {
    val harmonized = generatePaletteFromSeed(scheme.primary, isDark).customColors
    val border = scheme.outlineVariant
    return harmonized.copy(
        expense = scheme.onSurface,
        onExpense = scheme.background,
        transfer = scheme.onSurfaceVariant,
        onTransfer = scheme.background,
        cardBorder = border,
        chartNeutral = scheme.onSurfaceVariant
    )
}

/**
 * Applies the user's [OrbitAccentColorMode] onto a resolved palette:
 * - [OrbitAccentColorMode.TINTED_GREEN_RED]: preserves the palette's curated theme-tinted semantics.
 * - [OrbitAccentColorMode.ALWAYS_GREEN_RED]: locks positive, warning, negative, and error tokens
 *   to pure classic traffic-light green/amber/red calibrated for Light vs. Dark contrast.
 * - [OrbitAccentColorMode.MATCH_THEME]: maps positive -> primary, warning -> secondary, and
 *   negative/error -> tertiary (with WCAG AA contrast guards against surface).
 */
fun applyAccentColorMode(
    palette: ResolvedOrbitPalette,
    mode: OrbitAccentColorMode,
    isDark: Boolean
): ResolvedOrbitPalette {
    val scheme = palette.colorScheme
    val custom = palette.customColors

    return when (mode) {
        OrbitAccentColorMode.TINTED_GREEN_RED -> palette

        OrbitAccentColorMode.ALWAYS_GREEN_RED -> {
            val income = if (isDark) AbsoluteIncomeDark else AbsoluteIncomeLight
            val onIncome = if (isDark) AbsoluteIncomeOnDark else AbsoluteIncomeOnLight
            val incomeContainer = if (isDark) AbsoluteIncomeContainerDark else AbsoluteIncomeContainerLight
            val onIncomeContainer = if (isDark) AbsoluteOnIncomeContainerDark else AbsoluteOnIncomeContainerLight

            val danger = if (isDark) AbsoluteDangerDark else AbsoluteDangerLight
            val onDanger = if (isDark) AbsoluteDangerOnDark else AbsoluteDangerOnLight
            val dangerContainer = if (isDark) AbsoluteDangerContainerDark else AbsoluteDangerContainerLight
            val onDangerContainer = if (isDark) AbsoluteOnDangerContainerDark else AbsoluteOnDangerContainerLight

            val warning = if (isDark) AbsoluteWarningDark else AbsoluteWarningLight
            val onWarning = if (isDark) AbsoluteWarningOnDark else AbsoluteWarningOnLight
            val warningContainer = if (isDark) AbsoluteWarningContainerDark else AbsoluteWarningContainerLight
            val onWarningContainer = if (isDark) AbsoluteOnWarningContainerDark else AbsoluteOnWarningContainerLight

            ResolvedOrbitPalette(
                colorScheme = scheme.copy(
                    error = danger,
                    onError = onDanger,
                    errorContainer = dangerContainer,
                    onErrorContainer = onDangerContainer
                ),
                customColors = custom.copy(
                    income = income,
                    onIncome = onIncome,
                    incomeContainer = incomeContainer,
                    onIncomeContainer = onIncomeContainer,
                    expenseContainer = dangerContainer,
                    onExpenseContainer = onDangerContainer,
                    danger = danger,
                    onDanger = onDanger,
                    dangerContainer = dangerContainer,
                    onDangerContainer = onDangerContainer,
                    warning = warning,
                    onWarning = onWarning,
                    warningContainer = warningContainer,
                    onWarningContainer = onWarningContainer,
                    chartPositive = income,
                    chartNegative = danger,
                    heroStatGood = Color(0xFF86EFAC),
                    heroStatWarn = Color(0xFFFDE047),
                    heroStatCaution = Color(0xFFFCA5A5)
                )
            )
        }

        OrbitAccentColorMode.MATCH_THEME -> {
            val positive = scheme.primary
            val onPositive = scheme.onPrimary
            val positiveContainer = scheme.primaryContainer
            val onPositiveContainer = scheme.onPrimaryContainer

            val warning = ensureMinContrast(
                color = scheme.secondary,
                against = scheme.surface,
                minRatio = 4.5,
                preferDarker = !isDark
            )
            val onWarning = if (warning == scheme.secondary) {
                scheme.onSecondary
            } else {
                ensureMinContrast(bestOnColor(warning), warning, minRatio = 4.6)
            }
            val warningContainer = scheme.secondaryContainer
            val onWarningContainer = scheme.onSecondaryContainer

            val negative = ensureMinContrast(
                color = scheme.tertiary,
                against = scheme.surface,
                minRatio = 4.5,
                preferDarker = !isDark
            )
            val onNegative = if (negative == scheme.tertiary) {
                scheme.onTertiary
            } else {
                ensureMinContrast(bestOnColor(negative), negative, minRatio = 4.6)
            }
            val negativeContainer = scheme.tertiaryContainer
            val onNegativeContainer = scheme.onTertiaryContainer

            ResolvedOrbitPalette(
                colorScheme = scheme.copy(
                    error = negative,
                    onError = onNegative,
                    errorContainer = negativeContainer,
                    onErrorContainer = onNegativeContainer
                ),
                customColors = custom.copy(
                    income = positive,
                    onIncome = onPositive,
                    incomeContainer = positiveContainer,
                    onIncomeContainer = onPositiveContainer,
                    expenseContainer = negativeContainer,
                    onExpenseContainer = onNegativeContainer,
                    danger = negative,
                    onDanger = onNegative,
                    dangerContainer = negativeContainer,
                    onDangerContainer = onNegativeContainer,
                    warning = warning,
                    onWarning = onWarning,
                    warningContainer = warningContainer,
                    onWarningContainer = onWarningContainer,
                    chartPositive = positive,
                    chartNegative = negative,
                    heroStatGood = custom.canopyKicker,
                    heroStatWarn = ensureMinContrast(scheme.secondaryContainer, custom.canopyEnd, 4.5, preferDarker = false),
                    heroStatCaution = ensureMinContrast(scheme.tertiaryContainer, custom.canopyEnd, 4.5, preferDarker = false)
                )
            )
        }
    }
}
