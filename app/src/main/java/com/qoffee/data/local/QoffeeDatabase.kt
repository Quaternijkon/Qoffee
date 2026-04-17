package com.qoffee.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BeanProfileEntity::class,
        GrinderProfileEntity::class,
        BrewRecordEntity::class,
        SubjectiveEvaluationEntity::class,
        FlavorTagEntity::class,
        RecordFlavorTagCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class QoffeeDatabase : RoomDatabase() {
    abstract fun beanProfileDao(): BeanProfileDao
    abstract fun grinderProfileDao(): GrinderProfileDao
    abstract fun brewRecordDao(): BrewRecordDao
    abstract fun subjectiveEvaluationDao(): SubjectiveEvaluationDao
    abstract fun flavorTagDao(): FlavorTagDao
    abstract fun recordFlavorTagDao(): RecordFlavorTagDao
}
