package com.qoffee.core.analytics

import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalyticsDashboard
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.InsightCard
import com.qoffee.core.model.InsightConfidence
import com.qoffee.core.model.MethodAverage
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.ScatterPoint
import com.qoffee.core.model.SubjectiveDimensionAverage
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

        val insights = buildInsights(qualified, filter, methodAverages)

        return AnalyticsDashboard(
            filter = filter,
            sampleCount = qualified.size,
            insightCards = insights,
            methodAverages = methodAverages,
            timelinePoints = timeline,
            scatterSeries = scatterSeries,
            dimensionAverages = dimensionAverages,
        )
    }

    private fun buildInsights(
        records: List<CoffeeRecord>,
        filter: AnalysisFilter,
        methodAverages: List<MethodAverage>,
    ): List<InsightCard> {
        if (records.size < 5) return emptyList()
        val filterContext = buildFilterContext(filter)
        val insights = mutableListOf<InsightCard>()

        methodAverages
            .filter { it.sampleCount >= 3 }
            .maxByOrNull { it.averageScore }
            ?.let { best ->
                insights += InsightCard(
                    title = "制作方式表现",
                    message = "在你的记录中，${best.brewMethod.displayName}更常拿到更高的总体评价（平均 ${scoreFormat.format(best.averageScore)} 分，n=${best.sampleCount}）。",
                    sampleCount = best.sampleCount,
                    parameterType = "brew_method",
                    confidence = confidenceFrom(best.sampleCount, best.averageScore / 10.0),
                    filterContext = filterContext,
                )
            }

        NumericParameter.entries.forEach { parameter ->
            val points = records.mapNotNull { record ->
                val x = parameter.extract(record) ?: return@mapNotNull null
                val y = record.subjectiveEvaluation?.overall?.toDouble() ?: return@mapNotNull null
                x to y
            }
            if (points.size >= 5) {
                val rho = spearman(points.map { it.first }, points.map { it.second })
                if (abs(rho) >= 0.35) {
                    insights += InsightCard(
                        title = "${parameter.displayName}相关性",
                        message = "在你的记录中，${correlationDirection(rho)}的${parameter.displayName}与总体评价相关（ρ=${correlationFormat.format(rho)}，n=${points.size}）。",
                        sampleCount = points.size,
                        parameterType = parameter.name.lowercase(),
                        confidence = confidenceFrom(points.size, abs(rho)),
                        filterContext = filterContext,
                    )
                }
            }

            bucketInsight(parameter, records, filterContext)?.let { insights += it }
        }

        return insights
            .sortedWith(
                compareByDescending<InsightCard> {
                    when (it.confidence) {
                        InsightConfidence.HIGH -> 3
                        InsightConfidence.MEDIUM -> 2
                        InsightConfidence.LOW -> 1
                    }
                }.thenByDescending { it.sampleCount },
            )
            .take(6)
    }

    private fun bucketInsight(
        parameter: NumericParameter,
        records: List<CoffeeRecord>,
        filterContext: String,
    ): InsightCard? {
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
        if (low.size < 3 || high.size < 3) return null

        val lowAvg = low.map { it.second }.average()
        val highAvg = high.map { it.second }.average()
        val delta = highAvg - lowAvg
        if (abs(delta) < 0.8) return null

        val direction = if (delta > 0) "更高" else "更低"
        val betterRange = if (delta > 0) high else low
        val values = betterRange.map { it.first }
        val min = scoreFormat.format(values.minOrNull())
        val max = scoreFormat.format(values.maxOrNull())
        return InsightCard(
            title = "${parameter.displayName}区间表现",
            message = "在你的记录中，${parameter.displayName}位于 ${min}-${max}${parameter.unitLabel} 的样本更常出现${direction}评分（均值差 ${scoreFormat.format(abs(delta))} 分）。",
            sampleCount = low.size + high.size,
            parameterType = parameter.name.lowercase(),
            confidence = confidenceFrom(low.size + high.size, abs(delta) / 2.0),
            filterContext = filterContext,
        )
    }

    private fun buildFilterContext(filter: AnalysisFilter): String {
        val parts = buildList {
            add(filter.timeRange.displayName)
            filter.brewMethod?.let { add(it.displayName) }
            filter.roastLevel?.let { add(it.displayName) }
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

    private fun correlationDirection(rho: Double): String {
        return if (rho >= 0) "更高" else "更低"
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
