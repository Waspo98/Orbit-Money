package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimpleFinDirectClientTest {

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun decodeClaimUrlAndAccessUrlPassthroughWork() = runTest {
        val sfClient = SimpleFinDirectClient()
        val claimUrl = "https://beta-bridge.simplefin.org/simplefin/claim/TESTTOKEN123"
        val setupToken = Base64.encode(claimUrl.encodeToByteArray())

        assertEquals(claimUrl, sfClient.decodeClaimUrl(setupToken))

        val alreadyClaimed = "https://user123:pass456@beta-bridge.simplefin.org/simplefin"
        assertTrue(sfClient.isAccessUrl(alreadyClaimed))
        assertEquals(alreadyClaimed, sfClient.claimAccessUrl(alreadyClaimed).getOrThrow())
    }

    @Test
    fun mergeBridgeDataAutoCreatesAccountsHonorsCutoverDateAndDeduplicates() {
        val sfClient = SimpleFinDirectClient()
        val bridgeData = SimpleFinDataResponse(
            accounts = listOf(
                SimpleFinAccount(
                    id = "sf_acct_1",
                    name = "Sapphire Preferred Credit Card",
                    balance = "-1250.50",
                    balanceDate = 1794960000L, // 2026-11-18
                    org = SimpleFinOrg(name = "Chase"),
                    transactions = listOf(
                        SimpleFinTransaction(
                            id = "tx_old",
                            posted = 1794268800L, // 2026-11-10
                            amount = "-25.00",
                            description = "OLD COFFEE",
                            payee = "Old Coffee"
                        ),
                        SimpleFinTransaction(
                            id = "tx_new",
                            posted = 1794960000L, // 2026-11-18
                            amount = "-62.35",
                            description = "GROCERY MART",
                            payee = "Grocery Mart"
                        )
                    )
                )
            )
        )

        val firstRun = sfClient.mergeBridgeData(
            bridgeData = bridgeData,
            snapshot = OrbitWorkspaceSnapshot.empty(),
            cutoverDateIso = "2026-11-15"
        )

        assertEquals(1, firstRun.accountsCreatedCount)
        assertEquals(1, firstRun.accountsSyncedCount)
        assertEquals(1, firstRun.newTransactionCount)
        val createdAcct = firstRun.updatedSnapshot.accounts.single()
        assertEquals("Sapphire Preferred Credit Card", createdAcct.name)
        assertEquals("Chase", createdAcct.institution)
        assertEquals(AccountType.CREDIT, createdAcct.type)
        assertEquals(-125050L, createdAcct.currentBalanceCents)
        assertEquals("tx_new", firstRun.updatedSnapshot.transactions.single().externalId)
        assertEquals(-6235L, firstRun.updatedSnapshot.transactions.single().originalAmountCents)

        val secondRun = sfClient.mergeBridgeData(
            bridgeData = bridgeData,
            snapshot = firstRun.updatedSnapshot,
            cutoverDateIso = "2026-11-15"
        )
        assertEquals(0, secondRun.accountsCreatedCount)
        assertEquals(1, secondRun.accountsSyncedCount)
        assertEquals(0, secondRun.newTransactionCount)
    }

    @Test
    fun parsesExactDecimalCentsDeduplicatesIntraPayloadIdsAndInfersCdSavings() {
        val sfClient = SimpleFinDirectClient()
        assertEquals(-125050L, sfClient.parseDecimalStringToCents("-1250.50"))
        assertEquals(12345678999L, sfClient.parseDecimalStringToCents("123456789.99"))
        assertEquals(123456L, sfClient.parseDecimalStringToCents("  \$1,234.56  "))
        assertNull(sfClient.parseDecimalStringToCents(""))
        assertEquals(AccountType.SAVINGS, sfClient.inferAccountType("12-Month CD", "Ally Bank", 5000_00L))

        val dupPayload = SimpleFinDataResponse(
            accounts = listOf(
                SimpleFinAccount(
                    id = "sf_cd",
                    name = "12-Month CD",
                    balance = "123456789.99",
                    balanceDate = 1794960000L,
                    transactions = listOf(
                        SimpleFinTransaction(id = "dup_tx", posted = 1794960000L, amount = "10.25", description = "INTEREST"),
                        SimpleFinTransaction(id = "dup_tx", posted = 1794960000L, amount = "10.25", description = "INTEREST")
                    )
                )
            )
        )
        val outcome = sfClient.mergeBridgeData(dupPayload, OrbitWorkspaceSnapshot.empty())
        assertEquals(AccountType.SAVINGS, outcome.updatedSnapshot.accounts.single().type)
        assertEquals(12345678999L, outcome.updatedSnapshot.accounts.single().currentBalanceCents)
        assertEquals(1, outcome.newTransactionCount)
    }

    @OptIn(InternalAPI::class, ExperimentalCoroutinesApi::class)
    @Test
    fun fetchBridgeDataSurfacesNon2xxHttpStatusAsFailure() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val engine = object : HttpClientEngineBase("simplefin-http-500") {
            override val dispatcher: CoroutineDispatcher = dispatcher
            override val config: HttpClientEngineConfig = HttpClientEngineConfig()
            override suspend fun execute(data: HttpRequestData): HttpResponseData =
                HttpResponseData(
                    statusCode = HttpStatusCode.InternalServerError,
                    requestTime = GMTDate(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                    version = HttpProtocolVersion.HTTP_1_1,
                    body = ByteReadChannel("Upstream SimpleFIN outage"),
                    callContext = callContext()
                )
        }
        val sfClient = SimpleFinDirectClient(HttpClient(engine))
        val res = sfClient.fetchBridgeData("https://u:p@bridge.example.com/simplefin")
        assertTrue(res.isFailure)
    }
}
