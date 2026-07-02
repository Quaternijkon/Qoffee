package com.qoffee.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import javax.sql.DataSource
import org.flywaydb.core.Flyway

fun createDataSource(config: BackendConfig): HikariDataSource {
    val hikari = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.dbUser
        password = config.dbPassword
        maximumPoolSize = 8
        minimumIdle = 1
        poolName = "qoffee-api"
    }
    return HikariDataSource(hikari)
}

fun migrateDatabase(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}

inline fun <T> DataSource.withConnection(block: (Connection) -> T): T =
    connection.use { connection -> block(connection) }

inline fun <T> DataSource.transaction(block: (Connection) -> T): T =
    withConnection { connection ->
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }
