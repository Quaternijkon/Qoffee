package com.qoffee.domain.repository

import com.qoffee.core.model.AnalyticsDashboard
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.ObjectiveDraftUpdate
import com.qoffee.core.model.RecordValidationResult
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observeBeanProfiles(): Flow<List<BeanProfile>>
    fun observeGrinderProfiles(): Flow<List<GrinderProfile>>
    fun observeFlavorTags(): Flow<List<FlavorTag>>
    suspend fun getBeanProfile(id: Long): BeanProfile?
    suspend fun getGrinderProfile(id: Long): GrinderProfile?
    suspend fun saveBeanProfile(profile: BeanProfile): Long
    suspend fun saveGrinderProfile(profile: GrinderProfile): Long
    suspend fun ensureFlavorTag(name: String): FlavorTag
    suspend fun seedPresetFlavorTags()
}

interface RecordRepository {
    fun observeRecords(filter: AnalysisFilter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL)): Flow<List<CoffeeRecord>>
    fun observeRecord(recordId: Long): Flow<CoffeeRecord?>
    suspend fun getRecord(recordId: Long): CoffeeRecord?
    suspend fun getActiveDraftId(): Long?
    suspend fun getOrCreateActiveDraftId(autoRestore: Boolean = true): Long
    suspend fun updateObjective(recordId: Long, update: ObjectiveDraftUpdate)
    suspend fun updateSubjective(recordId: Long, evaluation: SubjectiveEvaluation)
    suspend fun completeRecord(recordId: Long): RecordValidationResult
    suspend fun duplicateRecordAsDraft(recordId: Long): Long
}

interface AnalyticsRepository {
    fun observeDashboard(filter: AnalysisFilter): Flow<AnalyticsDashboard>
}

interface PreferenceRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun setAutoRestoreDraft(enabled: Boolean)
    suspend fun setShowInsightConfidence(enabled: Boolean)
    suspend fun setDefaultAnalysisTimeRange(range: AnalysisTimeRange)
}
