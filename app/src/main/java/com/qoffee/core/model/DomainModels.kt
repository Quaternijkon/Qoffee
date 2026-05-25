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
    BREW_TIME("冲煮时长", "s"),
    WATER_TEMP("水温", "°C"),
    BREW_RATIO("粉水比", ""),
    TOTAL_WATER("总水量", "ml"),
    BYPASS_WATER("旁路水量", "ml"),
    GRIND_SETTING("研磨格数", ""),
    NORMALIZED_GRIND("归一化研磨度", ""),
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
    val normalization: GrindNormalizationProfile? = null,
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

enum class SubjectiveDimension(val displayName: String) {
    AROMA("香气"),
    ACIDITY("酸质"),
    SWEETNESS("甜感"),
    BITTERNESS("苦感"),
    BODY("醇厚"),
    AFTERTASTE("余韵"),
    OVERALL("总评"),
    ;

    fun extract(evaluation: SubjectiveEvaluation?): Int? {
        return when (this) {
            AROMA -> evaluation?.aroma
            ACIDITY -> evaluation?.acidity
            SWEETNESS -> evaluation?.sweetness
            BITTERNESS -> evaluation?.bitterness
            BODY -> evaluation?.body
            AFTERTASTE -> evaluation?.aftertaste
            OVERALL -> evaluation?.overall
        }
    }
}

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
    val normalizedGrindSetting: Double?
        get() = grindSetting?.let { raw ->
            grinderProfile?.normalization?.normalize(raw)
        }
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

enum class SampleQuality(
    val displayName: String,
    val description: String,
) {
    INSUFFICIENT("样本不足", "少于 5 条带评分记录，只能给出数据补全建议。"),
    EXPLORATORY("探索样本", "5-11 条带评分记录，可看描述统计，不下强结论。"),
    TESTABLE("可检验样本", "12-19 条带评分记录，可做参数与分组检验，但需标注样本有限。"),
    ROBUST("稳健样本", "20 条及以上带评分记录，可输出更明确的个人记录内结论。"),
}

enum class SignificanceLevel(val displayName: String) {
    NOT_TESTED("未检验"),
    NOT_SIGNIFICANT("未显著"),
    P_0_05("p < 0.05"),
    P_0_01("p < 0.01"),
}

data class StatisticalEvidence(
    val sampleCount: Int,
    val referenceSampleCount: Int? = null,
    val effectSize: Double? = null,
    val confidenceLow: Double? = null,
    val confidenceHigh: Double? = null,
    val pValue: Double? = null,
    val significance: SignificanceLevel = SignificanceLevel.NOT_TESTED,
    val method: String,
    val limitation: String,
)

data class StatisticalFinding(
    val title: String,
    val summary: String,
    val evidence: StatisticalEvidence,
    val action: String? = null,
)

data class ReportExecutiveSummary(
    val headline: String = "样本不足，暂不生成专业结论。",
    val supportingText: String = "继续补齐完成记录和总评分后，Qoffee 会形成冲煮质量报告。",
    val keyFindings: List<StatisticalFinding> = emptyList(),
    val nextActions: List<String> = emptyList(),
)

data class ScoreDistribution(
    val sampleCount: Int = 0,
    val mean: Double? = null,
    val median: Double? = null,
    val standardDeviation: Double? = null,
    val interquartileRange: Double? = null,
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val recentAverage: Double? = null,
    val firstRecordAt: Long? = null,
    val lastRecordAt: Long? = null,
)

data class SensoryDimensionReport(
    val label: String,
    val average: Double,
    val standardDeviation: Double,
    val sampleCount: Int,
)

data class DataQualityReport(
    val totalRecordCount: Int = 0,
    val completedRecordCount: Int = 0,
    val scoredRecordCount: Int = 0,
    val missingScoreCount: Int = 0,
    val missingParameterCounts: Map<NumericParameter, Int> = emptyMap(),
    val testableParameterCount: Int = 0,
    val sampleQuality: SampleQuality = SampleQuality.INSUFFICIENT,
    val notes: List<String> = emptyList(),
)

data class ParameterWindowFinding(
    val parameter: NumericParameter,
    val preferredRangeLabel: String,
    val comparisonLabel: String,
    val finding: StatisticalFinding,
)

