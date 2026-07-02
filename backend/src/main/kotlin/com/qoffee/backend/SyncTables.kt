package com.qoffee.backend

object SyncTables {
    const val SCHEMA_VERSION = 1

    val supportedTables: List<String> = listOf(
        "archives",
        "bean_profiles",
        "grinder_profiles",
        "recipe_templates",
        "brew_records",
        "subjective_evaluations",
        "flavor_tags",
        "record_flavor_tags",
        "archive",
        "coffee_product",
        "coffee_batch",
        "equipment_asset_type",
        "equipment_asset",
        "water_profile",
        "recipe",
        "recipe_version",
        "recipe_step_template",
        "metric_definition",
        "metric_enum_option",
        "event_definition",
        "tag_definition",
        "source_definition",
        "unit_definition",
        "collection",
        "collection_item_link",
        "brew_run",
        "brew_run_asset_link",
        "brew_stage_run",
        "observation",
        "event",
        "subject_tag_link",
        "inventory_transaction",
        "attachment",
        "import_log",
    )

    private val supportedSet = supportedTables.toSet()

    fun requireSupported(tableName: String): String {
        if (tableName !in supportedSet) {
            throw ApiException(
                io.ktor.http.HttpStatusCode.BadRequest,
                ApiErrorCode.UNSUPPORTED_SYNC_TABLE,
                "Unsupported sync table: $tableName",
            )
        }
        return tableName
    }

    fun quoted(tableName: String): String = "\"" + requireSupported(tableName).replace("\"", "\"\"") + "\""
}
