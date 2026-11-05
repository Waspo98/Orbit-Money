package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.BalanceRecordSource
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.data.model.HouseholdIncomeRecord
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.HouseholdSharingState
import app.overbay.orbit.data.model.ImportBatchRecord
import app.overbay.orbit.data.model.MerchantLogo
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.OccurrenceStatus
import app.overbay.orbit.data.model.PushNotificationConfigInfo
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.data.model.RulePreviewResult
import app.overbay.orbit.data.model.SimpleFinStatusInfo
import app.overbay.orbit.data.model.SimpleFinSyncLogEntry
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private fun HttpRequestBuilder.auth(cookie: String?) {
    cookie?.let { header("Cookie", it) }
}

// =============================================================================
// Accounts & Credit Cards Extensions
// =============================================================================

suspend fun OrbitApiClient.reorderAccounts(orderedIds: List<Long>): Result<Boolean> = runCatching {
    val res = client.post(url("/api/accounts/reorder")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(ReorderAccountsRequestDto(orderedIds))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.archiveAccount(id: Long): Result<Boolean> = runCatching {
    val res = client.post(url("/api/accounts/$id/archive")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.unarchiveAccount(id: Long): Result<Boolean> = runCatching {
    val res = client.post(url("/api/accounts/$id/unarchive")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.getAccountBalanceHistory(accountId: Long): Result<List<BalanceRecord>> = runCatching {
    val res = client.get(url("/api/accounts/$accountId/records")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<List<BalanceRecordWireDto>>().map { it.toDomain() }
}

suspend fun OrbitApiClient.addAccountBalanceRecord(
    accountId: Long, date: String, balanceCents: Cents, previousBalanceCents: Cents? = null
): Result<BalanceRecord> = runCatching {
    val req = AddBalanceRecordRequestDto(
        date = date, balance = MoneyUtils.centsToDollars(balanceCents),
        previousBalance = previousBalanceCents?.let { MoneyUtils.centsToDollars(it) }
    )
    val res = client.post(url("/api/accounts/$accountId/records")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<BalanceRecordMutationResponseDto>().record?.toDomain()
        ?: BalanceRecord(accountId = accountId, recordDate = date, balanceCents = balanceCents, source = BalanceRecordSource.MANUAL)
}

suspend fun OrbitApiClient.deleteAccountBalanceRecord(accountId: Long, recordId: Long): Result<Boolean> = runCatching {
    val res = client.delete(url("/api/accounts/$accountId/records/$recordId")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.saveCreditCardProfileRemote(
    accountId: Long, accountName: String?, accountInstitution: String?, profile: CreditCardProfile
): Result<CreditCardProfile> = runCatching {
    val req = CreditCardProfileUpsertRequestDto(
        accountName = accountName?.takeIf { it.isNotBlank() } ?: profile.cardName ?: "Credit Card",
        accountInstitution = accountInstitution,
        cardName = profile.cardName, issuerName = profile.issuerName, network = profile.network,
        imageUrl = profile.imageUrl, annualFee = profile.annualFeeCents?.let { MoneyUtils.centsToDollars(it) },
        annualFeePostDate = profile.annualFeePostDate, creditLimit = profile.creditLimitCents?.let { MoneyUtils.centsToDollars(it) },
        statementClosingDay = profile.statementClosingDay, paymentDueDay = profile.paymentDueDay,
        authorizedUsers = profile.authorizedUsers, rewardCategories = profile.rewardCategories.map { RewardCategoryWireDto.fromDomain(it) },
        benefits = profile.benefits.map { CardBenefitWireDto.fromDomain(it) }, notes = profile.notes
    )
    val res = client.put(url("/api/accounts/$accountId/credit-card-profile")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<CreditCardProfileMutationResponseDto>().profile?.toDomain()?.copy(accountId = accountId) ?: profile
}

// =============================================================================
// Goals & Budgets Extensions
// =============================================================================

suspend fun OrbitApiClient.reorderGoals(ids: List<Long>): Result<Boolean> = runCatching {
    val res = client.put(url("/api/goals/reorder")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(ReorderGoalsRequestDto(ids))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.getActiveBudgetMonths(): Result<List<String>> = runCatching {
    val res = client.get(url("/api/budgets/months")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<BudgetMonthsResponseDto>().items
}

suspend fun OrbitApiClient.saveGoalWithAllocations(goal: Goal, stealFromOthers: Boolean = false): Result<Goal> = runCatching {
    val allocItems = goal.allocations.map { a ->
        GoalAllocationUpsertItemDto(
            accountId = a.accountId,
            allocationType = if (a.allocationType == GoalAllocationType.PERCENT) "percent" else "fixed",
            allocationValue = if (a.allocationType == GoalAllocationType.PERCENT) a.allocationPercent else MoneyUtils.centsToDollars(a.allocationAmountCents),
            reserveAmount = MoneyUtils.centsToDollars(a.reserveAmountCents)
        )
    }
    val req = GoalUpsertRequestDto(
        name = goal.name, targetAmount = MoneyUtils.centsToDollars(goal.targetAmountCents),
        targetDate = goal.targetDate, kind = goal.kind, icon = goal.icon, notes = goal.notes,
        stealFromOthers = stealFromOthers, allocations = allocItems
    )
    val res = if (goal.id > 0L) {
        client.put(url("/api/goals/${goal.id}")) { contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req) }
    } else {
        client.post(url("/api/goals")) { contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req) }
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<GoalDto>().toDomain()
}

// =============================================================================
// Upcoming & Recurring Extensions
// =============================================================================

suspend fun OrbitApiClient.getUpcomingFull(startDate: String? = null, endDate: String? = null): Result<UpcomingFullResponseDto> = runCatching {
    val res = client.get(url("/api/upcoming")) {
        startDate?.let { parameter("start_date", it) }
        endDate?.let { parameter("end_date", it) }
        auth(sessionCookie)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<UpcomingFullResponseDto>()
}

suspend fun OrbitApiClient.createUpcomingFromTransaction(transactionId: Long): Result<UpcomingItem> = runCatching {
    val res = client.post(url("/api/upcoming/from-transaction")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(FromTransactionRequestDto(transactionId))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<UpcomingItemDto>().toDomain()
}

suspend fun OrbitApiClient.acceptUpcomingSuggestion(candidate: RecurringCandidate): Result<UpcomingItem> = runCatching {
    val req = AcceptSuggestionRequestDto(
        key = candidate.key, name = candidate.name, merchant = candidate.merchant,
        amount = MoneyUtils.centsToDollars(candidate.amountCents), direction = candidate.direction,
        kind = candidate.kind.name.lowercase(), frequencyType = candidate.frequency.name.lowercase(),
        frequencyInterval = candidate.frequencyInterval, frequencyUnit = candidate.frequencyUnit,
        nextDate = candidate.nextProjectedDate, categoryId = candidate.suggestedCategoryId,
        accountId = candidate.suggestedAccountId, sourceTransactionId = candidate.sourceTransactionId
    )
    val res = client.post(url("/api/upcoming/suggestions/accept")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<UpcomingItemDto>().toDomain()
}

suspend fun OrbitApiClient.dismissUpcomingSuggestion(key: String): Result<Boolean> = runCatching {
    val res = client.post(url("/api/upcoming/suggestions/dismiss")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(DismissSuggestionRequestDto(key))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.reconcileOccurrenceRemote(
    occurrenceId: Long, status: OccurrenceStatus, matchedTransactionId: Long? = null
): Result<Boolean> = runCatching {
    val endpoint = when (status) {
        OccurrenceStatus.MATCHED -> "/api/upcoming/occurrences/$occurrenceId/paid"
        OccurrenceStatus.MISSED -> "/api/upcoming/occurrences/$occurrenceId/skip"
        else -> "/api/upcoming/occurrences/$occurrenceId/unpaid"
    }
    val res = client.post(url(endpoint)) {
        contentType(ContentType.Application.Json); auth(sessionCookie)
        matchedTransactionId?.let { setBody(mapOf("matched_transaction_id" to it)) }
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.getUpcomingHistory(itemId: Long): Result<List<Transaction>> = runCatching {
    val res = client.get(url("/api/upcoming/$itemId/history")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<List<TransactionDto>>().map { it.toDomain() }
}

// =============================================================================
// Rules & Mutations Extensions
// =============================================================================

suspend fun OrbitApiClient.setRuleEnabled(ruleId: Long, enabled: Boolean): Result<Boolean> = runCatching {
    val res = client.patch(url("/api/rules/$ruleId/enabled")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(RuleEnabledRequestDto(enabled))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.reorderRules(orderedIds: List<Long>): Result<List<TransactionRule>> = runCatching {
    val res = client.post(url("/api/rules/reorder")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(ReorderAccountsRequestDto(orderedIds))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<RuleListResponseDto>().items.map { it.toDomain() }
}

suspend fun OrbitApiClient.deleteAllRules(): Result<Int> = runCatching {
    val res = client.delete(url("/api/rules/all")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<DeleteAllRulesResponseDto>().deleted
}

suspend fun OrbitApiClient.previewRuleRemote(rule: TransactionRule): Result<RulePreviewResult> = runCatching {
    val res = client.post(url("/api/rules/preview")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(RulePreviewRequestDto.fromDomain(rule))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<RulePreviewResponseDto>().toDomain()
}

// =============================================================================
// Merchant Logos Extensions
// =============================================================================

suspend fun OrbitApiClient.reportMerchantLogoStatus(merchantKey: String, status: String): Result<Boolean> = runCatching {
    val res = client.post(url("/api/merchant-logos/report")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(MerchantLogoReportRequestDto(merchantKey, status))
    }
    extractSessionCookie(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.ensureMerchantLogo(transactionId: Long): Result<MerchantLogoDto> = runCatching {
    val res = client.post(url("/api/merchant-logos/ensure")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(MerchantLogoEnsureRequestDto(transactionId))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<MerchantLogoMutationResponseDto>().merchantLogo
        ?: throw IllegalStateException("This transaction does not have a merchant name to override.")
}

suspend fun OrbitApiClient.searchMerchantLogos(transactionId: Long, query: String): Result<MerchantLogoSearchResponseDto> = runCatching {
    val res = client.post(url("/api/merchant-logos/search")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(MerchantLogoSearchRequestDto(transactionId, query))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<MerchantLogoSearchResponseDto>()
}

suspend fun OrbitApiClient.overrideMerchantLogo(
    merchantKey: String, logoUrl: String? = null, useCategoryIcon: Boolean = false
): Result<MerchantLogoDto> = runCatching {
    val res = client.post(url("/api/merchant-logos/override")) {
        contentType(ContentType.Application.Json); auth(sessionCookie)
        setBody(MerchantLogoOverrideRequestDto(merchantKey, logoUrl?.takeIf { it.isNotBlank() }, useCategoryIcon))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<MerchantLogoMutationResponseDto>().merchantLogo
        ?: throw IllegalStateException("Merchant logo override failed.")
}

// =============================================================================
// MHA Tracker Extensions
// =============================================================================

suspend fun OrbitApiClient.putMhaSettings(enabled: Boolean): Result<Boolean> = runCatching {
    val res = client.put(url("/api/mha/settings")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(MhaSettingsRequestDto(enabled))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.putMhaTaxProfile(profile: MhaTaxProfile): Result<MhaTaxProfile> = runCatching {
    val req = MhaTaxProfileDto(
        mode = profile.mode,
        simpleFederalRate = profile.simpleFederalRatePercent,
        simpleStateRate = profile.simpleStateRatePercent,
        filingStatus = profile.filingStatus.storageKey,
        state = profile.state,
        deductionMode = profile.deductionMode,
        itemizedDeduction = MoneyUtils.centsToDollars(profile.itemizedDeductionCents),
        taxableIncomeOverride = profile.taxableIncomeOverrideCents?.let { MoneyUtils.centsToDollars(it) },
        stateRateOverride = profile.stateRateOverridePercent
    )
    val res = client.put(url("/api/mha/tax-profile")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<MhaTaxProfileResponseDto>().taxProfile?.toDomain() ?: profile
}

suspend fun OrbitApiClient.putMhaAccountDefault(accountId: Long, eligible: Boolean): Result<Boolean> = runCatching {
    val res = client.put(url("/api/mha/accounts/$accountId/default")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(MhaAccountDefaultRequestDto(eligible))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.putMhaCategoryDefault(categoryId: Long, eligible: Boolean): Result<Boolean> = runCatching {
    val res = client.put(url("/api/mha/categories/$categoryId/default")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(MhaCategoryDefaultRequestDto(eligible))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.putMhaCategoryIgnoreDefault(categoryId: Long, ignored: Boolean): Result<Boolean> = runCatching {
    val res = client.put(url("/api/mha/categories/$categoryId/ignore-default")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(MhaCategoryIgnoreDefaultRequestDto(ignored))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

// =============================================================================
// Household & Sharing Extensions
// =============================================================================

suspend fun OrbitApiClient.saveHouseholdMemberRemote(member: HouseholdMember, effectiveDate: String? = null): Result<HouseholdMember> = runCatching {
    val req = HouseholdMemberUpsertRequestDto(
        name = member.name, role = member.role.lowercase(), birthDate = member.birthDate,
        employmentStatus = member.employmentStatus, employer = member.employer, jobTitle = member.jobTitle,
        grossIncomeAnnual = MoneyUtils.centsToDollars(member.grossIncomeAnnualCents),
        netPayPerPeriod = MoneyUtils.centsToDollars(member.netPayPerPeriodCents),
        payFrequency = member.payFrequency, payPeriodsPerYear = member.payPeriodsPerYear,
        retirementAccountType = member.retirementAccountType,
        employeeContributionPercent = member.employeeContributionPercent,
        employeeContributionAnnual = MoneyUtils.centsToDollars(member.employeeContributionAnnualCents),
        employerMatchPercent = member.employerMatchPercent, employerMatchLimitPercent = member.employerMatchLimitPercent,
        employerMatchAnnualCap = MoneyUtils.centsToDollars(member.employerMatchAnnualCapCents),
        healthPremiumPerMonth = MoneyUtils.centsToDollars(member.healthPremiumPerMonthCents),
        hsaContributionAnnual = MoneyUtils.centsToDollars(member.hsaContributionAnnualCents),
        dependentCareFsaAnnual = MoneyUtils.centsToDollars(member.dependentCareFsaAnnualCents),
        otherBenefitsAnnual = MoneyUtils.centsToDollars(member.otherBenefitsAnnualCents),
        notes = member.notes, retirementAccounts = member.retirementAccounts.map { HouseholdRetirementAccountWireDto.fromDomain(it) },
        effectiveDate = effectiveDate
    )
    val res = if (member.id > 0L) {
        client.put(url("/api/household/members/${member.id}")) { contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req) }
    } else {
        client.post(url("/api/household/members")) { contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req) }
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<HouseholdMemberDto>().toDomain()
}

suspend fun OrbitApiClient.deleteHouseholdMemberRemote(memberId: Long): Result<Boolean> = runCatching {
    val res = client.delete(url("/api/household/members/$memberId")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.addHouseholdIncomeRecordRemote(memberId: Long, record: HouseholdIncomeRecord): Result<HouseholdIncomeRecord> = runCatching {
    val req = HouseholdIncomeRecordWireDto(
        id = record.id, householdId = record.householdId, memberId = memberId, effectiveDate = record.effectiveDate,
        grossIncomeAnnual = MoneyUtils.centsToDollars(record.grossIncomeAnnualCents),
        netPayPerPeriod = MoneyUtils.centsToDollars(record.netPayPerPeriodCents),
        payFrequency = record.payFrequency, payPeriodsPerYear = record.payPeriodsPerYear,
        employeeContributionPercent = record.employeeContributionPercent,
        employeeContributionAnnual = MoneyUtils.centsToDollars(record.employeeContributionAnnualCents),
        employerMatchPercent = record.employerMatchPercent, employerMatchLimitPercent = record.employerMatchLimitPercent,
        employerMatchAnnualCap = MoneyUtils.centsToDollars(record.employerMatchAnnualCapCents),
        healthPremiumPerMonth = MoneyUtils.centsToDollars(record.healthPremiumPerMonthCents),
        hsaContributionAnnual = MoneyUtils.centsToDollars(record.hsaContributionAnnualCents),
        dependentCareFsaAnnual = MoneyUtils.centsToDollars(record.dependentCareFsaAnnualCents),
        otherBenefitsAnnual = MoneyUtils.centsToDollars(record.otherBenefitsAnnualCents),
        source = record.source, notes = record.notes
    )
    val res = client.post(url("/api/household/members/$memberId/income-records")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<HouseholdIncomeRecordWireDto>().toDomain()
}

suspend fun OrbitApiClient.getRetirementHistoryRemote(months: Int = 1200): Result<List<RetirementHistoryPoint>> = runCatching {
    val res = client.get(url("/api/household/retirement-history")) {
        parameter("months", months); auth(sessionCookie)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<RetirementHistoryResponseDto>().history.map { it.toDomain() }
}

suspend fun OrbitApiClient.getHouseholdSharingRemote(): Result<HouseholdSharingState> = runCatching {
    val res = client.get(url("/api/household-sharing")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<HouseholdSharingResponseDto>().toDomain()
}

suspend fun OrbitApiClient.createHouseholdShareRemote(
    email: String, role: String = "member", accessLevel: String = "write"
): Result<HouseholdShare> = runCatching {
    val res = client.post(url("/api/household-sharing/shares")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(CreateShareRequestDto(email, role, accessLevel))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<HouseholdShareWireDto>().toDomain()
}

suspend fun OrbitApiClient.updateShareAccessRemote(shareId: Long, accessLevel: String): Result<Boolean> = runCatching {
    val res = client.patch(url("/api/household-sharing/shares/$shareId/access")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(UpdateAccessRequestDto(accessLevel))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.revokeHouseholdShareRemote(shareId: Long): Result<Boolean> = runCatching {
    val res = client.delete(url("/api/household-sharing/shares/$shareId")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.updateHouseholdUserAccessRemote(userId: Long, accessLevel: String): Result<Boolean> = runCatching {
    val res = client.patch(url("/api/household-sharing/users/$userId/access")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(UpdateAccessRequestDto(accessLevel))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.removeHouseholdUserRemote(userId: Long): Result<Boolean> = runCatching {
    val res = client.delete(url("/api/household-sharing/users/$userId")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

// =============================================================================
// SimpleFIN, Notifications, Preferences & Import/Export Extensions
// =============================================================================

suspend fun OrbitApiClient.getSimpleFinStatus(): Result<SimpleFinStatusInfo> = runCatching {
    val res = client.get(url("/api/simplefin/status")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<SimpleFinStatusWireDto>().toDomain()
}

suspend fun OrbitApiClient.setupSimpleFin(setupToken: String, cutoverDate: String? = null): Result<SimpleFinStatusInfo> = runCatching {
    val res = client.post(url("/api/simplefin/setup")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(SimpleFinSetupRequestDto(setupToken, cutoverDate))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<SimpleFinStatusWireDto>().toDomain()
}

suspend fun OrbitApiClient.getSimpleFinSyncLogs(limit: Int = 20): Result<List<SimpleFinSyncLogEntry>> = runCatching {
    val res = client.get(url("/api/simplefin/sync-log")) {
        parameter("limit", limit); auth(sessionCookie)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<SimpleFinSyncLogResponseDto>().items.map { it.toDomain() }
}

suspend fun OrbitApiClient.disconnectSimpleFin(): Result<Boolean> = runCatching {
    val res = client.post(url("/api/simplefin/disconnect")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.getPushNotificationConfig(): Result<PushNotificationConfigInfo> = runCatching {
    val res = client.get(url("/api/notifications/config")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<NotificationsConfigWireDto>().toDomain()
}

suspend fun OrbitApiClient.registerPushSubscription(
    endpoint: String, p256dh: String, auth: String, expirationTime: Long? = null
): Result<Boolean> = runCatching {
    val req = PushSubscriptionRequestDto(endpoint, PushSubscriptionKeysDto(p256dh, auth), expirationTime)
    val res = client.post(url("/api/notifications/subscriptions")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(req)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.sendTestPushNotification(): Result<Boolean> = runCatching {
    val res = client.post(url("/api/notifications/test")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.getRemotePreferences(): Result<JsonObject> = runCatching {
    val res = client.get(url("/api/preferences")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<RemotePreferencesResponseDto>().preferences
}

suspend fun OrbitApiClient.putRemotePreferences(prefs: Map<String, JsonElement>): Result<Boolean> = runCatching {
    val res = client.put(url("/api/preferences")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(PutRemotePreferencesRequestDto(prefs))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.putRemotePreferenceKey(key: String, value: JsonElement): Result<Boolean> = runCatching {
    val res = client.put(url("/api/preferences/$key")) {
        contentType(ContentType.Application.Json); auth(sessionCookie); setBody(PutRemotePreferenceKeyRequestDto(value))
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}

suspend fun OrbitApiClient.exportBudgetingDataRemote(format: String): Result<String> = runCatching {
    val res = client.get(url("/api/data/budgeting-export")) {
        parameter("format", format); auth(sessionCookie)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.bodyAsText()
}

suspend fun OrbitApiClient.uploadRocketMoneyCsvRemote(
    rawCsvContent: String, fileName: String = "rocket_money.csv"
): Result<ImportBatchRecord> = runCatching {
    val res = client.submitFormWithBinaryData(
        url = url("/api/import/rocket-money"),
        formData = formData {
            append("file", rawCsvContent.encodeToByteArray(), Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                append(HttpHeaders.ContentType, "text/csv")
            })
        }
    ) {
        auth(sessionCookie)
    }
    extractSessionCookie(res); ensureSuccess(res)
    res.body<ImportRocketMoneyResponseDto>().toDomain(fileName)
}

suspend fun OrbitApiClient.undoImportBatchRemote(batchId: Long): Result<Boolean> = runCatching {
    val res = client.post(url("/api/import/batches/$batchId/undo")) { auth(sessionCookie) }
    extractSessionCookie(res); ensureSuccess(res)
    res.status.isSuccess()
}
