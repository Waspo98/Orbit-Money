package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.TransactionFilter
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.util.PlatformStorage
import app.overbay.orbit.util.PlatformStorageEpoch
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlin.concurrent.Volatile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/**
 * Multiplatform HTTP client communicating with the self-hosted Express API endpoints.
 * Uses Ktor 3 with persistent session cookie management via PlatformStorage.
 */
class OrbitApiClient(
    private val baseUrlProvider: () -> String,
    private val storage: PlatformStorage,
    private val customHttpClient: HttpClient? = null
) {
    internal val client: HttpClient = customHttpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000L
            connectTimeoutMillis = 10000L
            socketTimeoutMillis = 15000L
        }
    }

    @Volatile
    private var cachedSessionCookie: String? = null

    @Volatile
    private var cookieLoadedEpoch: Long = -1L

    internal var sessionCookie: String?
        get() {
            val currentEpoch = PlatformStorageEpoch.epoch
            if (cookieLoadedEpoch != currentEpoch) {
                cachedSessionCookie = storage.loadString(COOKIE_STORAGE_KEY)
                cookieLoadedEpoch = currentEpoch
            }
            return cachedSessionCookie
        }
        set(value) {
            val currentEpoch = PlatformStorageEpoch.epoch
            if (cookieLoadedEpoch == currentEpoch && cachedSessionCookie == value) return
            cachedSessionCookie = value
            cookieLoadedEpoch = currentEpoch
            if (value != null) storage.saveString(COOKIE_STORAGE_KEY, value)
            else storage.delete(COOKIE_STORAGE_KEY)
        }

    internal fun extractSessionCookie(response: HttpResponse) {
        val allCookies = response.headers.getAll("Set-Cookie")
            ?: response.headers.getAll("set-cookie")
            ?: listOfNotNull(response.headers["Set-Cookie"] ?: response.headers["set-cookie"])
        for (raw in allCookies) {
            val pair = raw.substringBefore(";").trim()
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0 && eqIdx < pair.length - 1) {
                sessionCookie = pair
            }
        }
    }

    internal fun ensureSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            if (response.status.value == 401) {
                throw IllegalStateException("Unauthorized (HTTP 401) — please sign in to the server")
            }
            throw IllegalStateException("Server returned HTTP ${response.status.value}")
        }
    }

    internal fun url(path: String): String {
        val base = ServerUrlNormalizer.normalize(baseUrlProvider())
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return if (base.isNotEmpty()) "$base$normalizedPath" else normalizedPath
    }

    // Auth Endpoints
    suspend fun getAuthConfig(): Result<AuthConfigResponseDto> = runCatching {
        val response = client.get(url("/api/auth/config")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        response.body()
    }

    suspend fun exchangeOidcCode(code: String): Result<AuthMeResponseDto> = runCatching {
        val response = client.post(url("/api/auth/oidc/native-exchange")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(OidcNativeExchangeRequestDto(code = code))
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        response.body()
    }

    suspend fun login(username: String, password: String): Result<AuthMeResponseDto> = runCatching {
        val response = client.post(url("/api/auth/login")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(LoginRequestDto(username = username, password = password))
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        getMe().getOrDefault(AuthMeResponseDto(authenticated = true, username = username))
    }

    suspend fun loginWithSampleData(deviceId: String): Result<AuthMeResponseDto> = runCatching {
        val response = client.post(url("/api/auth/sample")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(SampleLoginRequestDto(deviceId = deviceId))
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        response.body()
    }

    suspend fun checkHealth(): Result<Boolean> = runCatching {
        val response = client.get(url("/api/health"))
        ensureSuccess(response)
        val body = response.body<HealthResponseDto>()
        body.status.equals("ok", ignoreCase = true)
    }

    suspend fun getMe(): Result<AuthMeResponseDto> = runCatching {
        val response = client.get(url("/api/auth/me")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        response.body()
    }

    suspend fun logout(): Result<Boolean> = runCatching {
        val response = client.post(url("/api/auth/logout")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        sessionCookie = null
        response.status.isSuccess()
    }

    // Accounts Endpoints
    suspend fun getAccounts(includeArchived: Boolean = true): Result<List<AccountDto>> = runCatching {
        val response = client.get(url("/api/accounts")) {
            sessionCookie?.let { header("Cookie", it) }
            if (includeArchived) parameter("includeArchived", "1")
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        runCatching { response.body<AccountListResponseDto>().items }
            .getOrElse { response.body() }
    }

    suspend fun createAccount(account: AccountDto): Result<AccountDto> = runCatching {
        val response = client.post(url("/api/accounts")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(account)
        }
        extractSessionCookie(response)
        runCatching { response.body<AccountMutationResponseDto>().account ?: account }
            .getOrElse { response.body() }
    }

    suspend fun updateAccount(id: Long, account: AccountDto): Result<AccountDto> = runCatching {
        val response = client.put(url("/api/accounts/$id")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(account)
        }
        extractSessionCookie(response)
        runCatching { response.body<AccountMutationResponseDto>().account ?: account.copy(id = id) }
            .getOrElse { response.body() }
    }

    suspend fun deleteAccount(id: Long): Result<Boolean> = runCatching {
        val response = client.delete(url("/api/accounts/$id")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        response.status.isSuccess()
    }

    suspend fun mergeAccounts(sourceId: Long, targetId: Long): Result<Boolean> = runCatching {
        val response = client.post(url("/api/accounts/$sourceId/merge")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(MergeAccountRequestDto(targetId = targetId))
        }
        extractSessionCookie(response)
        response.status.isSuccess()
    }

    // Transactions Endpoints
    suspend fun getTransactions(
        page: Int = 1,
        limit: Int = 50,
        filter: TransactionFilter = TransactionFilter(),
        sort: String = "date_desc"
    ): Result<TransactionListResponseDto> = runCatching {
        val response = client.get(url("/api/transactions")) {
            sessionCookie?.let { header("Cookie", it) }
            parameter("page", page)
            parameter("limit", limit)
            parameter("sort", sort)
            if (filter.query.isNotBlank()) parameter("q", filter.query)
            if (filter.startDate != null) parameter("date_from", filter.startDate)
            if (filter.endDate != null) parameter("date_to", filter.endDate)
            if (filter.accountIds.isNotEmpty()) parameter("accounts", filter.accountIds.joinToString(","))
            if (filter.categoryIds.isNotEmpty() || filter.uncategorizedOnly) {
                val cats = buildList {
                    addAll(filter.categoryIds.map { it.toString() })
                    if (filter.uncategorizedOnly) add("uncategorized")
                }.joinToString(",")
                parameter("categories", cats)
            }
            if (filter.minAmountCents != null) parameter("amount_min", MoneyUtils.centsToDollars(filter.minAmountCents))
            if (filter.maxAmountCents != null) parameter("amount_max", MoneyUtils.centsToDollars(filter.maxAmountCents))
            if (filter.transactionType != "all") parameter("type", filter.transactionType)
            parameter("include_ignored", if (filter.includeIgnored) "1" else "0")
            parameter("include_transfers", if (filter.includeTransfers) "1" else "0")
            if (filter.hasEditsFilter != "any") parameter("has_edits", filter.hasEditsFilter)
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        response.body()
    }

    suspend fun getAllTransactions(maxPages: Int = 50): Result<List<TransactionDto>> = runCatching {
        val firstPage = getTransactions(page = 1, limit = 200).getOrThrow()
        val totalPages = firstPage.totalPages.coerceIn(1, maxPages)
        if (totalPages <= 1) return@runCatching firstPage.items
        val allItems = ArrayList<TransactionDto>(firstPage.total.coerceAtLeast(firstPage.items.size))
        allItems.addAll(firstPage.items)
        // Fetch remaining pages in bounded parallel batches of 4 to cut multi-page sync latency by ~4x
        // while preserving deterministic page order and avoiding connection-pool saturation.
        coroutineScope {
            for (batch in (2..totalPages).chunked(4)) {
                val pages = batch.map { page ->
                    async { getTransactions(page = page, limit = 200).getOrNull() }
                }.awaitAll()
                var reachedEnd = false
                for (pageResult in pages) {
                    if (pageResult == null || pageResult.items.isEmpty()) {
                        reachedEnd = true
                        break
                    }
                    allItems.addAll(pageResult.items)
                }
                if (reachedEnd) break
            }
        }
        allItems
    }

    suspend fun createTransaction(transaction: TransactionDto): Result<TransactionDto> = runCatching {
        val response = client.post(url("/api/transactions")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(transaction)
        }
        extractSessionCookie(response)
        runCatching { response.body<TransactionMutationResponseDto>().transaction ?: transaction }
            .getOrElse { response.body() }
    }

    suspend fun updateTransaction(id: Long, transaction: TransactionDto): Result<TransactionDto> = runCatching {
        val response = client.patch(url("/api/transactions/$id")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(transaction)
        }
        extractSessionCookie(response)
        runCatching { response.body<TransactionMutationResponseDto>().transaction ?: transaction.copy(id = id) }
            .getOrElse { response.body() }
    }

    suspend fun deleteTransaction(id: Long): Result<Boolean> = runCatching {
        val response = client.delete(url("/api/transactions/$id")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        response.status.isSuccess()
    }

    suspend fun resetTransaction(
        id: Long,
        fields: List<String> = listOf("merchant", "category_id", "is_transfer", "is_ignored", "mha_eligible")
    ): Result<TransactionDto> = runCatching {
        val response = client.post(url("/api/transactions/$id/reset")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(ResetTransactionRequestDto(fields = fields))
        }
        extractSessionCookie(response)
        runCatching { response.body<TransactionMutationResponseDto>().transaction ?: throw IllegalStateException("Missing transaction") }
            .getOrElse { response.body() }
    }

    // Categories Endpoints
    suspend fun getCategories(): Result<List<CategoryDto>> = runCatching {
        val response = client.get(url("/api/categories")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        val container: CategoryListResponseDto = response.body()
        container.items
    }

    suspend fun createCategory(category: CategoryDto): Result<CategoryDto> = runCatching {
        val response = client.post(url("/api/categories")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(category)
        }
        extractSessionCookie(response)
        runCatching { response.body<CategoryMutationResponseDto>().category ?: category }
            .getOrElse { response.body() }
    }

    suspend fun updateCategory(id: Long, category: CategoryDto): Result<CategoryDto> = runCatching {
        val response = client.put(url("/api/categories/$id")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(category)
        }
        extractSessionCookie(response)
        runCatching { response.body<CategoryMutationResponseDto>().category ?: category.copy(id = id) }
            .getOrElse { response.body() }
    }

    suspend fun deleteCategory(id: Long): Result<Boolean> = runCatching {
        val response = client.delete(url("/api/categories/$id")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        response.status.isSuccess()
    }

    // Budgets Endpoints
    suspend fun getBudgets(month: String): Result<MonthlyBudgetOverviewDto> = runCatching {
        val response = client.get(url("/api/budgets")) {
            sessionCookie?.let { header("Cookie", it) }
            if (month.isNotBlank()) parameter("month", month)
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        response.body()
    }

    suspend fun upsertBudget(categoryId: Long, amount: Double, rollover: Boolean, month: String = ""): Result<BudgetDto> = runCatching {
        val response = client.put(url("/api/budgets")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(BudgetUpsertRequestDto(categoryId = categoryId, month = month, amount = amount, rollover = rollover))
        }
        extractSessionCookie(response)
        runCatching {
            val res = response.body<IdMutationResponseDto>()
            BudgetDto(id = res.id, categoryId = categoryId, amount = amount, rollover = rollover)
        }.getOrElse { response.body() }
    }

    suspend fun deleteBudget(id: Long): Result<Boolean> = runCatching {
        val response = client.delete(url("/api/budgets/$id")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        response.status.isSuccess()
    }

    // Goals Endpoints
    suspend fun getGoals(): Result<List<GoalDto>> = runCatching {
        val response = client.get(url("/api/goals")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        runCatching { response.body<GoalsListResponseDto>().goals }
            .getOrElse { response.body() }
    }

    suspend fun createGoal(goal: GoalDto): Result<GoalDto> = runCatching {
        val response = client.post(url("/api/goals")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(goal)
        }
        extractSessionCookie(response)
        runCatching {
            val res = response.body<IdMutationResponseDto>()
            goal.copy(id = if (res.id > 0L) res.id else goal.id)
        }.getOrElse { response.body() }
    }

    suspend fun updateGoal(id: Long, goal: GoalDto): Result<GoalDto> = runCatching {
        val response = client.put(url("/api/goals/$id")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(goal)
        }
        extractSessionCookie(response)
        runCatching {
            val res = response.body<IdMutationResponseDto>()
            goal.copy(id = if (res.id > 0L) res.id else id)
        }.getOrElse { response.body() }
    }

    suspend fun deleteGoal(id: Long): Result<Boolean> = runCatching {
        val response = client.delete(url("/api/goals/$id")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        response.status.isSuccess()
    }

    // Rules Endpoints
    suspend fun getRules(): Result<List<RuleDto>> = runCatching {
        val response = client.get(url("/api/rules")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        runCatching { response.body<RuleListResponseDto>().items }
            .getOrElse { response.body() }
    }

    suspend fun createRule(rule: RuleDto): Result<RuleDto> = runCatching {
        val response = client.post(url("/api/rules")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(rule)
        }
        extractSessionCookie(response)
        runCatching {
            val res = response.body<IdMutationResponseDto>()
            rule.copy(id = if (res.id > 0L) res.id else rule.id)
        }.getOrElse { response.body() }
    }

    suspend fun updateRule(id: Long, rule: RuleDto): Result<RuleDto> = runCatching {
        val response = client.put(url("/api/rules/$id")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(rule)
        }
        extractSessionCookie(response)
        runCatching {
            val res = response.body<IdMutationResponseDto>()
            rule.copy(id = if (res.id > 0L) res.id else id)
        }.getOrElse { response.body() }
    }

    suspend fun deleteRule(id: Long): Result<Boolean> = runCatching {
        val response = client.delete(url("/api/rules/$id")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        response.status.isSuccess()
    }

    suspend fun runRules(): Result<Int> = runCatching {
        val response = client.post(url("/api/rules/reapply-all")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        val res: ReapplyRulesResponseDto = response.body()
        if (res.updated > 0) res.updated else res.appliedCount
    }

    fun close() {
        if (customHttpClient == null) runCatching { client.close() }
    }

    // Upcoming Endpoints
    suspend fun getUpcoming(): Result<List<UpcomingItemDto>> = runCatching {
        val response = client.get(url("/api/upcoming")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        val container: UpcomingListResponseDto = response.body()
        container.items
    }

    suspend fun getHousehold(): Result<HouseholdResponseDto> = runCatching {
        val response = client.get(url("/api/household")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        response.body()
    }

    suspend fun getMhaOverview(): Result<MhaOverviewResponseDto> = runCatching {
        val response = client.get(url("/api/mha")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        ensureSuccess(response)
        response.body()
    }

    suspend fun createUpcoming(item: UpcomingItemDto): Result<UpcomingItemDto> = runCatching {
        val response = client.post(url("/api/upcoming")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(item)
        }
        extractSessionCookie(response)
        runCatching {
            val res = response.body<IdMutationResponseDto>()
            item.copy(id = if (res.id > 0L) res.id else item.id)
        }.getOrElse { response.body() }
    }

    suspend fun updateUpcoming(id: Long, item: UpcomingItemDto): Result<UpcomingItemDto> = runCatching {
        val response = client.put(url("/api/upcoming/$id")) {
            contentType(ContentType.Application.Json)
            sessionCookie?.let { header("Cookie", it) }
            setBody(item)
        }
        extractSessionCookie(response)
        runCatching {
            val res = response.body<IdMutationResponseDto>()
            item.copy(id = if (res.id > 0L) res.id else id)
        }.getOrElse { response.body() }
    }

    suspend fun deleteUpcoming(id: Long): Result<Boolean> = runCatching {
        val response = client.delete(url("/api/upcoming/$id")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        response.status.isSuccess()
    }

    // -------------------------------------------------------------------------
    // SimpleFIN Sync Trigger
    // -------------------------------------------------------------------------
    suspend fun triggerSimpleFinSync(): Result<SimpleFinSyncResultDto> = runCatching {
        val response = client.post(url("/api/simplefin/sync")) {
            sessionCookie?.let { header("Cookie", it) }
        }
        extractSessionCookie(response)
        response.body()
    }

    companion object {
        const val COOKIE_STORAGE_KEY = "orbit_native_session_cookie"
    }
}
