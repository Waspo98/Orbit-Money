package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BalanceRecordSource
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.BalanceHistoryEngine
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class SimpleFinOrg(
    val name: String? = null,
    val domain: String? = null,
    @SerialName("sfin-url") val sfinUrl: String? = null
)

@Serializable
data class SimpleFinAccount(
    val id: String,
    val name: String,
    val currency: String = "USD",
    val balance: String = "0.00",
    @SerialName("available-balance") val availableBalance: String? = null,
    @SerialName("balance-date") val balanceDate: Long = 0L,
    val org: SimpleFinOrg? = null,
    val transactions: List<SimpleFinTransaction> = emptyList()
)

@Serializable
data class SimpleFinTransaction(
    val id: String,
    val posted: Long,
    val amount: String,
    val description: String,
    val payee: String? = null,
    val memo: String? = null
)

@Serializable
data class SimpleFinDataResponse(
    val errors: List<String> = emptyList(),
    val accounts: List<SimpleFinAccount> = emptyList()
)

data class DirectSyncOutcome(
    val updatedSnapshot: OrbitWorkspaceSnapshot,
    val newTransactionCount: Int,
    val accountsCreatedCount: Int = 0,
    val accountsSyncedCount: Int = 0
)

/**
 * Pure Kotlin multiplatform client communicating directly with the SimpleFIN Bridge protocol.
 * Enables Android in Single-User Local Mode to connect and sync banks without an Express backend.
 */
