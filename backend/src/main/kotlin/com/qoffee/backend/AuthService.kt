package com.qoffee.backend

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.sql.DataSource

class AuthService(
    private val dataSource: DataSource,
    private val config: BackendConfig,
) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    fun register(request: AuthRequest): AuthResponse {
        val email = normalizeEmail(request.email)
        validatePassword(request.password)
        return dataSource.transaction { connection ->
            if (findUserByEmail(connection, email) != null) {
                throw ApiException(HttpStatusCode.Conflict, ApiErrorCode.EMAIL_ALREADY_REGISTERED, "Email already registered.")
            }
            val userId = UUID.randomUUID()
            val now = nowMillis()
            connection.prepareStatement(
                """
                INSERT INTO users (id, email, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, userId)
                statement.setString(2, email)
                statement.setString(3, BCrypt.withDefaults().hashToString(12, request.password.toCharArray()))
                statement.executeUpdate()
            }
            issueSession(connection, userId, email, request.deviceName, now)
        }
    }

    fun login(request: AuthRequest): AuthResponse {
        val email = normalizeEmail(request.email)
        return dataSource.transaction { connection ->
            val user = findUserByEmail(connection, email)
                ?: throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.INVALID_CREDENTIALS, "Invalid email or password.")
            val verified = BCrypt.verifyer().verify(request.password.toCharArray(), user.passwordHash).verified
            if (!verified) {
                throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.INVALID_CREDENTIALS, "Invalid email or password.")
            }
            issueSession(connection, user.id, user.email, request.deviceName, nowMillis())
        }
    }

    fun refresh(refreshToken: String): AuthResponse {
        val tokenHash = sha256(refreshToken)
        return dataSource.transaction { connection ->
            val session = connection.prepareStatement(
                """
                SELECT u.id, u.email, rt.device_id
                FROM refresh_tokens rt
                INNER JOIN users u ON u.id = rt.user_id
                WHERE rt.token_hash = ?
                  AND rt.revoked_at IS NULL
                  AND rt.expires_at > now()
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.executeQuery().use { result ->
                    if (!result.next()) {
                        null
                    } else {
                        RefreshSession(
                            userId = result.getObject("id", UUID::class.java),
                            email = result.getString("email"),
                            deviceId = result.getObject("device_id", UUID::class.java),
                        )
                    }
                }
            } ?: throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.AUTH_EXPIRED, "Login expired.")

            connection.prepareStatement("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = ?").use { statement ->
                statement.setString(1, tokenHash)
                statement.executeUpdate()
            }
            issueSession(connection, session.userId, session.email, null, nowMillis(), session.deviceId)
        }
    }

    fun logout(refreshToken: String?) {
        if (refreshToken.isNullOrBlank()) return
        val tokenHash = sha256(refreshToken)
        dataSource.withConnection { connection ->
            connection.prepareStatement("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = ?").use { statement ->
                statement.setString(1, tokenHash)
                statement.executeUpdate()
            }
        }
    }

    fun validateUserDevice(userId: String?, deviceId: String?): UserDevice {
        val userUuid = runCatching { UUID.fromString(userId) }.getOrNull()
            ?: throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.INVALID_AUTH_STATE, "Invalid authentication state.")
        val deviceUuid = runCatching { UUID.fromString(deviceId) }.getOrNull()
            ?: throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.INVALID_DEVICE, "Invalid device state.")
        return UserDevice(userUuid, deviceUuid)
    }

    private fun issueSession(
        connection: Connection,
        userId: UUID,
        email: String,
        deviceName: String?,
        nowMillis: Long,
        existingDeviceId: UUID? = null,
    ): AuthResponse {
        val deviceId = existingDeviceId ?: UUID.randomUUID()
        if (existingDeviceId == null) {
            connection.prepareStatement(
                """
                INSERT INTO devices (id, user_id, name, created_at, last_seen_at)
                VALUES (?, ?, ?, now(), now())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, deviceId)
                statement.setObject(2, userId)
                statement.setString(3, deviceName?.takeIf { it.isNotBlank() } ?: "Android")
                statement.executeUpdate()
            }
        } else {
            connection.prepareStatement("UPDATE devices SET last_seen_at = now() WHERE id = ? AND user_id = ?").use { statement ->
                statement.setObject(1, deviceId)
                statement.setObject(2, userId)
                statement.executeUpdate()
            }
        }

        val accessExpiresAt = nowMillis + config.accessTokenTtlSeconds * 1000L
        val accessToken = JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withClaim("device_id", deviceId.toString())
            .withExpiresAt(Date(accessExpiresAt))
            .sign(algorithm)

        val refreshToken = UUID.randomUUID().toString() + "." + UUID.randomUUID()
        connection.prepareStatement(
            """
            INSERT INTO refresh_tokens (id, user_id, device_id, token_hash, created_at, expires_at)
            VALUES (?, ?, ?, ?, now(), ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, userId)
            statement.setObject(3, deviceId)
            statement.setString(4, sha256(refreshToken))
            statement.setTimestamp(5, Timestamp.from(Instant.ofEpochMilli(nowMillis + config.refreshTokenTtlSeconds * 1000L)))
            statement.executeUpdate()
        }

        return AuthResponse(
            account = AccountDto(userId.toString(), email, nowMillis),
            deviceId = deviceId.toString(),
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = accessExpiresAt,
        )
    }

    private fun findUserByEmail(connection: Connection, email: String): UserRow? {
        return connection.prepareStatement("SELECT id, email, password_hash FROM users WHERE email = ?").use { statement ->
            statement.setString(1, email)
            statement.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    UserRow(
                        id = result.getObject("id", UUID::class.java),
                        email = result.getString("email"),
                        passwordHash = result.getString("password_hash"),
                    )
                }
            }
        }
    }

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()
        if (!normalized.contains("@") || !normalized.substringAfter("@").contains(".")) {
            throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_EMAIL, "Invalid email.")
        }
        return normalized
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) {
            throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.WEAK_PASSWORD, "Password must be at least 8 characters.")
        }
    }

    private fun nowMillis(): Long = System.currentTimeMillis()

    private data class UserRow(
        val id: UUID,
        val email: String,
        val passwordHash: String,
    )

    private data class RefreshSession(
        val userId: UUID,
        val email: String,
        val deviceId: UUID,
    )
}

data class UserDevice(
    val userId: UUID,
    val deviceId: UUID,
)

fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}
