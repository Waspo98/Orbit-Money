package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

enum class SubTrackerStatus {
    COMPLETED,
    ON_TRACK,
    AT_RISK,
    EXPIRED
}

data class Chase524SlotItem(
    val accountId: Long,
    val cardTitle: String,
    val issuerName: String?,
    val cardholder: String?,
    val openedDate: String,
    val dropOffDate: String,
    val daysUntilDropOff: Int,
    val isClosed: Boolean
)

data class Chase524Summary(
    val count: Int = 0,
    val maxSlots: Int = 5,
    val isUnder524: Boolean = true,
    val nextSlotFreeDate: String? = null,
    val daysUntilNextSlotFree: Int? = null,
    val under524DropDate: String? = null,
    val slots: List<Chase524SlotItem> = emptyList(),
    val excludedBusinessCount: Int = 0
)

data class SubProgressSummary(
    val accountId: Long,
    val cardTitle: String,
    val cardholder: String?,
    val rewardText: String,
    val estimatedValueCents: Cents?,
    val targetSpendCents: Cents,
    val currentSpendCents: Cents,
    val remainingSpendCents: Cents,
    val progressRatio: Float,
    val deadlineDate: String?,
    val daysRemaining: Int?,
    val dailyPaceNeededCents: Cents?,
    val status: SubTrackerStatus
)

data class SubEligibilityInfo(
    val isEligibleNow: Boolean,
    val statusLabel: String,
    val eligibleDate: String? = null,
    val daysUntilEligible: Int? = null
)

data class ActivePerkItem(
    val accountId: Long,
    val cardTitle: String,
    val cardholder: String?,
    val benefitIndex: Int,
    val benefit: CardBenefit,
    val periodKey: String,
    val periodLabel: String,
    val periodValueCents: Cents,
    val isUsedInCurrentPeriod: Boolean
)

data class CategoryBestCardPick(
    val categoryName: String,
    val accountId: Long,
    val cardTitle: String,
    val cardholder: String?,
    val multiplier: Double,
    val rateType: String,
    val isRotating: Boolean,
    val notes: String?
)

data class CardHistorySuggestions(
    val suggestedOpenedDate: String? = null,
    val earliestTxnMerchant: String? = null,
    val suggestedAnnualFeeDate: String? = null,
    val detectedAnnualFeeCents: Cents? = null,
    val detectedFeeMerchant: String? = null
)

data class CreditCardPortfolioSummary(
    val availablePlayers: List<String> = emptyList(),
    val selectedPlayer: String = "ALL",
    val totalCardsCount: Int = 0,
    val chase524: Chase524Summary = Chase524Summary(),
    val activeSubs: List<SubProgressSummary> = emptyList(),
    val totalAnnualFeesCents: Cents = 0L,
    val totalAnnualCreditsPotentialCents: Cents = 0L,
    val capturedCreditsYtdCents: Cents = 0L,
    val netEffectiveAnnualFeeCents: Cents = 0L,
    val currentPeriodPerks: List<ActivePerkItem> = emptyList(),
    val bestCardsByCategory: List<CategoryBestCardPick> = emptyList()
)

object CreditCardStrategyEngine {

    const val ALL_PLAYERS = "ALL"

