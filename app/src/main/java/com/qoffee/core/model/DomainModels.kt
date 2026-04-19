package com.qoffee.core.model

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
    val grinderProfileId: Long? = null,
    val grinderNameSnapshot: String? = null,
    val grindSetting: Double? = null,
    val coffeeDoseG: Double? = null,
    val brewWaterMl: Double? = null,
    val bypassWaterMl: Double? = null,
    val waterTempC: Double? = null,
    val notes: String = "",
    val brewedAt: Long = 0L,
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
    val brewMethod: BrewMethod? = null,
    val beanProfileId: Long? = null,
    val grinderProfileId: Long? = null,
    val grindSetting: Double? = null,
    val coffeeDoseG: Double? = null,
    val brewWaterMl: Double? = null,
    val bypassWaterMl: Double? = null,
    val waterTempC: Double? = null,
    val notes: String = "",
)

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
    val insightCards: List<InsightCard> = emptyList(),
    val methodAverages: List<MethodAverage> = emptyList(),
    val timelinePoints: List<TimelinePoint> = emptyList(),
    val scatterSeries: Map<NumericParameter, List<ScatterPoint>> = emptyMap(),
    val dimensionAverages: List<SubjectiveDimensionAverage> = emptyList(),
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
