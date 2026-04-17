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
import com.qoffee.data.local.BrewRecordDao
import com.qoffee.data.local.FlavorTagDao
import com.qoffee.data.local.GrinderProfileDao
import com.qoffee.data.local.QoffeeDatabase
import com.qoffee.data.local.RecordFlavorTagDao
import com.qoffee.data.local.SubjectiveEvaluationDao
import com.qoffee.data.repository.AnalyticsRepositoryImpl
import com.qoffee.data.repository.CatalogRepositoryImpl
import com.qoffee.data.repository.PreferenceRepositoryImpl
import com.qoffee.data.repository.RecordRepositoryImpl
import com.qoffee.domain.repository.AnalyticsRepository
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecordRepository
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
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideBeanProfileDao(database: QoffeeDatabase): BeanProfileDao = database.beanProfileDao()

    @Provides
    fun provideGrinderProfileDao(database: QoffeeDatabase): GrinderProfileDao = database.grinderProfileDao()

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
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindRecordRepository(impl: RecordRepositoryImpl): RecordRepository

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository

    @Binds
    @Singleton
    abstract fun bindPreferenceRepository(impl: PreferenceRepositoryImpl): PreferenceRepository
}