    fun summarizePortfolio(
        accounts: List<Account>,
        transactions: List<Transaction>,
        selectedPlayer: String = ALL_PLAYERS,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): CreditCardPortfolioSummary {
        val creditAccounts = accounts.filter { it.type == AccountType.CREDIT }
        val players = creditAccounts
            .mapNotNull { it.creditCardProfile?.strategy?.cardholder?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sorted()

        val effectivePlayer = if (selectedPlayer != ALL_PLAYERS && selectedPlayer !in players) {
            ALL_PLAYERS
        } else {
            selectedPlayer
        }

        val playerFiltered = if (effectivePlayer == ALL_PLAYERS) {
            creditAccounts
        } else {
            creditAccounts.filter {
                it.creditCardProfile?.strategy?.cardholder?.equals(effectivePlayer, ignoreCase = true) == true
            }
        }

        val activeCards = playerFiltered.filter { !it.isArchived && it.creditCardProfile?.strategy?.closedDate.isNullOrBlank() }
        val chase524 = computeChase524Status(playerFiltered, today)

        val activeSubs = playerFiltered.mapNotNull { account ->
            computeSubProgress(account, transactions, today)
        }.sortedWith(
            compareBy<SubProgressSummary> { it.status == SubTrackerStatus.COMPLETED }
                .thenBy { it.daysRemaining ?: Int.MAX_VALUE }
        )

        var totalFees = 0L
        var totalPotentialCredits = 0L
        var capturedYtd = 0L
        val periodPerks = mutableListOf<ActivePerkItem>()

        for (account in activeCards) {
            val profile = account.creditCardProfile ?: continue
            val cardTitle = profile.cardName?.takeIf { it.isNotBlank() } ?: account.name
            val holder = profile.strategy.cardholder?.takeIf { it.isNotBlank() }
            totalFees += (profile.annualFeeCents ?: 0L)

            profile.benefits.forEachIndexed { idx, benefit ->
                val annualVal = benefit.annualCreditValueCents ?: 0L
                totalPotentialCredits += annualVal
                capturedYtd += capturedBenefitValueYtdCents(benefit, today)

                val pKey = currentPeriodKey(benefit.cadence, today)
                val pLabel = currentPeriodDisplayLabel(benefit.cadence, today)
                val perPeriodCents = if (annualVal > 0L) {
                    (annualVal.toDouble() / benefit.cadence.periodsPerYear.toDouble()).roundToLong()
                } else 0L
                val isUsed = isBenefitUsedInCurrentPeriod(benefit, today)

                periodPerks.add(
                    ActivePerkItem(
                        accountId = account.id,
                        cardTitle = cardTitle,
                        cardholder = holder,
                        benefitIndex = idx,
                        benefit = benefit,
                        periodKey = pKey,
                        periodLabel = pLabel,
                        periodValueCents = perPeriodCents,
                        isUsedInCurrentPeriod = isUsed
                    )
                )
            }
        }

        val sortedPerks = periodPerks.sortedWith(
            compareBy<ActivePerkItem> { it.isUsedInCurrentPeriod }
                .thenByDescending { it.periodValueCents }
                .thenBy { it.cardTitle }
        )

        val bestByCategory = computeBestCardsByCategory(activeCards)

        return CreditCardPortfolioSummary(
            availablePlayers = players,
            selectedPlayer = effectivePlayer,
            totalCardsCount = activeCards.size,
            chase524 = chase524,
            activeSubs = activeSubs,
            totalAnnualFeesCents = totalFees,
            totalAnnualCreditsPotentialCents = totalPotentialCredits,
            capturedCreditsYtdCents = capturedYtd,
            netEffectiveAnnualFeeCents = totalFees - capturedYtd,
            currentPeriodPerks = sortedPerks,
            bestCardsByCategory = bestByCategory
        )
    }

    fun computeChase524Status(
        creditAccounts: List<Account>,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): Chase524Summary {
        val cutoffDate = LocalDateUtils.addMonthsClamped(today, -24)
        var excludedBiz = 0
        val slots = mutableListOf<Chase524SlotItem>()

        for (account in creditAccounts) {
            val profile = account.creditCardProfile ?: continue
            val strategy = profile.strategy
            val opened = LocalDateUtils.parseIsoDate(strategy.openedDate) ?: continue

            // Opened within the trailing 24 months
            if (opened > cutoffDate && opened <= today) {
                if (!strategy.countsToward524) {
                    excludedBiz++
                    continue
                }
                val dropOff = LocalDateUtils.addMonthsClamped(opened, 24)
                val daysLeft = LocalDateUtils.daysBetween(today, dropOff).coerceAtLeast(0)
                val title = profile.cardName?.takeIf { it.isNotBlank() } ?: account.name
                slots.add(
                    Chase524SlotItem(
                        accountId = account.id,
                        cardTitle = title,
                        issuerName = profile.issuerName ?: account.institution,
                        cardholder = strategy.cardholder?.takeIf { it.isNotBlank() },
                        openedDate = LocalDateUtils.formatIsoDate(opened),
                        dropOffDate = LocalDateUtils.formatIsoDate(dropOff),
                        daysUntilDropOff = daysLeft,
                        isClosed = account.isArchived || !strategy.closedDate.isNullOrBlank()
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
            nextSlotFreeDate = nextFree?.dropOffDate,
            daysUntilNextSlotFree = nextFree?.daysUntilDropOff,
            under524DropDate = under524Slot?.dropOffDate,
            slots = sortedSlots,
            excludedBusinessCount = excludedBiz
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

        val openedIso = strategy.openedDate ?: "0000-01-01"
        val deadlineIso = strategy.subDeadlineDate
        val upperBoundIso = deadlineIso ?: "9999-12-31"

        val qualifyingTxns = transactions.filter { txn ->
            txn.accountId == account.id &&
                !txn.effectiveIsTransfer &&
                !txn.effectiveIsIgnored &&
                txn.effectiveDate >= openedIso &&
                txn.effectiveDate <= upperBoundIso &&
                !isAnnualFeeMerchant(txn.effectiveMerchant)
        }

        var netSpendCents = strategy.subManualSpendOffsetCents
        for (txn in qualifyingTxns) {
            val amt = txn.effectiveAmountCents
            if (amt < 0L) {
                netSpendCents += abs(amt)
            } else if (amt > 0L) {
                netSpendCents -= amt
            }
        }
        val currentSpend = netSpendCents.coerceAtLeast(0L)
        val effectiveSpend = if (strategy.subCompleted && currentSpend < targetCents) targetCents else currentSpend
        val remaining = (targetCents - effectiveSpend).coerceAtLeast(0L)
        val ratio = if (targetCents > 0L) {
            (effectiveSpend.toFloat() / targetCents.toFloat()).coerceIn(0f, 1f)
        } else 1f

        val deadlineDate = LocalDateUtils.parseIsoDate(deadlineIso)
        val daysLeft = deadlineDate?.let { LocalDateUtils.daysBetween(today, it) }
        val dailyPace = if (remaining > 0L && daysLeft != null && daysLeft > 0) {
            (remaining.toDouble() / daysLeft.toDouble()).roundToLong()
        } else if (remaining > 0L && daysLeft != null && daysLeft <= 0) {
            remaining
        } else null

        val isDone = strategy.subCompleted || remaining == 0L || !strategy.subReceivedDate.isNullOrBlank()
        val status = when {
            isDone -> SubTrackerStatus.COMPLETED
            daysLeft != null && daysLeft < 0 -> SubTrackerStatus.EXPIRED
            daysLeft != null && daysLeft <= 14 && ratio < 0.85f -> SubTrackerStatus.AT_RISK
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
            status = status
        )
    }

    fun computeSubEligibility(
        profile: CreditCardProfile,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): SubEligibilityInfo? {
        val strategy = profile.strategy
        val cooldown = strategy.subCooldownMonths ?: return null
        if (cooldown <= 0) return null
        if (cooldown >= 999) {
            return SubEligibilityInfo(
                isEligibleNow = false,
                statusLabel = "Once-per-Lifetime Rule (Amex)"
            )
        }

        val anchorIso = strategy.subReceivedDate?.takeIf { it.isNotBlank() } ?: strategy.openedDate
        val anchorDate = LocalDateUtils.parseIsoDate(anchorIso) ?: return SubEligibilityInfo(
            isEligibleNow = true,
            statusLabel = "${cooldown}-Mo Rule (Set SUB/Open Date)"
        )

        val eligibleLocalDate = LocalDateUtils.addMonthsClamped(anchorDate, cooldown)
        val eligibleIso = LocalDateUtils.formatIsoDate(eligibleLocalDate)
        val daysUntil = LocalDateUtils.daysBetween(today, eligibleLocalDate)

        return if (daysUntil <= 0) {
            SubEligibilityInfo(
                isEligibleNow = true,
                statusLabel = "SUB Eligible Now (Passed $eligibleIso)",
                eligibleDate = eligibleIso,
                daysUntilEligible = 0
            )
        } else {
            val approxMonths = ((daysUntil + 15) / 30).coerceAtLeast(1)
            val disp = LocalDateUtils.formatMonthShortLabel(eligibleIso.take(7))
            SubEligibilityInfo(
                isEligibleNow = false,
                statusLabel = "Eligible $disp ($approxMonths mo)",
                eligibleDate = eligibleIso,
                daysUntilEligible = daysUntil
            )
        }
    }

    fun currentPeriodKey(
        cadence: BenefitCadence,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): String {
        val y = today.year
        val m = today.monthNumber
        return when (cadence) {
            BenefitCadence.MONTHLY -> LocalDateUtils.formatYearMonth(y, m)
            BenefitCadence.QUARTERLY -> "$y-Q${((m - 1) / 3) + 1}"
            BenefitCadence.SEMI_ANNUAL -> "$y-H${if (m <= 6) 1 else 2}"
            BenefitCadence.ANNUAL, BenefitCadence.EVERY_4_YEARS -> y.toString()
        }
    }

    fun currentPeriodDisplayLabel(
        cadence: BenefitCadence,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): String {
        val y = today.year
        val m = today.monthNumber
        return when (cadence) {
            BenefitCadence.MONTHLY -> "${LocalDateUtils.monthShortName(m)} $y"
            BenefitCadence.QUARTERLY -> "Q${((m - 1) / 3) + 1} $y"
            BenefitCadence.SEMI_ANNUAL -> "${if (m <= 6) "Jan–Jun" else "Jul–Dec"} $y"
            BenefitCadence.ANNUAL -> "$y Annual"
            BenefitCadence.EVERY_4_YEARS -> "4-Yr Cycle"
        }
    }

    fun isBenefitUsedInCurrentPeriod(
        benefit: CardBenefit,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): Boolean {
        val key = currentPeriodKey(benefit.cadence, today)
        if (key in benefit.usedPeriods) return true
        if (benefit.cadence == BenefitCadence.EVERY_4_YEARS) {
            val recentYears = (0..3).map { (today.year - it).toString() }
            if (benefit.usedPeriods.any { it in recentYears }) return true
        }
        return benefit.usedPeriods.isEmpty() && benefit.usedThisYear
    }

    fun toggleBenefitCurrentPeriod(
        benefit: CardBenefit,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): CardBenefit {
        val key = currentPeriodKey(benefit.cadence, today)
        val currentlyUsed = isBenefitUsedInCurrentPeriod(benefit, today)
        val updatedPeriods = if (currentlyUsed) {
            if (benefit.cadence == BenefitCadence.EVERY_4_YEARS) {
                val recentYears = (0..3).map { (today.year - it).toString() }.toSet()
                benefit.usedPeriods.filterNot { it in recentYears }
            } else {
                benefit.usedPeriods.filterNot { it == key }
            }
        } else {
            (benefit.usedPeriods + key).distinct()
        }
        return benefit.copy(
            usedThisYear = !currentlyUsed,
            usedPeriods = updatedPeriods
        )
    }

    fun capturedBenefitValueYtdCents(
        benefit: CardBenefit,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): Cents {
        val annualVal = benefit.annualCreditValueCents ?: return 0L
        if (annualVal <= 0L) return 0L
        val yearPrefix = today.year.toString()
        val usedCountThisYear = when (benefit.cadence) {
            BenefitCadence.ANNUAL, BenefitCadence.EVERY_4_YEARS -> {
                if (isBenefitUsedInCurrentPeriod(benefit, today)) 1 else 0
            }
            else -> {
                val matched = benefit.usedPeriods.count { it.startsWith(yearPrefix) }
                if (matched > 0) matched else if (benefit.usedThisYear) 1 else 0
            }
        }
        val perPeriod = (annualVal.toDouble() / benefit.cadence.periodsPerYear.toDouble()).roundToLong()
        return (perPeriod * usedCountThisYear).coerceAtMost(annualVal)
    }

    fun computeCardEffectiveAnnualFeeCents(
        profile: CreditCardProfile,
        today: LocalDate = LocalDateUtils.todayLocalDate()
    ): Cents {
        val fee = profile.annualFeeCents ?: 0L
        val captured = profile.benefits.sumOf { capturedBenefitValueYtdCents(it, today) }
        return fee - captured
    }

    fun computeBestCardsByCategory(accounts: List<Account>): List<CategoryBestCardPick> {
        val bestByCat = mutableMapOf<String, CategoryBestCardPick>()
        for (account in accounts) {
            val profile = account.creditCardProfile ?: continue
            val cardTitle = profile.cardName?.takeIf { it.isNotBlank() } ?: account.name
            val holder = profile.strategy.cardholder?.takeIf { it.isNotBlank() }
            for (reward in profile.rewardCategories) {
                val rawCat = reward.label.ifBlank { reward.category }.trim()
                if (rawCat.isEmpty()) continue
                val normCat = normalizeCategoryBucket(rawCat)
                val existing = bestByCat[normCat]
                if (existing == null || reward.multiplier > existing.multiplier) {
                    bestByCat[normCat] = CategoryBestCardPick(
                        categoryName = normCat,
                        accountId = account.id,
                        cardTitle = cardTitle,
                        cardholder = holder,
                        multiplier = reward.multiplier,
                        rateType = reward.rateType,
                        isRotating = reward.isRotating,
                        notes = reward.notes ?: reward.description
                    )
                }
            }
        }
        return bestByCat.values.sortedByDescending { it.multiplier }
    }

    private fun normalizeCategoryBucket(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "dining" in lower || "restaurant" in lower -> "Dining & Restaurants"
            "grocer" in lower || "supermarket" in lower -> "Groceries"
            "travel" in lower || "flight" in lower || "hotel" in lower || "air" in lower -> "Travel & Flights"
            "gas" in lower || "EV" in raw || "transit" in lower -> "Gas & Transit"
            "stream" in lower || "entertain" in lower -> "Streaming & Entertainment"
            "online" in lower || "amazon" in lower -> "Online Shopping"
            "drug" in lower || "pharm" in lower -> "Drugstores"
            "rent" in lower -> "Rent"
            "every" in lower || "catch" in lower || "all other" in lower || "base" in lower -> "Everything Else (Catch-All)"
            else -> raw
        }
    }

    private fun isAnnualFeeMerchant(merchant: String): Boolean {
        val lower = merchant.lowercase()
        return "annual fee" in lower ||
            "annual membership" in lower ||
            "membership fee" in lower ||
            "renewal fee" in lower
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
            txn.effectiveAmountCents < 0L && isAnnualFeeMerchant(txn.effectiveMerchant)
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
