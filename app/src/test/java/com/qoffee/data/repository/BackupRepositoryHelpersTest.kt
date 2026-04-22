package com.qoffee.data.repository

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.PourStage
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RestoreStatus
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.core.model.ThermalContainerType
import com.qoffee.core.model.WaterCurve
import com.qoffee.core.model.WaterCurveJsonCodec
import com.qoffee.data.local.ArchiveEntity
import com.qoffee.data.local.BeanProfileEntity
import com.qoffee.data.local.BrewRecordEntity
import com.qoffee.data.local.FlavorTagEntity
import com.qoffee.data.local.GrinderProfileEntity
import com.qoffee.data.local.RecipeTemplateEntity
import com.qoffee.data.local.SubjectiveEvaluationEntity
import org.junit.Test

class BackupRepositoryHelpersTest {

    @Test
    fun backupJsonCodec_roundTripsSnapshot() {
        val waterCurveJson = WaterCurveJsonCodec.encode(
            WaterCurve(
                ambientTempC = 24.0,
                containerType = ThermalContainerType.GLASS_CUP,
                stages = listOf(
                    PourStage(endTimeSeconds = 30, cumulativeWaterMl = 120.0, quickTemperatureC = 92.0),
                ),
            ),
        )
        val snapshot = BackupSnapshot(
            schemaVersion = BackupJsonCodec.SCHEMA_VERSION,
            exportedAt = 1_700_000_000_000L,
            preferences = BackupPreferences(
                currentArchiveId = 7L,
                autoRestoreDraft = true,
                showInsightConfidence = false,
                defaultAnalysisTimeRange = "ALL",
                defaultBeanProfileId = 10L,
                defaultGrinderProfileId = 20L,
                showLearnInDock = false,
            ),
            archives = listOf(
                BackupArchive(
                    id = 7L,
                    name = "Main Archive",
                    createdAt = 100L,
                    updatedAt = 200L,
                    beans = listOf(
                        BeanProfileEntity(
                            id = 10L,
                            archiveId = 7L,
                            name = "Kenya AA",
                            roaster = "Qoffee",
                            origin = "Nyeri",
                            processValue = 1,
                            variety = "SL28",
                            roastLevelValue = 1,
                            roastDateEpochDay = 20_000L,
                            initialStockG = 250.0,
                            notes = "note",
                            createdAt = 100L,
                        ),
                    ),
                    grinders = listOf(
                        GrinderProfileEntity(
                            id = 20L,
                            archiveId = 7L,
                            name = "C40",
                            minSetting = 14.0,
                            maxSetting = 36.0,
                            stepSize = 1.0,
                            unitLabel = "click",
                            notes = "",
                            createdAt = 100L,
                        ),
                    ),
                    recipes = listOf(
                        RecipeTemplateEntity(
                            id = 30L,
                            archiveId = 7L,
                            name = "Baseline",
                            brewMethodCode = BrewMethod.POUR_OVER.code,
                            beanId = 10L,
                            beanNameSnapshot = "Kenya AA",
                            grinderId = 20L,
                            grinderNameSnapshot = "C40",
                            grindSetting = 22.0,
                            coffeeDoseG = 15.0,
                            brewWaterMl = 240.0,
                            bypassWaterMl = 0.0,
                            waterTempC = 92.0,
                            waterCurveJson = waterCurveJson,
                            notes = "",
                            createdAt = 100L,
                            updatedAt = 200L,
                        ),
                    ),
                    flavorTags = listOf(
                        FlavorTagEntity(
                            id = 40L,
                            archiveId = 7L,
                            name = "Berry",
                            isPreset = false,
                        ),
                    ),
                    records = listOf(
                        BackupRecord(
                            record = BrewRecordEntity(
                                id = 50L,
                                archiveId = 7L,
                                status = RecordStatus.COMPLETED.code,
                                brewMethodCode = BrewMethod.POUR_OVER.code,
                                beanId = 10L,
                                beanNameSnapshot = "Kenya AA",
                                beanRoastLevelSnapshotValue = 1,
                                beanProcessMethodSnapshotValue = 1,
                                recipeTemplateId = 30L,
                                recipeNameSnapshot = "Baseline",
                                grinderId = 20L,
                                grinderNameSnapshot = "C40",
                                grindSetting = 22.0,
                                coffeeDoseG = 15.0,
                                brewWaterMl = 240.0,
                                bypassWaterMl = 0.0,
                                waterTempC = 92.0,
                                waterCurveJson = waterCurveJson,
                                notes = "record",
                                brewedAt = 1_700_000_000_000L,
                                brewDurationSeconds = 165,
                                createdAt = 1_700_000_000_000L,
                                updatedAt = 1_700_000_000_000L,
                                totalWaterMl = 240.0,
                                brewRatio = 16.0,
                            ),
                            subjectiveEvaluation = SubjectiveEvaluationEntity(
                                recordId = 50L,
                                aroma = 4,
                                acidity = 4,
                                sweetness = 4,
                                bitterness = 2,
                                body = 3,
                                aftertaste = 4,
                                overall = 5,
                                notes = "juicy",
                            ),
                            flavorTagIds = listOf(40L),
                        ),
                    ),
                ),
            ),
        )

        val decoded = BackupJsonCodec.decode(BackupJsonCodec.encode(snapshot))

        assertThat(decoded.preferences.defaultAnalysisTimeRange).isEqualTo("ALL")
        assertThat(decoded.archives).hasSize(1)
        assertThat(decoded.archives.first().recipes.first().waterCurveJson).isEqualTo(waterCurveJson)
        assertThat(decoded.archives.first().records.first().record.brewDurationSeconds).isEqualTo(165)
        assertThat(decoded.archives.first().records.first().record.waterCurveJson).isEqualTo(waterCurveJson)
        assertThat(decoded.archives.first().records.first().flavorTagIds).containsExactly(40L)
    }

