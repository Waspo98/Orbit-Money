package app.overbay.orbit.data

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.InstitutionLogoDefaults
import app.overbay.orbit.data.model.MerchantLogo
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.cleanMerchantNameForLogo
import app.overbay.orbit.data.network.MerchantLogoCandidateDto
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.data.network.ensureAccountInstitutionLogo
import app.overbay.orbit.data.network.overrideMerchantLogo
import app.overbay.orbit.data.network.reportMerchantLogoStatus
import app.overbay.orbit.data.network.searchAccountInstitutionLogos
import app.overbay.orbit.data.network.searchMerchantLogos

suspend fun OrbitRepository.reportAccountInstitutionLogoStatus(merchantKey: String, status: String) {
    if (merchantKey.isBlank()) return
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        runCatching { createApiClient().reportMerchantLogoStatus(merchantKey, status) }
    }
    if (status == "loaded") return

    val updatedList = accounts.value.map { acct ->
        val currentLogo = acct.institutionLogo
        if (currentLogo?.merchantKey == merchantKey && currentLogo.status == "candidate") {
            acct.copy(institutionLogo = currentLogo.copy(status = status, url = if (status == "failed") null else currentLogo.url))
        } else acct
    }
    if (updatedList != accounts.value) {
        commitSnapshot(snapshot.value.copy(accounts = updatedList))
    }
}

suspend fun OrbitRepository.searchAccountInstitutionLogos(accountId: Long, query: String): Result<MerchantLogoSearchResponseDto> = runCatching {
    if (storageMode.value != StorageMode.SELF_HOSTED) {
        return@runCatching fallbackInstitutionLogoSearch(query)
    }
    val client = createApiClient()
    val res = client.searchAccountInstitutionLogos(accountId, query)
    if (res.isSuccess) return@runCatching res.getOrThrow()

    val msg = res.exceptionOrNull()?.message.orEmpty()
    if ("HTTP 400" in msg || "HTTP 404" in msg) {
        val sampleTxnId = transactions.value.firstOrNull { it.accountId == accountId }?.id
            ?: transactions.value.firstOrNull()?.id
        if (sampleTxnId != null) {
            val legacySearch = client.searchMerchantLogos(sampleTxnId, query)
            if (legacySearch.isSuccess) return@runCatching legacySearch.getOrThrow()
        }
        return@runCatching fallbackInstitutionLogoSearch(query)
    }
    res.getOrThrow()
}

private fun fallbackInstitutionLogoSearch(query: String): MerchantLogoSearchResponseDto {
    val domain = InstitutionLogoDefaults.resolveDomain(query)
    val candidates = if (domain != null) {
        listOf(
            MerchantLogoCandidateDto(
                name = query.trim(),
                domain = domain,
                logoUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
            )
        )
    } else emptyList()
    return MerchantLogoSearchResponseDto(configured = candidates.isNotEmpty(), candidates = candidates)
}

suspend fun OrbitRepository.overrideAccountInstitutionLogo(
    accountId: Long,
    logoUrl: String? = null,
    useAccountIcon: Boolean = false
): Result<MerchantLogo> = runCatching {
    val target = accounts.value.find { it.id == accountId }
        ?: throw IllegalStateException("Account not found.")
    val cleanedName = cleanMerchantNameForLogo(target.institution?.takeIf { it.isNotBlank() } ?: target.name)
        .ifBlank { target.name }
    val fallbackKey = target.resolvedInstitutionLogoKey
    val localConstructed = { key: String ->
        MerchantLogo(
            merchantKey = key,
            merchantName = cleanedName,
            providerQuery = cleanedName,
            url = if (useAccountIcon) null else logoUrl?.trim(),
            status = if (useAccountIcon) "hidden" else "manual"
        )
    }

    val updatedLogo = if (storageMode.value == StorageMode.SELF_HOSTED) {
        val client = createApiClient()
        val ensuredKey = target.institutionLogo?.merchantKey?.takeIf { it.isNotBlank() }
            ?: client.ensureAccountInstitutionLogo(accountId).getOrNull()?.merchantKey
            ?: fallbackKey
        val remoteRes = client.overrideMerchantLogo(
            merchantKey = ensuredKey,
            logoUrl = logoUrl,
            useCategoryIcon = useAccountIcon,
            accountId = accountId,
            merchantName = cleanedName
        )
        if (remoteRes.isSuccess) {
            remoteRes.getOrThrow().toDomain() ?: localConstructed(ensuredKey)
        } else {
            val errMsg = remoteRes.exceptionOrNull()?.message.orEmpty()
            if ("HTTP 404" in errMsg || "HTTP 400" in errMsg) {
                localConstructed(ensuredKey)
            } else {
                remoteRes.getOrThrow()
                localConstructed(ensuredKey)
            }
        }
    } else {
        localConstructed(fallbackKey)
    }

    val currentPrefs = preferences.value
    val nextOverrides = currentPrefs.accountInstitutionLogoOverrides + (updatedLogo.merchantKey to updatedLogo)
    val nextPrefs = currentPrefs.copy(accountInstitutionLogoOverrides = nextOverrides)

    val updatedAccounts = accounts.value.map { acct ->
        if (acct.marksSameInstitutionLogo(target, updatedLogo.merchantKey)) {
            acct.copy(institutionLogo = updatedLogo)
        } else acct
    }
    commitSnapshot(snapshot.value.copy(accounts = updatedAccounts, preferences = nextPrefs))
    updatePreferences(nextPrefs)
    updatedLogo
}

private fun Account.marksSameInstitutionLogo(target: Account, updatedKey: String): Boolean {
    if (id == target.id) return true
    if (institutionLogo?.merchantKey == updatedKey) return true
    if (!updatedKey.startsWith("inst:acct-") &&
        !institution.isNullOrBlank() &&
        institution.trim().equals(target.institution?.trim(), ignoreCase = true)
    ) {
        return true
    }
    return false
}
