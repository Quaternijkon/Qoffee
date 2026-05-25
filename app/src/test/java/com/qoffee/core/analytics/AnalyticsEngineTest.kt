package com.qoffee.core.analytics

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.SampleQuality
import com.qoffee.core.model.SignificanceLevel
import com.qoffee.core.model.SubjectiveEvaluation
import org.junit.Test

class AnalyticsEngineTest {

    private val now = 1_700_000_000_000L
    private val engine = AnalyticsEngine(
        timeProvider = object : TimeProvider {
            override fun nowMillis(): Long = now
        },
    )

    @Test
    fun dashboardBuildsChartsAndInsightsForQualifiedSamples() {
        val records = listOf(
            record(id = 1L, waterTemp = 88.0, overall = 2, ratio = 14.5),
            record(id = 2L, waterTemp = 89.0, overall = 2, ratio = 14.8),
            record(id = 3L, waterTemp = 90.0, overall = 3, ratio = 15.2),
            record(id = 4L, waterTemp = 91.0, overall = 3, ratio = 15.5),
            record(id = 5L, waterTemp = 92.0, overall = 4, ratio = 15.9),
            record(id = 6L, waterTemp = 93.0, overall = 4, ratio = 16.2),
            record(id = 7L, waterTemp = 94.0, overall = 5, ratio = 16.5),
            record(id = 8L, waterTemp = 95.0, overall = 5, ratio = 16.8),
            record(id = 9L, waterTemp = 96.0, overall = 5, ratio = 17.0),
        )

        val dashboard = engine.buildDashboard(
            records = records,
            filter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL),
        )

