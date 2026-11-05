package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.BalanceRecordSource
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.GoalAllocation
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.data.model.GoalEtaInfo
import app.overbay.orbit.data.model.GoalMonthlyPoint
import app.overbay.orbit.data.model.HouseholdIncomeRecord
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdRetirementAccount
import app.overbay.orbit.data.model.HouseholdRole
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.HouseholdSharingState
import app.overbay.orbit.data.model.HouseholdUserAccess
import app.overbay.orbit.data.model.ImportBatchRecord
import app.overbay.orbit.data.model.MerchantLogo
import app.overbay.orbit.data.model.MhaFilingStatusRef
import app.overbay.orbit.data.model.MhaStateTaxRef
import app.overbay.orbit.data.model.OccurrenceStatus
import app.overbay.orbit.data.model.ProjectedOccurrence
import app.overbay.orbit.data.model.PushNotificationConfigInfo
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurrenceRule
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.RetirementAccountMonthBalance
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.data.model.RuleConflictEntry
import app.overbay.orbit.data.model.RuleFieldDiff
import app.overbay.orbit.data.model.RuleMutationDiff
import app.overbay.orbit.data.model.RulePreviewResult
import app.overbay.orbit.data.model.SimpleFinStatusInfo
import app.overbay.orbit.data.model.SimpleFinSyncLogEntry
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.domain.MoneyUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

@Serializable
data class RewardCategoryWireDto(
    val label: String = "", val category: String? = null, val rate: Double? = 1.0, val type: String? = "multiplier", val notes: String? = null
) {
    fun toDomain(): RewardCategory = RewardCategory(category ?: label, rate ?: 1.0, false, null, notes, label, type ?: "multiplier", notes)
    companion object {
        fun fromDomain(d: RewardCategory): RewardCategoryWireDto = RewardCategoryWireDto(d.label.ifBlank { d.category }, d.category.takeIf { it.isNotBlank() }, d.multiplier, d.rateType, d.notes ?: d.description)
    }
}

@Serializable
data class CardBenefitWireDto(val name: String = "", val title: String = "", val value: JsonElement? = null, val description: String? = null, val category: String? = null) {
    fun toDomain(): CardBenefit = CardBenefit(
        title = title.ifBlank { name }, description = description,
        annualCreditValueCents = (value as? JsonPrimitive)?.content?.toDoubleOrNull()?.let { MoneyUtils.dollarsToCents(it) },
        usedThisYear = false, category = category, valueText = (value as? JsonPrimitive)?.content
    )
    companion object {
        fun fromDomain(d: CardBenefit): CardBenefitWireDto = CardBenefitWireDto(
            name = d.title, title = d.title,
            value = d.annualCreditValueCents?.let { JsonPrimitive(MoneyUtils.centsToDollars(it)) } ?: d.valueText?.let { JsonPrimitive(it) },
            description = d.description, category = d.category
        )
    }
}

@Serializable
data class CreditCardProfileUpsertRequestDto(
    @SerialName("account_name") val accountName: String, @SerialName("account_institution") val accountInstitution: String? = null,
    @SerialName("card_name") val cardName: String? = null, @SerialName("issuer_name") val issuerName: String? = null,
    val network: String? = null, @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("annual_fee") val annualFee: Double? = null, @SerialName("annual_fee_post_date") val annualFeePostDate: String? = null,
    @SerialName("credit_limit") val creditLimit: Double? = null, @SerialName("statement_closing_day") val statementClosingDay: Int? = null,
    @SerialName("payment_due_day") val paymentDueDay: Int? = null, @SerialName("authorized_users") val authorizedUsers: List<String> = emptyList(),
    @SerialName("reward_categories") val rewardCategories: List<RewardCategoryWireDto> = emptyList(),
    val benefits: List<CardBenefitWireDto> = emptyList(), val notes: String? = null
)

@Serializable
data class CreditCardProfileMutationResponseDto(val success: Boolean = true, val account: AccountDto? = null, val profile: CreditCardProfileDto? = null)

