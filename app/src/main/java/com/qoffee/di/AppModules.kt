package com.qoffee.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.qoffee.core.common.SystemTimeProvider
import com.qoffee.core.common.TimeProvider
import com.qoffee.data.local.BeanProfileDao
import com.qoffee.data.local.ArchiveDao
import com.qoffee.data.local.BrewRecordDao
import com.qoffee.data.local.FlavorTagDao
import com.qoffee.data.local.GrinderProfileDao
import com.qoffee.data.local.QoffeeDatabase
import com.qoffee.data.local.QoffeeDatabaseMigrations
import com.qoffee.data.local.RecipeTemplateDao
import com.qoffee.data.local.RecordFlavorTagDao
import com.qoffee.data.local.SubjectiveEvaluationDao
import com.qoffee.data.repository.ArchiveRepositoryImpl
import com.qoffee.data.repository.AnalyticsRepositoryImpl
import com.qoffee.data.repository.BackupRepositoryImpl
import com.qoffee.data.repository.CatalogRepositoryImpl
import com.qoffee.data.repository.PreferenceRepositoryImpl
import com.qoffee.data.repository.RecipeRepositoryImpl
import com.qoffee.data.repository.RecordRepositoryImpl
import com.qoffee.data.repository.SessionRepositoryImpl
import com.qoffee.data.repository.LearningRepositoryImpl
import com.qoffee.data.repository.ExperimentRepositoryFacade
import com.qoffee.data.repository.GuideRepositoryImpl
import com.qoffee.data.repository.EntitlementRepositoryImpl
import com.qoffee.data.repository.ShareRepositoryImpl
import com.qoffee.domain.repository.ArchiveRepository
import com.qoffee.domain.repository.AnalyticsRepository
import com.qoffee.domain.repository.BackupRepository
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.EntitlementRepository
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.GuideRepository
import com.qoffee.domain.repository.LearningRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.domain.repository.SessionRepository
import com.qoffee.domain.repository.ShareRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QoffeeDatabase {
        return Room.databaseBuilder(
            context,
            QoffeeDatabase::class.java,
            "qoffee.db",
        ).addMigrations(
            QoffeeDatabaseMigrations.MIGRATION_2_3,
            QoffeeDatabaseMigrations.MIGRATION_3_4,
            QoffeeDatabaseMigrations.MIGRATION_4_5,
            QoffeeDatabaseMigrations.MIGRATION_5_6,
            QoffeeDatabaseMigrations.MIGRATION_6_7,
            QoffeeDatabaseMigrations.MIGRATION_7_8,
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    @Provides
    fun provideArchiveDao(database: QoffeeDatabase): ArchiveDao = database.archiveDao()

    @Provides
    fun provideBeanProfileDao(database: QoffeeDatabase): BeanProfileDao = database.beanProfileDao()

    @Provides
    fun provideGrinderProfileDao(database: QoffeeDatabase): GrinderProfileDao = database.grinderProfileDao()

    @Provides
    fun provideRecipeTemplateDao(database: QoffeeDatabase): RecipeTemplateDao = database.recipeTemplateDao()

    @Provides
    fun provideBrewRecordDao(database: QoffeeDatabase): BrewRecordDao = database.brewRecordDao()

    @Provides
    fun provideSubjectiveEvaluationDao(database: QoffeeDatabase): SubjectiveEvaluationDao = database.subjectiveEvaluationDao()

    @Provides
    fun provideFlavorTagDao(database: QoffeeDatabase): FlavorTagDao = database.flavorTagDao()

    @Provides
    fun provideRecordFlavorTagDao(database: QoffeeDatabase): RecordFlavorTagDao = database.recordFlavorTagDao()
}

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("qoffee_preferences.preferences_pb") },
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindArchiveRepository(impl: ArchiveRepositoryImpl): ArchiveRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindRecipeRepository(impl: RecipeRepositoryImpl): RecipeRepository

    @Binds
    @Singleton
    abstract fun bindRecordRepository(impl: RecordRepositoryImpl): RecordRepository

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository

    @Binds
    @Singleton
    abstract fun bindPreferenceRepository(impl: PreferenceRepositoryImpl): PreferenceRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindLearningRepository(impl: LearningRepositoryImpl): LearningRepository

    @Binds
    @Singleton
    abstract fun bindExperimentRepository(impl: ExperimentRepositoryFacade): ExperimentRepository

    @Binds
    @Singleton
    abstract fun bindGuideRepository(impl: GuideRepositoryImpl): GuideRepository

    @Binds
    @Singleton
    abstract fun bindEntitlementRepository(impl: EntitlementRepositoryImpl): EntitlementRepository

    @Binds
    @Singleton
    abstract fun bindShareRepository(impl: ShareRepositoryImpl): ShareRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
}