data class SegmentComparisonFinding(
    val segmentType: String,
    val segmentName: String,
    val finding: StatisticalFinding,
)

data class OutlierReportItem(
    val recordId: Long,
    val title: String,
    val reason: String,
    val score: Int,
    val brewedAt: Long,
    val subtitle: String,
)

data class ReportSourceRecord(
    val recordId: Long,
    val label: String,
    val brewedAt: Long,
    val score: Int,
    val brewMethodLabel: String,
    val parameterSummary: String,
)

data class QualityFormPoint(
    val brewedAt: Long,
    val score: Double,
    val shortTermQuality: Double,
    val longTermQuality: Double,
    val formDelta: Double,
    val shortTermLoad: Double,
    val longTermLoad: Double,
    val loadDelta: Double,
)

data class QualityFormSummary(
    val shortTermQuality: Double? = null,
    val longTermQuality: Double? = null,
    val formDelta: Double? = null,
    val shortTermLoad: Double? = null,
    val longTermLoad: Double? = null,
    val loadDelta: Double? = null,
    val points: List<QualityFormPoint> = emptyList(),
)

data class QualityCurvePoint(
    val windowSize: Int,
    val label: String,
    val bestAverageScore: Double,
    val startAt: Long,
    val endAt: Long,
    val recordIds: List<Long>,
)

data class ParameterZone(
    val label: String,
    val minValue: Double,
    val maxValue: Double,
    val sampleCount: Int,
    val averageScore: Double,
    val standardDeviation: Double,
    val bestScore: Int,
)

data class ParameterZoneReport(
    val parameter: NumericParameter,
    val zones: List<ParameterZone>,
    val bestZoneLabel: String?,
    val insight: String,
)

data class PeriodComparisonReport(
    val currentLabel: String,
    val previousLabel: String,
    val currentSampleCount: Int,
    val previousSampleCount: Int,
    val currentAverageScore: Double?,
    val previousAverageScore: Double?,
    val averageScoreDelta: Double?,
    val currentConsistency: Double?,
    val previousConsistency: Double?,
    val consistencyDelta: Double?,
    val finding: String,
)

data class TastingScoreBucket(
    val label: String,
    val score: Int,
    val count: Int,
    val share: Double,
)

data class TastingMatrixCell(
    val groupType: String,
    val groupName: String,
    val dimensionLabel: String,
    val averageScore: Double,
    val sampleCount: Int,
)

data class TastingBalancePoint(
    val recordId: Long,
    val label: String,
    val brewedAt: Long,
    val acidity: Double,
    val sweetness: Double,
    val bitterness: Double? = null,
    val body: Double? = null,
    val overall: Int,
)

data class TastingParameterResponseCell(
    val parameter: NumericParameter,
    val zoneLabel: String,
    val minValue: Double,
    val maxValue: Double,
    val sampleCount: Int,
    val averageOverall: Double,
    val averageSweetness: Double? = null,
    val averageAcidity: Double? = null,
)

data class TastingConcentrationPoint(
    val sampleShare: Double,
    val cumulativeScoreShare: Double,
)

data class TastingDimensionSignal(
    val dimensionLabel: String,
    val finding: StatisticalFinding,
)

data class ObjectiveParameterAverage(
    val label: String,
    val value: Double,
    val unitLabel: String,
    val sampleCount: Int,
)

data class SweetPointReport(
    val sampleCount: Int = 0,
    val targetDescription: String = "甜感高分窗口样本不足。",
    val averageOverall: Double? = null,
    val averageSweetness: Double? = null,
    val parameterAverages: List<ObjectiveParameterAverage> = emptyList(),
    val sourceRecords: List<ReportSourceRecord> = emptyList(),
    val insight: String = "至少需要 3 条甜感 4 分及以上且有总评的记录，才能总结 Sweet Point。",
)

data class BeanConsumptionBucket(
    val beanName: String,
    val doseG: Double,
    val recordCount: Int,
    val share: Double,
)

data class HourlyConsumptionCell(
    val hour: Int,
    val recordCount: Int,
    val doseG: Double,
    val caffeineMg: Double,
    val intensity: Double,
)