@Serializable
data class BalanceRecordWireDto(
    val id: Long = 0L, @SerialName("account_id") val accountId: Long = 0L,
    val date: String? = null, @SerialName("record_date") val recordDate: String? = null,
    val balance: Double = 0.0, val source: String = "manual"
) {
    fun toDomain(): BalanceRecord = BalanceRecord(
        id = id, accountId = accountId, recordDate = recordDate ?: date ?: "",
        balanceCents = MoneyUtils.dollarsToCents(balance),
        source = if (source.equals("simplefin", ignoreCase = true)) BalanceRecordSource.SIMPLEFIN else BalanceRecordSource.MANUAL
    )
}

@Serializable
data class AddBalanceRecordRequestDto(val date: String, val balance: Double, val previousBalance: Double? = null)

@Serializable
data class BalanceRecordMutationResponseDto(val success: Boolean = true, val account: AccountDto? = null, val record: BalanceRecordWireDto? = null)

@Serializable
data class ReorderAccountsRequestDto(val orderedIds: List<Long>)

@Serializable
data class ReorderGoalsRequestDto(val ids: List<Long>)

@Serializable
data class GoalEtaWireDto(val date: String? = null, val months: Int? = null, val status: String = "projected") {
    fun toDomain(): GoalEtaInfo = GoalEtaInfo(date = date, months = months, status = status)
}

@Serializable
data class GoalHistoryPointWireDto(val month: String = "", val amount: Double = 0.0) {
    fun toDomain(): GoalMonthlyPoint = GoalMonthlyPoint(month = month, amountCents = MoneyUtils.dollarsToCents(amount))
}

@Serializable
data class GoalAllocationWireDto(
    val id: Long = 0L, @SerialName("goal_id") val goalId: Long = 0L, @SerialName("account_id") val accountId: Long = 0L,
    @SerialName("account_name") val accountName: String = "", @SerialName("allocation_type") val allocationType: String = "fixed",
    @SerialName("allocation_value") val allocationValue: Double = 0.0, @SerialName("reserve_amount") val reserveAmount: Double = 0.0,
    @SerialName("current_amount") val currentAmount: Double = 0.0
) {
    fun toDomain(): GoalAllocation {
        val isPct = allocationType.equals("percent", ignoreCase = true)
        return GoalAllocation(
            id, goalId, accountId, accountName,
            if (isPct) GoalAllocationType.PERCENT else GoalAllocationType.FIXED,
            if (isPct) allocationValue else 0.0,
            if (isPct) 0L else MoneyUtils.dollarsToCents(allocationValue),
            MoneyUtils.dollarsToCents(reserveAmount), MoneyUtils.dollarsToCents(currentAmount)
        )
    }
}

@Serializable
data class GoalAllocationUpsertItemDto(
    @SerialName("account_id") val accountId: Long, @SerialName("allocation_type") val allocationType: String = "fixed",
    @SerialName("allocation_value") val allocationValue: Double = 0.0, @SerialName("reserve_amount") val reserveAmount: Double = 0.0
)

@Serializable
data class GoalUpsertRequestDto(
    val name: String, @SerialName("target_amount") val targetAmount: Double, @SerialName("target_date") val targetDate: String? = null,
    val kind: String = "custom", val icon: String? = "🎯", val notes: String? = null, val stealFromOthers: Boolean = false,
    val allocations: List<GoalAllocationUpsertItemDto> = emptyList()
)

@Serializable
data class BudgetMonthsResponseDto(val items: List<String> = emptyList())

