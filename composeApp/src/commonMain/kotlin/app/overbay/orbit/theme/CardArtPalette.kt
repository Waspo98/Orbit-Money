package app.overbay.orbit.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Brand card-art exception to the semantic-token rule.
 *
 * Credit card faces use physical wallet-matched gradients and typography rather than
 * dynamic semantic tokens to maintain instant brand and card visual recognition.
 */
data class CardArtPreset(
    val key: String,
    val label: String,
    val defaultNetwork: String,
    val startColor: Color,
    val endColor: Color,
    val textColor: Color
) {
    val gradientColors: List<Color> get() = listOf(startColor, endColor)
    val brush: Brush get() = Brush.linearGradient(gradientColors)
}

object CardArtPalette {
    val SapphireBlue = CardArtPreset(
        "preset://sapphire-blue", "Sapphire Blue", "Visa",
        Color(0xFF0F2B5B), Color(0xFF1E56A0), Color.White
    )
    val AmexGold = CardArtPreset(
        "preset://amex-gold", "Amex Gold", "Amex",
        Color(0xFF8C621E), Color(0xFFD4AF37), Color(0xFF1B1406)
    )
    val VentureSilver = CardArtPreset(
        "preset://venture-silver", "Capital Silver", "Mastercard",
        Color(0xFF232931), Color(0xFF4E5B6E), Color.White
    )
    val AppleTitanium = CardArtPreset(
        "preset://apple-card", "Apple Titanium", "Mastercard",
        Color(0xFFD8DCE3), Color(0xFFF5F7FA), Color(0xFF1D1F23)
    )
    val DiscoverPlum = CardArtPreset(
        "preset://discover-plum", "Discover Plum", "Discover",
        Color(0xFF4A1C40), Color(0xFF8A307F), Color.White
    )

    val ALL: List<CardArtPreset> = listOf(SapphireBlue, AmexGold, VentureSilver, AppleTitanium, DiscoverPlum)

    fun resolve(key: String?): CardArtPreset? = ALL.firstOrNull { it.key == key }

    fun resolveColors(
        key: String?,
        fallbackStart: Color,
        fallbackEnd: Color,
        fallbackText: Color
    ): Triple<Color, Color, Color> {
        val p = resolve(key)
        return if (p != null) Triple(p.startColor, p.endColor, p.textColor)
        else Triple(fallbackStart, fallbackEnd, fallbackText)
    }
}
