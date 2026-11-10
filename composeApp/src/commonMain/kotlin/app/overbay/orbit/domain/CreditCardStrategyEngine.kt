package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

enum class SubTrackerStatus {
    ON_TRACK,
    AT_RISK,
    SPEND_MET,
    RECEIVED,
    EXPIRED
}

data class Chase524SlotItem(
    val accountId: Long, val cardTitle: String, val issuerName: String?, val cardholder: String?,
    val openedDate: String, val dropOffDate: String, val daysUntilDropOff: Int, val isClosed: Boolean,
    /** True if counted via authorized user status. Note: approximates opening date using card opened date. */
    val isAuthorizedUser: Boolean = false
)

data class Chase524Summary(
    val count: Int = 0, val maxSlots: Int = 5, val isUnder524: Boolean = true,
    val nextSlotFreeDate: String? = null, val daysUntilNextSlotFree: Int? = null, val under524DropDate: String? = null,
    val slots: List<Chase524SlotItem> = emptyList(), val excludedBusinessCount: Int = 0, val missingOpenDateCount: Int = 0
)

data class Chase524PersonSummary(val holderKey: String, val holderLabel: String, val summary: Chase524Summary)

data class SubProgressSummary(
    val accountId: Long, val cardTitle: String, val cardholder: String?, val rewardText: String,
    val estimatedValueCents: Cents?, val targetSpendCents: Cents, val currentSpendCents: Cents,
    val remainingSpendCents: Cents, val progressRatio: Float, val deadlineDate: String?,
    val daysRemaining: Int?, val dailyPaceNeededCents: Cents?, val status: SubTrackerStatus,
    val deadlineIsEstimated: Boolean = false
)

data class SubEligibilityInfo(
    val isEligibleNow: Boolean, val statusLabel: String, val eligibleDate: String? = null,
    val daysUntilEligible: Int? = null, val isEstimated: Boolean = false
)

data class ActivePerkItem(
    val accountId: Long,
    val cardTitle: String,
    val cardholder: String?,
    val benefitId: String,
    val benefit: CardBenefit,
    val period: BenefitPeriod,
    val periodValueCents: Cents,
    val usedCents: Cents,
    val remainingCents: Cents,
    val daysUntilReset: Int,
    val isFullyUsed: Boolean,
    val benefitIndex: Int = 0
) {
    val periodKey: String get() = period.key
    val periodLabel: String get() = period.label
    val isUsedInCurrentPeriod: Boolean get() = isFullyUsed
}

data class CategoryBestCardPick(
    val categoryName: String, val accountId: Long, val cardTitle: String, val cardholder: String?,
    val multiplier: Double, val rateType: String, val isRotating: Boolean, val notes: String?
)

data class CardHistorySuggestions(
    val suggestedOpenedDate: String? = null, val earliestTxnMerchant: String? = null,
    val suggestedAnnualFeeDate: String? = null, val detectedAnnualFeeCents: Cents? = null,
    val detectedFeeMerchant: String? = null
)

data class CreditCardPortfolioSummary(
    val availablePlayers: List<Pair<String, String>> = emptyList(),
    val selectedPlayer: String = CreditCardStrategyEngine.ALL_PLAYERS,
    val totalCardsCount: Int = 0,
    val chase524ByPerson: List<Chase524PersonSummary> = emptyList(),
    val activeSubs: List<SubProgressSummary> = emptyList(),
    val totalAnnualFeesCents: Cents = 0L,
    val totalAnnualCreditsPotentialCents: Cents = 0L,
    val capturedCreditsCardYearCents: Cents = 0L,
    val feeAfterCreditsCents: Cents = 0L,
    val currentPeriodPerks: List<ActivePerkItem> = emptyList(),
    val bestCardsByCategory: List<CategoryBestCardPick> = emptyList()
) {
    val capturedCreditsYtdCents: Cents get() = capturedCreditsCardYearCents
    val netEffectiveAnnualFeeCents: Cents get() = feeAfterCreditsCents
}

const val UNASSIGNED_KEY = "~unassigned"
const val LIFETIME_COOLDOWN_MONTHS = 999

