package com.qoffee.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ArchiveEntity::class,
        BeanProfileEntity::class,
        GrinderProfileEntity::class,
        RecipeTemplateEntity::class,
        BrewRecordEntity::class,
        SubjectiveEvaluationEntity::class,
        FlavorTagEntity::class,
        RecordFlavorTagCrossRef::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class QoffeeDatabase : RoomDatabase() {
    abstract fun archiveDao(): ArchiveDao
    abstract fun beanProfileDao(): BeanProfileDao
    abstract fun grinderProfileDao(): GrinderProfileDao
    abstract fun recipeTemplateDao(): RecipeTemplateDao
    abstract fun brewRecordDao(): BrewRecordDao
    abstract fun subjectiveEvaluationDao(): SubjectiveEvaluationDao
    abstract fun flavorTagDao(): FlavorTagDao
    abstract fun recordFlavorTagDao(): RecordFlavorTagDao
}