data class WeekdayTimeConsumptionCell(
    val weekday: Int,
    val weekdayLabel: String,
    val timeBand: Int,
    val timeBandLabel: String,
    val recordCount: Int,
    val doseG: Double,
    val caffeineMg: Double,
    val intensity: Double,
)

data class ConsumptionChartReport(
    val recordCount: Int = 0,
    val totalDoseG: Double = 0.0,
    val estimatedCaffeineMg: Double = 0.0,
    val averageDoseG: Double? = null,
    val averageCaffeineMg: Double? = null,
    val peakHourLabel: String? = null,
    val dominantBeanName: String? = null,
    val beanBuckets: List<BeanConsumptionBucket> = emptyList(),
    val hourlySpectrum: List<HourlyConsumptionCell> = emptyList(),
    val weekdayHeatmap: List<WeekdayTimeConsumptionCell> = emptyList(),
    val insight: String = "暂无足够粉量记录生成消耗图谱。",
)

data class TastingChartReport(
    val scoredSampleCount: Int = 0,
    val dimensionSampleCount: Int = 0,
    val scoreBuckets: List<TastingScoreBucket> = emptyList(),
    val dimensionMatrix: List<TastingMatrixCell> = emptyList(),
    val balancePoints: List<TastingBalancePoint> = emptyList(),
    val parameterResponse: List<TastingParameterResponseCell> = emptyList(),
    val concentrationCurve: List<TastingConcentrationPoint> = emptyList(),
    val dimensionSignals: List<TastingDimensionSignal> = emptyList(),
    val insight: String = "暂无足够品鉴样本形成图谱分析。",
)

data class AnalyticsReport(
    val generatedAt: Long,
    val filter: AnalysisFilter,
    val filterContext: String,
    val sampleQuality: SampleQuality = SampleQuality.INSUFFICIENT,
    val summary: AnalyticsSummary = AnalyticsSummary(),
    val dataQuality: DataQualityReport = DataQualityReport(),
    val executiveSummary: ReportExecutiveSummary = ReportExecutiveSummary(),
    val scoreDistribution: ScoreDistribution = ScoreDistribution(),
    val trendFinding: StatisticalFinding? = null,
    val sensoryProfile: List<SensoryDimensionReport> = emptyList(),
    val parameterFindings: List<ParameterWindowFinding> = emptyList(),
    val segmentFindings: List<SegmentComparisonFinding> = emptyList(),
    val outliers: List<OutlierReportItem> = emptyList(),
    val sourceRecords: List<ReportSourceRecord> = emptyList(),
    val qualityForm: QualityFormSummary = QualityFormSummary(),
    val qualityCurve: List<QualityCurvePoint> = emptyList(),
    val parameterZones: List<ParameterZoneReport> = emptyList(),
    val periodComparison: PeriodComparisonReport? = null,
    val tastingCharts: TastingChartReport = TastingChartReport(),
    val sweetPoint: SweetPointReport = SweetPointReport(),
    val consumptionCharts: ConsumptionChartReport = ConsumptionChartReport(),
)

enum class AppThemeStyle(val displayName: String, val description: String) {
    CLASSIC("古典", "保留 Qoffee 原有咖啡色、纸感层次和温暖材质。"),
    MINIMAL("简约", "以 Google 四元色作为功能主色，界面更扁平、更克制。"),
}

enum class ServerEnvironment(
    val displayName: String,
    val description: String,
    val endpointLabel: String,
    val backendLabel: String,
) {
    TEST(
        displayName = "测试",
        description = "阿里云 IP 直连，用于备案完成前的小范围验证。",
        endpointLabel = "114.55.247.31",
        backendLabel = "阿里云测试",
    ),
    PRODUCTION(
        displayName = "正式",
        description = "正式 HTTPS 域名入口，备案和证书就绪后使用。",
        endpointLabel = "qoffee-api.quaternijkon.online",
        backendLabel = "正式环境",
    ),
}

