package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.ImportBatchRecord
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class NormalizedCsvAccountDto(
    val key: String,
    val name: String,
    val type: String = "checking",
    val institution: String? = null,
    val last4: String? = null
)

@Serializable
data class NormalizedCsvRuleDto(
    val originalName: String,
    val customName: String,
    val friendlyRuleName: String
)

@Serializable
data class NormalizedCsvTransactionDto(
    val accountKey: String = "",
    val date: String,
    val amountCents: Long,
    val merchant: String,
    val description: String = "",
    val categoryId: Long? = null,
    val notes: String? = null,
    val isIgnored: Boolean = false,
    val externalId: String? = null
)

@Serializable
data class NormalizedCsvImportRequestDto(
    val fileName: String,
    val source: String = "csv",
    val fallbackAccountId: Long? = null,
    val accounts: List<NormalizedCsvAccountDto> = emptyList(),
    val transactions: List<NormalizedCsvTransactionDto> = emptyList(),
    val rules: List<NormalizedCsvRuleDto> = emptyList()
)

suspend fun OrbitApiClient.importNormalizedCsvRemote(
    request: NormalizedCsvImportRequestDto
): Result<ImportBatchRecord> = runCatching {
    val res = client.post(url("/api/import/csv")) {
        contentType(ContentType.Application.Json)
        sessionCookie?.let { header("Cookie", it) }
        setBody(request)
    }
    extractSessionCookie(res)
    ensureSuccess(res)
    val dto = res.body<ImportRocketMoneyResponseDto>()
    dto.toDomain(request.fileName).copy(source = request.source)
}
