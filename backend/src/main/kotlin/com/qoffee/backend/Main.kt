package com.qoffee.backend

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.UUID
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    val config = BackendConfig()
    val dataSource = createDataSource(config)
    migrateDatabase(dataSource)

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = false
    }
    val authService = AuthService(dataSource, config)
    val syncService = SyncService(dataSource, json)
    val snapshotService = SnapshotService(dataSource)

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        install(ContentNegotiation) { json(json) }
        install(CallLogging) { level = Level.INFO }
        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            anyHost()
        }
        install(StatusPages) {
            exception<ApiException> { call, cause ->
                call.respond(cause.status, ApiErrorResponse(cause.code.name, cause.message))
            }
            exception<BadRequestException> { call, _ ->
                call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(ApiErrorCode.BAD_REQUEST.name, "Bad request."))
            }
            exception<Throwable> { call, cause ->
                call.application.environment.log.error("Unhandled API error", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiErrorResponse(ApiErrorCode.INTERNAL_ERROR.name, "Service temporarily unavailable."),
                )
            }
        }
        install(Authentication) {
            jwt("jwt") {
                realm = config.jwtRealm
                verifier(
                    JWT.require(Algorithm.HMAC256(config.jwtSecret))
                        .withIssuer(config.jwtIssuer)
                        .withAudience(config.jwtAudience)
                        .build(),
                )
                validate { credential ->
                    if (credential.payload.subject != null && credential.payload.getClaim("device_id").asString() != null) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
            }
        }
        routing {
            route("/api/v1") {
                get("/health") {
                    call.respond(
                        HealthResponse(
                            status = "ok",
                            schemaVersion = SyncTables.SCHEMA_VERSION,
                            serverTime = System.currentTimeMillis(),
                        ),
                    )
                }
                route("/auth") {
                    post("/register") {
                        call.respond(authService.register(call.receive()))
                    }
                    post("/login") {
                        call.respond(authService.login(call.receive()))
                    }
                    post("/refresh") {
                        val request = call.receive<RefreshRequest>()
                        call.respond(authService.refresh(request.refreshToken))
                    }
                    post("/logout") {
                        val request = call.receive<LogoutRequest>()
                        authService.logout(request.refreshToken)
                        call.respond(LogoutResponse(success = true))
                    }
                }
                authenticate("jwt") {
                    route("/sync") {
                        get("/bootstrap") {
                            call.respond(syncService.bootstrap(call.requireDevice(authService)))
                        }
                        get("/changes") {
                            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
                            call.respond(syncService.changes(call.requireDevice(authService), since, limit))
                        }
                        post("/push") {
                            call.respond(syncService.push(call.requireDevice(authService), call.receive()))
                        }
                        post("/conflicts/{id}/resolve") {
                            val conflictId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?: throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.BAD_REQUEST, "Invalid conflict id.")
                            call.respond(syncService.resolveConflict(call.requireDevice(authService), conflictId, call.receive()))
                        }
                    }
                    route("/snapshots") {
                        post {
                            call.respond(snapshotService.create(call.requireDevice(authService), call.receive()))
                        }
                        get("/latest") {
                            call.respond(snapshotService.latest(call.requireDevice(authService)))
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}

private fun io.ktor.server.application.ApplicationCall.requireDevice(authService: AuthService): UserDevice {
    val principal = principal<JWTPrincipal>()
        ?: throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCode.AUTH_EXPIRED, "Authentication required.")
    return authService.validateUserDevice(
        userId = principal.payload.subject,
        deviceId = principal.payload.getClaim("device_id").asString(),
    )
}
