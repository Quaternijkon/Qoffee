package com.qoffee.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.qoffee.BuildConfig
import com.qoffee.core.model.ServerEnvironment
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
import com.qoffee.data.local.SyncMetadataDao
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
import com.qoffee.data.repository.EntitlementRepositoryPrefsImpl
import com.qoffee.data.repository.ShareRepositoryRecordImpl
import com.qoffee.data.repository.SyncDataBridge
import com.qoffee.data.repository.SyncRepositoryImpl
import com.qoffee.data.repository.RoomSyncBridge
import com.qoffee.data.repository.AiCoachRepositoryImpl
import com.qoffee.data.repository.PreferenceKeys
import com.qoffee.data.remote.QoffeeApi
import com.qoffee.data.remote.QoffeeApiClient
import com.qoffee.domain.repository.AiCoachRepository
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
import com.qoffee.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first

private val Context.qoffeePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "qoffee_preferences",
)

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
            QoffeeDatabaseMigrations.MIGRATION_8_9,
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

    @Provides
    fun provideSyncMetadataDao(database: QoffeeDatabase): SyncMetadataDao = database.syncMetadataDao()
}

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.qoffeePreferencesDataStore
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient {
        return HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000L
                requestTimeoutMillis = 30_000L
                socketTimeoutMillis = 30_000L
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    @Provides
    @Singleton
    fun provideQoffeeApiClient(
        httpClient: HttpClient,
        json: Json,
        dataStore: DataStore<Preferences>,
    ): QoffeeApi {
        return QoffeeApiClient(
            httpClient = httpClient,
            json = json,
            baseUrlProvider = {
                val environment = ServerEnvironment.entries.firstOrNull {
                    it.name == dataStore.data.first()[PreferenceKeys.SERVER_ENVIRONMENT]
                } ?: ServerEnvironment.TEST
                when (environment) {
                    ServerEnvironment.TEST -> BuildConfig.QOFFEE_TEST_API_BASE_URL
                    ServerEnvironment.PRODUCTION -> BuildConfig.QOFFEE_PRODUCTION_API_BASE_URL
                }
            },
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
    abstract fun bindEntitlementRepository(impl: EntitlementRepositoryPrefsImpl): EntitlementRepository

    @Binds
    @Singleton
    abstract fun bindShareRepository(impl: ShareRepositoryRecordImpl): ShareRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindSyncDataBridge(impl: RoomSyncBridge): SyncDataBridge

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindAiCoachRepository(impl: AiCoachRepositoryImpl): AiCoachRepository
}
