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
import com.qoffee.core.model.ParameterCorrelation
import com.qoffee.core.model.RangeInsight
import com.qoffee.core.model.RecordHighlight
import com.qoffee.core.model.RecordHighlightKind
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.ScatterPoint
import com.qoffee.core.model.SubjectiveDimensionAverage
import com.qoffee.core.model.SuggestedNextStep
import com.qoffee.core.model.TimelinePoint
import com.qoffee.core.model.normalizedBeanNameKey
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

        val filterContext = buildFilterContext(filter)
        val scoredRecords = qualified.sortedBy { it.brewedAt }
        val overallAverageScore = scoredRecords
            .mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
            .average()

        val summary = AnalyticsSummary(
            sampleCount = scoredRecords.size,
            beanCount = scoredRecords.mapNotNull { normalizedBeanNameKey(it.beanNameSnapshot) }.distinct().size,
            grinderCount = scoredRecords.mapNotNull { it.grinderNameSnapshot?.trim()?.takeIf(String::isNotBlank) }.distinct().size,
            methodCount = scoredRecords.mapNotNull { it.brewMethod }.distinct().size,
            firstRecordAt = scoredRecords.minOfOrNull { it.brewedAt },
            lastRecordAt = scoredRecords.maxOfOrNull { it.brewedAt },
        )

        val methodAverages = buildMethodAverages(scoredRecords)
        val timeline = buildTimeline(scoredRecords)
        val dimensionAverages = buildDimensionAverages(scoredRecords)
        val scatterSeries = NumericParameter.entries.associateWith { parameter ->
            buildScatterSeries(parameter, scoredRecords)
        }
        val parameterCorrelations = buildParameterCorrelations(scoredRecords)
        val rangeInsights = NumericParameter.entries.mapNotNull { parameter ->
            buildRangeInsight(
                parameter = parameter,
                records = scoredRecords,
                filterContext = filterContext,
            )
        }
        val methodComparisonInsights = buildMethodComparisonInsights(
            methodAverages = methodAverages,
            overallAverageScore = overallAverageScore,
            filterContext = filterContext,
        )
        val beanComparisonInsights = buildBeanComparisonInsights(
            records = scoredRecords,
            overallAverageScore = overallAverageScore,
            filterContext = filterContext,
        )
        val outlierInsights = buildOutlierInsights(scoredRecords)
        val highlightRecords = buildHighlightRecords(scoredRecords)
        val suggestedNextSteps = buildNextSteps(
            rangeInsights = rangeInsights,
            parameterCorrelations = parameterCorrelations,
            methodComparisonInsights = methodComparisonInsights,
            outlierInsights = outlierInsights,
        )
        val insightCards = buildInsightCards(
            rangeInsights = rangeInsights,
            parameterCorrelations = parameterCorrelations,
            methodComparisonInsights = methodComparisonInsights,
            beanComparisonInsights = beanComparisonInsights,
            outlierInsights = outlierInsights,
            filterContext = filterContext,
        )

        return AnalyticsDashboard(
            filter = filter,
            summary = summary,
            sampleCount = scoredRecords.size,
            scoreRange = 1..5,
            insightCards = insightCards,
            methodAverages = methodAverages,
            timelinePoints = timeline,
            scatterSeries = scatterSeries,
            dimensionAverages = dimensionAverages,
            parameterCorrelations = parameterCorrelations,
            highlightRecords = highlightRecords,
            rangeInsights = rangeInsights,
            methodComparisonInsights = methodComparisonInsights,
            beanComparisonInsights = beanComparisonInsights,
            outlierInsights = outlierInsights,
            suggestedNextSteps = suggestedNextSteps,
        )
    }

    private fun buildMethodAverages(records: List<CoffeeRecord>): List<MethodAverage> {
        return records
            .groupBy { it.brewMethod }
            .mapNotNull { (method, group) ->
                val safeMethod = method ?: return@mapNotNull null
                MethodAverage(
                    brewMethod = safeMethod,
                    averageScore = group.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }.average(),
                    sampleCount = group.size,
                )
            }
            .sortedByDescending { it.averageScore }
    }

    private fun buildTimeline(records: List<CoffeeRecord>): List<TimelinePoint> {
        return records.mapNotNull { record ->
            val overall = record.subjectiveEvaluation?.overall ?: return@mapNotNull null
            TimelinePoint(
                timestampMillis = record.brewedAt,
                label = dateFormat.format(Date(record.brewedAt)),
                score = overall.toDouble(),
            )
        }
    }

    private fun buildDimensionAverages(records: List<CoffeeRecord>): List<SubjectiveDimensionAverage> {
        return listOf(
            "香气" to records.mapNotNull { it.subjectiveEvaluation?.aroma?.toDouble() },
            "酸质" to records.mapNotNull { it.subjectiveEvaluation?.acidity?.toDouble() },
            "甜感" to records.mapNotNull { it.subjectiveEvaluation?.sweetness?.toDouble() },
            "苦感" to records.mapNotNull { it.subjectiveEvaluation?.bitterness?.toDouble() },
            "醇厚" to records.mapNotNull { it.subjectiveEvaluation?.body?.toDouble() },
            "余韵" to records.mapNotNull { it.subjectiveEvaluation?.aftertaste?.toDouble() },
        ).mapNotNull { (label, values) ->
            values.takeIf { it.isNotEmpty() }?.let {
                SubjectiveDimensionAverage(label = label, average = it.average())
            }
        }
    }

    private fun buildScatterSeries(
        parameter: NumericParameter,
        records: List<CoffeeRecord>,
    ): List<ScatterPoint> {
        return records.mapNotNull { record ->
            val x = parameter.extract(record) ?: return@mapNotNull null
            val y = record.subjectiveEvaluation?.overall?.toDouble() ?: return@mapNotNull null
            ScatterPoint(
                x = x,
                y = y,
                label = record.beanNameSnapshot ?: record.brewMethod?.displayName ?: "未命名记录",
            )
        }
    }

    private fun buildParameterCorrelations(records: List<CoffeeRecord>): List<ParameterCorrelation> {
        return NumericParameter.entries.mapNotNull { parameter ->
            val pairs = records.mapNotNull { record ->
                val x = parameter.extract(record) ?: return@mapNotNull null
                val y = record.subjectiveEvaluation?.overall?.toDouble() ?: return@mapNotNull null
                x to y
            }
            if (pairs.size < 8) {
                return@mapNotNull null
            }
            val coefficient = spearman(
                xs = pairs.map { it.first },
                ys = pairs.map { it.second },
            )
            ParameterCorrelation(
                parameter = parameter,
                coefficient = coefficient,
                sampleCount = pairs.size,
                confidence = confidenceFrom(pairs.size, abs(coefficient)),
            )
        }.sortedByDescending { abs(it.coefficient) }
    }

    private fun buildMethodComparisonInsights(
        methodAverages: List<MethodAverage>,
        overallAverageScore: Double,
        filterContext: String,
    ): List<MethodComparisonInsight> {
        return methodAverages
            .filter { it.sampleCount >= 3 }
            .map { average ->
                val delta = average.averageScore - overallAverageScore
                val direction = if (delta >= 0) "高于" else "低于"
                MethodComparisonInsight(
                    method = average.brewMethod,
                    message = "在 $filterContext 下，${average.brewMethod.displayName} 的均分是 ${scoreFormat.format(average.averageScore)}/5，较总体均分$direction ${scoreFormat.format(abs(delta))} 分。",
                    sampleCount = average.sampleCount,
                    confidence = confidenceFrom(
                        sampleSize = average.sampleCount,
                        effectMagnitude = abs(delta) / 4.0,
                    ),
                )
            }
            .sortedByDescending { it.sampleCount }
            .take(2)
    }

    private fun buildBeanComparisonInsights(
        records: List<CoffeeRecord>,
        overallAverageScore: Double,
        filterContext: String,
    ): List<BeanComparisonInsight> {
        return records
            .groupBy { normalizedBeanNameKey(it.beanNameSnapshot) }
            .mapNotNull { (beanNameKey, group) ->
                val safeKey = beanNameKey ?: return@mapNotNull null
                if (group.size < 3) {
                    return@mapNotNull null
                }
                val displayName = group.firstNotNullOfOrNull { it.beanNameSnapshot?.trim()?.takeIf(String::isNotBlank) } ?: safeKey
                val average = group.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }.average()
                val delta = average - overallAverageScore
                val direction = if (delta >= 0) "高于" else "低于"
                BeanComparisonInsight(
                    beanName = displayName,
                    message = "在 $filterContext 下，$displayName 的均分是 ${scoreFormat.format(average)}/5，较总体均分$direction ${scoreFormat.format(abs(delta))} 分。",
                    sampleCount = group.size,
                    confidence = confidenceFrom(
                        sampleSize = group.size,
                        effectMagnitude = abs(delta) / 4.0,
                    ),
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

        if (sorted.size < 9) {
            return null
        }
        val bucketSize = sorted.size / 3
        if (bucketSize < 3) {
            return null
        }

        val low = sorted.take(bucketSize)
        val high = sorted.takeLast(bucketSize)
        val lowAverage = low.map { it.second }.average()
        val highAverage = high.map { it.second }.average()
        val delta = highAverage - lowAverage
        if (abs(delta) < 0.35) {
            return null
        }

        val preferredRange = if (delta > 0) high else low
        val min = scoreFormat.format(preferredRange.minOf { it.first })
        val max = scoreFormat.format(preferredRange.maxOf { it.first })
        return RangeInsight(
            parameter = parameter,
            message = "在 $filterContext 下，${parameter.displayName} 位于 $min-$max${parameter.unitLabel} 的样本更常出现更高评分。",
            sampleCount = preferredRange.size,
            confidence = confidenceFrom(
                sampleSize = low.size + high.size,
                effectMagnitude = abs(delta) / 4.0,
            ),
        )
    }

    private fun buildOutlierInsights(records: List<CoffeeRecord>): List<OutlierInsight> {
        val sorted = records.sortedByDescending { it.subjectiveEvaluation?.overall ?: 0 }
        val highest = sorted.firstOrNull()
        val lowest = sorted.lastOrNull()?.takeIf { it.id != highest?.id }
        return buildList {
            highest?.subjectiveEvaluation?.overall?.let { score ->
                add(
                    OutlierInsight(
                        recordId = highest.id,
                        title = "高分样本",
                        message = "这条记录是当前筛选下的高分样本，适合反向查看参数组合与主观描述。",
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

    private fun buildHighlightRecords(records: List<CoffeeRecord>): List<RecordHighlight> {
        val highlights = mutableListOf<RecordHighlight>()
        val best = records.maxWithOrNull(
            compareBy<CoffeeRecord> { it.subjectiveEvaluation?.overall ?: Int.MIN_VALUE }
                .thenBy { it.brewedAt },
        )
        val lowest = records.minWithOrNull(
            compareBy<CoffeeRecord> { it.subjectiveEvaluation?.overall ?: Int.MAX_VALUE }
                .thenByDescending { it.brewedAt },
        )?.takeIf { it.id != best?.id }
        val recent = records.maxByOrNull { it.brewedAt }
            ?.takeIf { candidate -> candidate.id != best?.id && candidate.id != lowest?.id }

        best?.let { record ->
            highlights += record.toHighlight(
                title = "当前最稳的高分样本",
                kind = RecordHighlightKind.BEST_SCORE,
            )
        }
        lowest?.let { record ->
            highlights += record.toHighlight(
                title = "最值得复盘的低分样本",
                kind = RecordHighlightKind.LOW_SCORE,
            )
        }
        recent?.let { record ->
            highlights += record.toHighlight(
                title = "最近一次带评分记录",
                kind = RecordHighlightKind.RECENT,
            )
        }
        return highlights
    }

    private fun buildNextSteps(
        rangeInsights: List<RangeInsight>,
        parameterCorrelations: List<ParameterCorrelation>,
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
            parameterCorrelations.firstOrNull()?.let { correlation ->
                val direction = if (correlation.coefficient >= 0) "同向变化" else "反向变化"
                add(
                    SuggestedNextStep(
                        title = "先盯住最敏感的变量",
                        message = "${correlation.parameter.displayName} 与评分呈$direction（ρ=${correlationFormat.format(correlation.coefficient)}），适合继续做单变量验证。",
                    ),
                )
            }
            methodComparisonInsights.firstOrNull()?.let { insight ->
                add(
                    SuggestedNextStep(
                        title = "优先复做表现更稳的方法",
                        message = insight.message,
                    ),
                )
            }
            outlierInsights.firstOrNull()?.let { insight ->
                add(
                    SuggestedNextStep(
                        title = "回看离群样本",
                        message = "${insight.title} 这条记录值得优先复盘，确认是否存在参数偏移或记录噪声。",
                    ),
                )
            }
        }.take(3)
    }

    private fun buildInsightCards(
        rangeInsights: List<RangeInsight>,
        parameterCorrelations: List<ParameterCorrelation>,
        methodComparisonInsights: List<MethodComparisonInsight>,
        beanComparisonInsights: List<BeanComparisonInsight>,
        outlierInsights: List<OutlierInsight>,
        filterContext: String,
    ): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()
        cards += rangeInsights.map {
            InsightCard(
                title = "${it.parameter.displayName} 区间表现",
                message = it.message,
                sampleCount = it.sampleCount,
                parameterType = it.parameter.name.lowercase(),
                confidence = it.confidence,
                filterContext = filterContext,
            )
        }
        cards += parameterCorrelations.map {
            val direction = if (it.coefficient >= 0) "正相关" else "负相关"
            InsightCard(
                title = "${it.parameter.displayName} 敏感度",
                message = "在 $filterContext 下，${it.parameter.displayName} 与评分呈$direction（ρ=${correlationFormat.format(it.coefficient)}）。",
                sampleCount = it.sampleCount,
                parameterType = "correlation",
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
            filter.beanNameKey?.let { add("豆子 ${it.replaceFirstChar { ch -> ch.titlecase(Locale.CHINA) }}") }
            filter.roastLevel?.let { add(it.displayName) }
            filter.processMethod?.let { add(it.displayName) }
            filter.grinderId?.let { add("已选磨豆机") }
        }
        return parts.joinToString(" / ")
    }

    private fun confidenceFrom(sampleSize: Int, effectMagnitude: Double): InsightConfidence {
        return when {
            sampleSize >= 12 && effectMagnitude >= 0.45 -> InsightConfidence.HIGH
            sampleSize >= 8 && effectMagnitude >= 0.25 -> InsightConfidence.MEDIUM
            else -> InsightConfidence.LOW
        }
    }

    private fun NumericParameter.extract(record: CoffeeRecord): Double? {
        return when (this) {
            NumericParameter.BREW_TIME -> record.brewDurationSeconds?.toDouble()
            NumericParameter.WATER_TEMP -> record.waterTempC
            NumericParameter.BREW_RATIO -> record.brewRatio
            NumericParameter.TOTAL_WATER -> record.totalWaterMl
            NumericParameter.BYPASS_WATER -> record.bypassWaterMl
            NumericParameter.GRIND_SETTING -> record.grindSetting
        }
    }

    private fun CoffeeRecord.toHighlight(
        title: String,
        kind: RecordHighlightKind,
    ): RecordHighlight {
        val score = subjectiveEvaluation?.overall?.let { "$it/5" } ?: "--"
        val subtitle = buildString {
            append(beanNameSnapshot ?: brewMethod?.displayName ?: "未命名记录")
            append(" · ")
            append(dateFormat.format(Date(brewedAt)))
            append(" · ")
            append(score)
        }
        return RecordHighlight(
            recordId = id,
            title = title,
            subtitle = subtitle,
            kind = kind,
        )
    }

    private fun spearman(xs: List<Double>, ys: List<Double>): Double {
        if (xs.size != ys.size || xs.size < 2) {
            return 0.0
        }
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
        if (denominatorX == 0.0 || denominatorY == 0.0) {
            return 0.0
        }
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
            for (index in start until cursor) {
                ranks[indexed[index].first] = rank
            }
        }
        return ranks
    }
}