internal fun holderKey(s: CreditCardStrategy): String =
    s.cardholderMemberId?.let { "m:$it" } ?: s.cardholder?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: UNASSIGNED_KEY

object CreditCardStrategyEngine {

    const val ALL_PLAYERS = "ALL"

    fun summarizePortfolio(
        accounts: List<Account>,
        transactions: List<Transaction>,
        selectedPlayer: String = ALL_PLAYERS,
        today: LocalDate = LocalDateUtils.todayLocalDate(),
        householdMembers: List<HouseholdMember> = emptyList()
    ): CreditCardPortfolioSummary {
        val creditAccounts = accounts.filter { it.type == AccountType.CREDIT }
        val memberMap = householdMembers.associateBy { it.id }

        val playerMap = linkedMapOf<String, String>()
        for (account in creditAccounts) {
            val strat = account.creditCardProfile?.strategy ?: continue
            val k = holderKey(strat)
            if (k != UNASSIGNED_KEY && !playerMap.containsKey(k)) {
                val label = strat.cardholderMemberId?.let { memberMap[it]?.name }
                    ?: strat.cardholder?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "Member #${strat.cardholderMemberId}"
                playerMap[k] = label
            }
            for (auId in strat.authorizedUserMemberIds) {
                val auKey = "m:$auId"
                if (!playerMap.containsKey(auKey)) {
                    val label = memberMap[auId]?.name ?: "Member #$auId"
                    playerMap[auKey] = label
                }
            }
        }
        val players = playerMap.entries.map { it.key to it.value }.sortedBy { it.second }

        val effectivePlayer = if (selectedPlayer != ALL_PLAYERS && players.none { it.first == selectedPlayer }) {
            ALL_PLAYERS
        } else {
            selectedPlayer
        }

        val playerFiltered = if (effectivePlayer == ALL_PLAYERS) {
            creditAccounts
        } else {
            creditAccounts.filter {
                it.creditCardProfile?.let { p -> holderKey(p.strategy) } == effectivePlayer
            }
        }

        val activeCards = playerFiltered.filter { !it.isArchived && it.creditCardProfile?.strategy?.closedDate.isNullOrBlank() }

        // Partition 5/24 by person including authorized users
        val allKeys = if (creditAccounts.any { (it.creditCardProfile?.let { p -> holderKey(p.strategy) } ?: UNASSIGNED_KEY) == UNASSIGNED_KEY }) {
            playerMap.keys + UNASSIGNED_KEY
        } else {
            playerMap.keys
        }
        val chase524List = mutableListOf<Chase524PersonSummary>()
        for (hKey in allKeys) {
            if (effectivePlayer != ALL_PLAYERS && hKey != effectivePlayer) continue
            val label = if (hKey == UNASSIGNED_KEY) "Unassigned" else (playerMap[hKey] ?: hKey.replaceFirstChar { it.uppercase() })
            val mId = if (hKey.startsWith("m:")) hKey.removePrefix("m:").toLongOrNull() else null
            val acctsForPerson = when {
                hKey == UNASSIGNED_KEY -> creditAccounts.filter { (it.creditCardProfile?.let { p -> holderKey(p.strategy) } ?: UNASSIGNED_KEY) == UNASSIGNED_KEY }
                mId != null -> creditAccounts.filter {
                    val s = it.creditCardProfile?.strategy
                    s != null && (s.cardholderMemberId == mId || s.authorizedUserMemberIds.contains(mId))
                }
                else -> creditAccounts.filter { it.creditCardProfile?.let { p -> holderKey(p.strategy) } == hKey }
            }
            val sum = computeChase524Status(acctsForPerson, today, forMemberId = mId)
            chase524List.add(Chase524PersonSummary(hKey, label, sum))
        }
        val chase524ByPerson = chase524List.sortedWith(
            compareBy<Chase524PersonSummary> { it.holderKey == UNASSIGNED_KEY }.thenBy { it.holderLabel }
        )

        val cutoffReceivedDate = LocalDateUtils.addDays(today, -90)
        val activeSubs = playerFiltered.mapNotNull { account ->
            computeSubProgress(account, transactions, today)
        }.filter { sub ->
            if (sub.status != SubTrackerStatus.RECEIVED) true else {
                val strat = playerFiltered.firstOrNull { it.id == sub.accountId }?.creditCardProfile?.strategy
                val recDate = LocalDateUtils.parseIsoDate(strat?.subReceivedDate)
                recDate == null || recDate >= cutoffReceivedDate
            }
        }.sortedWith(
            compareBy<SubProgressSummary> {
                when (it.status) {
                    SubTrackerStatus.AT_RISK -> 0
                    SubTrackerStatus.ON_TRACK -> 1
                    SubTrackerStatus.SPEND_MET -> 2
                    SubTrackerStatus.EXPIRED -> 3
                    SubTrackerStatus.RECEIVED -> 4
                }
            }.thenBy { it.daysRemaining ?: Int.MAX_VALUE }
        )

        var totalFees = 0L
        var totalPotentialCredits = 0L
        var capturedCardYear = 0L
        val periodPerks = mutableListOf<ActivePerkItem>()

        for (account in activeCards) {
            val rawProfile = account.creditCardProfile ?: continue
            val profile = CreditCardBenefitEngine.upgradeProfile(rawProfile, today)
            val cardTitle = profile.cardName?.takeIf { it.isNotBlank() } ?: account.name
            val holder = profile.strategy.cardholder?.takeIf { it.isNotBlank() }
            totalFees += (profile.annualFeeCents ?: 0L)

            val ledger = CreditCardBenefitEngine.ledger(profile, today)
            totalPotentialCredits += ledger.creditsAvailableCents
            capturedCardYear += ledger.creditsCapturedCents

            val opened = profile.strategy.openedDate?.let { LocalDateUtils.parseIsoDate(it) }

            profile.benefits.forEachIndexed { idx, benefit ->
                val period = CreditCardBenefitEngine.currentPeriod(benefit, opened, today)
                val periodVal = CreditCardBenefitEngine.periodValueCents(benefit)
                val used = CreditCardBenefitEngine.usedCents(benefit, period)
                val remaining = CreditCardBenefitEngine.remainingCents(benefit, period)
                val daysReset = CreditCardBenefitEngine.daysUntilReset(period, today)
                val isFullyUsed = (used >= periodVal && periodVal > 0L) || (periodVal == 0L && used > 0L)

                periodPerks.add(
                    ActivePerkItem(
                        accountId = account.id,
                        cardTitle = cardTitle,
                        cardholder = holder,
                        benefitId = benefit.id,
                        benefit = benefit,
                        period = period,
                        periodValueCents = periodVal,
                        usedCents = used,
                        remainingCents = remaining,
                        daysUntilReset = daysReset,
                        isFullyUsed = isFullyUsed,
                        benefitIndex = idx
                    )
                )
            }
        }

        val sortedPerks = periodPerks.sortedWith(
            compareBy<ActivePerkItem> { it.isFullyUsed }
                .thenBy { it.daysUntilReset }
                .thenByDescending { it.remainingCents }
        )

        val bestByCategory = computeBestCardsByCategory(activeCards)

        return CreditCardPortfolioSummary(
            availablePlayers = players,
            selectedPlayer = effectivePlayer,
            totalCardsCount = activeCards.size,
            chase524ByPerson = chase524ByPerson,
            activeSubs = activeSubs,
            totalAnnualFeesCents = totalFees,
            totalAnnualCreditsPotentialCents = totalPotentialCredits,
            capturedCreditsCardYearCents = capturedCardYear,
            feeAfterCreditsCents = totalFees - capturedCardYear,
            currentPeriodPerks = sortedPerks,
            bestCardsByCategory = bestByCategory
        )
    }

