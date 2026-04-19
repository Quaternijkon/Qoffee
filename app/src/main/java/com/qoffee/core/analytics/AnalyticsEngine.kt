package com.qoffee.core.analytics

import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalyticsDashboard
import com.qoffee.core.model.AnalyticsSummary
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.BeanComparisonInsight
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.InsightCard
import com.qoffee.core.model.InsightConfidence
import com.qoffee.core.model.MethodAverage
import com.qoffee.core.model.MethodComparisonInsight
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.OutlierInsight
import com.qoffee.core.model.RangeInsight
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.ScatterPoint
import com.qoffee.core.model.SubjectiveDimensionAverage
import com.qoffee.core.model.SuggestedNextStep
import com.qoffee.core.model.TimelinePoint
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

class AnalyticsEngine @Inject constructor(
    private val timeProvider: TimeProvider,
) {
    private val scoreFormat = DecimalFormat("0.0")
    private val correlationFormat = DecimalFormat("0.00")
    private val dateFormat = SimpleDateFormat("M/d", Locale.CHINA)

    fun buildDashboard(
        records: List<CoffeeRecord>,
        filter: AnalysisFilter,
    ): AnalyticsDashboard {
        val qualified = records
            .filter { it.status == RecordStatus.COMPLETED && it.subjectiveEvaluation?.overall != null }
            .filter { filter.matches(it, timeProvider.nowMillis()) }

        if (qualified.isEmpty()) {
            return AnalyticsDashboard(filter = filter)
        }

        val summary = AnalyticsSummary(
            sampleCount = qualified.size,
            beanCount = qualified.mapNotNull { it.beanNameSnapshot }.distinct().size,
            grinderCount = qualified.mapNotNull { it.grinderNameSnapshot }.distinct().size,
            methodCount = qualified.mapNotNull { it.brewMethod }.distinct().size,
            firstRecordAt = qualified.minOfOrNull { it.brewedAt },
            lastRecordAt = qualified.maxOfOrNull { it.brewedAt },
        )

        val methodAverages = qualified
            .groupBy { it.brewMethod }
            .mapNotNull { (method, group) ->
                val validMethod = method ?: return@mapNotNull null
                MethodAverage(
                    brewMethod = validMethod,
                    averageScore = group.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }.average(),
                    sampleCount = group.size,
                )
            }
            .sortedByDescending { it.averageScore }

        val timeline = qualified
            .sortedBy { it.brewedAt }
            .mapNotNull { record ->
                val overall = record.subjectiveEvaluation?.overall ?: return@mapNotNull null
                TimelinePoint(
                    timestampMillis = record.brewedAt,
                    label = dateFormat.format(Date(record.brewedAt)),
                    score = overall.toDouble(),
                )
            }

        val dimensionAverages = listOf(
            "香气" to qualified.mapNotNull { it.subjectiveEvaluation?.aroma?.toDouble() },
            "酸质" to qualified.mapNotNull { it.subjectiveEvaluation?.acidity?.toDouble() },
            "甜感" to qualified.mapNotNull { it.subjectiveEvaluation?.sweetness?.toDouble() },
            "苦感" to qualified.mapNotNull { it.subjectiveEvaluation?.bitterness?.toDouble() },
            "醇厚" to qualified.mapNotNull { it.subjectiveEvaluation?.body?.toDouble() },
            "余韵" to qualified.mapNotNull { it.subjectiveEvaluation?.aftertaste?.toDouble() },
        ).mapNotNull { (label, values) ->
            values.takeIf { it.isNotEmpty() }?.let {
                SubjectiveDimensionAverage(label = label, average = it.average())
            }
        }

        val scatterSeries = NumericParameter.entries.associateWith { parameter ->
            qualified.mapNotNull { record ->
                val x = parameter.extract(record) ?: return@mapNotNull null
                val y = record.subjectiveEvaluation?.overall?.toDouble() ?: return@mapNotNull null
                ScatterPoint(
                    x = x,
                    y = y,
                    label = record.brewMethod?.displayName ?: "未命名记录",
                )
            }
        }

        val rangeInsights = NumericParameter.entries.mapNotNull { parameter ->
            buildRangeInsight(parameter, qualified, buildFilterContext(filter))
        }

        val methodComparisonInsights = buildMethodComparisonInsights(methodAverages, buildFilterContext(filter))
        val beanComparisonInsights = buildBeanComparisonInsights(qualified, buildFilterContext(filter))
        val outlierInsights = buildOutlierInsights(qualified)
        val suggestedNextSteps = buildNextSteps(rangeInsights, methodComparisonInsights, outlierInsights)
        val insightCards = buildInsightCards(rangeInsights, methodComparisonInsights, beanComparisonInsights, outlierInsights, buildFilterContext(filter))

        return AnalyticsDashboard(
            filter = filter,
            summary = summary,
            sampleCount = qualified.size,
            insightCards = insightCards,
            methodAverages = methodAverages,
            timelinePoints = timeline,
            scatterSeries = scatterSeries,
            dimensionAverages = dimensionAverages,
            rangeInsights = rangeInsights,
            methodComparisonInsights = methodComparisonInsights,
            beanComparisonInsights = beanComparisonInsights,
            outlierInsights = outlierInsights,
            suggestedNextSteps = suggestedNextSteps,
        )
    }

    private fun buildMethodComparisonInsights(
        methodAverages: List<MethodAverage>,
        filterContext: String,
    ): List<MethodComparisonInsight> {
        return methodAverages
            .filter { it.sampleCount >= 3 }
            .take(2)
            .map { average ->
                MethodComparisonInsight(
                    method = average.brewMethod,
                    message = "在 $filterContext 条件下，${average.brewMethod.displayName} 的平均总体评分为 ${scoreFormat.format(average.averageScore)}。",
                    sampleCount = average.sampleCount,
                    confidence = confidenceFrom(average.sampleCount, average.averageScore / 10.0),
                )
            }
    }

    private fun buildBeanComparisonInsights(
        records: List<CoffeeRecord>,
        filterContext: String,
    ): List<BeanComparisonInsight> {
        return records
            .groupBy { it.beanNameSnapshot }
            .mapNotNull { (beanName, group) ->
                val validName = beanName ?: return@mapNotNull null
                if (group.size < 3) return@mapNotNull null
                val avg = group.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }.average()
                BeanComparisonInsight(
                    beanName = validName,
                    message = "在 $filterContext 条件下，$validName 的平均总体评分为 ${scoreFormat.format(avg)}。",
                    sampleCount = group.size,
                    confidence = confidenceFrom(group.size, avg / 10.0),
                )
            }
            .sortedByDescending { it.sampleCount }
            .take(2)
    }

    private fun buildRangeInsight(
        parameter: NumericParameter,
        records: List<CoffeeRecord>,
        filterContext: String,
    ): RangeInsight? {
        val sorted = records.mapNotNull { record ->
            val x = parameter.extract(record) ?: return@mapNotNull null
            val y = record.subjectiveEvaluation?.overall?.toDouble() ?: return@mapNotNull null
            x to y
        }.sortedBy { it.first }

        if (sorted.size < 9) return null
        val bucketSize = sorted.size / 3
        if (bucketSize < 3) return null

        val low = sorted.take(bucketSize)
        val high = sorted.takeLast(bucketSize)
        val lowAvg = low.map { it.second }.average()
        val highAvg = high.map { it.second }.average()
        val delta = highAvg - lowAvg
        if (abs(delta) < 0.8) return null

        val betterRange = if (delta > 0) high else low
        val min = scoreFormat.format(betterRange.minOf { it.first })
        val max = scoreFormat.format(betterRange.maxOf { it.first })
        return RangeInsight(
            parameter = parameter,
            message = "在 $filterContext 下，${parameter.displayName}位于 ${min}-${max}${parameter.unitLabel} 的样本更常出现更高评分。",
            sampleCount = betterRange.size,
            confidence = confidenceFrom(low.size + high.size, abs(delta) / 2.0),
        )
    }

    private fun buildOutlierInsights(records: List<CoffeeRecord>): List<OutlierInsight> {
        val sorted = records.sortedByDescending { it.subjectiveEvaluation?.overall ?: 0 }
        val highest = sorted.firstOrNull()
        val lowest = sorted.lastOrNull()
        return buildList {
            highest?.subjectiveEvaluation?.overall?.let { score ->
                add(
                    OutlierInsight(
                        recordId = highest.id,
                        title = "高分样本",
                        message = "这条记录是当前筛选下的高分样本，适合拿来反向查看参数组合。",
                        score = score,
                    ),
                )
            }
            lowest?.subjectiveEvaluation?.overall?.let { score ->
                add(
                    OutlierInsight(
                        recordId = lowest.id,
                        title = "低分样本",
                        message = "这条记录是当前筛选下的低分样本，适合排查参数偏离区间的原因。",
                        score = score,
                    ),
                )
            }
        }
    }

    private fun buildNextSteps(
        rangeInsights: List<RangeInsight>,
        methodComparisonInsights: List<MethodComparisonInsight>,
        outlierInsights: List<OutlierInsight>,
    ): List<SuggestedNextStep> {
        return buildList {
            rangeInsights.firstOrNull()?.let { insight ->
                add(
                    SuggestedNextStep(
                        title = "优先验证参数区间",
                        message = insight.message,
                    ),
                )
            }
            methodComparisonInsights.firstOrNull()?.let { insight ->
                add(
                    SuggestedNextStep(
                        title = "优先复做表现最好的制作方式",
                        message = insight.message,
                    ),
                )
            }
            outlierInsights.firstOrNull()?.let { insight ->
                add(
                    SuggestedNextStep(
                        title = "回看离群样本",
                        message = "${insight.title}这条记录值得重点复盘。",
                    ),
                )
            }
        }
    }

    private fun buildInsightCards(
        rangeInsights: List<RangeInsight>,
        methodComparisonInsights: List<MethodComparisonInsight>,
        beanComparisonInsights: List<BeanComparisonInsight>,
        outlierInsights: List<OutlierInsight>,
        filterContext: String,
    ): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()
        cards += rangeInsights.map {
            InsightCard(
                title = "${it.parameter.displayName}区间表现",
                message = it.message,
                sampleCount = it.sampleCount,
                parameterType = it.parameter.name.lowercase(),
                confidence = it.confidence,
                filterContext = filterContext,
            )
        }
        cards += methodComparisonInsights.map {
            InsightCard(
                title = "制作方式对比",
                message = it.message,
                sampleCount = it.sampleCount,
                parameterType = "method_comparison",
                confidence = it.confidence,
                filterContext = filterContext,
            )
        }
        cards += beanComparisonInsights.map {
            InsightCard(
                title = "豆子对比",
                message = it.message,
                sampleCount = it.sampleCount,
                parameterType = "bean_comparison",
                confidence = it.confidence,
                filterContext = filterContext,
            )
        }
        cards += outlierInsights.map {
            InsightCard(
                title = it.title,
                message = it.message,
                sampleCount = 1,
                parameterType = "outlier",
                confidence = InsightConfidence.MEDIUM,
                filterContext = filterContext,
            )
        }
        return cards.take(6)
    }

    private fun buildFilterContext(filter: AnalysisFilter): String {
        val parts = buildList {
            add(filter.timeRange.displayName)
            filter.brewMethod?.let { add(it.displayName) }
            filter.roastLevel?.let { add(it.displayName) }
            filter.processMethod?.let { add(it.displayName) }
        }
        return parts.joinToString(" / ")
    }

    private fun confidenceFrom(sampleSize: Int, effect: Double): InsightConfidence {
        return when {
            sampleSize >= 12 && effect >= 0.65 -> InsightConfidence.HIGH
            sampleSize >= 8 && effect >= 0.45 -> InsightConfidence.MEDIUM
            else -> InsightConfidence.LOW
        }
    }

    private fun NumericParameter.extract(record: CoffeeRecord): Double? {
        return when (this) {
            NumericParameter.WATER_TEMP -> record.waterTempC
            NumericParameter.BREW_RATIO -> record.brewRatio
            NumericParameter.TOTAL_WATER -> record.totalWaterMl
            NumericParameter.BYPASS_WATER -> record.bypassWaterMl
            NumericParameter.GRIND_SETTING -> record.grindSetting
        }
    }

    private fun spearman(xs: List<Double>, ys: List<Double>): Double {
        if (xs.size != ys.size || xs.size < 2) return 0.0
        val rankedX = xs.toRanks()
        val rankedY = ys.toRanks()
        val meanX = rankedX.average()
        val meanY = rankedY.average()
        var numerator = 0.0
        var denominatorX = 0.0
        var denominatorY = 0.0
        rankedX.indices.forEach { index ->
            val x = rankedX[index] - meanX
            val y = rankedY[index] - meanY
            numerator += x * y
            denominatorX += x * x
            denominatorY += y * y
        }
        if (denominatorX == 0.0 || denominatorY == 0.0) return 0.0
        return numerator / kotlin.math.sqrt(denominatorX * denominatorY)
    }

    private fun List<Double>.toRanks(): List<Double> {
        val indexed = mapIndexed { index, value -> index to value }.sortedBy { it.second }
        val ranks = MutableList(size) { 0.0 }
        var cursor = 0
        while (cursor < indexed.size) {
            val start = cursor
            val value = indexed[cursor].second
            while (cursor < indexed.size && indexed[cursor].second == value) {
                cursor++
            }
            val rank = (start + cursor + 1).toDouble() / 2.0
            for (i in start until cursor) {
                ranks[indexed[i].first] = rank
            }
        }
        return ranks
    }
}
