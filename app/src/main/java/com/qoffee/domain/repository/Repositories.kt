package com.qoffee.domain.repository

import com.qoffee.core.model.AnalyticsDashboard
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.ArchiveSeedStatus
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.BrewSession
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.Experiment
import com.qoffee.core.model.ExperimentRun
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.GlossaryTerm
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.LearningTrack
import com.qoffee.core.model.Lesson
import com.qoffee.core.model.ObjectiveDraftUpdate
import com.qoffee.core.model.PracticeBlock
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.RecipeVersion
import com.qoffee.core.model.RecordValidationResult
import com.qoffee.core.model.RecordPrefillSource
import com.qoffee.core.model.DraftReplacePolicy
import com.qoffee.core.model.FileExportPayload
import com.qoffee.core.model.RestoreOutcome
import com.qoffee.core.model.ShareCard
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.core.model.TroubleshootingItem
import com.qoffee.core.model.UserEntitlements
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
    suspend fun deleteBeanProfile(id: Long)
    suspend fun deleteGrinderProfile(id: Long)
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
    suspend fun createDraft(
        prefillSource: RecordPrefillSource,
        replacePolicy: DraftReplacePolicy = DraftReplacePolicy.REPLACE_CURRENT,
    ): Long
    suspend fun getOrCreateActiveDraftId(autoRestore: Boolean = true): Long
    suspend fun createEmptyDraft(replaceActiveDraft: Boolean = false): Long
    suspend fun createDraftFromRecipe(recipeId: Long, replaceActiveDraft: Boolean = false): Long
    suspend fun applyRecipeToDraft(recordId: Long, recipeId: Long)
    suspend fun getLatestComparableRecord(beanId: Long?, brewMethod: BrewMethod?, excludingRecordId: Long? = null): CoffeeRecord?
    suspend fun duplicateLatestComparableAsDraft(beanId: Long?, brewMethod: BrewMethod?): Long?
    suspend fun saveRecordAsRecipe(recordId: Long, name: String, targetRecipeId: Long? = null): Long
    suspend fun updateObjective(recordId: Long, update: ObjectiveDraftUpdate)
    suspend fun updateSubjective(recordId: Long, evaluation: SubjectiveEvaluation)
    suspend fun completeRecord(recordId: Long): RecordValidationResult
    suspend fun duplicateRecordAsDraft(recordId: Long): Long
    suspend fun deleteRecord(recordId: Long)
}

interface AnalyticsRepository {
    fun observeDashboard(filter: AnalysisFilter): Flow<AnalyticsDashboard>
}

interface PreferenceRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun setAutoRestoreDraft(enabled: Boolean)
    suspend fun setShowInsightConfidence(enabled: Boolean)
    suspend fun setDefaultAnalysisTimeRange(range: AnalysisTimeRange)
    suspend fun setDefaultBeanProfile(beanId: Long?)
    suspend fun setDefaultGrinderProfile(grinderId: Long?)
    suspend fun setShowLearnInDock(enabled: Boolean)
}

interface SessionRepository {
    fun observeActiveSession(): Flow<BrewSession?>
    suspend fun startSession(method: BrewMethod, practiceBlockId: String? = null): BrewSession
    suspend fun moveToNextStage()
    suspend fun moveToPreviousStage()
    suspend fun finishActiveSession()
    suspend fun discardActiveSession()
}

interface LearningRepository {
    fun observeTracks(): Flow<List<LearningTrack>>
    fun observeLessons(): Flow<List<Lesson>>
    fun observeGlossaryTerms(): Flow<List<GlossaryTerm>>
    fun observeTroubleshootingItems(): Flow<List<TroubleshootingItem>>
}

interface ExperimentRepository {
    fun observePracticeBlocks(): Flow<List<PracticeBlock>>
    fun observeRecipeVersions(): Flow<List<RecipeVersion>>
    fun observeExperiments(): Flow<List<Experiment>>
    fun observeExperimentRuns(): Flow<List<ExperimentRun>>
    fun observeBeanInventory(): Flow<List<BeanInventory>>
}

interface EntitlementRepository {
    fun observeEntitlements(): Flow<UserEntitlements>
}

interface ShareRepository {
    fun observeShareCards(): Flow<List<ShareCard>>
}

interface BackupRepository {
    suspend fun exportBackup(): FileExportPayload
    suspend fun restoreBackup(json: String): RestoreOutcome
    suspend fun exportRecordsCsv(filter: AnalysisFilter): FileExportPayload
}
