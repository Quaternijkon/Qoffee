package com.qoffee.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object QoffeeDatabaseMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recipe_templates` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `brewMethodCode` TEXT,
                    `beanId` INTEGER,
                    `beanNameSnapshot` TEXT,
                    `grinderId` INTEGER,
                    `grinderNameSnapshot` TEXT,
                    `grindSetting` REAL,
                    `coffeeDoseG` REAL,
                    `brewWaterMl` REAL,
                    `bypassWaterMl` REAL,
                    `waterTempC` REAL,
                    `notes` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archives`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`beanId`) REFERENCES `bean_profiles`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`grinderId`) REFERENCES `grinder_profiles`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_templates_archiveId` ON `recipe_templates` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_templates_beanId` ON `recipe_templates` (`beanId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_templates_grinderId` ON `recipe_templates` (`grinderId`)")

            db.execSQL("ALTER TABLE `brew_records` ADD COLUMN `recipeTemplateId` INTEGER")
            db.execSQL("ALTER TABLE `brew_records` ADD COLUMN `recipeNameSnapshot` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_records_recipeTemplateId` ON `brew_records` (`recipeTemplateId`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `bean_profiles` ADD COLUMN `initialStockG` REAL")
            db.execSQL(
                """
                UPDATE `subjective_evaluations`
                SET
                    `aroma` = CASE
                        WHEN `aroma` IS NULL THEN NULL
                        WHEN `aroma` > 5 THEN ((`aroma` + 1) / 2)
                        ELSE `aroma`
                    END,
                    `acidity` = CASE
                        WHEN `acidity` IS NULL THEN NULL
                        WHEN `acidity` > 5 THEN ((`acidity` + 1) / 2)
                        ELSE `acidity`
                    END,
                    `sweetness` = CASE
                        WHEN `sweetness` IS NULL THEN NULL
                        WHEN `sweetness` > 5 THEN ((`sweetness` + 1) / 2)
                        ELSE `sweetness`
                    END,
                    `bitterness` = CASE
                        WHEN `bitterness` IS NULL THEN NULL
                        WHEN `bitterness` > 5 THEN ((`bitterness` + 1) / 2)
                        ELSE `bitterness`
                    END,
                    `body` = CASE
                        WHEN `body` IS NULL THEN NULL
                        WHEN `body` > 5 THEN ((`body` + 1) / 2)
                        ELSE `body`
                    END,
                    `aftertaste` = CASE
                        WHEN `aftertaste` IS NULL THEN NULL
                        WHEN `aftertaste` > 5 THEN ((`aftertaste` + 1) / 2)
                        ELSE `aftertaste`
                    END,
                    `overall` = CASE
                        WHEN `overall` IS NULL THEN NULL
                        ELSE ((`overall` + 1) / 2)
                    END
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `brew_records` ADD COLUMN `brewDurationSeconds` INTEGER")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `archive` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `typeCode` TEXT NOT NULL,
                    `isReadOnly` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `coffee_product` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `roaster` TEXT NOT NULL,
                    `country` TEXT NOT NULL,
                    `region` TEXT NOT NULL,
                    `siteName` TEXT NOT NULL,
                    `processMethodCode` TEXT NOT NULL,
                    `variety` TEXT NOT NULL,
                    `altitudeMinM` REAL,
                    `altitudeMaxM` REAL,
                    `harvestSeason` TEXT NOT NULL,
                    `roastLevelTargetCode` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_coffee_product_archiveId` ON `coffee_product` (`archiveId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `coffee_batch` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `batchCode` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `roastDateEpochDay` INTEGER,
                    `purchasedAt` INTEGER,
                    `openedAt` INTEGER,
                    `expiresAt` INTEGER,
                    `initialStockG` REAL,
                    `packageSizeG` REAL,
                    `costAmount` REAL,
                    `currencyCode` TEXT NOT NULL,
                    `supplierName` TEXT NOT NULL,
                    `greenBeanMoisturePct` REAL,
                    `densityGPerMl` REAL,
                    `awValue` REAL,
                    `sieveSizeText` TEXT NOT NULL,
                    `fermentationBatchText` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`productId`) REFERENCES `coffee_product`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_coffee_batch_archiveId` ON `coffee_batch` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_coffee_batch_productId` ON `coffee_batch` (`productId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_coffee_batch_isActive` ON `coffee_batch` (`isActive`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `equipment_asset_type` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `code` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `description` TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_equipment_asset_type_code` ON `equipment_asset_type` (`code`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `equipment_asset` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `typeId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `brand` TEXT NOT NULL,
                    `model` TEXT NOT NULL,
                    `serialNumber` TEXT NOT NULL,
                    `minValue` REAL,
                    `maxValue` REAL,
                    `stepSize` REAL,
                    `defaultUnitCode` TEXT NOT NULL,
                    `burrType` TEXT NOT NULL,
                    `filterType` TEXT NOT NULL,
                    `capacityValue` REAL,
                    `capacityUnitCode` TEXT NOT NULL,
                    `specJson` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`typeId`) REFERENCES `equipment_asset_type`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipment_asset_archiveId` ON `equipment_asset` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipment_asset_typeId` ON `equipment_asset` (`typeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_equipment_asset_isActive` ON `equipment_asset` (`isActive`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `water_profile` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `tdsPpm` REAL,
                    `ghPpm` REAL,
                    `khPpm` REAL,
                    `ph` REAL,
                    `calciumPpm` REAL,
                    `magnesiumPpm` REAL,
                    `sodiumPpm` REAL,
                    `bicarbonatePpm` REAL,
                    `recipeText` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_profile_archiveId` ON `water_profile` (`archiveId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recipe` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `brewMethodCode` TEXT NOT NULL,
                    `currentVersionId` INTEGER,
                    `isActive` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_archiveId` ON `recipe` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_isActive` ON `recipe` (`isActive`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recipe_version` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `recipeId` INTEGER NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `versionNumber` INTEGER NOT NULL,
                    `brewMethodCode` TEXT NOT NULL,
                    `waterProfileId` INTEGER,
                    `notes` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`recipeId`) REFERENCES `recipe`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`waterProfileId`) REFERENCES `water_profile`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_version_recipeId` ON `recipe_version` (`recipeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_version_archiveId` ON `recipe_version` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_version_waterProfileId` ON `recipe_version` (`waterProfileId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recipe_version_recipeId_versionNumber` ON `recipe_version` (`recipeId`, `versionNumber`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recipe_step_template` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `recipeVersionId` INTEGER NOT NULL,
                    `stageCode` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `targetSummaryJson` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    FOREIGN KEY(`recipeVersionId`) REFERENCES `recipe_version`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_step_template_recipeVersionId` ON `recipe_step_template` (`recipeVersionId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `metric_definition` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `code` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `scopeType` TEXT NOT NULL,
                    `valueType` TEXT NOT NULL,
                    `defaultUnitCode` TEXT NOT NULL,
                    `isAggregatable` INTEGER NOT NULL,
                    `isSystem` INTEGER NOT NULL,
                    `isRequired` INTEGER NOT NULL,
                    `isFilterable` INTEGER NOT NULL,
                    `isChartable` INTEGER NOT NULL,
                    `normalizationStrategy` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_metric_definition_code` ON `metric_definition` (`code`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `metric_enum_option` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `metricDefinitionId` INTEGER NOT NULL,
                    `code` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `isDefault` INTEGER NOT NULL,
                    FOREIGN KEY(`metricDefinitionId`) REFERENCES `metric_definition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_metric_enum_option_metricDefinitionId` ON `metric_enum_option` (`metricDefinitionId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_metric_enum_option_metricDefinitionId_code` ON `metric_enum_option` (`metricDefinitionId`, `code`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `event_definition` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `code` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `defaultSeverity` TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_event_definition_code` ON `event_definition` (`code`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tag_definition` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER,
                    `categoryCode` TEXT NOT NULL,
                    `code` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL,
                    `isSystem` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tag_definition_archiveId` ON `tag_definition` (`archiveId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_definition_archiveId_categoryCode_displayName` ON `tag_definition` (`archiveId`, `categoryCode`, `displayName`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `source_definition` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `code` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `isMeasured` INTEGER NOT NULL,
                    `isEstimated` INTEGER NOT NULL,
                    `priority` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_source_definition_code` ON `source_definition` (`code`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `unit_definition` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `code` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `quantityType` TEXT NOT NULL,
                    `symbol` TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_unit_definition_code` ON `unit_definition` (`code`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collection` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `typeCode` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `hypothesis` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_archiveId` ON `collection` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_typeCode` ON `collection` (`typeCode`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collection_item_link` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `collectionId` INTEGER NOT NULL,
                    `itemType` TEXT NOT NULL,
                    `itemId` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `groupLabel` TEXT NOT NULL,
                    `roleText` TEXT NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`collectionId`) REFERENCES `collection`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_item_link_collectionId` ON `collection_item_link` (`collectionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_item_link_archiveId` ON `collection_item_link` (`archiveId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_collection_item_link_collectionId_itemType_itemId` ON `collection_item_link` (`collectionId`, `itemType`, `itemId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `brew_run` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `runKind` TEXT NOT NULL,
                    `brewMethodCode` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `startedAt` INTEGER,
                    `endedAt` INTEGER,
                    `brewedAt` INTEGER NOT NULL,
                    `timezoneOffsetMinutes` INTEGER,
                    `recipeVersionId` INTEGER,
                    `coffeeBatchId` INTEGER,
                    `waterProfileId` INTEGER,
                    `locationText` TEXT NOT NULL,
                    `operatorText` TEXT NOT NULL,
                    `sourceId` INTEGER,
                    `confidenceScore` REAL,
                    `notes` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`recipeVersionId`) REFERENCES `recipe_version`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`coffeeBatchId`) REFERENCES `coffee_batch`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`waterProfileId`) REFERENCES `water_profile`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`sourceId`) REFERENCES `source_definition`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_archiveId` ON `brew_run` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_recipeVersionId` ON `brew_run` (`recipeVersionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_coffeeBatchId` ON `brew_run` (`coffeeBatchId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_waterProfileId` ON `brew_run` (`waterProfileId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_sourceId` ON `brew_run` (`sourceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_brewedAt` ON `brew_run` (`brewedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_archiveId_brewMethodCode_brewedAt` ON `brew_run` (`archiveId`, `brewMethodCode`, `brewedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_archiveId_status` ON `brew_run` (`archiveId`, `status`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `brew_run_asset_link` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `brewRunId` INTEGER NOT NULL,
                    `assetId` INTEGER NOT NULL,
                    `roleCode` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`brewRunId`) REFERENCES `brew_run`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`assetId`) REFERENCES `equipment_asset`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_asset_link_archiveId` ON `brew_run_asset_link` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_asset_link_brewRunId` ON `brew_run_asset_link` (`brewRunId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_run_asset_link_assetId` ON `brew_run_asset_link` (`assetId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_brew_run_asset_link_brewRunId_roleCode` ON `brew_run_asset_link` (`brewRunId`, `roleCode`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `brew_stage_run` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `brewRunId` INTEGER NOT NULL,
                    `stageCode` TEXT NOT NULL,
                    `stageLabel` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `startedAt` INTEGER,
                    `endedAt` INTEGER,
                    `targetSummaryJson` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`brewRunId`) REFERENCES `brew_run`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_stage_run_archiveId` ON `brew_stage_run` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_brew_stage_run_brewRunId` ON `brew_stage_run` (`brewRunId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_brew_stage_run_brewRunId_sortOrder` ON `brew_stage_run` (`brewRunId`, `sortOrder`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `observation` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `subjectType` TEXT NOT NULL,
                    `subjectId` INTEGER NOT NULL,
                    `metricDefinitionId` INTEGER NOT NULL,
                    `capturedAt` INTEGER NOT NULL,
                    `sourceId` INTEGER,
                    `confidenceScore` REAL,
                    `valueType` TEXT NOT NULL,
                    `numericValue` REAL,
                    `normalizedNumericValue` REAL,
                    `unitCode` TEXT,
                    `boolValue` INTEGER,
                    `textValue` TEXT,
                    `enumOptionId` INTEGER,
                    `timestampValue` INTEGER,
                    `jsonValue` TEXT,
                    `sequenceNo` INTEGER,
                    `isTargetValue` INTEGER NOT NULL,
                    `isActualValue` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`metricDefinitionId`) REFERENCES `metric_definition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`sourceId`) REFERENCES `source_definition`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`enumOptionId`) REFERENCES `metric_enum_option`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_observation_archiveId` ON `observation` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_observation_metricDefinitionId` ON `observation` (`metricDefinitionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_observation_sourceId` ON `observation` (`sourceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_observation_enumOptionId` ON `observation` (`enumOptionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_observation_subjectType_subjectId_metricDefinitionId_capturedAt` ON `observation` (`subjectType`, `subjectId`, `metricDefinitionId`, `capturedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_observation_archiveId_metricDefinitionId_capturedAt` ON `observation` (`archiveId`, `metricDefinitionId`, `capturedAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `event` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `subjectType` TEXT NOT NULL,
                    `subjectId` INTEGER NOT NULL,
                    `eventDefinitionId` INTEGER NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `endedAt` INTEGER,
                    `sourceId` INTEGER,
                    `confidenceScore` REAL,
                    `payloadJson` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`eventDefinitionId`) REFERENCES `event_definition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`sourceId`) REFERENCES `source_definition`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_archiveId` ON `event` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_eventDefinitionId` ON `event` (`eventDefinitionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_sourceId` ON `event` (`sourceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_subjectType_subjectId_eventDefinitionId_startedAt` ON `event` (`subjectType`, `subjectId`, `eventDefinitionId`, `startedAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `subject_tag_link` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `subjectType` TEXT NOT NULL,
                    `subjectId` INTEGER NOT NULL,
                    `tagDefinitionId` INTEGER NOT NULL,
                    `taggedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`tagDefinitionId`) REFERENCES `tag_definition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subject_tag_link_archiveId` ON `subject_tag_link` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subject_tag_link_tagDefinitionId` ON `subject_tag_link` (`tagDefinitionId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subject_tag_link_subjectType_subjectId_tagDefinitionId` ON `subject_tag_link` (`subjectType`, `subjectId`, `tagDefinitionId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `inventory_transaction` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `coffeeBatchId` INTEGER NOT NULL,
                    `transactionTypeCode` TEXT NOT NULL,
                    `deltaGrams` REAL,
                    `costDelta` REAL,
                    `currencyCode` TEXT NOT NULL,
                    `occurredAt` INTEGER NOT NULL,
                    `sourceId` INTEGER,
                    `relatedRunId` INTEGER,
                    `notes` TEXT NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`coffeeBatchId`) REFERENCES `coffee_batch`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`relatedRunId`) REFERENCES `brew_run`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`sourceId`) REFERENCES `source_definition`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_transaction_archiveId` ON `inventory_transaction` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_transaction_coffeeBatchId` ON `inventory_transaction` (`coffeeBatchId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_transaction_relatedRunId` ON `inventory_transaction` (`relatedRunId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_transaction_sourceId` ON `inventory_transaction` (`sourceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_transaction_coffeeBatchId_occurredAt` ON `inventory_transaction` (`coffeeBatchId`, `occurredAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `attachment` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `subjectType` TEXT NOT NULL,
                    `subjectId` INTEGER NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `uriText` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `sizeBytes` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `sourceId` INTEGER,
                    `notes` TEXT NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`sourceId`) REFERENCES `source_definition`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachment_archiveId` ON `attachment` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachment_sourceId` ON `attachment` (`sourceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachment_subjectType_subjectId` ON `attachment` (`subjectType`, `subjectId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `import_log` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `archiveId` INTEGER NOT NULL,
                    `sourceName` TEXT NOT NULL,
                    `importTypeCode` TEXT NOT NULL,
                    `externalId` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `importedAt` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL,
                    FOREIGN KEY(`archiveId`) REFERENCES `archive`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_log_archiveId` ON `import_log` (`archiveId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_log_archiveId_externalId` ON `import_log` (`archiveId`, `externalId`)")

            db.execSQL("INSERT OR IGNORE INTO `source_definition` (`code`, `displayName`, `description`, `isMeasured`, `isEstimated`, `priority`) VALUES ('manual', 'Manual', '', 1, 0, 10)")
            db.execSQL("INSERT OR IGNORE INTO `source_definition` (`code`, `displayName`, `description`, `isMeasured`, `isEstimated`, `priority`) VALUES ('imported', 'Imported', '', 1, 0, 20)")
            db.execSQL("INSERT OR IGNORE INTO `source_definition` (`code`, `displayName`, `description`, `isMeasured`, `isEstimated`, `priority`) VALUES ('device', 'Device', '', 1, 0, 30)")
            db.execSQL("INSERT OR IGNORE INTO `source_definition` (`code`, `displayName`, `description`, `isMeasured`, `isEstimated`, `priority`) VALUES ('derived', 'Derived', '', 0, 0, 40)")
            db.execSQL("INSERT OR IGNORE INTO `source_definition` (`code`, `displayName`, `description`, `isMeasured`, `isEstimated`, `priority`) VALUES ('estimated', 'Estimated', '', 0, 1, 50)")
            db.execSQL("INSERT OR IGNORE INTO `source_definition` (`code`, `displayName`, `description`, `isMeasured`, `isEstimated`, `priority`) VALUES ('migrated', 'Migrated', '', 1, 0, 60)")

            db.execSQL("INSERT OR IGNORE INTO `unit_definition` (`code`, `displayName`, `quantityType`, `symbol`) VALUES ('g', 'Gram', 'mass', 'g')")
            db.execSQL("INSERT OR IGNORE INTO `unit_definition` (`code`, `displayName`, `quantityType`, `symbol`) VALUES ('ml', 'Milliliter', 'volume', 'ml')")
            db.execSQL("INSERT OR IGNORE INTO `unit_definition` (`code`, `displayName`, `quantityType`, `symbol`) VALUES ('s', 'Second', 'time', 's')")
            db.execSQL("INSERT OR IGNORE INTO `unit_definition` (`code`, `displayName`, `quantityType`, `symbol`) VALUES ('celsius', 'Celsius', 'temperature', '°C')")
            db.execSQL("INSERT OR IGNORE INTO `unit_definition` (`code`, `displayName`, `quantityType`, `symbol`) VALUES ('ratio', 'Ratio', 'ratio', '')")
            db.execSQL("INSERT OR IGNORE INTO `unit_definition` (`code`, `displayName`, `quantityType`, `symbol`) VALUES ('score_5', 'Score / 5', 'score', '/5')")
            db.execSQL("INSERT OR IGNORE INTO `unit_definition` (`code`, `displayName`, `quantityType`, `symbol`) VALUES ('text', 'Text', 'text', '')")

            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('grinder', 'Grinder', '')")
            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('brewer', 'Brewer', '')")
            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('dripper', 'Dripper', '')")
            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('kettle', 'Kettle', '')")
            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('scale', 'Scale', '')")
            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('espresso_machine', 'Espresso Machine', '')")
            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('filter', 'Filter', '')")
            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('basket', 'Basket', '')")
            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('server', 'Server', '')")
            db.execSQL("INSERT OR IGNORE INTO `equipment_asset_type` (`code`, `displayName`, `description`) VALUES ('cup', 'Cup', '')")

            val now = System.currentTimeMillis()
            fun insertMetric(code: String, displayName: String, valueType: String, unitCode: String, chartable: Int = 0, filterable: Int = 0) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `metric_definition` (
                        `code`, `displayName`, `description`, `scopeType`, `valueType`, `defaultUnitCode`,
                        `isAggregatable`, `isSystem`, `isRequired`, `isFilterable`, `isChartable`, `normalizationStrategy`, `createdAt`
                    ) VALUES (
                        '$code', '$displayName', '', 'run', '$valueType', '$unitCode',
                        1, 1, 0, $filterable, $chartable, '', $now
                    )
                    """.trimIndent(),
                )
            }
            insertMetric("grind_setting", "Grind Setting", "numeric", "ratio", 1, 1)
            insertMetric("coffee_dose_g", "Coffee Dose", "numeric", "g", 1, 1)
            insertMetric("brew_water_ml", "Brew Water", "numeric", "ml", 1, 1)
            insertMetric("bypass_water_ml", "Bypass Water", "numeric", "ml", 1, 1)
            insertMetric("total_water_ml", "Total Water", "numeric", "ml", 1, 0)
            insertMetric("brew_ratio", "Brew Ratio", "numeric", "ratio", 1, 1)
            insertMetric("water_temp_c", "Water Temperature", "numeric", "celsius", 1, 1)
            insertMetric("brew_duration_seconds", "Brew Time", "numeric", "s", 1, 1)
            insertMetric("aroma_score", "Aroma", "numeric", "score_5", 0, 0)
            insertMetric("acidity_score", "Acidity", "numeric", "score_5", 0, 0)
            insertMetric("sweetness_score", "Sweetness", "numeric", "score_5", 0, 0)
            insertMetric("bitterness_score", "Bitterness", "numeric", "score_5", 0, 0)
            insertMetric("body_score", "Body", "numeric", "score_5", 0, 0)
            insertMetric("aftertaste_score", "Aftertaste", "numeric", "score_5", 0, 0)
            insertMetric("overall_score", "Overall", "numeric", "score_5", 1, 0)
            db.execSQL(
                """
                INSERT OR IGNORE INTO `metric_definition` (
                    `code`, `displayName`, `description`, `scopeType`, `valueType`, `defaultUnitCode`,
                    `isAggregatable`, `isSystem`, `isRequired`, `isFilterable`, `isChartable`, `normalizationStrategy`, `createdAt`
                ) VALUES (
                    'subjective_note', 'Subjective Note', '', 'run', 'text', 'text',
                    0, 1, 0, 0, 0, '', $now
                )
                """.trimIndent(),
            )

            db.execSQL("INSERT OR IGNORE INTO `archive` (`id`, `name`, `typeCode`, `isReadOnly`, `createdAt`, `updatedAt`, `sortOrder`) SELECT `id`, `name`, `typeCode`, `isReadOnly`, `createdAt`, `updatedAt`, `sortOrder` FROM `archives`")
            db.execSQL(
                """
                INSERT OR IGNORE INTO `coffee_product` (
                    `id`, `archiveId`, `name`, `roaster`, `country`, `region`, `siteName`, `processMethodCode`, `variety`,
                    `altitudeMinM`, `altitudeMaxM`, `harvestSeason`, `roastLevelTargetCode`, `description`, `notes`, `createdAt`, `updatedAt`
                )
                SELECT
                    `id`,
                    `archiveId`,
                    `name`,
                    `roaster`,
                    '',
                    `origin`,
                    '',
                    CASE `processValue`
                        WHEN 0 THEN 'natural'
                        WHEN 1 THEN 'washed'
                        WHEN 2 THEN 'honey'
                        ELSE ''
                    END,
                    `variety`,
                    NULL,
                    NULL,
                    '',
                    CAST(`roastLevelValue` AS TEXT),
                    '',
                    `notes`,
                    `createdAt`,
                    `createdAt`
                FROM `bean_profiles`
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `coffee_batch` (
                    `id`, `archiveId`, `productId`, `batchCode`, `displayName`, `roastDateEpochDay`, `purchasedAt`, `openedAt`, `expiresAt`,
                    `initialStockG`, `packageSizeG`, `costAmount`, `currencyCode`, `supplierName`, `greenBeanMoisturePct`,
                    `densityGPerMl`, `awValue`, `sieveSizeText`, `fermentationBatchText`, `notes`, `isActive`, `createdAt`, `updatedAt`
                )
                SELECT
                    `id`,
                    `archiveId`,
                    `id`,
                    '',
                    `name`,
                    `roastDateEpochDay`,
                    NULL,
                    NULL,
                    NULL,
                    `initialStockG`,
                    `initialStockG`,
                    NULL,
                    'CNY',
                    '',
                    NULL,
                    NULL,
                    NULL,
                    '',
                    '',
                    `notes`,
                    1,
                    `createdAt`,
                    `createdAt`
                FROM `bean_profiles`
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `equipment_asset` (
                    `id`, `archiveId`, `typeId`, `name`, `brand`, `model`, `serialNumber`, `minValue`, `maxValue`, `stepSize`,
                    `defaultUnitCode`, `burrType`, `filterType`, `capacityValue`, `capacityUnitCode`, `specJson`, `notes`, `isActive`, `createdAt`, `updatedAt`
                )
                SELECT
                    g.`id`,
                    g.`archiveId`,
                    (SELECT `id` FROM `equipment_asset_type` WHERE `code` = 'grinder' LIMIT 1),
                    g.`name`,
                    '',
                    '',
                    '',
                    g.`minSetting`,
                    g.`maxSetting`,
                    g.`stepSize`,
                    g.`unitLabel`,
                    '',
                    '',
                    NULL,
                    '',
                    '',
                    g.`notes`,
                    1,
                    g.`createdAt`,
                    g.`createdAt`
                FROM `grinder_profiles` g
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `recipe` (
                    `id`, `archiveId`, `name`, `brewMethodCode`, `currentVersionId`, `isActive`, `createdAt`, `updatedAt`
                )
                SELECT
                    `id`,
                    `archiveId`,
                    `name`,
                    COALESCE(`brewMethodCode`, ''),
                    `id`,
                    1,
                    `createdAt`,
                    `updatedAt`
                FROM `recipe_templates`
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `recipe_version` (
                    `id`, `recipeId`, `archiveId`, `versionNumber`, `brewMethodCode`, `waterProfileId`, `notes`, `createdAt`, `updatedAt`
                )
                SELECT
                    `id`,
                    `id`,
                    `archiveId`,
                    1,
                    COALESCE(`brewMethodCode`, ''),
                    NULL,
                    `notes`,
                    `createdAt`,
                    `updatedAt`
                FROM `recipe_templates`
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `brew_run` (
                    `id`, `archiveId`, `runKind`, `brewMethodCode`, `status`, `startedAt`, `endedAt`, `brewedAt`, `timezoneOffsetMinutes`,
                    `recipeVersionId`, `coffeeBatchId`, `waterProfileId`, `locationText`, `operatorText`, `sourceId`, `confidenceScore`,
                    `notes`, `createdAt`, `updatedAt`
                )
                SELECT
                    r.`id`,
                    r.`archiveId`,
                    'brew',
                    COALESCE(r.`brewMethodCode`, ''),
                    r.`status`,
                    NULL,
                    NULL,
                    r.`brewedAt`,
                    NULL,
                    r.`recipeTemplateId`,
                    r.`beanId`,
                    NULL,
                    '',
                    '',
                    (SELECT `id` FROM `source_definition` WHERE `code` = 'migrated' LIMIT 1),
                    1.0,
                    r.`notes`,
                    r.`createdAt`,
                    r.`updatedAt`
                FROM `brew_records` r
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `brew_run_asset_link` (
                    `archiveId`, `brewRunId`, `assetId`, `roleCode`, `sortOrder`
                )
                SELECT
                    r.`archiveId`,
                    r.`id`,
                    r.`grinderId`,
                    'grinder',
                    0
                FROM `brew_records` r
                WHERE r.`grinderId` IS NOT NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `tag_definition` (`archiveId`, `categoryCode`, `code`, `displayName`, `description`, `colorHex`, `isSystem`, `createdAt`)
                SELECT
                    `archiveId`,
                    'flavor',
                    '',
                    `name`,
                    '',
                    '',
                    CASE WHEN `isPreset` = 1 THEN 1 ELSE 0 END,
                    $now
                FROM `flavor_tags`
                """.trimIndent(),
            )
            fun insertObservation(metricCode: String, valueExpr: String, whereClause: String = "1 = 1", valueType: String = "numeric", unitExpr: String = "(SELECT `defaultUnitCode` FROM `metric_definition` WHERE `code` = '$metricCode' LIMIT 1)") {
                db.execSQL(
                    """
                    INSERT INTO `observation` (
                        `archiveId`, `subjectType`, `subjectId`, `metricDefinitionId`, `capturedAt`, `sourceId`, `confidenceScore`, `valueType`,
                        `numericValue`, `normalizedNumericValue`, `unitCode`, `boolValue`, `textValue`, `enumOptionId`, `timestampValue`,
                        `jsonValue`, `sequenceNo`, `isTargetValue`, `isActualValue`
                    )
                    SELECT
                        r.`archiveId`,
                        'run',
                        r.`id`,
                        (SELECT `id` FROM `metric_definition` WHERE `code` = '$metricCode' LIMIT 1),
                        r.`updatedAt`,
                        (SELECT `id` FROM `source_definition` WHERE `code` = 'migrated' LIMIT 1),
                        1.0,
                        '$valueType',
                        $valueExpr,
                        $valueExpr,
                        $unitExpr,
                        NULL,
                        NULL,
                        NULL,
                        NULL,
                        NULL,
                        NULL,
                        0,
                        1
                    FROM `brew_records` r
                    WHERE $whereClause
                    """.trimIndent(),
                )
            }
            insertObservation("grind_setting", "r.`grindSetting`", "r.`grindSetting` IS NOT NULL")
            insertObservation("coffee_dose_g", "r.`coffeeDoseG`", "r.`coffeeDoseG` IS NOT NULL")
            insertObservation("brew_water_ml", "r.`brewWaterMl`", "r.`brewWaterMl` IS NOT NULL")
            insertObservation("bypass_water_ml", "r.`bypassWaterMl`", "r.`bypassWaterMl` IS NOT NULL")
            insertObservation("total_water_ml", "r.`totalWaterMl`", "r.`totalWaterMl` IS NOT NULL")
            insertObservation("brew_ratio", "r.`brewRatio`", "r.`brewRatio` IS NOT NULL")
            insertObservation("water_temp_c", "r.`waterTempC`", "r.`waterTempC` IS NOT NULL")
            insertObservation("brew_duration_seconds", "CAST(r.`brewDurationSeconds` AS REAL)", "r.`brewDurationSeconds` IS NOT NULL")

            db.execSQL(
                """
                INSERT INTO `observation` (
                    `archiveId`, `subjectType`, `subjectId`, `metricDefinitionId`, `capturedAt`, `sourceId`, `confidenceScore`, `valueType`,
                    `numericValue`, `normalizedNumericValue`, `unitCode`, `boolValue`, `textValue`, `enumOptionId`, `timestampValue`,
                    `jsonValue`, `sequenceNo`, `isTargetValue`, `isActualValue`
                )
                SELECT
                    r.`archiveId`,
                    'run',
                    r.`id`,
                    (SELECT `id` FROM `metric_definition` WHERE `code` = 'subjective_note' LIMIT 1),
                    r.`updatedAt`,
                    (SELECT `id` FROM `source_definition` WHERE `code` = 'migrated' LIMIT 1),
                    1.0,
                    'text',
                    NULL,
                    NULL,
                    'text',
                    NULL,
                    s.`notes`,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    0,
                    1
                FROM `brew_records` r
                INNER JOIN `subjective_evaluations` s ON s.`recordId` = r.`id`
                WHERE s.`notes` IS NOT NULL AND s.`notes` != ''
                """.trimIndent(),
            )
            fun insertScore(metricCode: String, columnName: String) {
                db.execSQL(
                    """
                    INSERT INTO `observation` (
                        `archiveId`, `subjectType`, `subjectId`, `metricDefinitionId`, `capturedAt`, `sourceId`, `confidenceScore`, `valueType`,
                        `numericValue`, `normalizedNumericValue`, `unitCode`, `boolValue`, `textValue`, `enumOptionId`, `timestampValue`,
                        `jsonValue`, `sequenceNo`, `isTargetValue`, `isActualValue`
                    )
                    SELECT
                        r.`archiveId`,
                        'run',
                        r.`id`,
                        (SELECT `id` FROM `metric_definition` WHERE `code` = '$metricCode' LIMIT 1),
                        r.`updatedAt`,
                        (SELECT `id` FROM `source_definition` WHERE `code` = 'migrated' LIMIT 1),
                        1.0,
                        'numeric',
                        CAST(s.`$columnName` AS REAL),
                        CAST(s.`$columnName` AS REAL),
                        'score_5',
                        NULL,
                        NULL,
                        NULL,
                        NULL,
                        NULL,
                        NULL,
                        0,
                        1
                    FROM `brew_records` r
                    INNER JOIN `subjective_evaluations` s ON s.`recordId` = r.`id`
                    WHERE s.`$columnName` IS NOT NULL
                    """.trimIndent(),
                )
            }
            insertScore("aroma_score", "aroma")
            insertScore("acidity_score", "acidity")
            insertScore("sweetness_score", "sweetness")
            insertScore("bitterness_score", "bitterness")
            insertScore("body_score", "body")
            insertScore("aftertaste_score", "aftertaste")
            insertScore("overall_score", "overall")

            db.execSQL(
                """
                INSERT OR IGNORE INTO `subject_tag_link` (`archiveId`, `subjectType`, `subjectId`, `tagDefinitionId`, `taggedAt`)
                SELECT
                    ft.`archiveId`,
                    'run',
                    rft.`recordId`,
                    td.`id`,
                    $now
                FROM `record_flavor_tags` rft
                INNER JOIN `flavor_tags` ft ON ft.`id` = rft.`flavorTagId`
                INNER JOIN `tag_definition` td
                    ON td.`archiveId` = ft.`archiveId`
                   AND td.`categoryCode` = 'flavor'
                   AND td.`displayName` = ft.`name`
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `recipe_templates` ADD COLUMN `waterCurveJson` TEXT")
            db.execSQL("ALTER TABLE `brew_records` ADD COLUMN `waterCurveJson` TEXT")
        }
    }
}