@Serializable
data class UpcomingSuggestionWireDto(
    val key: String = "", val name: String = "", val merchant: String = "", val kind: String = "bill",
    val direction: String = "expense", val amount: Double = 0.0, @SerialName("next_date") val nextDate: String = "",
    @SerialName("category_id") val categoryId: Long? = null, @SerialName("category_name") val categoryName: String? = null,
    @SerialName("account_id") val accountId: Long? = null, @SerialName("source_transaction_id") val sourceTransactionId: Long? = null,
    @SerialName("transaction_count") val transactionCount: Int = 0, val confidence: Int = 0,
    @SerialName("frequency_type") val frequencyType: String = "monthly",
    @SerialName("frequency_interval") val frequencyInterval: Int = 1, @SerialName("frequency_unit") val frequencyUnit: String = "months"
) {
    fun toDomain(): RecurringCandidate = RecurringCandidate(
        merchant = merchant.ifBlank { name }, amountCents = MoneyUtils.dollarsToCents(amount),
        frequency = when (frequencyType.lowercase()) {
            "weekly" -> RecurrenceFrequency.WEEKLY; "biweekly" -> RecurrenceFrequency.BIWEEKLY
            "semimonthly" -> RecurrenceFrequency.SEMIMONTHLY; "bimonthly" -> RecurrenceFrequency.BIMONTHLY
            "quarterly" -> RecurrenceFrequency.QUARTERLY; "yearly" -> RecurrenceFrequency.YEARLY
            "custom" -> RecurrenceFrequency.CUSTOM; else -> RecurrenceFrequency.MONTHLY
        },
        transactionCount = transactionCount, confidenceScore = confidence,
        confidenceTier = when { confidence >= 70 -> "HIGH"; confidence >= 50 -> "MEDIUM"; else -> "LOW" },
        suggestedCategoryId = categoryId, suggestedAccountId = accountId, categoryName = categoryName,
        nextProjectedDate = nextDate, key = key, name = name.ifBlank { merchant },
        kind = when (kind.lowercase()) { "subscription" -> UpcomingKind.SUBSCRIPTION; "giving" -> UpcomingKind.GIVING; "income" -> UpcomingKind.INCOME; else -> UpcomingKind.BILL },
        direction = direction, sourceTransactionId = sourceTransactionId, frequencyInterval = frequencyInterval, frequencyUnit = frequencyUnit
    )
}

@Serializable
data class ProjectedOccurrenceWireDto(
    val id: JsonElement? = null, @SerialName("item_id") val itemId: Long = 0L, @SerialName("upcoming_item_id") val upcomingItemId: Long? = null,
    @SerialName("expected_date") val expectedDate: String? = null, val date: String? = null, @SerialName("next_date") val nextDate: String? = null,
    @SerialName("grace_until") val graceUntil: String? = null, val status: String = "pending",
    @SerialName("matched_transaction_id") val matchedTransactionId: Long? = null, @SerialName("matched_at") val matchedAt: String? = null, @SerialName("missed_at") val missedAt: String? = null
) {
    fun toDomain(): ProjectedOccurrence {
        val parsedId = (id as? JsonPrimitive)?.content?.substringBefore(':')?.toLongOrNull() ?: 0L
        val expDate = expectedDate ?: date ?: nextDate ?: ""
        val st = when (status.lowercase()) { "matched" -> OccurrenceStatus.MATCHED; "missed" -> OccurrenceStatus.MISSED; else -> OccurrenceStatus.PENDING }
        return ProjectedOccurrence(
            id = parsedId,
            householdId = 1L,
            upcomingItemId = if (itemId > 0L) itemId else (upcomingItemId ?: 0L),
            expectedDate = expDate,
            graceUntil = graceUntil ?: expDate,
            status = st,
            matchedTransactionId = matchedTransactionId,
            matchedAt = matchedAt,
            missedAt = missedAt
        )
    }
}

@Serializable
data class UpcomingFullResponseDto(
    val items: List<UpcomingItemDto> = emptyList(),
    val suggestions: List<UpcomingSuggestionWireDto> = emptyList(),
    val occurrences: List<ProjectedOccurrenceWireDto> = emptyList()
)

@Serializable
data class FromTransactionRequestDto(@SerialName("transaction_id") val transactionId: Long)

@Serializable
data class DismissSuggestionRequestDto(val key: String)

@Serializable
data class AcceptSuggestionRequestDto(
    val key: String, val name: String, val merchant: String, val amount: Double,
    val direction: String, val kind: String,
    @SerialName("frequency_type") val frequencyType: String, @SerialName("frequency_interval") val frequencyInterval: Int,
    @SerialName("frequency_unit") val frequencyUnit: String, @SerialName("next_date") val nextDate: String,
    @SerialName("category_id") val categoryId: Long?, @SerialName("account_id") val accountId: Long?,
    @SerialName("source_transaction_id") val sourceTransactionId: Long?
)

