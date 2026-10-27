package app.overbay.orbit.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import orbitmoney.composeapp.generated.resources.Res
import orbitmoney.composeapp.generated.resources.dm_sans_bold
import orbitmoney.composeapp.generated.resources.dm_sans_medium
import orbitmoney.composeapp.generated.resources.dm_sans_semibold
import orbitmoney.composeapp.generated.resources.inter_tight_bold
import orbitmoney.composeapp.generated.resources.inter_tight_medium
import orbitmoney.composeapp.generated.resources.inter_tight_regular
import orbitmoney.composeapp.generated.resources.inter_tight_semibold
import org.jetbrains.compose.resources.Font

/**
 * Refined geometric title font family (DM Sans) matching main branch `--font-title`
 * for canopy headers, card titles (`h1-h4`), and hero figures.
 */
@Composable
fun rememberDisplayFontFamily(): FontFamily = FontFamily(
    Font(Res.font.dm_sans_medium, FontWeight.Medium),
    Font(Res.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.dm_sans_bold, FontWeight.Bold)
)

/**
 * High-legibility UI and tabular numeral font family (Inter Tight) matching main branch `--font-sans`
 * for transaction rows, cards, labels, pills, and rolling tickers.
 */
@Composable
fun rememberBodyFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_tight_regular, FontWeight.Normal),
    Font(Res.font.inter_tight_medium, FontWeight.Medium),
    Font(Res.font.inter_tight_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_tight_bold, FontWeight.Bold)
)

/**
 * Non-composable fallback tabular numeral style for unit tests.
 */
val DefaultTabularNumStyle = TextStyle(
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp
)

/**
 * Tabular numeral style helper ensuring financial figures and rolling tickers never jitter horizontally.
 * Crucially reads [MaterialTheme.typography.bodyLarge] so passing `style = TabularNumStyle.copy(...)`
 * to `Text(...)` preserves the active `Inter Tight` fontFamily instead of resetting `fontFamily` to null (Roboto).
 */
val TabularNumStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.typography.bodyLarge.copy(
        fontFeatureSettings = "tnum",
        letterSpacing = 0.sp
    )

/**
 * Builds the Orbit Money typography scale matching main branch DM Sans titles & Inter Tight body.
 */
fun buildOrbitTypography(
    displayFontFamily: FontFamily = FontFamily.SansSerif,
    bodyFontFamily: FontFamily = FontFamily.SansSerif
): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 50.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    displayMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    displaySmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    headlineSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    titleLarge = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
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
