package com.qoffee.core.model

import java.util.Locale

enum class ArchiveType(
    val code: String,
    val displayName: String,
) {
    NORMAL("normal", "普通存档"),
    DEMO("demo", "示范存档"),
    ;

    companion object {
        fun fromCode(code: String?): ArchiveType = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

data class Archive(
    val id: Long = 0L,
    val name: String,
    val type: ArchiveType,
    val isReadOnly: Boolean,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sortOrder: Int = 0,
)

data class ArchiveSummary(
    val archive: Archive,
    val beanCount: Int = 0,
    val grinderCount: Int = 0,
    val recordCount: Int = 0,
    val lastRecordAt: Long? = null,
) {
    val isDemo: Boolean get() = archive.type == ArchiveType.DEMO
}

data class ArchiveSeedStatus(
    val hasSeededDemoArchive: Boolean = false,
    val demoArchiveId: Long? = null,
    val currentArchiveId: Long? = null,
)

enum class BrewMethod(
    val code: String,
    val displayName: String,
    val isHotBrew: Boolean,
) {
    ESPRESSO_MACHINE("espresso_machine", "意式咖啡机", true),
    MOKA_POT("moka_pot", "摩卡壶", true),
    POUR_OVER("pour_over", "手冲", true),
    CLEVER_DRIPPER("clever_dripper", "聪明杯", true),
    AEROPRESS("aeropress", "爱乐压", true),
    COLD_BREW("cold_brew", "冷萃", false),
    ;

    companion object {
        fun fromCode(code: String?): BrewMethod? = entries.firstOrNull { it.code == code }
    }
}

enum class RoastLevel(
    val storageValue: Int,
    val displayName: String,
    val shortLabel: String,
) {
    EXTREME_LIGHT(0, "极浅", "极浅"),
    LIGHT(1, "浅", "浅"),
    LIGHT_MEDIUM(2, "浅中", "浅中"),
    MEDIUM(3, "中", "中"),
    MEDIUM_DARK(4, "中深", "中深"),
    DARK(5, "深", "深"),
    EXTREME_DARK(6, "极深", "极深"),
    ;

    companion object {
        fun fromStorageValue(value: Int?): RoastLevel = entries.firstOrNull { it.storageValue == value } ?: MEDIUM
    }
}

enum class BeanProcessMethod(
    val storageValue: Int,
    val displayName: String,
) {
    NATURAL(0, "日晒"),
    WASHED(1, "水洗"),
    HONEY(2, "蜜处理"),
    ;

    companion object {
        fun fromStorageValue(value: Int?): BeanProcessMethod = entries.firstOrNull { it.storageValue == value } ?: WASHED
    }
}

enum class RecordStatus(val code: String) {
    DRAFT("draft"),
    COMPLETED("completed"),
    ;

    companion object {
        fun fromCode(code: String?): RecordStatus = entries.firstOrNull { it.code == code } ?: DRAFT
    }
}

enum class AnalysisTimeRange(
    val displayName: String,
    val days: Int?,
) {
    LAST_30_DAYS("近 30 天", 30),
    LAST_90_DAYS("近 90 天", 90),
    LAST_YEAR("近 1 年", 365),
    ALL("全部", null),
}

enum class NumericParameter(
    val displayName: String,
    val unitLabel: String,
) {
    BREW_TIME("Brew Time", "s"),
    WATER_TEMP("水温", "°C"),
    BREW_RATIO("粉水比", ""),
    TOTAL_WATER("总水量", "ml"),
    BYPASS_WATER("旁路水量", "ml"),
    GRIND_SETTING("研磨格数", ""),
}

enum class InsightConfidence(val displayName: String) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
}

data class AnalysisFilter(
    val archiveId: Long? = null,
    val timeRange: AnalysisTimeRange = AnalysisTimeRange.LAST_90_DAYS,
    val brewMethod: BrewMethod? = null,
    val beanId: Long? = null,
    val beanNameKey: String? = null,
    val roastLevel: RoastLevel? = null,
    val processMethod: BeanProcessMethod? = null,
    val grinderId: Long? = null,
) {
    fun matches(record: CoffeeRecord, nowMillis: Long): Boolean {
        val rangeMatches = when (val days = timeRange.days) {
            null -> true
            else -> record.brewedAt >= nowMillis - days * 24L * 60L * 60L * 1000L
        }
        return rangeMatches &&
            (archiveId == null || record.archiveId == archiveId) &&
            (brewMethod == null || record.brewMethod == brewMethod) &&
            (beanId == null || record.beanProfileId == beanId) &&
            (beanNameKey == null || normalizedBeanNameKey(record.beanNameSnapshot) == beanNameKey) &&
            (roastLevel == null || record.beanRoastLevelSnapshot == roastLevel) &&
            (processMethod == null || record.beanProcessMethodSnapshot == processMethod) &&
            (grinderId == null || record.grinderProfileId == grinderId)
    }
}

data class BeanProfile(
    val id: Long = 0L,
    val archiveId: Long = 0L,
    val name: String,
    val roaster: String = "",
    val origin: String = "",
    val processMethod: BeanProcessMethod = BeanProcessMethod.WASHED,
    val variety: String = "",
    val roastLevel: RoastLevel,
    val roastDateEpochDay: Long? = null,
    val initialStockG: Double? = null,
    val notes: String = "",
    val createdAt: Long = 0L,
)

data class GrinderProfile(
    val id: Long = 0L,
    val archiveId: Long = 0L,
    val name: String,
    val minSetting: Double,
    val maxSetting: Double,
    val stepSize: Double,
    val unitLabel: String,
    val notes: String = "",
    val createdAt: Long = 0L,
)

data class RecipeTemplate(
    val id: Long = 0L,
    val archiveId: Long = 0L,
    val name: String,
    val brewMethod: BrewMethod? = null,
    val beanProfileId: Long? = null,
    val beanNameSnapshot: String? = null,
    val grinderProfileId: Long? = null,
    val grinderNameSnapshot: String? = null,
    val grindSetting: Double? = null,
    val coffeeDoseG: Double? = null,
    val brewWaterMl: Double? = null,
    val bypassWaterMl: Double? = null,
    val waterTempC: Double? = null,
    val waterCurve: WaterCurve? = null,
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class FlavorTag(
    val id: Long = 0L,
    val archiveId: Long = 0L,
    val name: String,
    val isPreset: Boolean = false,
)

data class SubjectiveEvaluation(
    val recordId: Long = 0L,
    val aroma: Int? = null,
    val acidity: Int? = null,
    val sweetness: Int? = null,
    val bitterness: Int? = null,
    val body: Int? = null,
    val aftertaste: Int? = null,
    val overall: Int? = null,
    val notes: String = "",
    val flavorTags: List<FlavorTag> = emptyList(),
) {
    fun isEmpty(): Boolean {
        return aroma == null &&
            acidity == null &&
            sweetness == null &&
            bitterness == null &&
            body == null &&
            aftertaste == null &&
            overall == null &&
            notes.isBlank() &&
            flavorTags.isEmpty()
    }
}

data class CoffeeRecord(
    val id: Long = 0L,
    val archiveId: Long = 0L,
    val status: RecordStatus = RecordStatus.DRAFT,
    val brewMethod: BrewMethod? = null,
    val beanProfileId: Long? = null,
    val beanNameSnapshot: String? = null,
    val beanRoastLevelSnapshot: RoastLevel? = null,
    val beanProcessMethodSnapshot: BeanProcessMethod? = null,
    val recipeTemplateId: Long? = null,
    val recipeNameSnapshot: String? = null,
    val grinderProfileId: Long? = null,
    val grinderNameSnapshot: String? = null,
    val grindSetting: Double? = null,
    val coffeeDoseG: Double? = null,
    val brewWaterMl: Double? = null,
    val bypassWaterMl: Double? = null,
    val waterTempC: Double? = null,
    val waterCurve: WaterCurve? = null,
    val notes: String = "",
    val brewedAt: Long = 0L,
    val brewDurationSeconds: Int? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val totalWaterMl: Double? = null,
    val brewRatio: Double? = null,
    val beanProfile: BeanProfile? = null,
    val grinderProfile: GrinderProfile? = null,
    val subjectiveEvaluation: SubjectiveEvaluation? = null,
) {
    val hasSubjectiveScore: Boolean get() = subjectiveEvaluation?.overall != null
}

data class ObjectiveDraftUpdate(
    val recipeTemplateId: Long? = null,
    val recipeNameSnapshot: String? = null,
    val brewMethod: BrewMethod? = null,
    val beanProfileId: Long? = null,
    val grinderProfileId: Long? = null,
    val grindSetting: Double? = null,
    val coffeeDoseG: Double? = null,
    val brewWaterMl: Double? = null,
    val bypassWaterMl: Double? = null,
    val waterTempC: Double? = null,
    val waterCurve: WaterCurve? = null,
    val brewedAt: Long? = null,
    val brewDurationSeconds: Int? = null,
    val notes: String = "",
)

sealed interface RecordPrefillSource {
    data object Blank : RecordPrefillSource
    data object Draft : RecordPrefillSource
    data class Recipe(val recipeId: Long) : RecordPrefillSource
    data class Record(val recordId: Long) : RecordPrefillSource
    data class Bean(val beanId: Long) : RecordPrefillSource
}

enum class DraftReplacePolicy {
    KEEP_CURRENT,
    REPLACE_CURRENT,
}

enum class RecordDraftLaunchBehavior {
    CREATE_NEW,
    CONTINUE_CURRENT,
    CONFIRM_REPLACE,
}

fun resolveRecordDraftLaunchBehavior(
    activeDraft: CoffeeRecord?,
    prefillSource: RecordPrefillSource,
): RecordDraftLaunchBehavior {
    if (activeDraft == null) {
        return RecordDraftLaunchBehavior.CREATE_NEW
    }
    return when (prefillSource) {
        RecordPrefillSource.Blank,
        is RecordPrefillSource.Recipe,
        is RecordPrefillSource.Record,
        -> RecordDraftLaunchBehavior.CONFIRM_REPLACE

        RecordPrefillSource.Draft -> RecordDraftLaunchBehavior.CONTINUE_CURRENT

        is RecordPrefillSource.Bean -> {
            if (activeDraft.beanProfileId == prefillSource.beanId) {
                RecordDraftLaunchBehavior.CONTINUE_CURRENT
            } else {
                RecordDraftLaunchBehavior.CONFIRM_REPLACE
            }
        }
    }
}

data class MethodAverage(
    val brewMethod: BrewMethod,
    val averageScore: Double,
    val sampleCount: Int,
)

data class TimelinePoint(
    val timestampMillis: Long,
    val label: String,
    val score: Double,
)

data class ScatterPoint(
    val x: Double,
    val y: Double,
    val label: String,
)

data class SubjectiveDimensionAverage(
    val label: String,
    val average: Double,
)

data class AnalyticsSummary(
    val sampleCount: Int = 0,
    val beanCount: Int = 0,
    val grinderCount: Int = 0,
    val methodCount: Int = 0,
    val firstRecordAt: Long? = null,
    val lastRecordAt: Long? = null,
)

data class RangeInsight(
    val parameter: NumericParameter,
    val message: String,
    val sampleCount: Int,
    val confidence: InsightConfidence,
)

data class MethodComparisonInsight(
    val method: BrewMethod,
    val message: String,
    val sampleCount: Int,
    val confidence: InsightConfidence,
)

data class BeanComparisonInsight(
    val beanName: String,
    val message: String,
    val sampleCount: Int,
    val confidence: InsightConfidence,
)

data class OutlierInsight(
    val recordId: Long,
    val title: String,
    val message: String,
    val score: Int,
)

data class SuggestedNextStep(
    val title: String,
    val message: String,
)

data class ParameterCorrelation(
    val parameter: NumericParameter,
    val coefficient: Double,
    val sampleCount: Int,
    val confidence: InsightConfidence,
)

enum class RecordHighlightKind(val displayName: String) {
    BEST_SCORE("高分样本"),
    LOW_SCORE("低分样本"),
    RECENT("最近样本"),
}

data class RecordHighlight(
    val recordId: Long,
    val title: String,
    val subtitle: String,
    val kind: RecordHighlightKind,
)

data class InsightCard(
    val title: String,
    val message: String,
    val sampleCount: Int,
    val parameterType: String,
    val confidence: InsightConfidence,
    val filterContext: String,
)

data class AnalyticsDashboard(
    val filter: AnalysisFilter,
    val summary: AnalyticsSummary = AnalyticsSummary(),
    val sampleCount: Int = 0,
    val scoreRange: IntRange = 1..5,
    val insightCards: List<InsightCard> = emptyList(),
    val methodAverages: List<MethodAverage> = emptyList(),
    val timelinePoints: List<TimelinePoint> = emptyList(),
    val scatterSeries: Map<NumericParameter, List<ScatterPoint>> = emptyMap(),
    val dimensionAverages: List<SubjectiveDimensionAverage> = emptyList(),
    val parameterCorrelations: List<ParameterCorrelation> = emptyList(),
    val highlightRecords: List<RecordHighlight> = emptyList(),
    val rangeInsights: List<RangeInsight> = emptyList(),
    val methodComparisonInsights: List<MethodComparisonInsight> = emptyList(),
    val beanComparisonInsights: List<BeanComparisonInsight> = emptyList(),
    val outlierInsights: List<OutlierInsight> = emptyList(),
    val suggestedNextSteps: List<SuggestedNextStep> = emptyList(),
) {
    val hasEnoughData: Boolean get() = sampleCount >= 1
}

data class UserSettings(
    val autoRestoreDraft: Boolean = true,
    val showInsightConfidence: Boolean = true,
    val defaultAnalysisTimeRange: AnalysisTimeRange = AnalysisTimeRange.LAST_90_DAYS,
    val defaultBeanProfileId: Long? = null,
    val defaultGrinderProfileId: Long? = null,
    val showLearnInDock: Boolean = false,
)

data class FileExportPayload(
    val fileName: String,
    val mimeType: String,
    val content: String,
)

enum class RestoreStatus {
    SUCCESS,
    VALIDATION_ERROR,
    SYSTEM_ERROR,
}

data class RestoreOutcome(
    val importedArchiveCount: Int = 0,
    val importedArchiveNames: List<String> = emptyList(),
    val switchedArchiveId: Long? = null,
    val message: String = "",
    val status: RestoreStatus = RestoreStatus.SUCCESS,
)

enum class SkillLevel(val displayName: String) {
    BEGINNER("入门"),
    INTERMEDIATE("进阶"),
    ADVANCED("高阶"),
}

enum class LessonType(val displayName: String) {
    FOUNDATIONS("基础"),
    METHOD("方法"),
    DEVICE("设备"),
    TROUBLESHOOTING("故障排查"),
    SENSORY("感官训练"),
}

enum class ExperimentStatus(val displayName: String) {
    PLANNED("计划中"),
    ACTIVE("进行中"),
    REVIEW("复盘中"),
}

enum class EntitlementTier(val displayName: String) {
    FREE("Free"),
    PRO("Pro"),
}

data class BrewStage(
    val id: String,
    val title: String,
    val instruction: String,
    val targetDurationSeconds: Int,
    val targetValueLabel: String = "",
    val tip: String = "",
)

data class BrewSession(
    val id: String,
    val method: BrewMethod,
    val title: String,
    val practiceBlockId: String? = null,
    val startedAt: Long,
    val currentStageIndex: Int = 0,
    val stages: List<BrewStage> = emptyList(),
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
) {
    val currentStage: BrewStage? get() = stages.getOrNull(currentStageIndex)
    val progressFraction: Float
        get() = if (stages.isEmpty()) 0f else (currentStageIndex + 1).coerceAtMost(stages.size) / stages.size.toFloat()
}

data class PracticeBlock(
    val id: String,
    val title: String,
    val description: String,
    val method: BrewMethod? = null,
    val focus: String,
    val sessionTarget: Int,
    val level: SkillLevel,
    val proOnly: Boolean = false,
)

data class RecipeVersion(
    val id: String,
    val baseRecipeId: Long? = null,
    val name: String,
    val summary: String,
    val brewMethod: BrewMethod? = null,
    val sourceRecordId: Long? = null,
    val versionNumber: Int = 1,
    val proOnly: Boolean = false,
)

data class Experiment(
    val id: String,
    val title: String,
    val hypothesis: String,
    val brewMethod: BrewMethod? = null,
    val comparedParameter: NumericParameter? = null,
    val status: ExperimentStatus = ExperimentStatus.PLANNED,
    val practiceBlockId: String? = null,
)

data class ExperimentRun(
    val id: String,
    val experimentId: String,
    val label: String,
    val recordId: Long? = null,
    val notes: String = "",
    val score: Int? = null,
    val deltaSummary: String? = null,
)

data class LearningTrack(
    val id: String,
    val title: String,
    val summary: String,
    val level: SkillLevel,
    val lessonCount: Int,
    val estimatedMinutes: Int,
    val proOnly: Boolean = false,
)

data class Lesson(
    val id: String,
    val trackId: String,
    val title: String,
    val summary: String,
    val level: SkillLevel,
    val type: LessonType,
    val estimatedMinutes: Int,
    val keyPoints: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val glossaryTerms: List<String> = emptyList(),
    val proOnly: Boolean = false,
)

data class GlossaryTerm(
    val id: String,
    val term: String,
    val shortDefinition: String,
    val explanation: String,
    val relatedTerms: List<String> = emptyList(),
)

data class TroubleshootingItem(
    val id: String,
    val symptom: String,
    val likelyCauses: List<String>,
    val adjustments: List<String>,
    val relatedLessonId: String? = null,
)

data class BeanInventory(
    val beanId: Long? = null,
    val beanName: String,
    val roastDateEpochDay: Long? = null,
    val roastAgeLabel: String = "",
    val initialStockG: Double = 0.0,
    val usedStockG: Double = 0.0,
    val remainingStockG: Double = 0.0,
    val remainingRatio: Float = 0f,
    val remainingPercentage: Int = 0,
    val id: String = "",
    val batchLabel: String = "",
    val purchasedAt: Long? = null,
    val openedAt: Long? = null,
    val gramsRemaining: Int? = null,
    val costAmount: Double? = null,
    val currencyCode: String = "CNY",
    val recommendedWindow: String = "",
    val averageWeeklyUsageG: Double? = null,
)

fun normalizedBeanNameKey(name: String?): String? {
    val normalized = name
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
    return normalized
}

fun legacyTenPointScoreToFivePoint(score: Int): Int {
    return ((score + 1) / 2).coerceIn(1, 5)
}

data class ShareCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val badge: String? = null,
)

data class UserEntitlements(
    val tier: EntitlementTier = EntitlementTier.FREE,
    val unlockedFeatures: List<String> = emptyList(),
    val proHighlights: List<String> = emptyList(),
)

data class RecordValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
) {
    companion object {
        fun success() = RecordValidationResult(true)
        fun failure(errors: List<String>) = RecordValidationResult(false, errors)
    }
}
