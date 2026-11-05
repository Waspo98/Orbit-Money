package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.MoneyUtils
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class SimpleFinAccount(
    val id: String,
    val name: String,
    val currency: String = "USD",
    val balance: String = "0.00",
    @SerialName("available-balance") val availableBalance: String? = null,
    @SerialName("balance-date") val balanceDate: Long = 0L,
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
    val newTransactionCount: Int
)

/**
 * Pure Kotlin multiplatform client communicating directly with the SimpleFIN Bridge protocol.
 * Enables Android and Web PWA in Single-User Local Mode to connect and sync banks without an Express backend.
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
            requestTimeoutMillis = 20000L
            connectTimeoutMillis = 15000L
        }
    }

    /**
     * Claims a permanent access URL from a one-shot base64 setup token.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun claimAccessUrl(setupToken: String): Result<String> = runCatching {
        val trimmed = setupToken.trim()
        val decodedClaimUrl = Base64.decode(trimmed).decodeToString().trim()
        require(decodedClaimUrl.startsWith("https://", ignoreCase = true)) {
            "Setup token did not decode to a valid https URL."
        }

        val (cleanUrl, authHeader) = splitBasicAuth(decodedClaimUrl)
        val response = client.post(cleanUrl) {
            if (authHeader != null) header("Authorization", authHeader)
        }
        val accessUrl = response.bodyAsText().trim()
        require(accessUrl.startsWith("https://", ignoreCase = true) && accessUrl.contains("@")) {
            "SimpleFIN returned an invalid access URL: $accessUrl"
        }
        accessUrl
    }

    /**
     * Fetches accounts and posted transactions from SimpleFIN Bridge.
     */
    suspend fun fetchBridgeData(accessUrl: String, startDateSeconds: Long? = null, endDateSeconds: Long? = null): Result<SimpleFinDataResponse> = runCatching {
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
     * High-level direct sync that fetches bank data and merges new transactions into OrbitWorkspaceSnapshot.
     */
    suspend fun syncWithSnapshot(accessUrl: String, snapshot: OrbitWorkspaceSnapshot): DirectSyncOutcome {
        val bridgeData = fetchBridgeData(accessUrl).getOrThrow()
        val existingTxIds = snapshot.transactions.mapNotNull { it.externalId }.toSet()
        val accountBySimpleFinId = snapshot.accounts.associateBy { it.simplefinAccountId }

        val newTransactions = mutableListOf<Transaction>()
        var nextId = (snapshot.transactions.maxOfOrNull { it.id } ?: 0L) + 1L

        for (sfAccount in bridgeData.accounts) {
            val localAccount = accountBySimpleFinId[sfAccount.id] ?: continue
            for (sfTx in sfAccount.transactions) {
                if (existingTxIds.contains(sfTx.id)) continue

                val dateIso = formatEpochDate(sfTx.posted)
                val amountDouble = sfTx.amount.toDoubleOrNull() ?: 0.0
                val amountCents = MoneyUtils.dollarsToCents(amountDouble)
                val merchantName = sfTx.payee?.takeIf { it.isNotBlank() } ?: sfTx.description

                newTransactions.add(
                    Transaction(
                        id = nextId++,
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

        val merged = snapshot.copy(
            transactions = snapshot.transactions + newTransactions
        )
        return DirectSyncOutcome(merged, newTransactions.size)
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

    private fun formatEpochDate(epochSeconds: Long): String {
        // Approximate YYYY-MM-DD from epoch days
        val days = epochSeconds / 86400
        var l = days + 719468
        var n = (4 * l) / 146097
        l -= (146097 * n + 3) / 4
        var y = (4000 * (l + 1)) / 1461001
        l -= (1461 * y) / 4 - 31
        var m = (80 * l) / 2447
        val d = l - (2447 * m) / 80
        l = m / 11
        m = m + 2 - 12 * l
        y = 100 * (n - 49) + y + l
        val mm = if (m < 10) "0$m" else "$m"
        val dd = if (d < 10) "0$d" else "$d"
        return "$y-$mm-$dd"
    }
}