class SimpleFinDirectClient(
    private val customClient: HttpClient? = null
) {
    private val client: HttpClient = customClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 25000L
            connectTimeoutMillis = 15000L
        }
    }

    fun isAccessUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("https://", ignoreCase = true) && trimmed.contains("@")
    }

    @OptIn(ExperimentalEncodingApi::class)
    internal fun decodeClaimUrl(setupToken: String): String {
        val trimmed = setupToken.trim()
        require(trimmed.isNotEmpty()) { "Please paste your SimpleFIN setup token." }
        val decoded = if (trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            runCatching { Base64.decode(trimmed).decodeToString().trim() }
                .getOrElse { throw IllegalArgumentException("That doesn't look like a valid SimpleFIN setup token.") }
        }
        require(decoded.startsWith("https://", ignoreCase = true)) {
            "Setup token did not decode to a valid https URL."
        }
        return decoded
    }

    /**
     * Claims a permanent access URL from a one-shot base64 setup token.
     * If given an already-claimed SimpleFIN access URL (`https://user:pass@...`), returns it directly.
     */
    suspend fun claimAccessUrl(setupToken: String): Result<String> = runCatching {
        val trimmed = setupToken.trim()
        require(trimmed.isNotEmpty()) { "Please paste your SimpleFIN setup token." }
        if (isAccessUrl(trimmed)) return@runCatching trimmed

        val decodedClaimUrl = decodeClaimUrl(trimmed)
        val (cleanUrl, authHeader) = splitBasicAuth(decodedClaimUrl)
        val response = client.post(cleanUrl) {
            if (authHeader != null) header("Authorization", authHeader)
        }
        val accessUrl = response.bodyAsText().trim()
        require(isAccessUrl(accessUrl)) {
            "SimpleFIN rejected that setup token (tokens can only be claimed once)."
        }
        accessUrl
    }

    /**
     * Fetches accounts and posted transactions from SimpleFIN Bridge.
     */
    suspend fun fetchBridgeData(
        accessUrl: String,
        startDateSeconds: Long? = null,
        endDateSeconds: Long? = null
    ): Result<SimpleFinDataResponse> = runCatching {
        val (cleanUrl, authHeader) = splitBasicAuth(accessUrl)
        val endpoint = "${cleanUrl.trimEnd('/')}/accounts"
        val response = client.get(endpoint) {
            if (authHeader != null) header("Authorization", authHeader)
            if (startDateSeconds != null) parameter("start-date", startDateSeconds)
            if (endDateSeconds != null) parameter("end-date", endDateSeconds)
        }
        response.body()
    }

    /**
     * High-level direct sync that fetches bank data, auto-creates any unseen accounts,
     * updates balance checkpoints, and merges new transactions into OrbitWorkspaceSnapshot.
     */
    suspend fun syncWithSnapshot(
        accessUrl: String,
        snapshot: OrbitWorkspaceSnapshot,
        cutoverDateIso: String? = null
    ): DirectSyncOutcome {
        val normalizedCutover = cutoverDateIso?.trim()?.takeIf { it.length == 10 }
        val startDateSec = resolveStartDateEpochSeconds(normalizedCutover)
        val bridgeData = fetchBridgeData(accessUrl, startDateSeconds = startDateSec).getOrThrow()
        return mergeBridgeData(bridgeData, snapshot, normalizedCutover)
    }

    internal fun mergeBridgeData(
        bridgeData: SimpleFinDataResponse,
        snapshot: OrbitWorkspaceSnapshot,
        cutoverDateIso: String? = null
    ): DirectSyncOutcome {
        val normalizedCutover = cutoverDateIso?.trim()?.takeIf { it.length == 10 }
        val existingTxIds = snapshot.transactions.mapNotNull { it.externalId }.toMutableSet()
        val accountBySimpleFinId = snapshot.accounts
            .filter { !it.simplefinAccountId.isNullOrBlank() }
            .associateBy { it.simplefinAccountId!! }
            .toMutableMap()
        val todayIso = LocalDateUtils.todayIsoDate()

        val createdAccounts = mutableListOf<Account>()
        val newTransactions = mutableListOf<Transaction>()
        val updatedBalancesByAccountId = mutableMapOf<Long, Cents>()
        var updatedRecords = snapshot.balanceRecords
        var nextAccountId = (snapshot.accounts.maxOfOrNull { it.id } ?: 0L) + 1L
        var nextSortOrder = (snapshot.accounts.maxOfOrNull { it.sortOrder } ?: 0) + 10
        var nextTxId = (snapshot.transactions.maxOfOrNull { it.id } ?: 0L) + 1L
        var syncedCount = 0

        for (sfAccount in bridgeData.accounts) {
            val balanceCentsParsed = parseDecimalStringToCents(sfAccount.balance)
            val balanceCents = balanceCentsParsed ?: 0L
            val recordDate = if (sfAccount.balanceDate > 0L) formatEpochDate(sfAccount.balanceDate) else todayIso

            val existing = accountBySimpleFinId[sfAccount.id]
            val localAccount = if (existing != null) {
                existing
            } else {
                val created = Account(
                    id = nextAccountId++,
                    name = sfAccount.name.ifBlank { "Linked Account" },
                    type = inferAccountType(sfAccount.name, sfAccount.org?.name, balanceCents),
                    institution = sfAccount.org?.name?.takeIf { it.isNotBlank() },
                    currentBalanceCents = balanceCents,
                    isManual = false,
                    simplefinAccountId = sfAccount.id,
                    sortOrder = nextSortOrder
                )
                nextSortOrder += 10
                createdAccounts.add(created)
                accountBySimpleFinId[sfAccount.id] = created
                created
            }

            syncedCount += 1

            if (balanceCentsParsed != null) {
                updatedBalancesByAccountId[localAccount.id] = balanceCents
                updatedRecords = BalanceHistoryEngine.upsertAutoCheckpoint(
                    records = updatedRecords,
                    account = localAccount.copy(currentBalanceCents = balanceCents),
                    recordDate = recordDate,
                    balanceCents = balanceCents,
                    source = BalanceRecordSource.SIMPLEFIN
                )
            }

            for (sfTx in sfAccount.transactions) {
                if (!existingTxIds.add(sfTx.id)) continue
                val dateIso = formatEpochDate(sfTx.posted)
                if (normalizedCutover != null && dateIso < normalizedCutover) continue

                val amountCents = parseDecimalStringToCents(sfTx.amount) ?: 0L
                val merchantName = sfTx.payee?.takeIf { it.isNotBlank() } ?: sfTx.description

                newTransactions.add(
                    Transaction(
                        id = nextTxId++,
                        accountId = localAccount.id,
                        accountName = localAccount.name,
                        originalDate = dateIso,
                        originalMerchant = merchantName,
                        originalDescription = sfTx.description,
                        originalAmountCents = amountCents,
                        source = "simplefin",
                        externalId = sfTx.id
                    )
                )
            }
        }

        val updatedExistingAccounts = snapshot.accounts.map { acct ->
            val syncedBal = updatedBalancesByAccountId[acct.id]
            if (syncedBal != null) acct.copy(currentBalanceCents = syncedBal) else acct
        }
        val merged = snapshot.copy(
            accounts = updatedExistingAccounts + createdAccounts,
            balanceRecords = updatedRecords,
            transactions = snapshot.transactions + newTransactions
        )
        return DirectSyncOutcome(
            updatedSnapshot = merged,
            newTransactionCount = newTransactions.size,
            accountsCreatedCount = createdAccounts.size,
            accountsSyncedCount = syncedCount
        )
    }

    internal fun inferAccountType(name: String, orgName: String?, balanceCents: Cents): AccountType {
        val text = "$name ${orgName.orEmpty()}".lowercase()
        return when {
            Regex("mortgage|home loan").containsMatchIn(text) -> AccountType.MORTGAGE
            Regex("credit|card|sapphire|amex|visa|mastercard|discover|venture|freedom|cash back").containsMatchIn(text) -> AccountType.CREDIT
            Regex("auto loan|student loan|loan|line of credit|heloc").containsMatchIn(text) -> AccountType.LOAN
            Regex("401\\(?k\\)?|ira|roth|vanguard|fidelity|schwab|betterment|brokerage|hsa|403b|tsp|investment").containsMatchIn(text) -> AccountType.INVESTMENT
            Regex("saving|money market|reserve|\\bcd\\b").containsMatchIn(text) -> AccountType.SAVINGS
            Regex("checking|debit|everyday").containsMatchIn(text) -> AccountType.CHECKING
            balanceCents < 0L -> AccountType.CREDIT
            else -> AccountType.CHECKING
        }
    }

    internal fun parseDecimalStringToCents(raw: String): Cents? {
        val s = raw.trim().replace("$", "").replace(",", "")
        if (s.isEmpty()) return null
        val negative = s.startsWith("-")
        val unsigned = if (negative || s.startsWith("+")) s.substring(1) else s
        if (unsigned.isEmpty() || unsigned.count { it == '.' } > 1) return null
        val parts = unsigned.split('.')
        val wholeStr = parts[0].ifEmpty { "0" }
        val fracRaw = parts.getOrElse(1) { "" }
        if (!wholeStr.all(Char::isDigit) || !fracRaw.all(Char::isDigit)) return null
        val whole = wholeStr.toLongOrNull() ?: return null
        val paddedFrac = fracRaw.padEnd(3, '0')
        val twoDigits = paddedFrac.substring(0, 2).toLong()
        val roundUp = if (paddedFrac[2] >= '5') 1L else 0L
        val total = whole * 100L + twoDigits + roundUp
        return if (negative) -total else total
    }

    private fun resolveStartDateEpochSeconds(cutoverDateIso: String?): Long {
        if (!cutoverDateIso.isNullOrBlank()) {
            val parsed = runCatching { LocalDate.parse(cutoverDateIso) }.getOrNull()
            if (parsed != null) {
                return parsed.atStartOfDayIn(TimeZone.UTC).epochSeconds
            }
        }
        return Clock.System.now().epochSeconds - (90L * 86400L)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun splitBasicAuth(rawUrl: String): Pair<String, String?> {
        val schemeEnd = rawUrl.indexOf("://")
        if (schemeEnd == -1) return Pair(rawUrl, null)

        val afterScheme = rawUrl.substring(schemeEnd + 3)
        val atIndex = afterScheme.indexOf('@')
        val slashIndex = afterScheme.indexOf('/')

        if (atIndex != -1 && (slashIndex == -1 || atIndex < slashIndex)) {
            val userPass = afterScheme.substring(0, atIndex)
            val rest = afterScheme.substring(atIndex + 1)
            val cleanUrl = "${rawUrl.substring(0, schemeEnd + 3)}$rest"
            val authHeader = "Basic ${Base64.encode(userPass.encodeToByteArray())}"
            return Pair(cleanUrl, authHeader)
        }
        return Pair(rawUrl, null)
    }

    internal fun formatEpochDate(epochSeconds: Long): String =
        Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.UTC).date.toString()
}
