package com.qoffee.core.analytics

import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalyticsReport
import com.qoffee.core.model.AnalyticsSummary
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanConsumptionBucket
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.ConsumptionChartReport
import com.qoffee.core.model.DataQualityReport
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.ObjectiveParameterAverage
import com.qoffee.core.model.OutlierReportItem
import com.qoffee.core.model.ParameterZone
import com.qoffee.core.model.ParameterZoneReport
import com.qoffee.core.model.ParameterWindowFinding
import com.qoffee.core.model.PeriodComparisonReport
import com.qoffee.core.model.QualityCurvePoint
import com.qoffee.core.model.QualityFormPoint
import com.qoffee.core.model.QualityFormSummary
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.ReportExecutiveSummary
import com.qoffee.core.model.ReportSourceRecord
import com.qoffee.core.model.SampleQuality
import com.qoffee.core.model.ScoreDistribution
import com.qoffee.core.model.SegmentComparisonFinding
import com.qoffee.core.model.SensoryDimensionReport
import com.qoffee.core.model.SignificanceLevel
import com.qoffee.core.model.StatisticalEvidence
import com.qoffee.core.model.StatisticalFinding
import com.qoffee.core.model.SweetPointReport
import com.qoffee.core.model.TastingBalancePoint
import com.qoffee.core.model.TastingChartReport
import com.qoffee.core.model.TastingConcentrationPoint
import com.qoffee.core.model.TastingDimensionSignal
import com.qoffee.core.model.TastingMatrixCell
import com.qoffee.core.model.TastingParameterResponseCell
import com.qoffee.core.model.TastingScoreBucket
import com.qoffee.core.model.HourlyConsumptionCell
import com.qoffee.core.model.WeekdayTimeConsumptionCell
import com.qoffee.core.model.normalizedBeanNameKey
import java.text.DecimalFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

