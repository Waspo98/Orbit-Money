package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.data.overrideAccountInstitutionLogo
import app.overbay.orbit.data.reportAccountInstitutionLogoStatus
import app.overbay.orbit.data.searchAccountInstitutionLogos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountLogoController(
    private val repository: OrbitRepository,
    private val scope: CoroutineScope
) {
    private val _logoFinderAccount = MutableStateFlow<Account?>(null)
    val logoFinderAccount: StateFlow<Account?> = _logoFinderAccount.asStateFlow()

    fun open(account: Account) {
        _logoFinderAccount.value = account
    }

    fun close() {
        _logoFinderAccount.value = null
    }

    suspend fun search(accountId: Long, query: String): Result<MerchantLogoSearchResponseDto> =
        repository.searchAccountInstitutionLogos(accountId, query)

    suspend fun saveLogoUrl(accountId: Long, logoUrl: String): Result<Unit> =
        repository.overrideAccountInstitutionLogo(accountId, logoUrl = logoUrl, useAccountIcon = false).map { }

    suspend fun useAccountIcon(accountId: Long): Result<Unit> =
        repository.overrideAccountInstitutionLogo(accountId, logoUrl = null, useAccountIcon = true).map { }

    fun resetToAccountIconAsync(accountId: Long) {
        scope.launch {
            repository.overrideAccountInstitutionLogo(accountId, logoUrl = null, useAccountIcon = true)
        }
    }

    fun reportStatus(merchantKey: String, status: String) {
        scope.launch {
            repository.reportAccountInstitutionLogoStatus(merchantKey, status)
        }
    }
}