    @Test
    fun recordsCsvExporter_includesBrewTimeAndEscapesText() {
        val csv = RecordsCsvExporter.export(
            listOf(
                CoffeeRecord(
                    id = 1L,
                    status = RecordStatus.COMPLETED,
                    brewMethod = BrewMethod.POUR_OVER,
                    beanNameSnapshot = "Kenya, AA",
                    recipeNameSnapshot = "Baseline",
                    grinderNameSnapshot = "C40",
                    coffeeDoseG = 15.0,
                    brewWaterMl = 240.0,
                    bypassWaterMl = 0.0,
                    totalWaterMl = 240.0,
                    brewRatio = 16.0,
                    waterTempC = 92.0,
                    brewDurationSeconds = 165,
                    brewedAt = 1_700_000_000_000L,
                    notes = "note with \"quotes\"",
                    subjectiveEvaluation = SubjectiveEvaluation(
                        recordId = 1L,
                        overall = 5,
                        notes = "sweet, juicy",
                        flavorTags = listOf(FlavorTag(id = 9L, archiveId = 1L, name = "Berry")),
                    ),
                ),
            ),
        )

        assertThat(csv).contains("brewDurationSeconds")
        assertThat(csv).contains("165")
        assertThat(csv).contains("\"Kenya, AA\"")
        assertThat(csv).contains("\"note with \"\"quotes\"\"\"")
    }

    @Test
    fun uniqueArchiveName_appendsNumericSuffixWhenNeeded() {
        val name = uniqueArchiveName("Main Archive", setOf("Main Archive", "Main Archive (2)"))

        assertThat(name).isEqualTo("Main Archive (3)")
    }

    @Test
    fun defaultExportFileNames_useExpectedSuffixes() {
        assertThat(defaultBackupFileName(1_700_000_000_000L)).endsWith(".json")
        assertThat(defaultBackupFileName(1_700_000_000_000L)).contains("qoffee-backup-")
        assertThat(defaultRecordsCsvFileName(1_700_000_000_000L)).endsWith(".csv")
        assertThat(defaultRecordsCsvFileName(1_700_000_000_000L)).contains("qoffee-records-")
    }

    @Test
    fun restoreOutcomeMapsValidationErrors() {
        val outcome = IllegalArgumentException("bad backup").toRestoreOutcome()

        assertThat(outcome.status).isEqualTo(RestoreStatus.VALIDATION_ERROR)
        assertThat(outcome.message).isEqualTo("bad backup")
    }

    @Test
    fun buildRestoreSuccessMessageSummarizesImportedArchives() {
        val message = buildRestoreSuccessMessage(listOf("Main", "Lab", "Travel"))

        assertThat(message).contains("已恢复 3 个存档")
        assertThat(message).contains("Main、Lab、Travel")
    }
}
