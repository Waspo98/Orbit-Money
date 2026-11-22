package app.overbay.orbit.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Perceptual HSL representation of a Compose [Color] (`hue` in 0..360, `saturation`/`lightness` in 0..1).
 */
data class HslColor(
    val hue: Float,
    val saturation: Float,
    val lightness: Float
)

fun Color.toHsl(): HslColor {
    val r = red.coerceIn(0f, 1f)
    val g = green.coerceIn(0f, 1f)
    val b = blue.coerceIn(0f, 1f)

    val maxVal = max(r, max(g, b))
    val minVal = min(r, min(g, b))
    val delta = maxVal - minVal
    val l = (maxVal + minVal) / 2f

    if (delta < 1e-5f) {
        return HslColor(hue = 0f, saturation = 0f, lightness = l)
    }

    val s = if (l > 0.5f) {
        delta / (2f - maxVal - minVal)
    } else {
        delta / (maxVal + minVal)
    }.coerceIn(0f, 1f)

    val rawHue = when (maxVal) {
        r -> ((g - b) / delta) + (if (g < b) 6f else 0f)
        g -> ((b - r) / delta) + 2f
        else -> ((r - g) / delta) + 4f
    } * 60f

    val h = ((rawHue % 360f) + 360f) % 360f
    return HslColor(hue = h, saturation = s, lightness = l)
}

fun hslToColor(
    hue: Float,
    saturation: Float,
    lightness: Float,
    alpha: Float = 1f
): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    if (s <= 1e-5f) {
        return Color(red = l, green = l, blue = l, alpha = alpha.coerceIn(0f, 1f))
    }

    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = l - c / 2f

    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0f, 1f)
    )
}

private fun linearizeChannel(channel: Float): Double {
    val c = channel.toDouble().coerceIn(0.0, 1.0)
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

/**
 * WCAG 2.1 relative luminance in `[0.0, 1.0]`.
 */
fun Color.relativeLuminance(): Double =
    0.2126 * linearizeChannel(red) +
        0.7152 * linearizeChannel(green) +
        0.0722 * linearizeChannel(blue)

/**
 * WCAG 2.1 contrast ratio in `[1.0, 21.0]`.
 */
fun contrastRatio(foreground: Color, background: Color): Double {
    val l1 = foreground.relativeLuminance()
    val l2 = background.relativeLuminance()
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Picks whichever of [darkInk] or [lightInk] achieves stronger WCAG contrast against [background].
 */
fun bestOnColor(
    background: Color,
    darkInk: Color = Color(0xFF141518),
    lightInk: Color = Color.White
): Color = if (contrastRatio(lightInk, background) >= contrastRatio(darkInk, background)) {
    lightInk
} else {
    darkInk
}

/**
 * Adjusts [color]'s HSL lightness minimally until it satisfies [minRatio] contrast against [against].
 */
fun ensureMinContrast(
    color: Color,
    against: Color,
    minRatio: Double = 4.5,
    preferDarker: Boolean? = null
): Color {
    if (contrastRatio(color, against) >= minRatio) return color

    val hsl = color.toHsl()
    val goDarkerFirst = preferDarker ?: (against.relativeLuminance() > 0.35)

    fun searchDirection(darker: Boolean): Color? {
        val extreme = hslToColor(hsl.hue, hsl.saturation, if (darker) 0.02f else 0.98f, color.alpha)
        if (contrastRatio(extreme, against) < minRatio) return null

        var low = if (darker) 0.02f else hsl.lightness
        var high = if (darker) hsl.lightness else 0.98f
        var best = extreme
        repeat(18) {
            val mid = (low + high) / 2f
            val candidate = hslToColor(hsl.hue, hsl.saturation, mid, color.alpha)
            if (contrastRatio(candidate, against) >= minRatio) {
                best = candidate
                if (darker) low = mid else high = mid
            } else {
                if (darker) high = mid else low = mid
            }
        }
        return best
    }

    searchDirection(goDarkerFirst)?.let { return it }
    searchDirection(!goDarkerFirst)?.let { return it }
    return bestOnColor(against)
}

/**
 * Linear RGB blend of [base] toward [overlay] by [amount] (`0f..1f`).
 */
fun blendColors(base: Color, overlay: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    val r = base.red + (overlay.red - base.red) * t
    val g = base.green + (overlay.green - base.green) * t
    val b = base.blue + (overlay.blue - base.blue) * t
    return Color(
        red = r.coerceIn(0f, 1f),
        green = g.coerceIn(0f, 1f),
        blue = b.coerceIn(0f, 1f),
        alpha = 1f
    )
}

/**
 * Shortest signed angular difference in `(-180, 180]` from [fromDeg] to [toDeg].
 */
fun hueDelta(fromDeg: Float, toDeg: Float): Float {
    val diff = ((toDeg - fromDeg) % 360f + 540f) % 360f - 180f
    return diff
}

/**
 * Nudges [baseHue] subtly toward [targetHue] by at most [maxShiftDeg] so semantic hues
 * harmonize with a palette's color temperature while remaining universally recognizable.
 */
fun nudgeHueToward(baseHue: Float, targetHue: Float, maxShiftDeg: Float): Float {
    val delta = hueDelta(baseHue, targetHue)
    val shift = (delta * 0.14f).coerceIn(-maxShiftDeg, maxShiftDeg)
    return ((baseHue + shift) % 360f + 360f) % 360f
}

/**
 * Formats an opaque Compose [Color] as uppercase `#RRGGBB`.
 */
fun Color.toHexString(): String {
    fun channelHex(value: Float): String =
        (value.coerceIn(0f, 1f) * 255f).roundToInt().toString(16).uppercase().padStart(2, '0')
    return "#${channelHex(red)}${channelHex(green)}${channelHex(blue)}"
}

/**
 * Normalizes a `#RGB`, `RGB`, `#RRGGBB`, or `RRGGBB` input into canonical `#RRGGBB` uppercase,
 * returning `null` if invalid.
 */
fun normalizeAccentHex(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val cleaned = raw.trim().removePrefix("#").uppercase()
    val expanded = when (cleaned.length) {
        3 -> buildString(6) {
            for (ch in cleaned) {
                append(ch)
                append(ch)
            }
        }
        6 -> cleaned
        else -> return null
    }
    if (expanded.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
    return "#$expanded"
}
