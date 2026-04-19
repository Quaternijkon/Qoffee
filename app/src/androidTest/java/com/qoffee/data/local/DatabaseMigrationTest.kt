package com.qoffee.data.local

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
}