@Serializable
data class UpcomingMutationResponseDto(val success: Boolean = true, val id: Long = 0L, val items: List<UpcomingItemDto> = emptyList())

@Serializable
data class HouseholdRetirementAccountWireDto(
    val id: Long = 0L, @SerialName("household_id") val householdId: Long = 1L, @SerialName("member_id") val memberId: Long = 0L,
    @SerialName("account_id") val accountId: Long = 0L, @SerialName("account_kind") val accountKind: String = "other"
) {
    fun toDomain(parentMemberId: Long = 0L): HouseholdRetirementAccount = HouseholdRetirementAccount(id, householdId, if (memberId > 0L) memberId else parentMemberId, accountId, accountKind)
    companion object {
        fun fromDomain(d: HouseholdRetirementAccount): HouseholdRetirementAccountWireDto = HouseholdRetirementAccountWireDto(d.id, d.householdId, d.memberId, d.accountId, d.accountKind)
    }
}

@Serializable
data class HouseholdIncomeRecordWireDto(
    val id: Long = 0L, @SerialName("household_id") val householdId: Long = 1L, @SerialName("member_id") val memberId: Long = 0L,
    @SerialName("effective_date") val effectiveDate: String = "", @SerialName("gross_income_annual") val grossIncomeAnnual: Double = 0.0,
    @SerialName("net_pay_per_period") val netPayPerPeriod: Double = 0.0, @SerialName("pay_frequency") val payFrequency: String = "biweekly",
    @SerialName("pay_periods_per_year") val payPeriodsPerYear: Double = 26.0, @SerialName("employee_contribution_percent") val employeeContributionPercent: Double = 0.0,
    @SerialName("employee_contribution_annual") val employeeContributionAnnual: Double = 0.0, @SerialName("employer_match_percent") val employerMatchPercent: Double = 0.0,
    @SerialName("employer_match_limit_percent") val employerMatchLimitPercent: Double = 0.0, @SerialName("employer_match_annual_cap") val employerMatchAnnualCap: Double = 0.0,
    @SerialName("health_premium_per_month") val healthPremiumPerMonth: Double = 0.0, @SerialName("hsa_contribution_annual") val hsaContributionAnnual: Double = 0.0,
    @SerialName("dependent_care_fsa_annual") val dependentCareFsaAnnual: Double = 0.0, @SerialName("other_benefits_annual") val otherBenefitsAnnual: Double = 0.0,
    val source: String = "manual", val notes: String? = null
) {
    fun toDomain(): HouseholdIncomeRecord = HouseholdIncomeRecord(
        id = id, householdId = householdId, memberId = memberId, effectiveDate = effectiveDate,
        grossIncomeAnnualCents = MoneyUtils.dollarsToCents(grossIncomeAnnual), netPayPerPeriodCents = MoneyUtils.dollarsToCents(netPayPerPeriod),
        payFrequency = payFrequency, payPeriodsPerYear = payPeriodsPerYear, employeeContributionPercent = employeeContributionPercent,
        employeeContributionAnnualCents = MoneyUtils.dollarsToCents(employeeContributionAnnual), employerMatchPercent = employerMatchPercent,
        employerMatchLimitPercent = employerMatchLimitPercent, employerMatchAnnualCapCents = MoneyUtils.dollarsToCents(employerMatchAnnualCap),
        healthPremiumPerMonthCents = MoneyUtils.dollarsToCents(healthPremiumPerMonth), hsaContributionAnnualCents = MoneyUtils.dollarsToCents(hsaContributionAnnual),
        dependentCareFsaAnnualCents = MoneyUtils.dollarsToCents(dependentCareFsaAnnual), otherBenefitsAnnualCents = MoneyUtils.dollarsToCents(otherBenefitsAnnual),
        source = source, notes = notes
    )
}

