package app.overbay.orbit.data

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.MerchantLogo
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.cleanMerchantNameForLogo
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.data.network.ensureAccountInstitutionLogo
import app.overbay.orbit.data.network.overrideMerchantLogo
import app.overbay.orbit.data.network.reportMerchantLogoStatus
import app.overbay.orbit.data.network.searchAccountInstitutionLogos

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
        MerchantLogoSearchResponseDto(configured = false, candidates = emptyList())
    } else {
        createApiClient().searchAccountInstitutionLogos(accountId, query).getOrThrow()
    }
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

    val updatedLogo = if (storageMode.value == StorageMode.SELF_HOSTED) {
        val client = createApiClient()
        val ensuredKey = target.institutionLogo?.merchantKey?.takeIf { it.isNotBlank() }
            ?: client.ensureAccountInstitutionLogo(accountId).getOrNull()?.merchantKey
            ?: fallbackKey
        client.overrideMerchantLogo(ensuredKey, logoUrl, useAccountIcon).getOrThrow().toDomain()
            ?: MerchantLogo(
                merchantKey = ensuredKey,
                merchantName = cleanedName,
                url = if (useAccountIcon) null else logoUrl?.trim(),
                status = if (useAccountIcon) "hidden" else "manual"
            )
    } else {
        MerchantLogo(
            merchantKey = fallbackKey,
            merchantName = cleanedName,
            providerQuery = cleanedName,
            url = if (useAccountIcon) null else logoUrl?.trim(),
            status = if (useAccountIcon) "hidden" else "manual"
        )
    }

    val updatedAccounts = accounts.value.map { acct ->
        if (acct.marksSameInstitutionLogo(target, updatedLogo.merchantKey)) {
            acct.copy(institutionLogo = updatedLogo)
        } else acct
    }
    commitSnapshot(snapshot.value.copy(accounts = updatedAccounts))
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