    fun computeChase524Status(
        creditAccounts: List<Account>,
        today: LocalDate = LocalDateUtils.todayLocalDate(),
        forMemberId: Long? = null
    ): Chase524Summary {
        val cutoffDate = LocalDateUtils.addMonthsClamped(today, -24)
        var excludedBiz = 0
        var missingOpenDates = 0
        val slots = mutableListOf<Chase524SlotItem>()

        for (account in creditAccounts) {
            val profile = account.creditCardProfile ?: continue
            val strategy = profile.strategy
            if (!strategy.countsToward524) {
                excludedBiz++
                continue
            }
            val opened = LocalDateUtils.parseIsoDate(strategy.openedDate)
            if (opened == null) {
                missingOpenDates++
                continue
            }

            if (opened > cutoffDate && opened <= today) {
                val dropOff = LocalDateUtils.addMonthsClamped(opened, 24)
                val daysLeft = LocalDateUtils.daysBetween(today, dropOff).coerceAtLeast(0)
                val title = profile.cardName?.takeIf { it.isNotBlank() } ?: account.name
                val isAu = forMemberId != null && strategy.cardholderMemberId != forMemberId && strategy.authorizedUserMemberIds.contains(forMemberId)
                slots.add(
                    Chase524SlotItem(
                        accountId = account.id,
                        cardTitle = title,
                        issuerName = profile.issuerName ?: account.institution,
                        cardholder = strategy.cardholder?.takeIf { it.isNotBlank() },
                        openedDate = LocalDateUtils.formatIsoDate(opened),
                        dropOffDate = LocalDateUtils.formatIsoDate(dropOff),
                        daysUntilDropOff = daysLeft,
                        isClosed = account.isArchived || !strategy.closedDate.isNullOrBlank(),
                        isAuthorizedUser = isAu
                    )
                )
            }
        }

        val sortedSlots = slots.sortedBy { it.openedDate }
        val count = sortedSlots.size
        val nextFree = sortedSlots.firstOrNull()
        val under524Slot = if (count >= 5) sortedSlots.getOrNull(count - 5) else null

        return Chase524Summary(
            count = count,
            maxSlots = 5,
            isUnder524 = count < 5,
            nextSlotFreeDate = if (count >= 1) nextFree?.dropOffDate else null,
            daysUntilNextSlotFree = if (count >= 1) nextFree?.daysUntilDropOff else null,
            under524DropDate = under524Slot?.dropOffDate,
            slots = sortedSlots,
            excludedBusinessCount = excludedBiz,
            missingOpenDateCount = missingOpenDates
        )
    }