@Serializable
data class HouseholdMemberUpsertRequestDto(
    val name: String, val role: String = "adult", @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("employment_status") val employmentStatus: String = "employed", val employer: String? = null, @SerialName("job_title") val jobTitle: String? = null,
    @SerialName("gross_income_annual") val grossIncomeAnnual: Double = 0.0, @SerialName("net_pay_per_period") val netPayPerPeriod: Double = 0.0,
    @SerialName("pay_frequency") val payFrequency: String = "biweekly", @SerialName("pay_periods_per_year") val payPeriodsPerYear: Double = 26.0,
    @SerialName("retirement_account_type") val retirementAccountType: String? = null, @SerialName("employee_contribution_percent") val employeeContributionPercent: Double? = 0.0,
    @SerialName("employee_contribution_annual") val employeeContributionAnnual: Double = 0.0, @SerialName("employer_match_percent") val employerMatchPercent: Double = 0.0,
    @SerialName("employer_match_limit_percent") val employerMatchLimitPercent: Double = 0.0, @SerialName("employer_match_annual_cap") val employerMatchAnnualCap: Double = 0.0,
    @SerialName("health_premium_per_month") val healthPremiumPerMonth: Double = 0.0, @SerialName("hsa_contribution_annual") val hsaContributionAnnual: Double = 0.0,
    @SerialName("dependent_care_fsa_annual") val dependentCareFsaAnnual: Double = 0.0, @SerialName("other_benefits_annual") val otherBenefitsAnnual: Double = 0.0,
    val notes: String? = null, @SerialName("retirement_accounts") val retirementAccounts: List<HouseholdRetirementAccountWireDto> = emptyList(),
    @SerialName("effective_date") val effectiveDate: String? = null
)

@Serializable
data class RetirementAccountMonthBalanceWireDto(@SerialName("account_id") val accountId: Long = 0L, val balance: Double = 0.0) {
    fun toDomain(): RetirementAccountMonthBalance = RetirementAccountMonthBalance(accountId = accountId, balanceCents = MoneyUtils.dollarsToCents(balance))
}

@Serializable
data class RetirementHistoryPointWireDto(val month: String = "", val balance: Double = 0.0, val accounts: List<RetirementAccountMonthBalanceWireDto> = emptyList()) {
    fun toDomain(): RetirementHistoryPoint = RetirementHistoryPoint(month = month, balanceCents = MoneyUtils.dollarsToCents(balance), accounts = accounts.map { it.toDomain() })
}

@Serializable
data class RetirementHistoryResponseDto(val history: List<RetirementHistoryPointWireDto> = emptyList())

@Serializable
data class HouseholdUserAccessWireDto(
    val id: Long = 0L, val email: String? = null, val username: String? = null,
    @SerialName("display_name") val displayName: String? = null, val role: String = "member", @SerialName("access_level") val accessLevel: String = "write"
) {
    fun toDomain(): HouseholdUserAccess = HouseholdUserAccess(id, email, username, displayName ?: username ?: email ?: "Member $id", role, accessLevel)
}

@Serializable
data class HouseholdShareWireDto(
    val id: Long = 0L, @SerialName("household_id") val householdId: Long = 1L, @SerialName("invited_email") val invitedEmail: String = "",
    val role: String = "member", @SerialName("access_level") val accessLevel: String = "write",
    @SerialName("accepted_at") val acceptedAt: String? = null, @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    fun toDomain(): HouseholdShare = HouseholdShare(
        id = id, householdId = householdId, invitedEmail = invitedEmail,
        role = when (role.lowercase()) { "owner" -> HouseholdRole.OWNER; "admin" -> HouseholdRole.ADMIN; else -> HouseholdRole.MEMBER },
        accessLevel = accessLevel, acceptedAt = acceptedAt, revokedAt = revokedAt, createdAt = createdAt
    )
}

@Serializable
data class HouseholdSharingCurrentUserWireDto(
    val id: Long? = null, val email: String? = null, val displayName: String? = null,
    val role: String = "owner", val accessLevel: String = "write", val canManageSharing: Boolean = true, val canRemoveUsers: Boolean = true
)

