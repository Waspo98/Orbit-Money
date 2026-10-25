package app.overbay.orbit.domain

import kotlin.math.abs
import kotlin.math.roundToLong

typealias Cents = Long

object MoneyUtils {
    const val ZERO_CENTS: Cents = 0L

    /**
     * Converts a dollar floating-point value to integer cents.
     * Replicates backend sign-preserving rounding: sign * round((abs(v) + EPSILON) * 100).
     */
    fun dollarsToCents(dollars: Double): Cents {
        if (dollars.isNaN() || dollars.isInfinite()) return 0L
        val sign = if (dollars < 0.0) -1L else 1L
        val absolute = abs(dollars)
        return sign * (absolute * 100.0 + 1e-9).roundToLong()
    }

    /**
     * Converts a user-entered dollar string (e.g. "$1,234.56", "-50.00") into integer cents.
     */
    fun dollarsToCents(dollarString: String?, fallback: Cents = 0L): Cents {
        if (dollarString.isNullOrBlank()) return fallback
        val clean = dollarString.replace("$", "").replace(",", "").trim()
        val parsed = clean.toDoubleOrNull() ?: return fallback
        return dollarsToCents(parsed)
    }

    /**
     * Converts integer cents to a standard floating-point dollar value (for API / external display).
     */
    fun centsToDollars(cents: Cents): Double = cents.toDouble() / 100.0

    /**
     * Converts integer cents to a formatted two-decimal string, e.g. "1234.56" or "-45.00".
     */
    fun centsToDollarString(cents: Cents): String {
        val sign = if (cents < 0) "-" else ""
        val absVal = abs(cents)
        val dollars = absVal / 100
        val remainder = absVal % 100
        val centsPadded = if (remainder < 10) "0$remainder" else remainder.toString()
        return "$sign$dollars.$centsPadded"
    }

    /**
     * Formats integer cents with thousands separators and currency symbol.
     * E.g. formatCents(123456) -> "$1,234.56"
     * E.g. formatCents(-123456) -> "-$1,234.56"
     * E.g. formatCents(123456, showCents = false) -> "$1,235"
     * E.g. formatCents(123456, signed = true) -> "+$1,234.56"
     */
    fun formatCents(
        cents: Cents,
        showCents: Boolean = true,
        signed: Boolean = false,
        currencySymbol: String = "$"
    ): String {
        val isNegative = cents < 0
        val absVal = abs(cents)

        val dollarsPart = if (showCents) {
            absVal / 100
        } else {
            // Round to nearest dollar
            (absVal + 50) / 100
        }

        val formattedDollars = insertThousandsSeparators(dollarsPart.toString())
        val signPrefix = when {
            isNegative -> "-"
            signed && cents > 0 -> "+"
            else -> ""
        }

        return if (showCents) {
            val centsRemainder = absVal % 100
            val centsPadded = if (centsRemainder < 10) "0$centsRemainder" else centsRemainder.toString()
            "$signPrefix$currencySymbol$formattedDollars.$centsPadded"
        } else {
            "$signPrefix$currencySymbol$formattedDollars"
        }
    }

    /**
     * Compact currency formatter for charts and summary pills:
     * E.g. $4.2M, $500K, $250.
     */
    fun formatCompactCents(cents: Cents, currencySymbol: String = "$"): String {
        val isNegative = cents < 0
        val absVal = abs(cents)
        val dollars = absVal / 100.0

        val (formatted, unit) = when {
            dollars >= 1_000_000.0 -> {
                val millions = dollars / 1_000_000.0
                val rounded = (millions * 10.0).roundToLong() / 10.0
                val text = if (rounded % 1.0 == 0.0) "${rounded.toLong()}" else "$rounded"
                text to "M"
            }
            dollars >= 1_000.0 -> {
                val thousands = dollars / 1_000.0
                val rounded = (thousands * 10.0).roundToLong() / 10.0
                val text = if (rounded % 1.0 == 0.0) "${rounded.toLong()}" else "$rounded"
                text to "K"
            }
            else -> dollars.roundToLong().toString() to ""
        }

        val sign = if (isNegative) "-" else ""
        return "$sign$currencySymbol$formatted$unit"
    }

    /**
     * Computes the ratio between part and total (0.0 to 1.0+). Safe against division by zero.
     */
    fun ratio(part: Cents, total: Cents, fallback: Double = 0.0): Double {
        if (total == 0L) return fallback
        return part.toDouble() / total.toDouble()
    }

    /**
     * Percentage calculation (0 to 100).
     */
    fun percentOf(part: Cents, total: Cents): Double = ratio(part, total) * 100.0

    /**
     * Multiplies cents by a percentage rate (e.g., 5.0 for 5%) and rounds to nearest integer cent.
     */
    fun multiplyPercent(cents: Cents, percent: Double): Cents {
        if (cents == 0L || percent == 0.0) return 0L
        return (cents.toDouble() * (percent / 100.0)).roundToLong()
    }

    /**
     * Multiplies cents by an annual rate and converts to monthly equivalent.
     */
    fun monthlyInterestCents(principalCents: Cents, annualRatePercent: Double): Cents {
        if (principalCents <= 0L || annualRatePercent <= 0.0) return 0L
        val monthlyRate = annualRatePercent / 100.0 / 12.0
        return (principalCents.toDouble() * monthlyRate).roundToLong()
    }

    private fun insertThousandsSeparators(numberStr: String): String {
        val len = numberStr.length
        if (len <= 3) return numberStr
        val sb = StringBuilder()
        var count = 0
        for (i in len - 1 downTo 0) {
            sb.append(numberStr[i])
            count++
            if (count % 3 == 0 && i > 0) {
                sb.append(',')
            }
        }
        return sb.reverse().toString()
    }
}

inline fun <T> Iterable<T>.sumOfCents(selector: (T) -> Cents): Cents {
    var sum: Cents = 0L
    for (element in this) {
        sum += selector(element)
    }
    return sum
}
