package com.qoffee.domain.repository

import com.qoffee.core.model.AnalyticsDashboard
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.ArchiveSeedStatus
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.ObjectiveDraftUpdate
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.RecordValidationResult
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface ArchiveRepository {
    fun observeArchives(): Flow<List<ArchiveSummary>>
    fun observeCurrentArchive(): Flow<ArchiveSummary?>
    suspend fun getCurrentArchiveId(): Long?
    suspend fun switchArchive(id: Long)
    suspend fun createArchive(name: String): Long
    suspend fun duplicateArchive(sourceArchiveId: Long, newName: String, switchToNew: Boolean = true): Long
    suspend fun renameArchive(id: Long, newName: String)
    suspend fun deleteArchive(id: Long)
    suspend fun copyDemoArchiveAsEditable(name: String): Long
    suspend fun resetDemoArchive(): Long
    suspend fun seedDemoArchiveIfNeeded(): ArchiveSeedStatus
}

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

interface RecipeRepository {
    fun observeRecipes(): Flow<List<RecipeTemplate>>
    suspend fun getRecipe(id: Long): RecipeTemplate?
    suspend fun saveRecipe(template: RecipeTemplate): Long
    suspend fun deleteRecipe(id: Long)
}

interface RecordRepository {
    fun observeRecords(filter: AnalysisFilter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL)): Flow<List<CoffeeRecord>>
    fun observeRecentRecords(limit: Int): Flow<List<CoffeeRecord>>
    fun observeRecord(recordId: Long): Flow<CoffeeRecord?>
    suspend fun getRecord(recordId: Long): CoffeeRecord?
    suspend fun getActiveDraftId(): Long?
    suspend fun getOrCreateActiveDraftId(autoRestore: Boolean = true): Long
    suspend fun createEmptyDraft(replaceActiveDraft: Boolean = false): Long
    suspend fun createDraftFromRecipe(recipeId: Long, replaceActiveDraft: Boolean = false): Long
    suspend fun applyRecipeToDraft(recordId: Long, recipeId: Long)
    suspend fun getLatestComparableRecord(beanId: Long?, brewMethod: BrewMethod?, excludingRecordId: Long? = null): CoffeeRecord?
    suspend fun duplicateLatestComparableAsDraft(beanId: Long?, brewMethod: BrewMethod?): Long?
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