@Serializable
data class HouseholdSharingResponseDto(
    val household: HouseholdDto? = null, val currentUser: HouseholdSharingCurrentUserWireDto? = null,
    val users: List<HouseholdUserAccessWireDto> = emptyList(), val shares: List<HouseholdShareWireDto> = emptyList()
) {
    fun toDomain(): HouseholdSharingState = HouseholdSharingState(
        householdId = household?.id ?: 1L, householdName = household?.name ?: "My Household",
        currentUserId = currentUser?.id ?: 0L, currentUserRole = currentUser?.role ?: "owner",
        currentUserAccessLevel = currentUser?.accessLevel ?: "write", canManageSharing = currentUser?.canManageSharing ?: true,
        canRemoveUsers = currentUser?.canRemoveUsers ?: true, users = users.map { it.toDomain() }, shares = shares.map { it.toDomain() }
    )
}

@Serializable
data class CreateShareRequestDto(val email: String, val role: String = "member", val accessLevel: String = "write")

@Serializable
data class UpdateAccessRequestDto(val accessLevel: String)

@Serializable
data class MhaFilingStatusWireDto(val value: String = "", val label: String = "", @SerialName("standardDeduction") val standardDeduction: Double = 0.0) {
    fun toDomain(): MhaFilingStatusRef = MhaFilingStatusRef(value = value, label = label, standardDeductionCents = MoneyUtils.dollarsToCents(standardDeduction))
}

@Serializable
data class MhaStateTaxWireDto(val code: String = "", val name: String = "", @SerialName("ratePercent") val ratePercent: Double = 0.0, @SerialName("hasIncomeTax") val hasIncomeTax: Boolean = true) {
    fun toDomain(): MhaStateTaxRef = MhaStateTaxRef(code = code, name = name, ratePercent = ratePercent, hasIncomeTax = hasIncomeTax)
}

@Serializable
data class MhaTaxReferenceWireDto(
    @SerialName("taxYear") val taxYear: Int = 2026,
    @SerialName("filingStatuses") val filingStatuses: List<MhaFilingStatusWireDto> = emptyList(), val states: List<MhaStateTaxWireDto> = emptyList()
)

@Serializable
data class MhaSettingsRequestDto(val enabled: Boolean)

@Serializable
data class MhaAccountDefaultRequestDto(@SerialName("mha_default_eligible") val mhaDefaultEligible: Boolean, val eligible: Boolean = mhaDefaultEligible)

@Serializable
data class MhaCategoryDefaultRequestDto(@SerialName("mha_default_eligible") val mhaDefaultEligible: Boolean, val eligible: Boolean = mhaDefaultEligible)

@Serializable
data class MhaCategoryIgnoreDefaultRequestDto(@SerialName("mha_default_ignored") val mhaDefaultIgnored: Boolean, val ignored: Boolean = mhaDefaultIgnored)

@Serializable
data class MhaTaxProfileResponseDto(val taxProfile: MhaTaxProfileDto? = null, val taxReference: MhaTaxReferenceWireDto? = null)

@Serializable
data class SimpleFinLastSyncWireDto(
    val id: Long = 0L, @SerialName("started_at") val startedAt: String? = null, @SerialName("finished_at") val finishedAt: String? = null,
    val status: String? = null, @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("transactions_inserted") val transactionsInserted: Int = 0, @SerialName("accounts_unmatched") val accountsUnmatched: Int = 0
)

@Serializable
data class SimpleFinStatusWireDto(
    val connected: Boolean = false, val syncEnabled: Boolean = false,
    val cutoverDate: String? = null, val lastSyncAt: String? = null,
    val lastSync: SimpleFinLastSyncWireDto? = null, val bridgeUrl: String = "https://beta-bridge.simplefin.org"
) {
    fun toDomain(): SimpleFinStatusInfo = SimpleFinStatusInfo(
        configured = connected || (cutoverDate != null), connected = connected, syncEnabled = syncEnabled,
        cutoverDate = cutoverDate, lastSyncAt = lastSyncAt, lastSyncStatus = lastSync?.status,
        lastSyncError = lastSync?.errorMessage, bridgeUrl = bridgeUrl
    )
}

@Serializable
data class SimpleFinSetupRequestDto(val setupToken: String, val cutoverDate: String? = null)

