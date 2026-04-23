package com.qoffee.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(QoffeeDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate2To3_preservesRecordsAndAddsRecipeSchema() {
        val databaseName = "migration-test"

        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO archives (id, name, typeCode, isReadOnly, createdAt, updatedAt, sortOrder)
                VALUES (1, '主存档', 'normal', 0, 1000, 1000, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO brew_records (
                    id, archiveId, status, brewMethodCode, beanId, beanNameSnapshot,
                    beanRoastLevelSnapshotValue, beanProcessMethodSnapshotValue,
                    grinderId, grinderNameSnapshot, grindSetting, coffeeDoseG,
                    brewWaterMl, bypassWaterMl, waterTempC, notes, brewedAt,
                    createdAt, updatedAt, totalWaterMl, brewRatio
                ) VALUES (
                    1, 1, 'completed', 'pour_over', NULL, 'Kenya AB',
                    NULL, NULL, NULL, 'Comandante', 20.0, 15.0,
                    240.0, 0.0, 92.0, 'old note', 2000,
                    2000, 2000, 240.0, 16.0
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            QoffeeDatabaseMigrations.MIGRATION_2_3,
        ).apply {
            val countCursor = query("SELECT COUNT(*) FROM brew_records")
            assertTrue(countCursor.moveToFirst())
            assertEquals(1, countCursor.getInt(0))
            countCursor.close()

            val recordCursor = query("SELECT recipeTemplateId, recipeNameSnapshot FROM brew_records WHERE id = 1")
            assertTrue(recordCursor.moveToFirst())
            assertTrue(recordCursor.isNull(0))
            assertTrue(recordCursor.isNull(1))
            recordCursor.close()

            val tableCursor = query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'recipe_templates'")
            assertTrue(tableCursor.moveToFirst())
            assertEquals("recipe_templates", tableCursor.getString(0))
            tableCursor.close()
            close()
        }
    }

    @Test
    fun migrate3To4_addsBeanStockAndRescalesSubjectiveScores() {
        val databaseName = "migration-test-v4"

        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                """
                INSERT INTO archives (id, name, typeCode, isReadOnly, createdAt, updatedAt, sortOrder)
                VALUES (1, '主存档', 'normal', 0, 1000, 1000, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO bean_profiles (
                    id, archiveId, name, roaster, origin, processValue, variety,
                    roastLevelValue, roastDateEpochDay, notes, createdAt
                ) VALUES (
                    1, 1, 'Kenya AA', 'Qoffee', 'Nyeri', 1, 'SL28',
                    1, 20000, 'note', 1000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO brew_records (
                    id, archiveId, status, brewMethodCode, beanId, beanNameSnapshot,
                    beanRoastLevelSnapshotValue, beanProcessMethodSnapshotValue,
                    recipeTemplateId, recipeNameSnapshot, grinderId, grinderNameSnapshot,
                    grindSetting, coffeeDoseG, brewWaterMl, bypassWaterMl, waterTempC,
                    notes, brewedAt, createdAt, updatedAt, totalWaterMl, brewRatio
                ) VALUES (
                    1, 1, 'completed', 'pour_over', 1, 'Kenya AA',
                    1, 1, NULL, NULL, NULL, NULL,
                    20.0, 15.0, 240.0, 0.0, 92.0,
                    'note', 2000, 2000, 2000, 240.0, 16.0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO subjective_evaluations (
                    recordId, aroma, acidity, sweetness, bitterness, body, aftertaste, overall, notes
                ) VALUES (
                    1, 8, 7, 6, 4, 5, 3, 9, 'legacy score'
                )
                """.trimIndent(),
            )
            close()
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, QoffeeDatabase::class.java, databaseName)
            .addMigrations(QoffeeDatabaseMigrations.MIGRATION_3_4)
            .build()

        database.openHelper.writableDatabase.apply {
            val beanCursor = query("SELECT initialStockG FROM bean_profiles WHERE id = 1")
            assertTrue(beanCursor.moveToFirst())
            assertTrue(beanCursor.isNull(0))
            beanCursor.close()

            val scoreCursor = query(
                """
                SELECT aroma, acidity, sweetness, bitterness, body, aftertaste, overall
                FROM subjective_evaluations
                WHERE recordId = 1
                """.trimIndent(),
            )
            assertTrue(scoreCursor.moveToFirst())
            assertEquals(4, scoreCursor.getInt(0))
            assertEquals(4, scoreCursor.getInt(1))
            assertEquals(3, scoreCursor.getInt(2))
            assertEquals(4, scoreCursor.getInt(3))
            assertEquals(5, scoreCursor.getInt(4))
            assertEquals(3, scoreCursor.getInt(5))
            assertEquals(5, scoreCursor.getInt(6))
            scoreCursor.close()
            close()
        }
        database.close()
    }

    @Test
    fun migrate4To5_addsBrewDurationSeconds() {
        val databaseName = "migration-test-v5"

        helper.createDatabase(databaseName, 4).apply {
            execSQL(
                """
                INSERT INTO archives (id, name, typeCode, isReadOnly, createdAt, updatedAt, sortOrder)
                VALUES (1, '涓诲瓨妗?, 'normal', 0, 1000, 1000, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO brew_records (
                    id, archiveId, status, brewMethodCode, beanId, beanNameSnapshot,
                    beanRoastLevelSnapshotValue, beanProcessMethodSnapshotValue,
                    recipeTemplateId, recipeNameSnapshot, grinderId, grinderNameSnapshot,
                    grindSetting, coffeeDoseG, brewWaterMl, bypassWaterMl, waterTempC,
                    notes, brewedAt, createdAt, updatedAt, totalWaterMl, brewRatio
                ) VALUES (
                    1, 1, 'completed', 'pour_over', NULL, 'Kenya AA',
                    NULL, NULL, NULL, NULL, NULL, NULL,
                    20.0, 15.0, 240.0, 0.0, 92.0,
                    'note', 2000, 2000, 2000, 240.0, 16.0
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            QoffeeDatabaseMigrations.MIGRATION_4_5,
        ).apply {
            val cursor = query("SELECT brewDurationSeconds FROM brew_records WHERE id = 1")
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            cursor.close()
            close()
        }
    }

    @Test
    fun migrate5To6_createsFrozenFactsAndCopiesLegacyData() {
        val databaseName = "migration-test-v6"

        helper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO archives (id, name, typeCode, isReadOnly, createdAt, updatedAt, sortOrder)
                VALUES (1, 'Main Archive', 'normal', 0, 1000, 1000, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO bean_profiles (
                    id, archiveId, name, roaster, origin, processValue, variety,
                    roastLevelValue, roastDateEpochDay, initialStockG, notes, createdAt
                ) VALUES (
                    1, 1, 'Kenya AA', 'Qoffee', 'Nyeri', 1, 'SL28',
                    1, 20000, 250.0, 'batch note', 1000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO grinder_profiles (
                    id, archiveId, name, minSetting, maxSetting, stepSize, unitLabel, notes, createdAt
                ) VALUES (
                    2, 1, 'C40', 14.0, 36.0, 1.0, 'click', 'grinder note', 1000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO recipe_templates (
                    id, archiveId, name, brewMethodCode, beanId, beanNameSnapshot, grinderId, grinderNameSnapshot,
                    grindSetting, coffeeDoseG, brewWaterMl, bypassWaterMl, waterTempC, notes, createdAt, updatedAt
                ) VALUES (
                    3, 1, 'Baseline', 'pour_over', 1, 'Kenya AA', 2, 'C40',
                    22.0, 15.0, 240.0, 0.0, 92.0, 'recipe note', 1000, 1500
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO flavor_tags (id, archiveId, name, isPreset)
                VALUES (4, 1, 'Berry', 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO brew_records (
                    id, archiveId, status, brewMethodCode, beanId, beanNameSnapshot,
                    beanRoastLevelSnapshotValue, beanProcessMethodSnapshotValue,
                    recipeTemplateId, recipeNameSnapshot, grinderId, grinderNameSnapshot,
                    grindSetting, coffeeDoseG, brewWaterMl, bypassWaterMl, waterTempC,
                    notes, brewedAt, brewDurationSeconds, createdAt, updatedAt, totalWaterMl, brewRatio
                ) VALUES (
                    5, 1, 'completed', 'pour_over', 1, 'Kenya AA',
                    1, 1, 3, 'Baseline', 2, 'C40',
                    22.0, 15.0, 240.0, 0.0, 92.0,
                    'record note', 3000, 165, 3000, 3000, 240.0, 16.0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO subjective_evaluations (
                    recordId, aroma, acidity, sweetness, bitterness, body, aftertaste, overall, notes
                ) VALUES (
                    5, 4, 4, 4, 2, 3, 4, 5, 'juicy'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO record_flavor_tags (recordId, flavorTagId)
                VALUES (5, 4)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            QoffeeDatabaseMigrations.MIGRATION_5_6,
        ).apply {
            val archiveCursor = query("SELECT COUNT(*) FROM `archive`")
            assertTrue(archiveCursor.moveToFirst())
            assertEquals(1, archiveCursor.getInt(0))
            archiveCursor.close()

            val batchCursor = query("SELECT productId, initialStockG FROM coffee_batch WHERE id = 1")
            assertTrue(batchCursor.moveToFirst())
            assertEquals(1, batchCursor.getInt(0))
            assertEquals(250.0, batchCursor.getDouble(1), 0.001)
            batchCursor.close()

            val runCursor = query("SELECT coffeeBatchId, recipeVersionId FROM brew_run WHERE id = 5")
            assertTrue(runCursor.moveToFirst())
            assertEquals(1, runCursor.getInt(0))
            assertEquals(3, runCursor.getInt(1))
            runCursor.close()

            val obsCursor = query(
                """
                SELECT COUNT(*) FROM observation
                WHERE subjectType = 'run' AND subjectId = 5
                """.trimIndent(),
            )
            assertTrue(obsCursor.moveToFirst())
            assertTrue(obsCursor.getInt(0) >= 5)
            obsCursor.close()

            val tagCursor = query("SELECT COUNT(*) FROM subject_tag_link WHERE subjectType = 'run' AND subjectId = 5")
            assertTrue(tagCursor.moveToFirst())
            assertEquals(1, tagCursor.getInt(0))
            tagCursor.close()
            close()
        }
    }

    @Test
    fun migrate6To7_addsWaterCurveJsonColumns() {
        val databaseName = "migration-test-v7"

        helper.createDatabase(databaseName, 6).apply {
            execSQL(
                """
                INSERT INTO archives (id, name, typeCode, isReadOnly, createdAt, updatedAt, sortOrder)
                VALUES (1, 'Main Archive', 'normal', 0, 1000, 1000, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO recipe_templates (
                    id, archiveId, name, brewMethodCode, beanId, beanNameSnapshot, grinderId, grinderNameSnapshot,
                    grindSetting, coffeeDoseG, brewWaterMl, bypassWaterMl, waterTempC, notes, createdAt, updatedAt
                ) VALUES (
                    3, 1, 'Baseline', 'pour_over', NULL, 'Kenya AA', NULL, 'C40',
                    22.0, 15.0, 240.0, 0.0, 92.0, 'recipe note', 1000, 1500
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO brew_records (
                    id, archiveId, status, brewMethodCode, beanId, beanNameSnapshot,
                    beanRoastLevelSnapshotValue, beanProcessMethodSnapshotValue,
                    recipeTemplateId, recipeNameSnapshot, grinderId, grinderNameSnapshot,
                    grindSetting, coffeeDoseG, brewWaterMl, bypassWaterMl, waterTempC,
                    notes, brewedAt, brewDurationSeconds, createdAt, updatedAt, totalWaterMl, brewRatio
                ) VALUES (
                    5, 1, 'completed', 'pour_over', NULL, 'Kenya AA',
                    NULL, NULL, 3, 'Baseline', NULL, 'C40',
                    22.0, 15.0, 240.0, 0.0, 92.0,
                    'record note', 3000, 165, 3000, 3000, 240.0, 16.0
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            QoffeeDatabaseMigrations.MIGRATION_6_7,
        ).apply {
            val recipeCursor = query("SELECT waterCurveJson FROM recipe_templates WHERE id = 3")
            assertTrue(recipeCursor.moveToFirst())
            assertTrue(recipeCursor.isNull(0))
            recipeCursor.close()

            val recordCursor = query("SELECT waterCurveJson FROM brew_records WHERE id = 5")
            assertTrue(recordCursor.moveToFirst())
            assertTrue(recordCursor.isNull(0))
            recordCursor.close()
            close()
        }
    }

    @Test
    fun migrate7To8_addsGrinderNormalizationAndCollectionConfigColumns() {
        val databaseName = "migration-test-v8"

        helper.createDatabase(databaseName, 7).apply {
            execSQL(
                """
                INSERT INTO archives (id, name, typeCode, isReadOnly, createdAt, updatedAt, sortOrder)
                VALUES (1, 'Main Archive', 'normal', 0, 1000, 1000, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO grinder_profiles (
                    id, archiveId, name, minSetting, maxSetting, stepSize, unitLabel, notes, createdAt
                ) VALUES (
                    3, 1, 'C40', 10.0, 30.0, 1.0, 'click', 'note', 1000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO `collection` (
                    id, archiveId, typeCode, title, description, hypothesis, notes, createdAt, updatedAt
                ) VALUES (
                    4, 1, 'experiment_project', '磨豆实验', '', '', '', 1000, 1000
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            8,
            true,
            QoffeeDatabaseMigrations.MIGRATION_7_8,
        ).apply {
            val grinderCursor = query("SELECT normalizationJson FROM grinder_profiles WHERE id = 3")
            assertTrue(grinderCursor.moveToFirst())
            assertTrue(grinderCursor.isNull(0))
            grinderCursor.close()

            val collectionCursor = query("SELECT configJson FROM `collection` WHERE id = 4")
            assertTrue(collectionCursor.moveToFirst())
            assertEquals("", collectionCursor.getString(0))
            collectionCursor.close()
            close()
        }
    }
}