data class UserSettings(
    val autoRestoreDraft: Boolean = true,
    val showInsightConfidence: Boolean = true,
    val defaultAnalysisTimeRange: AnalysisTimeRange = AnalysisTimeRange.LAST_90_DAYS,
    val defaultBeanProfileId: Long? = null,
    val defaultGrinderProfileId: Long? = null,
    val showLearnInDock: Boolean = false,
    val themeStyle: AppThemeStyle = AppThemeStyle.CLASSIC,
    val serverEnvironment: ServerEnvironment = ServerEnvironment.TEST,
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
    val sourceGuideId: Long? = null,
    val startedAt: Long,
    val currentStageIndex: Int = 0,
    val currentStageStartedAt: Long = startedAt,
    val stages: List<BrewStage> = emptyList(),
    val isPaused: Boolean = false,
    val pausedAt: Long? = null,
    val accumulatedPauseMillis: Long = 0L,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
) {
    val currentStage: BrewStage? get() = stages.getOrNull(currentStageIndex)
    val progressFraction: Float
        get() = if (stages.isEmpty()) 0f else (currentStageIndex + 1).coerceAtMost(stages.size) / stages.size.toFloat()

    fun activeElapsedMillis(nowMillis: Long): Long {
        val end = if (isCompleted) completedAt ?: nowMillis else nowMillis
        val livePause = if (isPaused) {
            (end - (pausedAt ?: end)).coerceAtLeast(0L)
        } else {
            0L
        }
        return (end - startedAt - accumulatedPauseMillis - livePause).coerceAtLeast(0L)
    }

    fun stageElapsedMillis(nowMillis: Long): Long {
        return when {
            isCompleted -> currentStage?.targetDurationSeconds?.times(1_000L) ?: 0L
            isPaused -> {
                val safePausedAt = pausedAt ?: nowMillis
                (safePausedAt - currentStageStartedAt).coerceAtLeast(0L)
            }
            else -> (nowMillis - currentStageStartedAt).coerceAtLeast(0L)
        }
    }
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
    val sourceType: String = "",
    val importHint: String? = null,
    val isRevocable: Boolean = true,
)

data class UserEntitlements(
    val tier: EntitlementTier = EntitlementTier.FREE,
    val unlockedFeatures: List<String> = emptyList(),
    val proHighlights: List<String> = emptyList(),
)

enum class SyncPhase(val displayName: String) {
    SIGNED_OUT("未登录"),
    IDLE("已就绪"),
    PUSHING("正在上传"),
    PULLING("正在拉取"),
    SNAPSHOTTING("正在生成快照"),
    ERROR("需要处理"),
}

data class SyncAccount(
    val email: String,
    val signedInAt: Long,
)

data class CloudSnapshotSummary(
    val id: String,
    val createdAt: Long,
    val checksum: String,
    val byteSize: Long,
)

enum class SyncConflictResolution {
    KEEP_REMOTE,
    KEEP_LOCAL,
}

data class SyncConflict(
    val id: String,
    val entityType: String,
    val entityId: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val summary: String,
)

data class SyncState(
    val account: SyncAccount? = null,
    val phase: SyncPhase = SyncPhase.SIGNED_OUT,
    val backendLabel: String = "阿里云内测",
    val archiveScope: String = "单设备内测档案",
    val lastPushedAt: Long? = null,
    val lastPulledAt: Long? = null,
    val lastSnapshot: CloudSnapshotSummary? = null,
    val pendingConflicts: List<SyncConflict> = emptyList(),
    val lastMessage: String? = null,
) {
    val isSignedIn: Boolean get() = account != null
    val isBusy: Boolean
        get() = phase == SyncPhase.PUSHING ||
            phase == SyncPhase.PULLING ||
            phase == SyncPhase.SNAPSHOTTING
}

data class SyncOperationResult(
    val success: Boolean,
    val message: String,
)

sealed interface AiCoachAction {
    data class OpenRecord(val recordId: Long) : AiCoachAction
    data class DuplicateRecord(val recordId: Long) : AiCoachAction
    data object OpenAnalysis : AiCoachAction
}

data class AiCoachSourceRecord(
    val recordId: Long,
    val label: String,
    val score: Int? = null,
)

data class AiCoachSuggestion(
    val id: String,
    val title: String,
    val message: String,
    val actionLabel: String,
    val action: AiCoachAction,
    val sourceRecords: List<AiCoachSourceRecord>,
    val confidenceLabel: String = "本地规则",
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