@Serializable
data class SimpleFinSyncLogWireDto(
    val id: Long = 0L, @SerialName("started_at") val startedAt: String = "", @SerialName("finished_at") val finishedAt: String? = null,
    val status: String = "", @SerialName("accounts_created") val accountsCreated: Int = 0,
    @SerialName("transactions_inserted") val transactionsInserted: Int = 0, @SerialName("transactions_skipped") val transactionsSkipped: Int = 0,
    @SerialName("error_message") val errorMessage: String? = null
) {
    fun toDomain(): SimpleFinSyncLogEntry = SimpleFinSyncLogEntry(id, startedAt, finishedAt, status, accountsCreated, transactionsInserted, transactionsSkipped, errorMessage)
}

@Serializable
data class SimpleFinSyncLogResponseDto(val items: List<SimpleFinSyncLogWireDto> = emptyList())

@Serializable
data class SubscriptionsSummaryWireDto(val activeSubscriptionCount: Int = 0, val activeDeviceCount: Int = 0)

@Serializable
data class NotificationsConfigWireDto(val configured: Boolean = false, val vapidPublicKey: String? = null, val subscriptions: SubscriptionsSummaryWireDto? = null) {
    fun toDomain(): PushNotificationConfigInfo = PushNotificationConfigInfo(configured, vapidPublicKey, subscriptions?.activeSubscriptionCount ?: 0)
}

@Serializable
data class PushSubscriptionKeysDto(val p256dh: String, val auth: String)

@Serializable
data class PushSubscriptionRequestDto(val endpoint: String, val keys: PushSubscriptionKeysDto, val expirationTime: Long? = null)

@Serializable
data class RuleEnabledRequestDto(val enabled: Boolean)

@Serializable
data class DeleteAllRulesResponseDto(val success: Boolean = true, val deleted: Int = 0)

@Serializable
data class RulePreviewRequestDto(
    @SerialName("ruleId") val ruleId: Long? = null, val name: String = "",
    val conditions: List<RuleConditionDto> = emptyList(), val actions: List<RuleActionDto> = emptyList(),
    val priority: Int = 0, val enabled: Boolean = true
) {
    companion object {
        fun fromDomain(rule: TransactionRule): RulePreviewRequestDto = RulePreviewRequestDto(
            if (rule.id > 0L) rule.id else null, rule.name, RuleWireMapper.toDto(rule).conditions, RuleWireMapper.toDto(rule).actions, rule.priority, rule.enabled
        )
    }
}

@Serializable
data class RulePreviewFieldWireDto(
    val field: String = "", val label: String = "", val to: JsonElement = JsonPrimitive(""),
    val before: JsonElement? = null, val after: JsonElement? = null,
    @SerialName("ruleId") val ruleId: Long? = null, @SerialName("ruleName") val ruleName: String? = null
)

@Serializable
data class RulePreviewTransactionRowDto(val id: Long = 0L, val date: String = "", val merchant: String = "", val amount: Double = 0.0)

@Serializable
data class RulePreviewItemWireDto(val transaction: RulePreviewTransactionRowDto? = null, val fields: List<RulePreviewFieldWireDto> = emptyList())

@Serializable
data class RulePreviewResponseDto(
    val count: Int = 0, @SerialName("affectedCount") val affectedCount: Int = 0,
    @SerialName("willChangeCount") val willChangeCount: Int = 0, @SerialName("conflictCount") val conflictCount: Int = 0,
    @SerialName("willChange") val willChange: List<RulePreviewItemWireDto> = emptyList(), val conflicts: List<RulePreviewItemWireDto> = emptyList()
) {
    fun toDomain(): RulePreviewResult {
        val conflictEntries = conflicts.flatMap { item ->
            val txn = item.transaction
            item.fields.map { f ->
                RuleConflictEntry(txn?.id ?: 0L, txn?.merchant ?: "", f.field, f.ruleId ?: 0L, f.ruleName ?: "", f.before?.toString()?.trim('"') ?: "", f.to.toString().trim('"'))
            }
        }
        val diffEntries = willChange.map { item ->
            val txn = item.transaction
            RuleMutationDiff(
                txn?.id ?: 0L, txn?.date ?: "", txn?.merchant ?: "", MoneyUtils.dollarsToCents(txn?.amount ?: 0.0),
                item.fields.map { f -> RuleFieldDiff(f.field, f.before?.toString()?.trim('"') ?: "", f.after?.toString()?.trim('"') ?: f.to.toString().trim('"')) }
            )
        }
        return RulePreviewResult(
            count, if (willChangeCount > 0) willChangeCount else diffEntries.size,
            if (conflictCount > 0) conflictCount else conflictEntries.size, conflictEntries, diffEntries
        )
    }
}

