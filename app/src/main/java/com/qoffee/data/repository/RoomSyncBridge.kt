package com.qoffee.data.repository

import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qoffee.data.local.QoffeeDatabase
import com.qoffee.data.local.SyncEntityMapEntity
import com.qoffee.data.local.SyncMetadataDao
import com.qoffee.data.remote.SyncAcceptedItemDto
import com.qoffee.data.remote.SyncChangeDto
import com.qoffee.data.remote.SyncPushItemDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Singleton
class RoomSyncBridge @Inject constructor(
    private val database: QoffeeDatabase,
    private val syncMetadataDao: SyncMetadataDao,
    private val json: Json,
) : SyncDataBridge {
    override fun supportedTables(): List<String> = SYNC_TABLES.map { it.name }

    override suspend fun exportAll(clientChangedAt: Long): List<SyncPushItemDto> {
        val db = database.openHelper.readableDatabase
        return buildList {
            SYNC_TABLES.forEach { table ->
                db.query(SimpleSQLiteQuery("SELECT * FROM ${quote(table.name)}")).use { cursor ->
                    while (cursor.moveToNext()) {
                        val payload = cursor.toJsonObject()
                        val localKey = table.localKey(payload) ?: continue
                        val mapped = findMap(db, table.name, localKey)
                        add(
                            SyncPushItemDto(
                                tableName = table.name,
                                localKey = localKey,
                                remoteId = mapped?.remoteId,
                                baseVersion = mapped?.serverVersion,
                                operation = "UPSERT",
                                payload = payload,
                                clientChangedAt = clientChangedAt,
                            ),
                        )
                    }
                }
            }
        }
    }

    override suspend fun markAccepted(items: List<SyncAcceptedItemDto>, syncedAt: Long) {
        items.forEach { item ->
            syncMetadataDao.upsertEntityMap(
                SyncEntityMapEntity(
                    tableName = item.tableName,
                    localKey = item.localKey,
                    remoteId = item.remoteId,
                    serverVersion = item.version,
                    lastSyncedAt = syncedAt,
                ),
            )
        }
    }

    override suspend fun applyChanges(changes: List<SyncChangeDto>, syncedAt: Long) {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            changes.forEach { change ->
                val table = tableByName(change.tableName) ?: return@forEach
                val localKey = table.localKey(change.payload) ?: return@forEach
                if (change.operation.uppercase() == "DELETE") {
                    deleteByLocalKey(db, table, localKey)
                } else {
                    upsertPayload(db, table, change.payload)
                }
                upsertEntityMap(
                    db = db,
                    tableName = change.tableName,
                    localKey = localKey,
                    remoteId = change.remoteId,
                    serverVersion = change.version,
                    syncedAt = syncedAt,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun upsertEntityMap(
        db: SupportSQLiteDatabase,
        tableName: String,
        localKey: String,
        remoteId: String,
        serverVersion: Long,
        syncedAt: Long,
    ) {
        db.compileStatement(
            """
            INSERT OR REPLACE INTO `sync_entity_map` (
                `tableName`, `localKey`, `remoteId`, `serverVersion`, `lastSyncedAt`
            ) VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.bindString(1, tableName)
            statement.bindString(2, localKey)
            statement.bindString(3, remoteId)
            statement.bindLong(4, serverVersion)
            statement.bindLong(5, syncedAt)
            statement.executeInsert()
        }
    }

    private fun findMap(db: SupportSQLiteDatabase, tableName: String, localKey: String): SyncMapRow? {
        return db.query(
            SimpleSQLiteQuery(
                "SELECT remoteId, serverVersion FROM sync_entity_map WHERE tableName = ? AND localKey = ?",
                arrayOf(tableName, localKey),
            ),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                SyncMapRow(
                    remoteId = cursor.getString(0),
                    serverVersion = cursor.getLong(1),
                )
            }
        }
    }

    private fun upsertPayload(db: SupportSQLiteDatabase, table: SyncTable, payload: JsonObject) {
        val columns = payload.keys.toList()
        if (columns.isEmpty()) return
        val sql = buildString {
            append("INSERT OR REPLACE INTO ")
            append(quote(table.name))
            append(" (")
            append(columns.joinToString(",") { quote(it) })
            append(") VALUES (")
            append(columns.joinToString(",") { "?" })
            append(")")
        }
        db.compileStatement(sql).use { statement ->
            columns.forEachIndexed { index, column ->
                statement.bindJson(index + 1, payload[column])
            }
            statement.executeInsert()
        }
    }

    private fun deleteByLocalKey(db: SupportSQLiteDatabase, table: SyncTable, localKey: String) {
        val sql = table.deleteSql(localKey)
        db.compileStatement(sql.first).use { statement ->
            sql.second.forEachIndexed { index, value ->
                statement.bindString(index + 1, value)
            }
            statement.executeUpdateDelete()
        }
    }

    private fun Cursor.toJsonObject(): JsonObject {
        val values = buildMap {
            columnNames.forEachIndexed { index, name ->
                val element = when (getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> JsonNull
                    Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(index))
                    Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(index))
                    Cursor.FIELD_TYPE_STRING -> JsonPrimitive(getString(index))
                    Cursor.FIELD_TYPE_BLOB -> JsonPrimitive(getBlob(index).joinToString(separator = "") { "%02x".format(it) })
                    else -> JsonNull
                }
                put(name, element)
            }
        }
        return JsonObject(values)
    }

    private fun androidx.sqlite.db.SupportSQLiteStatement.bindJson(index: Int, element: JsonElement?) {
        when (element) {
            null, JsonNull -> bindNull(index)
            is JsonPrimitive -> {
                val boolValue = element.booleanOrNull
                val longValue = element.longOrNull
                val doubleValue = element.doubleOrNull
                when {
                    boolValue != null -> bindLong(index, if (boolValue) 1L else 0L)
                    longValue != null -> bindLong(index, longValue)
                    doubleValue != null -> bindDouble(index, doubleValue)
                    else -> bindString(index, element.contentOrNull.orEmpty())
                }
            }
            else -> bindString(index, json.encodeToString(element))
        }
    }

    private data class SyncMapRow(
        val remoteId: String,
        val serverVersion: Long,
    )
}

interface SyncDataBridge {
    fun supportedTables(): List<String>
    suspend fun exportAll(clientChangedAt: Long): List<SyncPushItemDto>
    suspend fun markAccepted(items: List<SyncAcceptedItemDto>, syncedAt: Long)
    suspend fun applyChanges(changes: List<SyncChangeDto>, syncedAt: Long)
}

private data class SyncTable(
    val name: String,
    val keyColumns: List<String>,
) {
    fun localKey(payload: JsonObject): String? {
        val values = keyColumns.map { column -> payload[column]?.primitiveString() ?: return null }
        return values.joinToString(":")
    }

    fun deleteSql(localKey: String): Pair<String, List<String>> {
        val values = localKey.split(":")
        val where = keyColumns.joinToString(" AND ") { "${quote(it)} = ?" }
        return "DELETE FROM ${quote(name)} WHERE $where" to values
    }
}

private fun tableByName(name: String): SyncTable? = SYNC_TABLES.firstOrNull { it.name == name }

private fun JsonElement.primitiveString(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.contentOrNull
}

private fun quote(identifier: String): String = "`" + identifier.replace("`", "``") + "`"

private val SYNC_TABLES = listOf(
    SyncTable("archives", listOf("id")),
    SyncTable("bean_profiles", listOf("id")),
    SyncTable("grinder_profiles", listOf("id")),
    SyncTable("recipe_templates", listOf("id")),
    SyncTable("brew_records", listOf("id")),
    SyncTable("subjective_evaluations", listOf("recordId")),
    SyncTable("flavor_tags", listOf("id")),
    SyncTable("record_flavor_tags", listOf("recordId", "flavorTagId")),
    SyncTable("archive", listOf("id")),
    SyncTable("coffee_product", listOf("id")),
    SyncTable("coffee_batch", listOf("id")),
    SyncTable("equipment_asset_type", listOf("id")),
    SyncTable("equipment_asset", listOf("id")),
    SyncTable("water_profile", listOf("id")),
    SyncTable("recipe", listOf("id")),
    SyncTable("recipe_version", listOf("id")),
    SyncTable("recipe_step_template", listOf("id")),
    SyncTable("metric_definition", listOf("id")),
    SyncTable("metric_enum_option", listOf("id")),
    SyncTable("event_definition", listOf("id")),
    SyncTable("tag_definition", listOf("id")),
    SyncTable("source_definition", listOf("id")),
    SyncTable("unit_definition", listOf("id")),
    SyncTable("collection", listOf("id")),
    SyncTable("collection_item_link", listOf("id")),
    SyncTable("brew_run", listOf("id")),
    SyncTable("brew_run_asset_link", listOf("id")),
    SyncTable("brew_stage_run", listOf("id")),
    SyncTable("observation", listOf("id")),
    SyncTable("event", listOf("id")),
    SyncTable("subject_tag_link", listOf("id")),
    SyncTable("inventory_transaction", listOf("id")),
    SyncTable("attachment", listOf("id")),
    SyncTable("import_log", listOf("id")),
)