    fun computeSubProgress(
        account: Account,
        transactions: List<Transaction>,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): SubProgressSummary? {
        val profile = account.creditCardProfile ?: return null
        val strategy = profile.strategy
        val targetCents = strategy.subTargetSpendCents ?: return null
        if (targetCents <= 0L && strategy.subRewardText.isNullOrBlank()) return null

        val opened = LocalDateUtils.parseIsoDate(strategy.openedDate) ?: return null
        val deadlineIsEstimated = strategy.subDeadlineDate.isNullOrBlank()
        val deadline = LocalDateUtils.parseIsoDate(strategy.subDeadlineDate)
            ?: LocalDateUtils.addMonthsClamped(opened, 3)

        val openedIso = LocalDateUtils.formatIsoDate(opened)
        val deadlineIso = LocalDateUtils.formatIsoDate(deadline)

        val window = transactions.filter { txn ->
            txn.accountId == account.id &&
                txn.effectiveDate >= openedIso &&
                txn.effectiveDate <= deadlineIso
        }

        val spendDelta = window.sumOf { CardTransactionClassifier.bonusSpendDeltaCents(it) }
        val netSpend = (strategy.subManualSpendOffsetCents + spendDelta).coerceAtLeast(0L)
        val effectiveSpend = if (strategy.subCompleted && netSpend < targetCents) targetCents else netSpend
        val remaining = (targetCents - effectiveSpend).coerceAtLeast(0L)
        val ratio = if (targetCents > 0L) {
            (effectiveSpend.toFloat() / targetCents.toFloat()).coerceIn(0f, 1f)
        } else 1f

        val daysLeft = LocalDateUtils.daysBetween(today, deadline)

        // Trailing 30-day pace projection
        val since = maxOf(opened, LocalDateUtils.addDays(today, -30))
        val paceDays = LocalDateUtils.daysBetween(since, today).coerceAtLeast(1)
        val sinceIso = LocalDateUtils.formatIsoDate(since)
        val recent = window.filter { it.effectiveDate >= sinceIso }
            .sumOf { CardTransactionClassifier.bonusSpendDeltaCents(it) }
            .coerceAtLeast(0L)
        val projected = netSpend + (recent * daysLeft.coerceAtLeast(0)) / paceDays

        val dailyPace = if (remaining > 0L && daysLeft > 0) {
            remaining / daysLeft.toLong()
        } else if (remaining > 0L && daysLeft <= 0) {
            remaining
        } else null

        val daysSinceOpened = LocalDateUtils.daysBetween(opened, today)
        val status = when {
            !strategy.subReceivedDate.isNullOrBlank() -> SubTrackerStatus.RECEIVED
            strategy.subCompleted || remaining == 0L -> SubTrackerStatus.SPEND_MET
            daysLeft < 0 -> SubTrackerStatus.EXPIRED
            daysSinceOpened >= 14 && projected < targetCents -> SubTrackerStatus.AT_RISK
            daysLeft <= 14 && ratio < 0.85f -> SubTrackerStatus.AT_RISK
            else -> SubTrackerStatus.ON_TRACK
        }

        val title = profile.cardName?.takeIf { it.isNotBlank() } ?: account.name
        return SubProgressSummary(
            accountId = account.id,
            cardTitle = title,
            cardholder = strategy.cardholder?.takeIf { it.isNotBlank() },
            rewardText = strategy.subRewardText?.takeIf { it.isNotBlank() } ?: "Welcome Bonus",
            estimatedValueCents = strategy.subEstimatedValueCents,
            targetSpendCents = targetCents,
            currentSpendCents = effectiveSpend,
            remainingSpendCents = remaining,
            progressRatio = ratio,
            deadlineDate = deadlineIso,
            daysRemaining = daysLeft,
            dailyPaceNeededCents = dailyPace,
            status = status,
            deadlineIsEstimated = deadlineIsEstimated
        )
    }

