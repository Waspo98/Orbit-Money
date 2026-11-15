package app.overbay.orbit.data

import app.overbay.orbit.data.engine.LocalSnapshotEngine
import app.overbay.orbit.data.engine.SelfHostedApiEngine
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.util.PlatformStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SelfHostedPreferencesSyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private val wireJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeOrbitBackend(
        private val wireJson: Json
    ) {
        val preferences: MutableMap<String, JsonElement> = mutableMapOf()

        fun createClient(storage: PlatformStorage, dispatcher: CoroutineDispatcher): OrbitApiClient {
            val engine = FakeEngine(dispatcher)
            val httpClient = HttpClient(engine) {
                install(ContentNegotiation) {
                    json(wireJson)
                }
            }
            return OrbitApiClient(
                baseUrlProvider = { "https://orbit.example.com" },
                storage = storage,
                customHttpClient = httpClient
            )
        }

        @OptIn(InternalAPI::class)
        private inner class FakeEngine(
            override val dispatcher: CoroutineDispatcher
        ) : HttpClientEngineBase("fake-orbit-engine") {
            override val config: HttpClientEngineConfig = HttpClientEngineConfig()

            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                val path = data.url.encodedPath
                val bodyText = when (val content = data.body) {
                    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
                    else -> ""
                }

                val responseJson = when {
                    path == "/api/auth/me" -> """{"authenticated":true,"username":"alice","household":{"id":1,"name":"Home","role":"owner"}}"""
                    path == "/api/accounts" -> """{"items":[]}"""
                    path == "/api/accounts/balance-records" -> """{"items":[]}"""
                    path == "/api/transactions" -> """{"items":[],"total":0,"page":1,"limit":200,"totalPages":1}"""
                    path == "/api/categories" -> """{"items":[{"id":99,"name":"Uncategorized","color":"#9E9E9E","icon":"❓"}]}"""
                    path == "/api/budgets" -> """{"items":[]}"""
                    path == "/api/goals" -> """{"items":[]}"""
                    path == "/api/rules" -> """{"items":[]}"""
                    path == "/api/upcoming" -> """{"items":[],"occurrences":[],"suggestions":[]}"""
                    path == "/api/household" -> """{"members":[],"records":[],"retirementAccounts":[]}"""
                    path == "/api/mha/overview" -> """{"enabled":false}"""
                    path == "/api/household/shares" -> """{"shares":[],"currentUserRole":"owner","currentUserAccessLevel":"write"}"""
                    path == "/api/preferences" && data.method == HttpMethod.Get -> {
                        val prefsObj = JsonObject(preferences.toMap())
                        JsonObject(mapOf("preferences" to prefsObj)).toString()
                    }
                    path == "/api/preferences" && data.method == HttpMethod.Put -> {
                        val incoming = wireJson.parseToJsonElement(bodyText).jsonObject["preferences"]?.jsonObject
                        incoming?.forEach { (k, v) -> preferences[k] = v }
                        val prefsObj = JsonObject(preferences.toMap())
                        JsonObject(mapOf("preferences" to prefsObj)).toString()
                    }
                    else -> """{}"""
                }

                return HttpResponseData(
                    statusCode = HttpStatusCode.OK,
                    requestTime = GMTDate(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    version = HttpProtocolVersion.HTTP_1_1,
                    body = ByteReadChannel(responseJson.encodeToByteArray()),
                    callContext = callContext()
                )
            }
        }
    }

    @Test
    fun testHandledReviewTransactionIds_syncAcrossAppInstancesOnStartupAndPush() = runTest(testDispatcher) {
        val backend = FakeOrbitBackend(wireJson)

        // Instance A signs in / starts in SELF_HOSTED mode and dismisses/approves two review transactions
        val storageA = PlatformStorage().apply { clear() }
        LocalSnapshotEngine(storageA).saveStartupPreferences(
            OrbitPreferences(storageMode = StorageMode.SELF_HOSTED, selfHostedServerUrl = "https://orbit.example.com", onboardingCompleted = true)
        )
        val scopeA = CoroutineScope(SupervisorJob() + testDispatcher)
        val repoA = OrbitRepository(storageA, customApiClient = backend.createClient(storageA, testDispatcher), scope = scopeA)
        repoA.initialize()
        repoA.updatePreferences(repoA.preferences.value.copy(handledReviewTransactionIds = listOf(101L, 102L)))
        testScheduler.advanceUntilIdle()

        val remoteArray = backend.preferences["handledReviewTransactionIds"]?.jsonArray?.map { it.jsonPrimitive.long }
        assertEquals(listOf(101L, 102L), remoteArray)

        // Instance B starts fresh with empty local handledReviewTransactionIds and loads server prefs cleanly on initialize()
        val storageB = PlatformStorage().apply { clear() }
        LocalSnapshotEngine(storageB).saveStartupPreferences(
            OrbitPreferences(storageMode = StorageMode.SELF_HOSTED, selfHostedServerUrl = "https://orbit.example.com", onboardingCompleted = true)
        )
        val scopeB = CoroutineScope(SupervisorJob() + testDispatcher)
        val repoB = OrbitRepository(storageB, customApiClient = backend.createClient(storageB, testDispatcher), scope = scopeB)
        repoB.initialize()
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(101L, 102L),
            repoB.preferences.value.handledReviewTransactionIds,
            "Fresh app instance must retain dismissed/approved review IDs from self-hosted backend on cold start"
        )

        scopeA.cancel()
        scopeB.cancel()
        storageA.clear()
        storageB.clear()
    }

    @Test
    fun testHandledReviewTransactionIds_offlineLocalIdsMergeAndHealServer() = runTest(testDispatcher) {
        val backend = FakeOrbitBackend(wireJson)
        backend.preferences["handledReviewTransactionIds"] = JsonArray(listOf(JsonPrimitive(101L), JsonPrimitive(104L)))

        val storage = PlatformStorage().apply { clear() }
        val apiClient = backend.createClient(storage, testDispatcher)
        val engine = SelfHostedApiEngine(apiClient, storage) {
            OrbitPreferences(
                storageMode = StorageMode.SELF_HOSTED,
                selfHostedServerUrl = "https://orbit.example.com",
                handledReviewTransactionIds = listOf(101L, 103L)
            )
        }

        val loaded = engine.loadSnapshot()
        assertEquals(listOf(101L, 104L, 103L), loaded.preferences.handledReviewTransactionIds)

        val pushedArray = backend.preferences["handledReviewTransactionIds"]?.jsonArray?.map { it.jsonPrimitive.long }
        assertEquals(listOf(101L, 104L, 103L), pushedArray)
        storage.clear()
    }

    @Test
    fun testRetirementIncludedAccountIds_syncsAndClearsAcrossInstances() = runTest(testDispatcher) {
        val backend = FakeOrbitBackend(wireJson)
        val storageA = PlatformStorage().apply { clear() }
        LocalSnapshotEngine(storageA).saveStartupPreferences(
            OrbitPreferences(storageMode = StorageMode.SELF_HOSTED, selfHostedServerUrl = "https://orbit.example.com", onboardingCompleted = true)
        )
        val scopeA = CoroutineScope(SupervisorJob() + testDispatcher)
        val repoA = OrbitRepository(storageA, customApiClient = backend.createClient(storageA, testDispatcher), scope = scopeA)
        repoA.initialize()
        repoA.updatePreferences(repoA.preferences.value.copy(retirementIncludedAccountIds = listOf(10L, 12L)))
        testScheduler.advanceUntilIdle()

        val remoteIds = backend.preferences["retirementIncludedAccountIds"]?.jsonArray?.map { it.jsonPrimitive.long }
        assertEquals(listOf(10L, 12L), remoteIds)

        val storageB = PlatformStorage().apply { clear() }
        LocalSnapshotEngine(storageB).saveStartupPreferences(
            OrbitPreferences(storageMode = StorageMode.SELF_HOSTED, selfHostedServerUrl = "https://orbit.example.com", onboardingCompleted = true)
        )
        val scopeB = CoroutineScope(SupervisorJob() + testDispatcher)
        val repoB = OrbitRepository(storageB, customApiClient = backend.createClient(storageB, testDispatcher), scope = scopeB)
        repoB.initialize()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(10L, 12L), repoB.preferences.value.retirementIncludedAccountIds)

        // Clearing back to Auto Default pushes JsonNull and syncs null on reload
        repoB.updatePreferences(repoB.preferences.value.copy(retirementIncludedAccountIds = null))
        testScheduler.advanceUntilIdle()
        repoA.initialize()
        testScheduler.advanceUntilIdle()
        assertEquals(null, repoA.preferences.value.retirementIncludedAccountIds)

        scopeA.cancel()
        scopeB.cancel()
        storageA.clear()
        storageB.clear()
    }
}
