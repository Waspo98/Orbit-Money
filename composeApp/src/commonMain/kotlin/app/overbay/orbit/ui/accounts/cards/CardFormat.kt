package app.overbay.orbit.ui.accounts.cards

import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.LocalDateUtils
import kotlin.math.absoluteValue

object CardFormat {

    fun formatCardDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "Not Set"
        val parsed = LocalDateUtils.parseIsoDate(iso) ?: return "Not Set"
        return if (parsed.year != LocalDateUtils.todayLocalDate().year) {
            LocalDateUtils.formatDisplayDate(parsed)
        } else {
            LocalDateUtils.formatShortDate(parsed)
        }
    }

    fun formatDaysUntil(n: Int): String = when {
        n == 0 -> "Today"
        n == 1 -> "Tomorrow"
        n > 1 -> "in $n days"
        n == -1 -> "Yesterday"
        n < -1 -> "${-n} days ago"
        else -> "Today"
    }

    fun formatEarnRate(r: RewardCategory): String {
        val mult = r.multiplier
        val numStr = if (mult == mult.toLong().toDouble()) {
            mult.toLong().toString()
        } else {
            val rounded = (mult * 100.0 + 0.5).toLong() / 100.0
            val s = rounded.toString()
            if (s.endsWith(".0")) s.dropLast(2) else s
        }
        return if (r.rateType.equals("percent", ignoreCase = true)) {
            "$numStr% cash"
        } else {
            "$numStr\u00D7 points"
        }
    }

    fun formatBps(bps: Int?, fallback: String = "—"): String {
        if (bps == null) return fallback
        val isNegative = bps < 0
        val absBps = bps.toLong().absoluteValue
        val whole = absBps / 100
        val frac = (absBps % 100).toInt()
        val sign = if (isNegative) "-" else ""
        return if (frac == 0) {
            "$sign$whole%"
        } else {
            val fracStr = frac.toString().padStart(2, '0')
            if (fracStr.endsWith("0")) {
                "$sign$whole.${fracStr.first()}%"
            } else {
                "$sign$whole.$fracStr%"
            }
        }
    }
}