        assertThat(dashboard.sampleCount).isEqualTo(9)
        assertThat(dashboard.scoreRange).isEqualTo(1..5)
        assertThat(dashboard.methodAverages).isNotEmpty()
        assertThat(dashboard.timelinePoints).hasSize(9)
        assertThat(dashboard.scatterSeries).containsKey(com.qoffee.core.model.NumericParameter.WATER_TEMP)
        assertThat(dashboard.scatterSeries).containsKey(com.qoffee.core.model.NumericParameter.BREW_TIME)
        assertThat(dashboard.parameterCorrelations.map { it.parameter })
            .contains(com.qoffee.core.model.NumericParameter.WATER_TEMP)
        assertThat(dashboard.highlightRecords).isNotEmpty()
        assertThat(dashboard.insightCards).isNotEmpty()
    }

    @Test
    fun dashboardSuppressesRangeInsightsWhenSampleSizeIsTooSmall() {
        val records = listOf(
            record(id = 1L, waterTemp = 90.0, overall = 3, ratio = 15.5),
            record(id = 2L, waterTemp = 91.0, overall = 4, ratio = 16.0),
            record(id = 3L, waterTemp = 92.0, overall = 4, ratio = 16.2),
            record(id = 4L, waterTemp = 93.0, overall = 5, ratio = 16.5),
        )

        val dashboard = engine.buildDashboard(
            records = records,
            filter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL),
        )

        assertThat(dashboard.sampleCount).isEqualTo(4)
        assertThat(dashboard.rangeInsights).isEmpty()
        assertThat(dashboard.parameterCorrelations).isEmpty()
    }

    @Test
    fun reportClassifiesSampleQualityBoundaries() {
        assertThat(reportForCount(4).sampleQuality).isEqualTo(SampleQuality.INSUFFICIENT)
        assertThat(reportForCount(5).sampleQuality).isEqualTo(SampleQuality.EXPLORATORY)
        assertThat(reportForCount(12).sampleQuality).isEqualTo(SampleQuality.TESTABLE)
        assertThat(reportForCount(20).sampleQuality).isEqualTo(SampleQuality.ROBUST)
    }

    @Test
    fun reportFiltersOutDraftsMissingScoresAndOutOfRangeRecords() {
        val records = listOf(
            record(id = 1L, waterTemp = 92.0, overall = 4, ratio = 16.0, brewedAt = now - 2 * DAY_MILLIS),
            record(id = 2L, waterTemp = 93.0, overall = null, ratio = 16.0, brewedAt = now - 2 * DAY_MILLIS),
            record(id = 3L, waterTemp = 94.0, overall = 5, ratio = 16.0, brewedAt = now - 2 * DAY_MILLIS, status = RecordStatus.DRAFT),
            record(id = 4L, waterTemp = 95.0, overall = 5, ratio = 16.0, brewedAt = now - 45 * DAY_MILLIS),
        )

        val report = engine.buildReport(
            records = records,
            filter = AnalysisFilter(timeRange = AnalysisTimeRange.LAST_30_DAYS),
        )

        assertThat(report.summary.sampleCount).isEqualTo(1)
        assertThat(report.dataQuality.missingScoreCount).isEqualTo(1)
        assertThat(report.sourceRecords.map { it.recordId }).containsExactly(1L)
    }

    @Test
    fun reportSuppressesConstantParameterWindow() {
        val records = (1L..12L).map { id ->
            record(id = id, waterTemp = 92.0, overall = (2 + (id % 4)).toInt(), ratio = 16.0)
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

        assertThat(report.parameterFindings.map { it.parameter }).doesNotContain(NumericParameter.WATER_TEMP)
    }

    @Test
    fun reportMarksParameterWindowSignificantWhenCiDoesNotCrossZero() {
        val records = (1L..12L).map { id ->
            val bucket = ((id - 1) / 4).toInt()
            record(
                id = id,
                waterTemp = 88.0 + id,
                overall = when (bucket) {
                    0 -> 2
                    1 -> 3
                    else -> 5
                },
                ratio = 14.0 + id / 10.0,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))
        val waterTemp = report.parameterFindings.first { it.parameter == NumericParameter.WATER_TEMP }

        assertThat(waterTemp.finding.evidence.significance).isEqualTo(SignificanceLevel.P_0_01)
        assertThat(waterTemp.finding.evidence.confidenceLow).isGreaterThan(0.0)
        assertThat(waterTemp.finding.evidence.effectSize).isAtLeast(0.3)
    }

    @Test
    fun reportKeepsNoisyParameterWindowNonSignificant() {
        val scores = listOf(3, 4, 3, 4, 3, 4, 3, 4, 3, 4, 3, 4)
        val records = scores.mapIndexed { index, score ->
            record(
                id = index.toLong() + 1L,
                waterTemp = 88.0 + index,
                overall = score,
                ratio = 16.0,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))
        val waterTemp = report.parameterFindings.first { it.parameter == NumericParameter.WATER_TEMP }

        assertThat(waterTemp.finding.evidence.significance).isEqualTo(SignificanceLevel.NOT_SIGNIFICANT)
    }

    @Test
    fun reportSuppressesSegmentComparisonWhenGroupsAreTooSmall() {
        val records = (1L..6L).map { id ->
            record(
                id = id,
                waterTemp = 90.0 + id,
                overall = if (id <= 3) 5 else 2,
                ratio = 16.0,
                brewMethod = if (id <= 3) BrewMethod.POUR_OVER else BrewMethod.AEROPRESS,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

        assertThat(report.segmentFindings).isEmpty()
    }

    @Test
    fun reportBuildsSegmentComparisonForEligibleGroups() {
        val records = (1L..12L).map { id ->
            record(
                id = id,
                waterTemp = 90.0 + id,
                overall = if (id <= 4) 5 else 2,
                ratio = 16.0,
                brewMethod = if (id <= 4) BrewMethod.POUR_OVER else BrewMethod.AEROPRESS,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

        assertThat(report.segmentFindings.map { it.segmentName }).contains(BrewMethod.POUR_OVER.displayName)
        assertThat(report.segmentFindings.first { it.segmentName == BrewMethod.POUR_OVER.displayName }.finding.evidence.sampleCount)
            .isEqualTo(4)
    }

    @Test
    fun reportTrendIsStableForUnsortedInput() {
        val records = (1L..8L).map { id ->
            record(
                id = id,
                waterTemp = 90.0 + id,
                overall = if (id <= 4) 2 else 5,
                ratio = 16.0,
                brewedAt = now + id * DAY_MILLIS,
            )
        }
        val shuffled = listOf(records[4], records[1], records[7], records[0], records[5], records[2], records[6], records[3])

        val report = engine.buildReport(shuffled, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

        assertThat(report.trendFinding).isNotNull()
        assertThat(report.trendFinding!!.evidence.effectSize).isGreaterThan(0.0)
    }

    @Test
    fun reportIncludesHighLowAndRecentOutliers() {
        val records = listOf(
            record(id = 1L, waterTemp = 90.0, overall = 5, ratio = 16.0, brewedAt = now - 4 * DAY_MILLIS),
            record(id = 2L, waterTemp = 91.0, overall = 4, ratio = 16.0, brewedAt = now - 3 * DAY_MILLIS),
            record(id = 3L, waterTemp = 92.0, overall = 4, ratio = 16.0, brewedAt = now - 2 * DAY_MILLIS),
            record(id = 4L, waterTemp = 93.0, overall = 1, ratio = 16.0, brewedAt = now - DAY_MILLIS),
            record(id = 5L, waterTemp = 94.0, overall = 3, ratio = 16.0, brewedAt = now),
        )

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

        assertThat(report.outliers.map { it.title }).containsAtLeast("高分参考杯", "低分排查杯", "最近样本")
    }

    @Test
    fun reportBuildsQualityFormFromShortAndLongTermEwma() {
        val records = (1L..12L).map { id ->
            record(
                id = id,
                waterTemp = 90.0 + id,
                overall = if (id <= 6) 2 else 5,
                ratio = 16.0,
                brewedAt = now + id * DAY_MILLIS,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

        assertThat(report.qualityForm.points).hasSize(12)
        assertThat(report.qualityForm.shortTermQuality).isGreaterThan(report.qualityForm.longTermQuality)
        assertThat(report.qualityForm.formDelta).isGreaterThan(0.0)
    }

    @Test
    fun reportBuildsQualityCurveForRepeatableWindows() {
        val scores = listOf(2, 3, 4, 5, 5, 5, 3, 2, 4, 4)
        val records = scores.mapIndexed { index, score ->
            record(
                id = index.toLong() + 1L,
                waterTemp = 90.0 + index,
                overall = score,
                ratio = 16.0,
                brewedAt = now + index * DAY_MILLIS,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

        assertThat(report.qualityCurve.map { it.windowSize }).containsAtLeast(1, 3, 5, 10)
        assertThat(report.qualityCurve.first { it.windowSize == 3 }.bestAverageScore).isEqualTo(5.0)
    }

    @Test
    fun reportBuildsParameterZonesBeforeSignificanceThreshold() {
        val records = (1L..6L).map { id ->
            record(
                id = id,
                waterTemp = 88.0 + id,
                overall = if (id <= 3) 3 else 5,
                ratio = 16.0,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

        val zones = report.parameterZones.first { it.parameter == NumericParameter.WATER_TEMP }
        assertThat(zones.zones).hasSize(3)
        assertThat(zones.bestZoneLabel).contains("Z3")
    }

    @Test
    fun reportBuildsPeriodComparisonForCurrentAndPreviousRanges() {
        val records = listOf(
            record(id = 1L, waterTemp = 90.0, overall = 2, ratio = 16.0, brewedAt = now - 50 * DAY_MILLIS),
            record(id = 2L, waterTemp = 91.0, overall = 2, ratio = 16.0, brewedAt = now - 45 * DAY_MILLIS),
            record(id = 3L, waterTemp = 92.0, overall = 5, ratio = 16.0, brewedAt = now - 10 * DAY_MILLIS),
            record(id = 4L, waterTemp = 93.0, overall = 5, ratio = 16.0, brewedAt = now - 5 * DAY_MILLIS),
        )

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.LAST_30_DAYS))

        assertThat(report.periodComparison).isNotNull()
        val comparison = checkNotNull(report.periodComparison)
        assertThat(comparison.currentSampleCount).isEqualTo(2)
        assertThat(comparison.previousSampleCount).isEqualTo(2)
        assertThat(comparison.averageScoreDelta).isEqualTo(3.0)
    }

    @Test
    fun reportBuildsTastingScoreHistogramAndBalancePoints() {
        val records = listOf(
            record(id = 1L, waterTemp = 90.0, overall = 5, ratio = 16.0, sweetness = 5, acidity = 4),
            record(id = 2L, waterTemp = 91.0, overall = 4, ratio = 16.0, sweetness = 4, acidity = 4),
            record(id = 3L, waterTemp = 92.0, overall = 3, ratio = 16.0, sweetness = 2, acidity = 3),
        )

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))
        val charts = report.tastingCharts

        assertThat(charts.scoredSampleCount).isEqualTo(3)
        assertThat(charts.scoreBuckets.first { it.score == 5 }.count).isEqualTo(1)
        assertThat(charts.scoreBuckets.first { it.score == 4 }.share).isWithin(0.001).of(1.0 / 3.0)
        assertThat(charts.balancePoints.map { it.recordId }).containsAtLeast(1L, 2L, 3L)
    }

    @Test
    fun reportBuildsTastingDimensionSignalsFromSensoryScores() {
        val records = (1L..8L).map { id ->
            val score = if (id <= 4) 2 else 5
            record(
                id = id,
                waterTemp = 90.0 + id,
                overall = score,
                ratio = 16.0,
                sweetness = score,
                acidity = 3,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))
        val sweetnessSignal = report.tastingCharts.dimensionSignals.first { it.dimensionLabel == "甜感" }

        assertThat(sweetnessSignal.finding.evidence.effectSize).isGreaterThan(0.9)
        assertThat(sweetnessSignal.finding.summary).contains("甜感")
    }

    @Test
    fun reportBuildsTastingParameterResponseCells() {
        val records = (1L..6L).map { id ->
            record(
                id = id,
                waterTemp = 88.0 + id,
                overall = if (id <= 3) 3 else 5,
                ratio = 16.0,
                sweetness = if (id <= 3) 3 else 5,
                acidity = 4,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))
        val waterTempCells = report.tastingCharts.parameterResponse.filter { it.parameter == NumericParameter.WATER_TEMP }

        assertThat(waterTempCells.map { it.zoneLabel }).containsAtLeast("Z1", "Z2", "Z3")
        assertThat(waterTempCells.maxOf { it.averageOverall }).isEqualTo(5.0)
    }

    @Test
    fun reportBuildsMonotonicScoreConcentrationCurve() {
        val records = listOf(5, 4, 3, 2, 1).mapIndexed { index, score ->
            record(
                id = index.toLong() + 1L,
                waterTemp = 90.0 + index,
                overall = score,
                ratio = 16.0,
                sweetness = score,
                acidity = 3,
            )
        }

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))
        val curve = report.tastingCharts.concentrationCurve

        assertThat(curve).isNotEmpty()
        assertThat(curve.map { it.sampleShare }).isInStrictOrder()
        assertThat(curve.zipWithNext().all { (left, right) -> left.cumulativeScoreShare <= right.cumulativeScoreShare }).isTrue()
    }

    @Test
    fun reportBuildsSweetPointFromHighSweetnessRecords() {
        val records = listOf(
            record(id = 1L, waterTemp = 91.0, overall = 5, ratio = 15.5, sweetness = 5, coffeeDoseG = 15.0),
            record(id = 2L, waterTemp = 92.0, overall = 4, ratio = 16.0, sweetness = 4, coffeeDoseG = 16.0),
            record(id = 3L, waterTemp = 93.0, overall = 5, ratio = 16.5, sweetness = 5, coffeeDoseG = 17.0),
            record(id = 4L, waterTemp = 88.0, overall = 3, ratio = 17.0, sweetness = 2, coffeeDoseG = 18.0),
        )

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

        assertThat(report.sweetPoint.sampleCount).isEqualTo(3)
        assertThat(report.sweetPoint.averageSweetness).isWithin(0.001).of(14.0 / 3.0)
        assertThat(report.sweetPoint.parameterAverages.map { it.label }).contains("粉量")
        assertThat(report.sweetPoint.sourceRecords.map { it.recordId }).containsAtLeast(1L, 2L, 3L)
    }

    @Test
    fun reportBuildsConsumptionChartsFromCoffeeDose() {
        val records = listOf(
            record(id = 1L, waterTemp = 91.0, overall = 5, ratio = 16.0, coffeeDoseG = 15.0),
            record(id = 2L, waterTemp = 92.0, overall = 4, ratio = 16.0, coffeeDoseG = 18.0),
            record(id = 3L, waterTemp = 93.0, overall = 4, ratio = 16.0, coffeeDoseG = 12.0),
            record(id = 4L, waterTemp = 94.0, overall = 3, ratio = 16.0, coffeeDoseG = null),
        )

        val report = engine.buildReport(records, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))
        val consumption = report.consumptionCharts

        assertThat(consumption.recordCount).isEqualTo(3)
        assertThat(consumption.totalDoseG).isWithin(0.001).of(45.0)
        assertThat(consumption.estimatedCaffeineMg).isWithin(0.001).of(450.0)
        assertThat(consumption.beanBuckets.first().beanName).isEqualTo("Kenya AB")
        assertThat(consumption.hourlySpectrum.sumOf { it.recordCount }).isEqualTo(3)
        assertThat(consumption.weekdayHeatmap.sumOf { it.recordCount }).isEqualTo(3)
    }

    private fun record(
        id: Long,
        waterTemp: Double,
        overall: Int?,
        ratio: Double,
        brewTimeSeconds: Int? = 150,
        brewedAt: Long = now - id * 60_000L,
        status: RecordStatus = RecordStatus.COMPLETED,
        brewMethod: BrewMethod = BrewMethod.POUR_OVER,
        aroma: Int = 4,
        acidity: Int = 4,
        sweetness: Int = 4,
        bitterness: Int = 2,
        body: Int = 3,
        aftertaste: Int = 4,
        coffeeDoseG: Double? = 15.0,
    ) = CoffeeRecord(
        id = id,
        status = status,
        brewMethod = brewMethod,
        beanProfileId = 10L,
        beanNameSnapshot = "Kenya AB",
        coffeeDoseG = coffeeDoseG,
        brewWaterMl = 240.0,
        waterTempC = waterTemp,
        totalWaterMl = 240.0,
        brewRatio = ratio,
        brewDurationSeconds = brewTimeSeconds,
        brewedAt = brewedAt,
        subjectiveEvaluation = overall?.let {
            SubjectiveEvaluation(
                recordId = id,
                aroma = aroma,
                acidity = acidity,
                sweetness = sweetness,
                bitterness = bitterness,
                body = body,
                aftertaste = aftertaste,
                overall = it,
            )
        },
    )

    private fun reportForCount(count: Int) = engine.buildReport(
        records = (1L..count.toLong()).map { id ->
            record(id = id, waterTemp = 90.0 + id, overall = 4, ratio = 16.0)
        },
        filter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL),
    )

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
