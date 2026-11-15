package app.overbay.orbit.ui.accounts.cards

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import kotlin.math.absoluteValue

object CardFormat {

    /**
     * Formats [cents] cleanly: omits `.00` for whole-dollar amounts while preserving exact cents
     * when fractional values exist so hero pills and perk rows stay consistent.
     */
    fun formatAdaptiveCents(cents: Cents): String =
        MoneyUtils.formatCents(cents, showCents = cents % 100L != 0L)

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

    fun resolveCardholderName(
        profile: CreditCardProfile?,
        householdMembers: List<HouseholdMember>
    ): String? {
        if (profile == null) return null
        val byId = profile.strategy.cardholderMemberId?.let { id ->
            householdMembers.firstOrNull { it.id == id }?.name
        }
        return (byId ?: profile.strategy.cardholder)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun resolveAuthorizedUsers(
        profile: CreditCardProfile?,
        householdMembers: List<HouseholdMember>
    ): List<String> {
        if (profile == null) return emptyList()
        val ownerName = resolveCardholderName(profile, householdMembers)
        val ownerId = profile.strategy.cardholderMemberId
        val seenLower = mutableSetOf<String>()
        val result = mutableListOf<String>()

        for (memberId in profile.strategy.authorizedUserMemberIds) {
            if (ownerId != null && memberId == ownerId) continue
            val name = householdMembers.firstOrNull { it.id == memberId }?.name?.trim()
                ?.takeIf { it.isNotEmpty() } ?: continue
            if (ownerName != null && name.equals(ownerName, ignoreCase = true)) continue
            if (seenLower.add(name.lowercase())) result.add(name)
        }
        for (custom in profile.authorizedUsers) {
            val clean = custom.trim()
            if (clean.isEmpty()) continue
            if (ownerName != null && clean.equals(ownerName, ignoreCase = true)) continue
            if (seenLower.add(clean.lowercase())) result.add(clean)
        }
        return result
    }

    fun matchesSelectedPlayer(
        account: Account,
        selectedPlayer: String,
        householdMembers: List<HouseholdMember>
    ): Boolean = CreditCardStrategyEngine.matchesPlayer(account, selectedPlayer, householdMembers)
}
