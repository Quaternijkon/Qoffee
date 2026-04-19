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
}