    fun computeSubEligibility(
        profile: CreditCardProfile,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): SubEligibilityInfo? {
        val strategy = profile.strategy
        val cooldown = strategy.subCooldownMonths ?: return null
        if (cooldown <= 0) return null
        if (cooldown >= LIFETIME_COOLDOWN_MONTHS) {
            return SubEligibilityInfo(
                isEligibleNow = false,
                statusLabel = "Once per Lifetime",
                isEstimated = false
            )
        }

        val hasReceivedDate = !strategy.subReceivedDate.isNullOrBlank()
        val isEstimated = !hasReceivedDate
        val anchorDate = if (hasReceivedDate) {
            LocalDateUtils.parseIsoDate(strategy.subReceivedDate)
        } else {
            LocalDateUtils.parseIsoDate(strategy.openedDate)?.let { LocalDateUtils.addMonthsClamped(it, 3) }
        } ?: return SubEligibilityInfo(
            isEligibleNow = true,
            statusLabel = "${cooldown}-Mo Rule (Set Opened Date)",
            isEstimated = false
        )

        val eligibleLocalDate = LocalDateUtils.addMonthsClamped(anchorDate, cooldown)
        val eligibleIso = LocalDateUtils.formatIsoDate(eligibleLocalDate)
        val daysUntil = LocalDateUtils.daysBetween(today, eligibleLocalDate)
        val estPrefix = if (isEstimated) "Est. " else ""

        return if (daysUntil <= 0) {
            SubEligibilityInfo(
                isEligibleNow = true,
                statusLabel = "${estPrefix}SUB Eligible Now (Passed ${LocalDateUtils.formatShortDate(eligibleLocalDate)})",
                eligibleDate = eligibleIso,
                daysUntilEligible = 0,
                isEstimated = isEstimated
            )
        } else {
            val approxMonths = ((daysUntil + 15) / 30).coerceAtLeast(1)
            val disp = LocalDateUtils.formatMonthShortLabel(eligibleIso.take(7))
            SubEligibilityInfo(
                isEligibleNow = false,
                statusLabel = "${estPrefix}Eligible $disp ($approxMonths mo)",
                eligibleDate = eligibleIso,
                daysUntilEligible = daysUntil,
                isEstimated = isEstimated
            )
        }
    }