internal class AnalyticsReportBuilder(
    private val timeProvider: TimeProvider,
) {
    private val oneDecimal = DecimalFormat("0.0")
    private val twoDecimals = DecimalFormat("0.00")
    private val pValueFormat = DecimalFormat("0.000")

    fun build(
        records: List<CoffeeRecord>,
        filter: AnalysisFilter,
    ): AnalyticsReport {
        val now = timeProvider.nowMillis()
        val filtered = records.filter { filter.matches(it, now) }
        val comparableFiltered = records.filter { filter.copy(timeRange = AnalysisTimeRange.ALL).matches(it, now) }
        val completed = filtered.filter { it.status == RecordStatus.COMPLETED }
        val comparableScored = comparableFiltered
            .filter { it.status == RecordStatus.COMPLETED && it.subjectiveEvaluation?.overall != null }
            .sortedBy { it.brewedAt }
        val scored = completed
            .filter { it.subjectiveEvaluation?.overall != null }
            .sortedBy { it.brewedAt }
        val quality = sampleQuality(scored.size)
        val effectiveFilter = filter
        val filterContext = buildFilterContext(effectiveFilter)
        val summary = AnalyticsSummary(
            sampleCount = scored.size,
            beanCount = scored.mapNotNull { normalizedBeanNameKey(it.beanNameSnapshot) }.distinct().size,
            grinderCount = scored.mapNotNull { it.grinderNameSnapshot?.trim()?.takeIf(String::isNotBlank) }.distinct().size,
            methodCount = scored.mapNotNull { it.brewMethod }.distinct().size,
            firstRecordAt = scored.minOfOrNull { it.brewedAt },
            lastRecordAt = scored.maxOfOrNull { it.brewedAt },
        )
        val scoreDistribution = buildScoreDistribution(scored)
        val dataQuality = buildDataQuality(
            totalRecordCount = filtered.size,
            completed = completed,
            scored = scored,
            sampleQuality = quality,
        )
        val trendFinding = buildTrendFinding(scored, quality)
        val sensoryProfile = buildSensoryProfile(scored)
        val parameterFindings = buildParameterFindings(scored, quality)
        val segmentFindings = buildSegmentFindings(scored, quality)
        val outliers = buildOutliers(scored)
        val sourceRecords = buildSourceRecords(scored, outliers)
        val qualityForm = buildQualityForm(scored)
        val qualityCurve = buildQualityCurve(scored)
        val parameterZones = buildParameterZones(scored)
        val periodComparison = buildPeriodComparison(comparableScored, filter)
        val tastingCharts = buildTastingCharts(scored)
        val sweetPoint = buildSweetPoint(scored)
        val consumptionCharts = buildConsumptionCharts(completed)
        val executiveSummary = buildExecutiveSummary(
            sampleQuality = quality,
            scoreDistribution = scoreDistribution,
            dataQuality = dataQuality,
            trendFinding = trendFinding,
            parameterFindings = parameterFindings,
            segmentFindings = segmentFindings,
            outliers = outliers,
            qualityForm = qualityForm,
            periodComparison = periodComparison,
            tastingCharts = tastingCharts,
        )

        return AnalyticsReport(
            generatedAt = now,
            filter = effectiveFilter,
            filterContext = filterContext,
            sampleQuality = quality,
            summary = summary,
            dataQuality = dataQuality,
            executiveSummary = executiveSummary,
            scoreDistribution = scoreDistribution,
            trendFinding = trendFinding,
            sensoryProfile = sensoryProfile,
            parameterFindings = parameterFindings,
            segmentFindings = segmentFindings,
            outliers = outliers,
            sourceRecords = sourceRecords,
            qualityForm = qualityForm,
            qualityCurve = qualityCurve,
            parameterZones = parameterZones,
            periodComparison = periodComparison,
            tastingCharts = tastingCharts,
            sweetPoint = sweetPoint,
            consumptionCharts = consumptionCharts,
        )
    }

    private fun buildScoreDistribution(records: List<CoffeeRecord>): ScoreDistribution {
        val scores = records.mapNotNull { it.subjectiveEvaluation?.overall }
        if (scores.isEmpty()) return ScoreDistribution()
        val scoreValues = scores.map(Int::toDouble)
        val recentAverage = records
            .sortedByDescending { it.brewedAt }
            .take(5)
            .mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
            .takeIf { it.isNotEmpty() }
            ?.average()
        return ScoreDistribution(
            sampleCount = scores.size,
            mean = scoreValues.average(),
            median = scoreValues.median(),
            standardDeviation = scoreValues.sampleStandardDeviation(),
            interquartileRange = scoreValues.quantile(0.75) - scoreValues.quantile(0.25),
            minScore = scores.minOrNull(),
            maxScore = scores.maxOrNull(),
            recentAverage = recentAverage,
            firstRecordAt = records.minOfOrNull { it.brewedAt },
            lastRecordAt = records.maxOfOrNull { it.brewedAt },
        )
    }

    private fun buildDataQuality(
        totalRecordCount: Int,
        completed: List<CoffeeRecord>,
        scored: List<CoffeeRecord>,
        sampleQuality: SampleQuality,
    ): DataQualityReport {
        val missingParameterCounts = NumericParameter.entries.associateWith { parameter ->
            scored.count { parameter.extract(it) == null }
        }
        val testableParameterCount = NumericParameter.entries.count { parameter ->
            scored.count { parameter.extract(it) != null } >= 12
        }
        val notes = buildList {
            if (sampleQuality == SampleQuality.INSUFFICIENT) {
                add("至少需要 5 条完成且有总评分的记录，才能生成稳定的报告摘要。")
            }
            val missingScoreCount = completed.size - scored.size
            if (missingScoreCount > 0) {
                add("有 $missingScoreCount 条完成记录缺少总评分，暂未纳入统计。")
            }
            val missingParameter = missingParameterCounts
                .filterValues { it > 0 }
                .minByOrNull { it.value }
            if (missingParameter != null && scored.size >= 5) {
                add("${missingParameter.key.displayName} 是当前最接近可检验的参数，补齐后可提升报告解释力。")
            }
        }
        return DataQualityReport(
            totalRecordCount = totalRecordCount,
            completedRecordCount = completed.size,
            scoredRecordCount = scored.size,
            missingScoreCount = completed.size - scored.size,
            missingParameterCounts = missingParameterCounts,
            testableParameterCount = testableParameterCount,
            sampleQuality = sampleQuality,
            notes = notes,
        )
    }

    private fun buildTrendFinding(
        records: List<CoffeeRecord>,
        sampleQuality: SampleQuality,
    ): StatisticalFinding? {
        if (records.size < 6) return null
        val sorted = records.sortedBy { it.brewedAt }
        val firstTimestamp = sorted.first().brewedAt
        val points = sorted.mapNotNull { record ->
            val score = record.subjectiveEvaluation?.overall?.toDouble() ?: return@mapNotNull null
            val day = (record.brewedAt - firstTimestamp).toDouble() / DAY_MILLIS
            day to score
        }
        if (points.size < 6 || points.map { it.first }.distinct().size < 2) return null
        val xs = points.map { it.first }
        val ys = points.map { it.second }
        val meanX = xs.average()
        val meanY = ys.average()
        val ssX = xs.sumOf { (it - meanX) * (it - meanX) }
        if (ssX <= EPSILON) return null
        val slopePerDay = points.sumOf { (it.first - meanX) * (it.second - meanY) } / ssX
        val intercept = meanY - slopePerDay * meanX
        val residualSum = points.sumOf { point ->
            val fitted = intercept + slopePerDay * point.first
            val residual = point.second - fitted
            residual * residual
        }
        val seSlope = if (points.size > 2) {
            sqrt((residualSum / (points.size - 2).coerceAtLeast(1)) / ssX)
        } else {
            0.0
        }
        val effectPer30Days = slopePerDay * 30.0
        val ciHalfWidth = 1.96 * seSlope * 30.0
        val pValue = if (seSlope <= EPSILON) {
            if (abs(effectPer30Days) <= EPSILON) 1.0 else 0.0
        } else {
            twoSidedNormalP(abs(slopePerDay / seSlope))
        }
        val direction = when {
            effectPer30Days > 0.05 -> "上升"
            effectPer30Days < -0.05 -> "下降"
            else -> "基本持平"
        }
        val limitation = qualityLimitation(sampleQuality)
        return StatisticalFinding(
            title = "评分趋势",
            summary = "按时间回归估计，评分每 30 天${direction} ${oneDecimal.format(abs(effectPer30Days))} 分。",
            evidence = StatisticalEvidence(
                sampleCount = points.size,
                effectSize = effectPer30Days,
                confidenceLow = effectPer30Days - ciHalfWidth,
                confidenceHigh = effectPer30Days + ciHalfWidth,
                pValue = pValue,
                significance = significanceFrom(pValue),
                method = "线性趋势回归",
                limitation = limitation,
            ),
            action = if (direction == "下降") "优先回看最近低分杯，确认是否有参数漂移或豆子状态变化。" else null,
        )
    }

    private fun buildSensoryProfile(records: List<CoffeeRecord>): List<SensoryDimensionReport> {
        return listOf(
            "香气" to records.mapNotNull { it.subjectiveEvaluation?.aroma?.toDouble() },
            "酸质" to records.mapNotNull { it.subjectiveEvaluation?.acidity?.toDouble() },
            "甜感" to records.mapNotNull { it.subjectiveEvaluation?.sweetness?.toDouble() },
            "苦感" to records.mapNotNull { it.subjectiveEvaluation?.bitterness?.toDouble() },
            "醇厚" to records.mapNotNull { it.subjectiveEvaluation?.body?.toDouble() },
            "余韵" to records.mapNotNull { it.subjectiveEvaluation?.aftertaste?.toDouble() },
        ).mapNotNull { (label, values) ->
            values.takeIf { it.isNotEmpty() }?.let {
                SensoryDimensionReport(
                    label = label,
                    average = it.average(),
                    standardDeviation = it.sampleStandardDeviation(),
                    sampleCount = it.size,
                )
            }
        }
    }

    private fun buildParameterFindings(
        records: List<CoffeeRecord>,
        sampleQuality: SampleQuality,
    ): List<ParameterWindowFinding> {
        return NumericParameter.entries.mapNotNull { parameter ->
            val pairs = records.mapNotNull { record ->
                val x = parameter.extract(record) ?: return@mapNotNull null
                val score = record.subjectiveEvaluation?.overall?.toDouble() ?: return@mapNotNull null
                ParameterScore(record = record, value = x, score = score)
            }.sortedBy { it.value }

            if (pairs.size < 12 || pairs.map { it.value }.distinct().size < 3) {
                return@mapNotNull null
            }
            val bucketSize = pairs.size / 3
            if (bucketSize < 3) return@mapNotNull null

            val low = pairs.take(bucketSize)
            val high = pairs.takeLast(bucketSize)
            val lowScores = low.map { it.score }
            val highScores = high.map { it.score }
            val highDelta = highScores.average() - lowScores.average()
            val preferred = if (highDelta >= 0.0) high else low
            val reference = if (highDelta >= 0.0) low else high
            val preferredScores = preferred.map { it.score }
            val referenceScores = reference.map { it.score }
            val effect = preferredScores.average() - referenceScores.average()
            val ci = bootstrapMeanDifference(preferredScores, referenceScores)
            val pValue = meanDifferencePValue(preferredScores, referenceScores)
            val significant = isMeaningfulDifference(effect, ci, pValue)
            val correlation = spearman(pairs.map { it.value }, pairs.map { it.score })
            val preferredRange = preferred.rangeLabel(parameter)
            val comparison = if (highDelta >= 0.0) "高段 vs 低段" else "低段 vs 高段"
            val significance = if (significant) significanceFrom(pValue) else SignificanceLevel.NOT_SIGNIFICANT
            ParameterWindowFinding(
                parameter = parameter,
                preferredRangeLabel = preferredRange,
                comparisonLabel = comparison,
                finding = StatisticalFinding(
                    title = "${parameter.displayName}窗口",
                    summary = "${parameter.displayName}在 $preferredRange 的样本均分更高，较对照段高 ${oneDecimal.format(effect)} 分；Spearman ρ=${twoDecimals.format(correlation)}。",
                    evidence = StatisticalEvidence(
                        sampleCount = preferredScores.size,
                        referenceSampleCount = referenceScores.size,
                        effectSize = effect,
                        confidenceLow = ci.first,
                        confidenceHigh = ci.second,
                        pValue = pValue,
                        significance = significance,
                        method = "三分段窗口 + bootstrap 95% CI",
                        limitation = qualityLimitation(sampleQuality),
                    ),
                    action = if (significant) {
                        "下一杯可围绕 $preferredRange 做单变量复测，其他参数保持不变。"
                    } else {
                        "当前差异未达到显著标准，继续补样本后再固定窗口。"
                    },
                ),
            )
        }.sortedWith(
            compareByDescending<ParameterWindowFinding> { it.finding.evidence.significance.rank() }
                .thenByDescending { abs(it.finding.evidence.effectSize ?: 0.0) }
                .thenByDescending { it.finding.evidence.sampleCount + (it.finding.evidence.referenceSampleCount ?: 0) },
        )
    }

    private fun buildSegmentFindings(
        records: List<CoffeeRecord>,
        sampleQuality: SampleQuality,
    ): List<SegmentComparisonFinding> {
        if (records.size < 8) return emptyList()
        val candidates = buildList {
            addAll(records.groupBy { it.brewMethod?.displayName }.toCandidates("制作方式"))
            addAll(records.groupBy { normalizedBeanNameKey(it.beanNameSnapshot) }.toCandidates("豆子") { key, group ->
                group.firstNotNullOfOrNull { it.beanNameSnapshot?.trim()?.takeIf(String::isNotBlank) } ?: key
            })
            addAll(records.groupBy { it.beanRoastLevelSnapshot?.displayName }.toCandidates("烘焙度"))
            addAll(records.groupBy { it.beanProcessMethodSnapshot?.displayName }.toCandidates("处理法"))
            addAll(records.groupBy { it.grinderNameSnapshot?.trim()?.takeIf(String::isNotBlank) }.toCandidates("磨豆机"))
        }
        return candidates.mapNotNull { candidate ->
            if (candidate.records.size < 4) return@mapNotNull null
            val candidateIds = candidate.records.map { it.id }.toSet()
            val reference = records.filterNot { it.id in candidateIds }
            if (reference.size < 4) return@mapNotNull null
            val candidateScores = candidate.records.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
            val referenceScores = reference.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
            if (candidateScores.size < 4 || referenceScores.size < 4) return@mapNotNull null
            val delta = candidateScores.average() - referenceScores.average()
            val ci = bootstrapMeanDifference(candidateScores, referenceScores)
            val pValue = meanDifferencePValue(candidateScores, referenceScores)
            val significant = isMeaningfulDifference(delta, ci, pValue)
            val direction = if (delta >= 0.0) "高于" else "低于"
            SegmentComparisonFinding(
                segmentType = candidate.type,
                segmentName = candidate.name,
                finding = StatisticalFinding(
                    title = "${candidate.type}：${candidate.name}",
                    summary = "${candidate.name} 均分${direction}其他样本 ${oneDecimal.format(abs(delta))} 分。",
                    evidence = StatisticalEvidence(
                        sampleCount = candidateScores.size,
                        referenceSampleCount = referenceScores.size,
                        effectSize = delta,
                        confidenceLow = ci.first,
                        confidenceHigh = ci.second,
                        pValue = pValue,
                        significance = if (significant) significanceFrom(pValue) else SignificanceLevel.NOT_SIGNIFICANT,
                        method = "分组均值差 + bootstrap 95% CI",
                        limitation = qualityLimitation(sampleQuality),
                    ),
                    action = if (significant && delta > 0.0) {
                        "优先复做该组表现，并用记录详情核对参数组合。"
                    } else if (significant) {
                        "该组表现偏低，建议回看低分样本排查共同变量。"
                    } else {
                        "当前组间差异未达到显著标准。"
                    },
                ),
            )
        }.sortedWith(
            compareByDescending<SegmentComparisonFinding> { it.finding.evidence.significance.rank() }
                .thenByDescending { abs(it.finding.evidence.effectSize ?: 0.0) }
                .thenByDescending { it.finding.evidence.sampleCount },
        ).take(8)
    }

    private fun buildOutliers(records: List<CoffeeRecord>): List<OutlierReportItem> {
        if (records.isEmpty()) return emptyList()
        val scores = records.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
        val q1 = scores.quantile(0.25)
        val q3 = scores.quantile(0.75)
        val iqr = q3 - q1
        val lowerFence = q1 - 1.5 * iqr
        val upperFence = q3 + 1.5 * iqr
        val best = records.maxWithOrNull(compareBy<CoffeeRecord> { it.subjectiveEvaluation?.overall ?: Int.MIN_VALUE }.thenBy { it.brewedAt })
        val lowest = records.minWithOrNull(compareBy<CoffeeRecord> { it.subjectiveEvaluation?.overall ?: Int.MAX_VALUE }.thenByDescending { it.brewedAt })
        val recent = records.maxByOrNull { it.brewedAt }
        val items = mutableListOf<OutlierReportItem>()
        fun add(record: CoffeeRecord?, title: String, reason: String) {
            record ?: return
            if (items.any { it.recordId == record.id }) return
            val score = record.subjectiveEvaluation?.overall ?: return
            items += OutlierReportItem(
                recordId = record.id,
                title = title,
                reason = reason,
                score = score,
                brewedAt = record.brewedAt,
                subtitle = recordSubtitle(record),
            )
        }
        add(best, "高分参考杯", "当前筛选内最高评分，适合作为复做参数参考。")
        add(lowest?.takeIf { it.id != best?.id }, "低分排查杯", "当前筛选内最低评分，适合排查参数偏移、豆子状态或记录噪声。")
        add(recent?.takeIf { it.id != best?.id && it.id != lowest?.id }, "最近样本", "最近一次带评分记录，可用于判断当前手感是否延续。")
        records.forEach { record ->
            val score = record.subjectiveEvaluation?.overall?.toDouble() ?: return@forEach
            if (score < lowerFence || score > upperFence) {
                add(record, "IQR 离群杯", "评分落在 IQR 围栏之外，建议单独复盘。")
            }
        }
        return items.take(6)
    }

    private fun buildQualityForm(records: List<CoffeeRecord>): QualityFormSummary {
        if (records.isEmpty()) return QualityFormSummary()
        val daily = records
            .groupBy { it.brewedAt / DAY_MILLIS_LONG }
            .toSortedMap()
            .mapNotNull { (_, dayRecords) ->
                val sortedDayRecords = dayRecords.sortedBy { it.brewedAt }
                val scores = sortedDayRecords.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
                if (scores.isEmpty()) return@mapNotNull null
                DailyQuality(
                    brewedAt = sortedDayRecords.maxOf { it.brewedAt },
                    score = scores.average(),
                    load = sortedDayRecords.sumOf { record -> record.completenessWeight() },
                )
            }
        if (daily.isEmpty()) return QualityFormSummary()

        var shortQuality = daily.first().score
        var longQuality = daily.first().score
        var shortLoad = daily.first().load
        var longLoad = daily.first().load
        var previousAt = daily.first().brewedAt
        val points = daily.mapIndexed { index, item ->
            if (index == 0) {
                previousAt = item.brewedAt
            } else {
                val deltaDays = ((item.brewedAt - previousAt).toDouble() / DAY_MILLIS).coerceAtLeast(1.0)
                shortQuality = ewma(
                    previous = shortQuality,
                    value = item.score,
                    days = deltaDays,
                    timeConstantDays = SHORT_TERM_DAYS,
                )
                longQuality = ewma(
                    previous = longQuality,
                    value = item.score,
                    days = deltaDays,
                    timeConstantDays = LONG_TERM_DAYS,
                )
                shortLoad = ewma(
                    previous = shortLoad,
                    value = item.load,
                    days = deltaDays,
                    timeConstantDays = SHORT_TERM_DAYS,
                )
                longLoad = ewma(
                    previous = longLoad,
                    value = item.load,
                    days = deltaDays,
                    timeConstantDays = LONG_TERM_DAYS,
                )
                previousAt = item.brewedAt
            }
            QualityFormPoint(
                brewedAt = item.brewedAt,
                score = item.score,
                shortTermQuality = shortQuality,
                longTermQuality = longQuality,
                formDelta = shortQuality - longQuality,
                shortTermLoad = shortLoad,
                longTermLoad = longLoad,
                loadDelta = shortLoad - longLoad,
            )
        }
        val latest = points.last()
        return QualityFormSummary(
            shortTermQuality = latest.shortTermQuality,
            longTermQuality = latest.longTermQuality,
            formDelta = latest.formDelta,
            shortTermLoad = latest.shortTermLoad,
            longTermLoad = latest.longTermLoad,
            loadDelta = latest.loadDelta,
            points = points,
        )
    }

    private fun buildQualityCurve(records: List<CoffeeRecord>): List<QualityCurvePoint> {
        val sorted = records
            .filter { it.subjectiveEvaluation?.overall != null }
            .sortedBy { it.brewedAt }
        val windows = listOf(1, 3, 5, 10)
        return windows.mapNotNull { window ->
            if (sorted.size < window) return@mapNotNull null
            val best = sorted.windowed(window)
                .maxByOrNull { group -> group.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }.average() }
                ?: return@mapNotNull null
            QualityCurvePoint(
                windowSize = window,
                label = if (window == 1) "单杯峰值" else "$window 杯复做窗口",
                bestAverageScore = best.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }.average(),
                startAt = best.first().brewedAt,
                endAt = best.last().brewedAt,
                recordIds = best.map { it.id },
            )
        }
    }

    private fun buildParameterZones(records: List<CoffeeRecord>): List<ParameterZoneReport> {
        return NumericParameter.entries.mapNotNull { parameter ->
            val pairs = records.mapNotNull { record ->
                val value = parameter.extract(record) ?: return@mapNotNull null
                val score = record.subjectiveEvaluation?.overall ?: return@mapNotNull null
                ParameterScore(record = record, value = value, score = score.toDouble())
            }.sortedBy { it.value }
            if (pairs.size < 5 || pairs.map { it.value }.distinct().size < 2) {
                return@mapNotNull null
            }
            val zoneCount = when {
                pairs.size >= 20 -> 5
                pairs.size >= 12 -> 4
                else -> 3
            }
            val zones = buildList {
                repeat(zoneCount) { index ->
                    val start = index * pairs.size / zoneCount
                    val end = ((index + 1) * pairs.size / zoneCount).coerceAtMost(pairs.size)
                    val bucket = pairs.subList(start, end)
                    if (bucket.isEmpty()) return@repeat
                    val scores = bucket.map { it.score }
                    add(
                        ParameterZone(
                            label = "Z${index + 1}",
                            minValue = bucket.minOf { it.value },
                            maxValue = bucket.maxOf { it.value },
                            sampleCount = bucket.size,
                            averageScore = scores.average(),
                            standardDeviation = scores.sampleStandardDeviation(),
                            bestScore = scores.maxOf { it.toInt() },
                        ),
                    )
                }
            }
            if (zones.isEmpty()) return@mapNotNull null
            val best = zones.maxWithOrNull(
                compareBy<ParameterZone> { it.averageScore }
                    .thenBy { it.sampleCount }
                    .thenBy { it.bestScore },
            )
            val bestLabel = best?.let { zone ->
                "${zone.label} ${oneDecimal.format(zone.minValue)}-${oneDecimal.format(zone.maxValue)}${parameter.unitLabel}"
            }
            ParameterZoneReport(
                parameter = parameter,
                zones = zones,
                bestZoneLabel = bestLabel,
                insight = best?.let {
                    "${parameter.displayName} 的最佳分区是 $bestLabel，均分 ${oneDecimal.format(it.averageScore)}/5，样本 ${it.sampleCount}。"
                } ?: "${parameter.displayName} 暂无最佳分区。",
            )
        }
    }

    private fun buildPeriodComparison(
        records: List<CoffeeRecord>,
        filter: AnalysisFilter,
    ): PeriodComparisonReport? {
        if (records.size < 4) return null
        val lastAt = records.maxOfOrNull { it.brewedAt } ?: return null
        val rangeDays = filter.timeRange.days ?: DEFAULT_COMPARISON_DAYS
        val rangeMillis = rangeDays * DAY_MILLIS_LONG
        val currentStart = lastAt - rangeMillis
        val previousStart = currentStart - rangeMillis
        val current = records.filter { it.brewedAt > currentStart && it.brewedAt <= lastAt }
        val previous = records.filter { it.brewedAt > previousStart && it.brewedAt <= currentStart }
        val currentScores = current.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
        val previousScores = previous.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
        if (currentScores.isEmpty() && previousScores.isEmpty()) return null
        val currentAverage = currentScores.takeIf { it.isNotEmpty() }?.average()
        val previousAverage = previousScores.takeIf { it.isNotEmpty() }?.average()
        val averageDelta = if (currentAverage != null && previousAverage != null) currentAverage - previousAverage else null
        val currentConsistency = currentScores.takeIf { it.size >= 2 }?.sampleStandardDeviation()
        val previousConsistency = previousScores.takeIf { it.size >= 2 }?.sampleStandardDeviation()
        val consistencyDelta = if (currentConsistency != null && previousConsistency != null) {
            currentConsistency - previousConsistency
        } else {
            null
        }
        val finding = when {
            averageDelta == null -> "当前或前一周期样本不足，暂不能判断质量变化。"
            averageDelta >= 0.3 -> "当前周期均分较前一周期上升 ${oneDecimal.format(averageDelta)} 分，近期冲煮状态改善。"
            averageDelta <= -0.3 -> "当前周期均分较前一周期下降 ${oneDecimal.format(abs(averageDelta))} 分，建议回看低分杯和参数漂移。"
            else -> "当前周期与前一周期均分接近，主要观察稳定性和参数完整度。"
        }
        return PeriodComparisonReport(
            currentLabel = "最近 $rangeDays 天",
            previousLabel = "前 $rangeDays 天",
            currentSampleCount = currentScores.size,
            previousSampleCount = previousScores.size,
            currentAverageScore = currentAverage,
            previousAverageScore = previousAverage,
            averageScoreDelta = averageDelta,
            currentConsistency = currentConsistency,
            previousConsistency = previousConsistency,
            consistencyDelta = consistencyDelta,
            finding = finding,
        )
    }

    private fun buildTastingCharts(records: List<CoffeeRecord>): TastingChartReport {
        if (records.isEmpty()) return TastingChartReport()
        val dimensionRows = records.mapNotNull { record ->
            val evaluation = record.subjectiveEvaluation ?: return@mapNotNull null
            TastingDimensionRow(
                record = record,
                aroma = evaluation.aroma?.toDouble(),
                acidity = evaluation.acidity?.toDouble(),
                sweetness = evaluation.sweetness?.toDouble(),
                bitterness = evaluation.bitterness?.toDouble(),
                body = evaluation.body?.toDouble(),
                aftertaste = evaluation.aftertaste?.toDouble(),
                overall = evaluation.overall?.toDouble(),
            )
        }
        val scoreBuckets = buildScoreBuckets(records)
        val dimensionMatrix = buildTastingDimensionMatrix(records)
        val balancePoints = buildTastingBalancePoints(records)
        val parameterResponse = buildTastingParameterResponse(records)
        val concentrationCurve = buildScoreConcentrationCurve(records)
        val dimensionSignals = buildTastingDimensionSignals(dimensionRows)
        return TastingChartReport(
            scoredSampleCount = records.size,
            dimensionSampleCount = dimensionRows.size,
            scoreBuckets = scoreBuckets,
            dimensionMatrix = dimensionMatrix,
            balancePoints = balancePoints,
            parameterResponse = parameterResponse,
            concentrationCurve = concentrationCurve,
            dimensionSignals = dimensionSignals,
            insight = buildTastingChartInsight(
                records = records,
                dimensionSignals = dimensionSignals,
                balancePoints = balancePoints,
                parameterResponse = parameterResponse,
            ),
        )
    }

    private fun buildScoreBuckets(records: List<CoffeeRecord>): List<TastingScoreBucket> {
        val total = records.size.coerceAtLeast(1)
        return (1..5).map { score ->
            val count = records.count { it.subjectiveEvaluation?.overall == score }
            TastingScoreBucket(
                label = "$score 分",
                score = score,
                count = count,
                share = count.toDouble() / total.toDouble(),
            )
        }
    }

    private fun buildTastingDimensionMatrix(records: List<CoffeeRecord>): List<TastingMatrixCell> {
        val methodGroups = records
            .groupBy { it.brewMethod?.displayName ?: "未指定方式" }
            .filterValues { it.size >= 2 }
            .map { (name, group) -> "方式" to name to group }
        val beanGroups = records
            .groupBy { it.beanNameSnapshot?.trim()?.takeIf(String::isNotBlank) ?: "未命名豆子" }
            .filterValues { it.size >= 2 }
            .toList()
            .sortedByDescending { (_, group) -> group.size }
            .take(4)
            .map { (name, group) -> "豆子" to name to group }
        return (methodGroups + beanGroups).flatMap { pair ->
            val groupType = pair.first.first
            val groupName = pair.first.second
            val group = pair.second
            sensoryDimensionValues(group).mapNotNull { dimension ->
                val values = dimension.values
                values.takeIf { it.isNotEmpty() }?.let {
                    TastingMatrixCell(
                        groupType = groupType,
                        groupName = groupName,
                        dimensionLabel = dimension.label,
                        averageScore = it.average(),
                        sampleCount = it.size,
                    )
                }
            }
        }
    }

    private fun buildTastingBalancePoints(records: List<CoffeeRecord>): List<TastingBalancePoint> {
        return records.mapNotNull { record ->
            val evaluation = record.subjectiveEvaluation ?: return@mapNotNull null
            val acidity = evaluation.acidity?.toDouble() ?: return@mapNotNull null
            val sweetness = evaluation.sweetness?.toDouble() ?: return@mapNotNull null
            val overall = evaluation.overall ?: return@mapNotNull null
            TastingBalancePoint(
                recordId = record.id,
                label = record.beanNameSnapshot ?: record.recipeNameSnapshot ?: record.brewMethod?.displayName ?: "未命名记录",
                brewedAt = record.brewedAt,
                acidity = acidity,
                sweetness = sweetness,
                bitterness = evaluation.bitterness?.toDouble(),
                body = evaluation.body?.toDouble(),
                overall = overall,
            )
        }.sortedWith(
            compareByDescending<TastingBalancePoint> { it.overall }
                .thenByDescending { it.brewedAt },
        ).take(40)
    }

    private fun buildTastingParameterResponse(records: List<CoffeeRecord>): List<TastingParameterResponseCell> {
        return NumericParameter.entries.flatMap { parameter ->
            val pairs = records.mapNotNull { record ->
                val value = parameter.extract(record) ?: return@mapNotNull null
                val evaluation = record.subjectiveEvaluation ?: return@mapNotNull null
                val overall = evaluation.overall ?: return@mapNotNull null
                TastingParameterSample(
                    record = record,
                    value = value,
                    overall = overall.toDouble(),
                    sweetness = evaluation.sweetness?.toDouble(),
                    acidity = evaluation.acidity?.toDouble(),
                )
            }.sortedBy { it.value }
            if (pairs.size < 5 || pairs.map { it.value }.distinct().size < 2) return@flatMap emptyList()
            val zoneCount = when {
                pairs.size >= 20 -> 5
                pairs.size >= 12 -> 4
                else -> 3
            }
            (0 until zoneCount).mapNotNull { index ->
                val start = index * pairs.size / zoneCount
                val end = ((index + 1) * pairs.size / zoneCount).coerceAtMost(pairs.size)
                val bucket = pairs.subList(start, end)
                if (bucket.isEmpty()) return@mapNotNull null
                TastingParameterResponseCell(
                    parameter = parameter,
                    zoneLabel = "Z${index + 1}",
                    minValue = bucket.minOf { it.value },
                    maxValue = bucket.maxOf { it.value },
                    sampleCount = bucket.size,
                    averageOverall = bucket.map { it.overall }.average(),
                    averageSweetness = bucket.mapNotNull { it.sweetness }.takeIf { it.isNotEmpty() }?.average(),
                    averageAcidity = bucket.mapNotNull { it.acidity }.takeIf { it.isNotEmpty() }?.average(),
                )
            }
        }
    }

    private fun buildScoreConcentrationCurve(records: List<CoffeeRecord>): List<TastingConcentrationPoint> {
        val scores = records.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }.sortedDescending()
        if (scores.size < 2) return emptyList()
        val total = scores.sum().takeIf { it > EPSILON } ?: return emptyList()
        return listOf(0.2, 0.4, 0.6, 0.8, 1.0).map { share ->
            val count = ceil(scores.size * share).toInt().coerceIn(1, scores.size)
            TastingConcentrationPoint(
                sampleShare = count.toDouble() / scores.size.toDouble(),
                cumulativeScoreShare = scores.take(count).sum() / total,
            )
        }.distinctBy { (it.sampleShare * 100).toInt() }
    }

    private fun buildTastingDimensionSignals(rows: List<TastingDimensionRow>): List<TastingDimensionSignal> {
        val candidates = listOf(
            "香气" to rows.mapNotNull { it.aroma?.let { value -> value to it.overall } },
            "酸质" to rows.mapNotNull { it.acidity?.let { value -> value to it.overall } },
            "甜感" to rows.mapNotNull { it.sweetness?.let { value -> value to it.overall } },
            "苦感" to rows.mapNotNull { it.bitterness?.let { value -> value to it.overall } },
            "醇厚" to rows.mapNotNull { it.body?.let { value -> value to it.overall } },
            "余韵" to rows.mapNotNull { it.aftertaste?.let { value -> value to it.overall } },
        )
        return candidates.mapNotNull { (label, pairs) ->
            if (pairs.size < 6) return@mapNotNull null
            val xs = pairs.map { it.first }
            val ys = pairs.mapNotNull { it.second }
            if (xs.size != ys.size || xs.distinct().size < 2 || ys.distinct().size < 2) return@mapNotNull null
            val rho = spearman(xs, ys)
            val pValue = twoSidedNormalP(abs(rho) * sqrt((pairs.size - 1).toDouble()))
            val direction = when {
                rho >= 0.35 -> "正向关联"
                rho <= -0.35 -> "反向关联"
                else -> "关联较弱"
            }
            TastingDimensionSignal(
                dimensionLabel = label,
                finding = StatisticalFinding(
                    title = "$label 对总评的信号",
                    summary = "$label 与总评$direction，Spearman ρ=${twoDecimals.format(rho)}；样本 ${pairs.size}。",
                    evidence = StatisticalEvidence(
                        sampleCount = pairs.size,
                        effectSize = rho,
                        pValue = pValue,
                        significance = significanceFrom(pValue),
                        method = "感官维度 vs 总评 Spearman ρ",
                        limitation = "感官评分为个人主观记录，只用于识别本人品鉴偏好和复盘线索。",
                    ),
                    action = if (abs(rho) >= 0.35) {
                        "复做高分杯时优先核对 $label 的表现，并回看对应风味备注。"
                    } else {
                        "当前 $label 对总评解释力较弱，先结合其他维度观察。"
                    },
                ),
            )
        }.sortedByDescending { abs(it.finding.evidence.effectSize ?: 0.0) }
    }

    private fun buildTastingChartInsight(
        records: List<CoffeeRecord>,
        dimensionSignals: List<TastingDimensionSignal>,
        balancePoints: List<TastingBalancePoint>,
        parameterResponse: List<TastingParameterResponseCell>,
    ): String {
        val bestDimension = dimensionSignals.firstOrNull()
        val highScoreCount = records.count { (it.subjectiveEvaluation?.overall ?: 0) >= 4 }
        val bestParameter = parameterResponse.maxWithOrNull(
            compareBy<TastingParameterResponseCell> { it.averageOverall }
                .thenBy { it.sampleCount },
        )
        val balanceSummary = balancePoints.takeIf { it.isNotEmpty() }?.let { points ->
            val averageSweetness = points.map { it.sweetness }.average()
            val averageAcidity = points.map { it.acidity }.average()
            "甜感均值 ${oneDecimal.format(averageSweetness)}、酸质均值 ${oneDecimal.format(averageAcidity)}"
        }
        return buildList {
            add("当前有 ${records.size} 条带总评的品鉴样本，其中 $highScoreCount 条达到 4 分及以上")
            bestDimension?.let { add("${it.dimensionLabel} 是当前最强感官信号") }
            bestParameter?.let {
                add("${it.parameter.displayName} 的 ${it.zoneLabel} 分区当前均分最高，为 ${oneDecimal.format(it.averageOverall)}/5")
            }
            balanceSummary?.let { add(it) }
        }.joinToString("；") + "。"
    }

    private fun buildSweetPoint(records: List<CoffeeRecord>): SweetPointReport {
        val sweetSamples = records
            .filter { record ->
                val evaluation = record.subjectiveEvaluation
                (evaluation?.sweetness ?: 0) >= 4 && evaluation?.overall != null
            }
            .sortedWith(
                compareByDescending<CoffeeRecord> { it.subjectiveEvaluation?.overall ?: 0 }
                    .thenByDescending { it.subjectiveEvaluation?.sweetness ?: 0 }
                    .thenByDescending { it.brewedAt },
            )
        if (sweetSamples.size < 3) {
            return SweetPointReport(
                sampleCount = sweetSamples.size,
                sourceRecords = sweetSamples.take(3).mapNotNull(::toReportSourceRecord),
                insight = "当前只有 ${sweetSamples.size} 条甜感 4 分及以上样本，暂不总结 Sweet Point；建议继续补齐甜感、总评和关键参数。",
            )
        }

        val averageOverall = sweetSamples.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }.average()
        val averageSweetness = sweetSamples.mapNotNull { it.subjectiveEvaluation?.sweetness?.toDouble() }.average()
        val parameterAverages = buildList {
            addParameterAverage("粉量", "g", sweetSamples.mapNotNull { it.coffeeDoseG })
            addParameterAverage("总水量", "ml", sweetSamples.mapNotNull { it.totalWaterMl ?: it.brewWaterMl })
            addParameterAverage("粉水比", "", sweetSamples.mapNotNull { it.brewRatio })
            addParameterAverage("水温", "°C", sweetSamples.mapNotNull { it.waterTempC })
            addParameterAverage("冲煮时长", "s", sweetSamples.mapNotNull { it.brewDurationSeconds?.toDouble() })
            addParameterAverage("研磨", "", sweetSamples.mapNotNull { it.grindSetting })
            addParameterAverage("归一化研磨", "", sweetSamples.mapNotNull { it.normalizedGrindSetting })
        }.sortedByDescending { it.sampleCount }

        val topParameters = parameterAverages
            .take(4)
            .joinToString("，") { average ->
                "${average.label} ${oneDecimal.format(average.value)}${average.unitLabel}"
            }
        val bestRecord = sweetSamples.firstOrNull()
        return SweetPointReport(
            sampleCount = sweetSamples.size,
            targetDescription = "甜感 ≥4 且有总评的历史记录",
            averageOverall = averageOverall,
            averageSweetness = averageSweetness,
            parameterAverages = parameterAverages,
            sourceRecords = sweetSamples.take(6).mapNotNull(::toReportSourceRecord),
            insight = buildString {
                append("你的 Sweet Point 样本均分 ${oneDecimal.format(averageOverall)}/5，甜感均值 ${oneDecimal.format(averageSweetness)}/5")
                if (topParameters.isNotBlank()) {
                    append("；常见参数中心为 $topParameters")
                }
                bestRecord?.let {
                    append("；最高参考杯是 #${it.id} ${it.beanNameSnapshot ?: it.brewMethod?.displayName ?: "未命名记录"}。")
                } ?: append("。")
            },
        )
    }

    private fun MutableList<ObjectiveParameterAverage>.addParameterAverage(
        label: String,
        unitLabel: String,
        values: List<Double>,
    ) {
        if (values.isEmpty()) return
        add(
            ObjectiveParameterAverage(
                label = label,
                value = values.average(),
                unitLabel = unitLabel,
                sampleCount = values.size,
            ),
        )
    }

    private fun buildConsumptionCharts(records: List<CoffeeRecord>): ConsumptionChartReport {
        val doseRecords = records
            .filter { it.status == RecordStatus.COMPLETED }
            .mapNotNull { record ->
                val dose = record.coffeeDoseG?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
                ConsumptionRecord(record = record, doseG = dose, caffeineMg = estimateCaffeineMg(dose))
            }
        if (doseRecords.isEmpty()) return ConsumptionChartReport()

        val totalDose = doseRecords.sumOf { it.doseG }
        val totalCaffeine = doseRecords.sumOf { it.caffeineMg }
        val beanBuckets = buildBeanConsumptionBuckets(doseRecords)
        val hourlySpectrum = buildHourlyConsumptionSpectrum(doseRecords)
        val weekdayHeatmap = buildWeekdayConsumptionHeatmap(doseRecords)
        val peakHour = hourlySpectrum.maxWithOrNull(
            compareBy<HourlyConsumptionCell> { it.recordCount }
                .thenBy { it.caffeineMg },
        )?.takeIf { it.recordCount > 0 }
        val dominantBean = beanBuckets.firstOrNull()
        return ConsumptionChartReport(
            recordCount = doseRecords.size,
            totalDoseG = totalDose,
            estimatedCaffeineMg = totalCaffeine,
            averageDoseG = totalDose / doseRecords.size,
            averageCaffeineMg = totalCaffeine / doseRecords.size,
            peakHourLabel = peakHour?.let { "${it.hour.toString().padStart(2, '0')}:00" },
            dominantBeanName = dominantBean?.beanName,
            beanBuckets = beanBuckets,
            hourlySpectrum = hourlySpectrum,
            weekdayHeatmap = weekdayHeatmap,
            insight = buildString {
                append("当前筛选内记录了 ${doseRecords.size} 杯，合计消耗 ${oneDecimal.format(totalDose)}g 咖啡豆")
                append("，估算咖啡因 ${oneDecimal.format(totalCaffeine)}mg")
                peakHour?.let { append("；最常出现的饮用时段是 ${it.hour.toString().padStart(2, '0')}:00") }
                dominantBean?.let { append("；消耗最多的豆子是 ${it.beanName}，占 ${oneDecimal.format(it.share * 100)}%。") }
                    ?: append("。")
            },
        )
    }

    private fun buildBeanConsumptionBuckets(records: List<ConsumptionRecord>): List<BeanConsumptionBucket> {
        val totalDose = records.sumOf { it.doseG }.takeIf { it > EPSILON } ?: return emptyList()
        return records
            .groupBy { it.record.beanNameSnapshot?.trim()?.takeIf(String::isNotBlank) ?: "未命名豆子" }
            .map { (beanName, group) ->
                val dose = group.sumOf { it.doseG }
                BeanConsumptionBucket(
                    beanName = beanName,
                    doseG = dose,
                    recordCount = group.size,
                    share = dose / totalDose,
                )
            }
            .sortedByDescending { it.doseG }
            .take(8)
    }

    private fun buildHourlyConsumptionSpectrum(records: List<ConsumptionRecord>): List<HourlyConsumptionCell> {
        val byHour = records.groupBy { it.record.brewedAt.calendarField(Calendar.HOUR_OF_DAY) }
        val maxDose = byHour.values.maxOfOrNull { group -> group.sumOf { it.doseG } }?.takeIf { it > EPSILON } ?: 1.0
        return (0..23).map { hour ->
            val group = byHour[hour].orEmpty()
            val dose = group.sumOf { it.doseG }
            val caffeine = group.sumOf { it.caffeineMg }
            HourlyConsumptionCell(
                hour = hour,
                recordCount = group.size,
                doseG = dose,
                caffeineMg = caffeine,
                intensity = (dose / maxDose).coerceIn(0.0, 1.0),
            )
        }
    }

    private fun buildWeekdayConsumptionHeatmap(records: List<ConsumptionRecord>): List<WeekdayTimeConsumptionCell> {
        val grouped = records.groupBy { record ->
            WeekdayBandKey(
                weekday = record.record.brewedAt.isoWeekday(),
                band = record.record.brewedAt.timeBand(),
            )
        }
        val maxDose = grouped.values.maxOfOrNull { group -> group.sumOf { it.doseG } }?.takeIf { it > EPSILON } ?: 1.0
        return (1..7).flatMap { weekday ->
            (0..5).map { band ->
                val group = grouped[WeekdayBandKey(weekday, band)].orEmpty()
                val dose = group.sumOf { it.doseG }
                WeekdayTimeConsumptionCell(
                    weekday = weekday,
                    weekdayLabel = weekdayLabel(weekday),
                    timeBand = band,
                    timeBandLabel = timeBandLabel(band),
                    recordCount = group.size,
                    doseG = dose,
                    caffeineMg = group.sumOf { it.caffeineMg },
                    intensity = (dose / maxDose).coerceIn(0.0, 1.0),
                )
            }
        }
    }

    private fun estimateCaffeineMg(doseG: Double): Double = doseG * DEFAULT_CAFFEINE_MG_PER_GRAM

    private fun Long.calendarField(field: Int): Int {
        return Calendar.getInstance(Locale.CHINA).apply { timeInMillis = this@calendarField }.get(field)
    }

    private fun Long.isoWeekday(): Int {
        val calendarDay = calendarField(Calendar.DAY_OF_WEEK)
        return if (calendarDay == Calendar.SUNDAY) 7 else calendarDay - 1
    }

    private fun Long.timeBand(): Int = (calendarField(Calendar.HOUR_OF_DAY) / 4).coerceIn(0, 5)

    private fun weekdayLabel(weekday: Int): String = when (weekday) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    private fun timeBandLabel(band: Int): String = when (band) {
        0 -> "0-4"
        1 -> "4-8"
        2 -> "8-12"
        3 -> "12-16"
        4 -> "16-20"
        else -> "20-24"
    }

    private fun sensoryDimensionValues(records: List<CoffeeRecord>): List<SensoryDimensionValues> {
        return listOf(
            SensoryDimensionValues("香气", records.mapNotNull { it.subjectiveEvaluation?.aroma?.toDouble() }),
            SensoryDimensionValues("酸质", records.mapNotNull { it.subjectiveEvaluation?.acidity?.toDouble() }),
            SensoryDimensionValues("甜感", records.mapNotNull { it.subjectiveEvaluation?.sweetness?.toDouble() }),
            SensoryDimensionValues("苦感", records.mapNotNull { it.subjectiveEvaluation?.bitterness?.toDouble() }),
            SensoryDimensionValues("醇厚", records.mapNotNull { it.subjectiveEvaluation?.body?.toDouble() }),
            SensoryDimensionValues("余韵", records.mapNotNull { it.subjectiveEvaluation?.aftertaste?.toDouble() }),
        )
    }

    private fun buildSourceRecords(
        records: List<CoffeeRecord>,
        outliers: List<OutlierReportItem>,
    ): List<ReportSourceRecord> {
        val outlierIds = outliers.map { it.recordId }.toSet()
        return (records.filter { it.id in outlierIds } + records.sortedByDescending { it.brewedAt }.take(8))
            .distinctBy { it.id }
            .mapNotNull { record ->
                val score = record.subjectiveEvaluation?.overall ?: return@mapNotNull null
                ReportSourceRecord(
                    recordId = record.id,
                    label = record.beanNameSnapshot ?: record.recipeNameSnapshot ?: record.brewMethod?.displayName ?: "未命名记录",
                    brewedAt = record.brewedAt,
                    score = score,
                    brewMethodLabel = record.brewMethod?.displayName ?: "未指定方式",
                    parameterSummary = buildParameterSummary(record),
                )
            }
            .take(12)
    }

    private fun toReportSourceRecord(record: CoffeeRecord): ReportSourceRecord? {
        val score = record.subjectiveEvaluation?.overall ?: return null
        return ReportSourceRecord(
            recordId = record.id,
            label = record.beanNameSnapshot ?: record.recipeNameSnapshot ?: record.brewMethod?.displayName ?: "未命名记录",
            brewedAt = record.brewedAt,
            score = score,
            brewMethodLabel = record.brewMethod?.displayName ?: "未指定方式",
            parameterSummary = buildParameterSummary(record),
        )
    }

    private fun buildExecutiveSummary(
        sampleQuality: SampleQuality,
        scoreDistribution: ScoreDistribution,
        dataQuality: DataQualityReport,
        trendFinding: StatisticalFinding?,
        parameterFindings: List<ParameterWindowFinding>,
        segmentFindings: List<SegmentComparisonFinding>,
        outliers: List<OutlierReportItem>,
        qualityForm: QualityFormSummary,
        periodComparison: PeriodComparisonReport?,
        tastingCharts: TastingChartReport,
    ): ReportExecutiveSummary {
        if (sampleQuality == SampleQuality.INSUFFICIENT) {
            val needed = (5 - dataQuality.scoredRecordCount).coerceAtLeast(1)
            return ReportExecutiveSummary(
                headline = "当前只有 ${dataQuality.scoredRecordCount} 条有效评分样本，暂不形成专业结论。",
                supportingText = "再补 $needed 条完成且有总评分的记录后，可进入探索型冲煮质量分析。",
                nextActions = dataQuality.notes.ifEmpty {
                    listOf("优先补齐总评分、水温、粉水比和冲煮时长，后续报告才能判断参数窗口。")
                },
            )
        }

        val keyFindings = buildList {
            trendFinding?.let { add(it) }
            addAll(parameterFindings.map { it.finding }.filter { it.evidence.significance.rank() > 0 })
            addAll(segmentFindings.map { it.finding }.filter { it.evidence.significance.rank() > 0 })
            if (isEmpty()) {
                addAll(parameterFindings.take(2).map { it.finding })
                addAll(segmentFindings.take(1).map { it.finding })
                addAll(tastingCharts.dimensionSignals.take(1).map { it.finding })
            }
        }.distinctBy { it.title }.take(3)

        val scoreHeadline = scoreDistribution.mean?.let { "当前均分 ${oneDecimal.format(it)}/5" } ?: "当前已有评分样本"
        val stability = scoreDistribution.standardDeviation?.let { sd ->
            when {
                sd <= 0.45 -> "波动较小"
                sd <= 0.8 -> "波动中等"
                else -> "波动偏大"
            }
        } ?: "波动未知"
        val headline = "$scoreHeadline，样本质量为${sampleQuality.displayName}，评分$stability。"
        val supporting = keyFindings.firstOrNull()?.summary
            ?: periodComparison?.finding
            ?: tastingCharts.insight.takeIf { tastingCharts.scoredSampleCount >= 3 }
            ?: "当前未出现达到显著标准的参数或分组差异，建议继续按单变量方式补样本。"
        val nextActions = buildList {
            qualityForm.formDelta?.let { form ->
                if (form <= -0.25) {
                    add("近期质量低于长期基线 ${oneDecimal.format(abs(form))} 分，优先复盘最近低分杯和参数漂移。")
                }
            }
            tastingCharts.dimensionSignals.firstOrNull()?.finding?.action?.let { add(it) }
            tastingCharts.parameterResponse.maxByOrNull { it.averageOverall }?.let { cell ->
                add("围绕 ${cell.parameter.displayName} 的 ${cell.zoneLabel} 分区复做一杯，观察总评、甜感和酸质是否重复。")
            }
            parameterFindings.firstOrNull { it.finding.evidence.significance.rank() > 0 }?.finding?.action?.let { add(it) }
            segmentFindings.firstOrNull { it.finding.evidence.significance.rank() > 0 }?.finding?.action?.let { add(it) }
            outliers.firstOrNull { it.title.contains("低分") }?.let { add("先打开 ${it.subtitle}，对照高分参考杯排查参数和主观备注。") }
            periodComparison?.averageScoreDelta?.takeIf { it <= -0.3 }?.let {
                add("当前周期均分下降 ${oneDecimal.format(abs(it))} 分，建议按周期筛选样本做复盘。")
            }
            if (isEmpty()) {
                add("保持一次只改一个变量，优先补齐样本量最高的参数。")
            }
        }.take(3)

        return ReportExecutiveSummary(
            headline = headline,
            supportingText = supporting,
            keyFindings = keyFindings,
            nextActions = nextActions,
        )
    }

    private fun Map<String?, List<CoffeeRecord>>.toCandidates(type: String): List<SegmentCandidate> =
        toCandidates(type) { key, _ -> key }

    private fun Map<String?, List<CoffeeRecord>>.toCandidates(
        type: String,
        displayName: (String, List<CoffeeRecord>) -> String?,
    ): List<SegmentCandidate> {
        return mapNotNull { (key, group) ->
            val safeKey = key?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val name = displayName(safeKey, group)?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            SegmentCandidate(type = type, name = name, records = group)
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
            NumericParameter.NORMALIZED_GRIND -> record.normalizedGrindSetting
        }?.takeIf { it.isFinite() }
    }

    private fun CoffeeRecord.completenessWeight(): Double {
        val presentParameters = NumericParameter.entries.count { parameter -> parameter.extract(this) != null }
        return 1.0 + presentParameters.toDouble() / NumericParameter.entries.size.toDouble()
    }

    private fun ewma(
        previous: Double,
        value: Double,
        days: Double,
        timeConstantDays: Double,
    ): Double {
        val alpha = 1.0 - exp(-days / timeConstantDays)
        return previous + alpha * (value - previous)
    }

    private fun List<ParameterScore>.rangeLabel(parameter: NumericParameter): String {
        val min = minOf { it.value }
        val max = maxOf { it.value }
        val unit = parameter.unitLabel
        return "${oneDecimal.format(min)}-${oneDecimal.format(max)}$unit"
    }

    private fun buildParameterSummary(record: CoffeeRecord): String {
        return listOfNotNull(
            record.brewRatio?.let { "粉水比 ${oneDecimal.format(it)}" },
            record.waterTempC?.let { "水温 ${oneDecimal.format(it)}°C" },
            record.brewDurationSeconds?.let { "时长 ${it}s" },
            record.grindSetting?.let { "研磨 ${oneDecimal.format(it)}" },
        ).ifEmpty { listOf("参数未完整") }.joinToString(" · ")
    }

    private fun recordSubtitle(record: CoffeeRecord): String {
        val name = record.beanNameSnapshot ?: record.recipeNameSnapshot ?: record.brewMethod?.displayName ?: "未命名记录"
        val method = record.brewMethod?.displayName ?: "未指定方式"
        return "$name · $method · ${record.subjectiveEvaluation?.overall ?: "--"}/5"
    }

    private fun sampleQuality(sampleCount: Int): SampleQuality {
        return when {
            sampleCount < 5 -> SampleQuality.INSUFFICIENT
            sampleCount < 12 -> SampleQuality.EXPLORATORY
            sampleCount < 20 -> SampleQuality.TESTABLE
            else -> SampleQuality.ROBUST
        }
    }

    private fun qualityLimitation(sampleQuality: SampleQuality): String {
        return when (sampleQuality) {
            SampleQuality.INSUFFICIENT -> "样本不足，只能作为记录补全提示。"
            SampleQuality.EXPLORATORY -> "探索样本，不建议据此固定配方。"
            SampleQuality.TESTABLE -> "样本可检验，但仍需继续复测确认稳定性。"
            SampleQuality.ROBUST -> "基于个人历史记录的统计结论，不代表普适因果关系。"
        }
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

    private fun bootstrapMeanDifference(
        candidate: List<Double>,
        reference: List<Double>,
        iterations: Int = 400,
    ): Pair<Double, Double> {
        if (candidate.isEmpty() || reference.isEmpty()) return 0.0 to 0.0
        val random = DeterministicRandom(
            seed = 31L * candidate.size + 17L * reference.size + candidate.sumOf { (it * 100).toLong() },
        )
        val values = MutableList(iterations) {
            candidate.bootstrapMean(random) - reference.bootstrapMean(random)
        }.sorted()
        val lowIndex = ((iterations - 1) * 0.025).toInt().coerceIn(values.indices)
        val highIndex = ((iterations - 1) * 0.975).toInt().coerceIn(values.indices)
        return values[lowIndex] to values[highIndex]
    }

    private fun List<Double>.bootstrapMean(random: DeterministicRandom): Double {
        if (isEmpty()) return 0.0
        var total = 0.0
        repeat(size) {
            total += this[random.nextInt(size)]
        }
        return total / size
    }

    private fun meanDifferencePValue(candidate: List<Double>, reference: List<Double>): Double? {
        if (candidate.size < 2 || reference.size < 2) return null
        val se = sqrt(
            candidate.sampleVariance() / candidate.size.toDouble() +
                reference.sampleVariance() / reference.size.toDouble(),
        )
        val effect = candidate.average() - reference.average()
        if (se <= EPSILON) return if (abs(effect) <= EPSILON) 1.0 else 0.0
        return twoSidedNormalP(abs(effect / se))
    }

    private fun isMeaningfulDifference(
        effect: Double,
        ci: Pair<Double, Double>,
        pValue: Double?,
    ): Boolean {
        return abs(effect) >= 0.3 &&
            pValue != null &&
            pValue < 0.05 &&
            ((ci.first > 0.0 && ci.second > 0.0) || (ci.first < 0.0 && ci.second < 0.0))
    }

    private fun significanceFrom(pValue: Double?): SignificanceLevel {
        return when {
            pValue == null -> SignificanceLevel.NOT_TESTED
            pValue < 0.01 -> SignificanceLevel.P_0_01
            pValue < 0.05 -> SignificanceLevel.P_0_05
            else -> SignificanceLevel.NOT_SIGNIFICANT
        }
    }

    private fun SignificanceLevel.rank(): Int {
        return when (this) {
            SignificanceLevel.P_0_01 -> 3
            SignificanceLevel.P_0_05 -> 2
            SignificanceLevel.NOT_SIGNIFICANT -> 1
            SignificanceLevel.NOT_TESTED -> 0
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
        if (denominatorX <= EPSILON || denominatorY <= EPSILON) return 0.0
        return numerator / sqrt(denominatorX * denominatorY)
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

    private fun List<Double>.median(): Double = quantile(0.5)

    private fun List<Double>.quantile(probability: Double): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        if (sorted.size == 1) return sorted.first()
        val position = probability.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lower = position.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
        val fraction = position - lower
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction
    }

    private fun List<Double>.sampleStandardDeviation(): Double = sqrt(sampleVariance())

    private fun List<Double>.sampleVariance(): Double {
        if (size < 2) return 0.0
        val mean = average()
        return sumOf { value ->
            val delta = value - mean
            delta * delta
        } / (size - 1)
    }

    private fun twoSidedNormalP(z: Double): Double {
        return (2.0 * (1.0 - normalCdf(z))).coerceIn(0.0, 1.0)
    }

    private fun normalCdf(x: Double): Double {
        val sign = if (x < 0) -1 else 1
        val absX = abs(x) / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * absX)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) *
            t * exp(-absX * absX)
        val erf = sign * y
        return 0.5 * (1.0 + erf)
    }

    private data class ParameterScore(
        val record: CoffeeRecord,
        val value: Double,
        val score: Double,
    )

    private data class SegmentCandidate(
        val type: String,
        val name: String,
        val records: List<CoffeeRecord>,
    )

    private data class DailyQuality(
        val brewedAt: Long,
        val score: Double,
        val load: Double,
    )

    private data class SensoryDimensionValues(
        val label: String,
        val values: List<Double>,
    )

    private data class TastingDimensionRow(
        val record: CoffeeRecord,
        val aroma: Double?,
        val acidity: Double?,
        val sweetness: Double?,
        val bitterness: Double?,
        val body: Double?,
        val aftertaste: Double?,
        val overall: Double?,
    )

    private data class TastingParameterSample(
        val record: CoffeeRecord,
        val value: Double,
        val overall: Double,
        val sweetness: Double?,
        val acidity: Double?,
    )

    private data class ConsumptionRecord(
        val record: CoffeeRecord,
        val doseG: Double,
        val caffeineMg: Double,
    )

    private data class WeekdayBandKey(
        val weekday: Int,
        val band: Int,
    )

    private class DeterministicRandom(seed: Long) {
        private var state: Long = seed

        fun nextInt(bound: Int): Int {
            require(bound > 0)
            state = state * 6364136223846793005L + 1442695040888963407L
            return ((state ushr 1) % bound.toLong()).toInt()
        }
    }

    private companion object {
        const val DAY_MILLIS = 24.0 * 60.0 * 60.0 * 1000.0
        const val DAY_MILLIS_LONG = 24L * 60L * 60L * 1000L
        const val SHORT_TERM_DAYS = 7.0
        const val LONG_TERM_DAYS = 42.0
        const val DEFAULT_COMPARISON_DAYS = 30
        const val DEFAULT_CAFFEINE_MG_PER_GRAM = 10.0
        const val EPSILON = 1e-9
    }
}
