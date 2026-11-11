package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.Account
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResolveAccountClosureRequestDto(
    val resolution: String
)

@Serializable
data class ResolveAccountClosureResponseDto(
    val success: Boolean = true,
    val account: AccountDto? = null,
    @SerialName("created_property") val createdProperty: AccountDto? = null
)

data class ResolvedClosureOutcome(
    val updatedAccount: Account?,
    val createdProperty: Account?
)

suspend fun OrbitApiClient.resolveAccountClosure(
    accountId: Long,
    resolution: String
): Result<ResolvedClosureOutcome> = runCatching {
    val response = client.post(url("/api/accounts/$accountId/resolve-closure")) {
        contentType(ContentType.Application.Json)
        sessionCookie?.let { header("Cookie", it) }
        setBody(ResolveAccountClosureRequestDto(resolution = resolution))
    }
    extractSessionCookie(response)
    ensureSuccess(response)
    val body = response.body<ResolveAccountClosureResponseDto>()
    ResolvedClosureOutcome(
        updatedAccount = body.account?.toDomain(),
        createdProperty = body.createdProperty?.toDomain()
    )
}
