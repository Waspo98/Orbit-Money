package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

private fun HttpRequestBuilder.auth(cookie: String?) {
    cookie?.let { header("Cookie", it) }
}

suspend fun OrbitApiClient.saveCreditCardProfileRemote(
    accountId: Long,
    accountName: String?,
    accountInstitution: String?,
    profile: CreditCardProfile
): Result<CreditCardProfile> = runCatching {
    val req = CreditCardProfileUpsertRequestDto(
        accountName = accountName?.takeIf { it.isNotBlank() } ?: profile.cardName ?: "Credit Card",
        accountInstitution = accountInstitution,
        cardName = profile.cardName,
        issuerName = profile.issuerName,
        network = profile.network,
        imageUrl = profile.imageUrl,
        annualFee = profile.annualFeeCents?.let { MoneyUtils.centsToDollars(it) },
        annualFeePostDate = profile.annualFeePostDate,
        creditLimit = profile.creditLimitCents?.let { MoneyUtils.centsToDollars(it) },
        statementClosingDay = profile.statementClosingDay,
        paymentDueDay = profile.paymentDueDay,
        aprBps = profile.aprBps,
        promoAprBps = profile.promoAprBps,
        promoEndDate = profile.promoEndDate,
        authorizedUsers = profile.authorizedUsers,
        rewardCategories = profile.rewardCategories.map { RewardCategoryWireDto.fromDomain(it) },
        benefits = profile.benefits.map { CardBenefitWireDto.fromDomain(it) },
        strategy = CreditCardStrategyWireDto.fromDomain(profile.strategy),
        notes = profile.notes
    )
    val res = client.put(url("/api/accounts/$accountId/credit-card-profile")) {
        contentType(ContentType.Application.Json)
        auth(sessionCookie)
        setBody(req)
    }
    extractSessionCookie(res)
    ensureSuccess(res)
    res.body<CreditCardProfileMutationResponseDto>().profile?.toDomain()?.copy(accountId = accountId) ?: profile
}

suspend fun OrbitApiClient.saveBenefitUsageRemote(
    accountId: Long,
    benefitId: String,
    periodKey: String,
    amountCents: Cents?,
    usedOn: String?
): Result<CreditCardProfile> = runCatching {
    val req = BenefitUsageRequestDto(periodKey, amountCents?.let { MoneyUtils.centsToDollars(it) }, usedOn)
    val res = client.put(url("/api/accounts/$accountId/credit-card-profile/benefits/$benefitId/usage")) {
        contentType(ContentType.Application.Json)
        auth(sessionCookie)
        setBody(req)
    }
    extractSessionCookie(res)
    ensureSuccess(res)
    res.body<CreditCardProfileMutationResponseDto>().profile?.toDomain()?.copy(accountId = accountId)
        ?: throw IllegalStateException("Missing profile in response")
}
