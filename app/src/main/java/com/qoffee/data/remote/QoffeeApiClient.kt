package com.qoffee.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Singleton
class QoffeeApiClient(
    private val httpClient: HttpClient,
    private val json: Json,
    private val baseUrlProvider: suspend () -> String,
) : QoffeeApi {
    override suspend fun register(email: String, password: String, deviceName: String): AuthResponseDto =
        request { baseUrl ->
            httpClient.post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(AuthRequestDto(email = email, password = password, deviceName = deviceName))
            }
        }

    override suspend fun login(email: String, password: String, deviceName: String): AuthResponseDto =
        request { baseUrl ->
            httpClient.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(AuthRequestDto(email = email, password = password, deviceName = deviceName))
            }
        }

    override suspend fun refresh(refreshToken: String): AuthResponseDto =
        request { baseUrl ->
            httpClient.post("$baseUrl/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequestDto(refreshToken))
            }
        }

    override suspend fun logout(refreshToken: String) {
        request<LogoutResponseDto> { baseUrl ->
            httpClient.post("$baseUrl/auth/logout") {
                contentType(ContentType.Application.Json)
                setBody(LogoutRequestDto(refreshToken))
            }
        }
    }

    override suspend fun bootstrap(accessToken: String): SyncBootstrapResponseDto =
        request { baseUrl ->
            httpClient.get("$baseUrl/sync/bootstrap") {
                bearerAuth(accessToken)
            }
        }

    override suspend fun push(accessToken: String, request: SyncPushRequestDto): SyncPushResponseDto =
        request { baseUrl ->
            httpClient.post("$baseUrl/sync/push") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun changes(accessToken: String, since: Long, limit: Int): SyncChangesResponseDto =
        request { baseUrl ->
            httpClient.get("$baseUrl/sync/changes") {
                bearerAuth(accessToken)
                parameter("since", since)
                parameter("limit", limit)
            }
        }

    override suspend fun resolveConflict(
        accessToken: String,
        conflictId: String,
        request: ResolveConflictRequestDto,
    ): ResolveConflictResponseDto =
        request { baseUrl ->
            httpClient.post("$baseUrl/sync/conflicts/$conflictId/resolve") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun createSnapshot(accessToken: String, request: SnapshotRequestDto): SnapshotResponseDto =
        request { baseUrl ->
            httpClient.post("$baseUrl/snapshots") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun latestSnapshot(accessToken: String): SnapshotDownloadResponseDto =
        request { baseUrl ->
            httpClient.get("$baseUrl/snapshots/latest") {
                bearerAuth(accessToken)
            }
        }

    private suspend inline fun <reified T> request(crossinline block: suspend (String) -> HttpResponse): T {
        return try {
            block(baseUrlProvider().trimEnd('/')).body()
        } catch (error: ResponseException) {
            throw error.toQoffeeApiException()
        } catch (error: SerializationException) {
            throw QoffeeApiException(
                statusCode = null,
                code = "INVALID_RESPONSE",
                userMessage = "服务器响应格式暂时无法识别，请稍后重试。",
                cause = error,
            )
        }
    }

    private suspend fun ResponseException.toQoffeeApiException(): QoffeeApiException {
        val rawBody = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching {
            json.decodeFromString(ApiErrorResponseDto.serializer(), rawBody)
        }.getOrNull()
        return QoffeeApiException(
            statusCode = response.status.value,
            code = parsed?.code?.takeIf { it.isNotBlank() } ?: "HTTP_${response.status.value}",
            userMessage = parsed?.message?.takeIf { it.isNotBlank() } ?: "服务器暂时无法处理请求。",
            cause = this,
        )
    }
}

class QoffeeApiException(
    val statusCode: Int?,
    val code: String,
    val userMessage: String,
    cause: Throwable? = null,
) : RuntimeException(userMessage, cause) {
    val isAuthExpired: Boolean
        get() = statusCode == 401 || code == "AUTH_EXPIRED" || code == "INVALID_AUTH_STATE" || code == "INVALID_DEVICE"
}

interface QoffeeApi {
    suspend fun register(email: String, password: String, deviceName: String): AuthResponseDto
    suspend fun login(email: String, password: String, deviceName: String): AuthResponseDto
    suspend fun refresh(refreshToken: String): AuthResponseDto
    suspend fun logout(refreshToken: String)
    suspend fun bootstrap(accessToken: String): SyncBootstrapResponseDto
    suspend fun push(accessToken: String, request: SyncPushRequestDto): SyncPushResponseDto
    suspend fun changes(accessToken: String, since: Long, limit: Int): SyncChangesResponseDto
    suspend fun resolveConflict(accessToken: String, conflictId: String, request: ResolveConflictRequestDto): ResolveConflictResponseDto
    suspend fun createSnapshot(accessToken: String, request: SnapshotRequestDto): SnapshotResponseDto
    suspend fun latestSnapshot(accessToken: String): SnapshotDownloadResponseDto
}