    private val CATEGORY_SYNONYMS = listOf(
        "Dining & Restaurants" to setOf("dining", "restaurant", "restaurants"),
        "Groceries" to setOf("grocery", "groceries", "supermarket", "supermarkets"),
        "Travel & Flights" to setOf("travel", "flight", "flights", "hotel", "hotels", "airline", "airlines"),
        "Gas & Transit" to setOf("gas", "fuel", "ev", "transit", "rideshare"),
        "Streaming & Entertainment" to setOf("streaming", "entertainment"),
        "Online Shopping" to setOf("online", "amazon"),
        "Drugstores" to setOf("drugstore", "drugstores", "pharmacy"),
        "Rent" to setOf("rent"),
        "Everything Else (Catch-All)" to setOf("everything", "catch-all", "base")
    )

    internal fun normalizeCategoryBucket(raw: String): String {
        val tokens = raw.lowercase().split(Regex("[^a-z0-9-]+")).filter(String::isNotBlank).toSet()
        return CATEGORY_SYNONYMS.firstOrNull { (_, w) -> w.any(tokens::contains) }?.first ?: raw.trim()
    }

    fun computeBestCardsByCategory(accounts: List<Account>): List<CategoryBestCardPick> {
        val bestByCat = mutableMapOf<String, CategoryBestCardPick>()
        for (account in accounts) {
            val profile = account.creditCardProfile ?: continue
            val cardTitle = profile.cardName?.takeIf { it.isNotBlank() } ?: account.name
            val holder = profile.strategy.cardholder?.takeIf { it.isNotBlank() }
            for (reward in profile.rewardCategories) {
                if (reward.isRotating) continue
                val rawCat = reward.category.ifBlank { reward.label }.trim()
                if (rawCat.isEmpty()) continue
                val normCat = normalizeCategoryBucket(rawCat)
                val existing = bestByCat[normCat]
                if (existing == null || reward.multiplier > existing.multiplier ||
                    (reward.multiplier == existing.multiplier && cardTitle < existing.cardTitle)
                ) {
                    bestByCat[normCat] = CategoryBestCardPick(
                        categoryName = normCat,
                        accountId = account.id,
                        cardTitle = cardTitle,
                        cardholder = holder,
                        multiplier = reward.multiplier,
                        rateType = reward.rateType,
                        isRotating = false,
                        notes = reward.notes ?: reward.description
                    )
                }
            }
        }
        return bestByCat.values.sortedWith(
            compareByDescending<CategoryBestCardPick> { it.multiplier }.thenBy { it.cardTitle }
        )
    }

    fun detectHistorySuggestions(
        accountId: Long,
        transactions: List<Transaction>,
        templateAnnualFeeCents: Cents? = null
    ): CardHistorySuggestions {
        val acctTxns = transactions.filter {
            (accountId == 0L || it.accountId == accountId) && !it.effectiveIsIgnored
        }
        if (acctTxns.isEmpty()) return CardHistorySuggestions()

        val earliest = acctTxns.minByOrNull { it.effectiveDate }
        val feeKeywordMatches = acctTxns.filter { txn ->
            txn.effectiveAmountCents < 0L &&
                CardTransactionClassifier.isNonSpendCharge(txn) &&
                ("fee" in txn.effectiveMerchant.lowercase() || "membership" in txn.effectiveMerchant.lowercase())
        }.sortedByDescending { it.effectiveDate }

        val detectedFeeTxn = feeKeywordMatches.firstOrNull() ?: run {
            val tplFee = templateAnnualFeeCents ?: return@run null
            if (tplFee < 50_00L) return@run null
            acctTxns.filter { txn ->
                txn.effectiveAmountCents < 0L &&
                    abs(txn.effectiveAmountCents) == tplFee &&
                    ("fee" in txn.effectiveMerchant.lowercase() || "membership" in txn.effectiveMerchant.lowercase())
            }.maxByOrNull { it.effectiveDate }
        }

        return CardHistorySuggestions(
            suggestedOpenedDate = earliest?.effectiveDate?.take(10),
            earliestTxnMerchant = earliest?.effectiveMerchant,
            suggestedAnnualFeeDate = detectedFeeTxn?.effectiveDate?.take(10),
            detectedAnnualFeeCents = detectedFeeTxn?.let { abs(it.effectiveAmountCents) },
            detectedFeeMerchant = detectedFeeTxn?.effectiveMerchant
        )
    }
}
