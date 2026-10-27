package app.overbay.orbit.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import orbitmoney.composeapp.generated.resources.Res
import orbitmoney.composeapp.generated.resources.inter_bold
import orbitmoney.composeapp.generated.resources.inter_medium
import orbitmoney.composeapp.generated.resources.inter_regular
import orbitmoney.composeapp.generated.resources.inter_semibold
import orbitmoney.composeapp.generated.resources.plus_jakarta_sans_bold
import orbitmoney.composeapp.generated.resources.plus_jakarta_sans_extrabold
import orbitmoney.composeapp.generated.resources.plus_jakarta_sans_medium
import orbitmoney.composeapp.generated.resources.plus_jakarta_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Expressive geometric display font family (Plus Jakarta Sans) for canopy headers,
 * hero financial figures, and modal/sheet headlines.
 */
@Composable
fun rememberDisplayFontFamily(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(Res.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(Res.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold)
)

/**
 * High-legibility UI and tabular numeral font family (Inter) for transaction rows,
 * cards, labels, pills, and rolling tickers.
 */
@Composable
fun rememberBodyFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold)
)

/**
 * Tabular numeral style helper ensuring financial figures and rolling tickers never jitter horizontally.
 * Inherits the active theme font family (Inter inside OrbitTheme) while enforcing OpenType 'tnum'
 * and crisp negative tracking suited for currency figures.
 */
val TabularNumStyle = TextStyle(
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.2).sp
)

/**
 * Builds the Orbit Money Material 3 Expressive typography scale with tight, modern fintech tracking.
 */
fun buildOrbitTypography(
    displayFontFamily: FontFamily = FontFamily.SansSerif,
    bodyFontFamily: FontFamily = FontFamily.SansSerif
): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 42.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    ),
    displaySmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.35).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum"
    ),
    titleLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.15).sp
    ),
    titleSmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.15).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.05).sp
    ),
    labelMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = "tnum"
    )
)

/**
 * Default non-composable typography instance for unit test verification.
 * Runtime UI uses [rememberOrbitTypography] inside [OrbitTheme].
 */
val OrbitTypography = buildOrbitTypography()

@Composable
fun rememberOrbitTypography(): Typography {
    val displayFontFamily = rememberDisplayFontFamily()
    val bodyFontFamily = rememberBodyFontFamily()
    return buildOrbitTypography(
        displayFontFamily = displayFontFamily,
        bodyFontFamily = bodyFontFamily
    )
}
