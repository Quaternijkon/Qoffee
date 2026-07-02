package com.qoffee.backend

data class BackendConfig(
    val port: Int = env("PORT")?.toIntOrNull() ?: 8080,
    val jdbcUrl: String = env("JDBC_URL") ?: "jdbc:postgresql://localhost:5432/qoffee",
    val dbUser: String = env("POSTGRES_USER") ?: "qoffee",
    val dbPassword: String = env("POSTGRES_PASSWORD") ?: "qoffee",
    val jwtIssuer: String = env("JWT_ISSUER") ?: "qoffee-api",
    val jwtAudience: String = env("JWT_AUDIENCE") ?: "qoffee-android",
    val jwtRealm: String = env("JWT_REALM") ?: "qoffee",
    val jwtSecret: String = env("JWT_SECRET") ?: "dev-only-change-me",
    val accessTokenTtlSeconds: Long = env("ACCESS_TOKEN_TTL_SECONDS")?.toLongOrNull() ?: 3600L,
    val refreshTokenTtlSeconds: Long = env("REFRESH_TOKEN_TTL_SECONDS")?.toLongOrNull() ?: 60L * 60L * 24L * 30L,
)

private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
