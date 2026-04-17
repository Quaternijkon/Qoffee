package com.qoffee.core.analytics

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
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
            record(id = 1L, waterTemp = 88.0, overall = 6, ratio = 14.5),
            record(id = 2L, waterTemp = 89.0, overall = 7, ratio = 15.0),
            record(id = 3L, waterTemp = 90.0, overall = 7, ratio = 15.5),
            record(id = 4L, waterTemp = 92.0, overall = 8, ratio = 16.0),
            record(id = 5L, waterTemp = 93.0, overall = 9, ratio = 16.5),
            record(id = 6L, waterTemp = 94.0, overall = 9, ratio = 17.0),
        )

        val dashboard = engine.buildDashboard(
            records = records,
            filter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL),
        )

        assertThat(dashboard.sampleCount).isEqualTo(6)
        assertThat(dashboard.methodAverages).isNotEmpty()
        assertThat(dashboard.timelinePoints).hasSize(6)
        assertThat(dashboard.scatterSeries).containsKey(com.qoffee.core.model.NumericParameter.WATER_TEMP)
        assertThat(dashboard.insightCards).isNotEmpty()
    }

    @Test
    fun dashboardSuppressesInsightsWhenSampleSizeIsTooSmall() {
        val records = listOf(
            record(id = 1L, waterTemp = 90.0, overall = 7, ratio = 15.5),
            record(id = 2L, waterTemp = 91.0, overall = 8, ratio = 16.0),
            record(id = 3L, waterTemp = 92.0, overall = 8, ratio = 16.2),
            record(id = 4L, waterTemp = 93.0, overall = 9, ratio = 16.5),
        )

        val dashboard = engine.buildDashboard(
            records = records,
            filter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL),
        )

        assertThat(dashboard.sampleCount).isEqualTo(4)
        assertThat(dashboard.insightCards).isEmpty()
    }

    private fun record(
        id: Long,
        waterTemp: Double,
        overall: Int,
        ratio: Double,
    ) = CoffeeRecord(
        id = id,
        status = RecordStatus.COMPLETED,
        brewMethod = BrewMethod.POUR_OVER,
        beanProfileId = 10L,
        beanNameSnapshot = "Kenya AB",
        coffeeDoseG = 15.0,
        brewWaterMl = 240.0,
        waterTempC = waterTemp,
        totalWaterMl = 240.0,
        brewRatio = ratio,
        brewedAt = now - id * 60_000L,
        subjectiveEvaluation = SubjectiveEvaluation(
            recordId = id,
            aroma = 4,
            acidity = 4,
            sweetness = 4,
            bitterness = 2,
            body = 3,
            aftertaste = 4,
            overall = overall,
        ),
    )
}