@Serializable
data class ImportRocketMoneyResponseDto(
    val success: Boolean = true, val batchId: Long = 0L, val inserted: Int = 0, val skipped: Int = 0,
    val accountsCreated: Int = 0, val rulesCreated: Int = 0, val totalRows: Int = 0,
    val parseWarnings: Int = 0, val reapplyUpdated: Int = 0
) {
    fun toDomain(fileName: String = "rocket_money.csv"): ImportBatchRecord = ImportBatchRecord(
        id = batchId, source = "rocket_money", fileName = fileName, importedAt = "",
        totalRows = totalRows, importedCount = inserted, duplicateCount = skipped
    )
}

@Serializable
data class MerchantLogoDto(
    @SerialName("merchant_key") val merchantKey: String = "", @SerialName("merchant_name") val merchantName: String = "",
    val provider: String = "logo_dev", @SerialName("provider_query") val providerQuery: String = "",
    val url: String? = null, val status: String = "candidate"
) {
    fun toDomain(): MerchantLogo? = if (merchantKey.isBlank()) null else MerchantLogo(merchantKey, merchantName, provider, providerQuery, url, status)
}

@Serializable
data class MerchantLogoCandidateDto(val name: String = "", val domain: String = "", @SerialName("logo_url") val logoUrl: String? = null)

@Serializable
data class MerchantLogoReportRequestDto(@SerialName("merchant_key") val merchantKey: String, val status: String)

@Serializable
data class MerchantLogoEnsureRequestDto(@SerialName("transaction_id") val transactionId: Long)

@Serializable
data class MerchantLogoOverrideRequestDto(
    @SerialName("merchant_key") val merchantKey: String, @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("use_category_icon") val useCategoryIcon: Boolean = false
)

@Serializable
data class MerchantLogoSearchRequestDto(@SerialName("transaction_id") val transactionId: Long, val query: String)

@Serializable
data class MerchantLogoMutationResponseDto(val success: Boolean = true, @SerialName("merchant_logo") val merchantLogo: MerchantLogoDto? = null)

@Serializable
data class MerchantLogoSearchResponseDto(
    val success: Boolean = true, val configured: Boolean = true,
    @SerialName("merchant_logo") val merchantLogo: MerchantLogoDto? = null, val candidates: List<MerchantLogoCandidateDto> = emptyList()
)

@Serializable
data class ReapplyRulesResponseDto(
    @Serializable(with = FlexibleBooleanSerializer::class) val success: Boolean = true,
    val updated: Int = 0, val appliedCount: Int = 0, val processed: Int = 0
)

@Serializable
data class MergeAccountRequestDto(val targetId: Long)

@Serializable
data class RemotePreferencesResponseDto(val preferences: JsonObject = JsonObject(emptyMap()))

@Serializable
data class PutRemotePreferencesRequestDto(val preferences: Map<String, JsonElement>)

@Serializable
data class PutRemotePreferenceKeyRequestDto(val value: JsonElement)

internal fun parseRecurrenceRuleJson(element: JsonElement?): RecurrenceRule? {
    if (element == null || element is JsonNull) return null
    val obj = element as? JsonObject ?: return null
    val mode = ((obj["mode"] ?: obj["type"]) as? JsonPrimitive)?.content
    return when {
        mode == "month_days" || obj["days"] is JsonArray -> {
            val days = (obj["days"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: emptyList()
            RecurrenceRule.MonthDays(days)
        }
        mode == "month_weekdays" || obj["ordinals"] is JsonArray -> {
            val ordinals = (obj["ordinals"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: emptyList()
            val weekdays = (obj["weekdays"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: emptyList()
            RecurrenceRule.MonthWeekdays(ordinals, weekdays)
        }
        else -> null
    }
}
