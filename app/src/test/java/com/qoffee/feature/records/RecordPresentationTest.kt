package com.qoffee.feature.records

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.SubjectiveEvaluation
import org.junit.Test

class RecordPresentationTest {

    @Test
    fun findPreviousComparableRecordReturnsLatestOlderMatch() {
        val records = listOf(
            record(id = 1L, brewedAt = 1_000L, overall = 7, waterTemp = 90.0),
            record(id = 2L, brewedAt = 2_000L, overall = 8, waterTemp = 91.0),
            record(id = 3L, brewedAt = 3_000L, overall = 9, waterTemp = 92.0),
        )

        val previous = findPreviousComparableRecord(records, records.last())

        assertThat(previous?.id).isEqualTo(2L)
    }

    @Test
    fun buildComparisonSummaryMapTracksDeltaAgainstPreviousCup() {
        val records = listOf(
            record(id = 1L, brewedAt = 1_000L, overall = 7, waterTemp = 90.0),
            record(id = 2L, brewedAt = 2_000L, overall = 9, waterTemp = 92.0),
        )

        val comparisonMap = buildComparisonSummaryMap(records)

        assertThat(comparisonMap[2L]?.headline).contains("更高分")
        assertThat(comparisonMap[2L]?.details).contains("总分 +2")
        assertThat(comparisonMap[2L]?.details).contains("水温 +2°C")
    }

    @Test
    fun buildBeanHistorySummaryUsesCompletedScoredRecordsOnly() {
        val summary = buildBeanHistorySummary(
            records = listOf(
                record(id = 1L, brewedAt = 1_000L, overall = 7, waterTemp = 90.0),
                record(id = 2L, brewedAt = 2_000L, overall = 9, waterTemp = 92.0),
            ),
            beanId = 10L,
        )

        assertThat(summary).contains("同豆已记录 2 杯")
        assertThat(summary).contains("平均总分 8.0")
    }

    private fun record(
        id: Long,
        brewedAt: Long,
        overall: Int,
        waterTemp: Double,
    ) = CoffeeRecord(
        id = id,
        status = RecordStatus.COMPLETED,
        brewMethod = BrewMethod.POUR_OVER,
        beanProfileId = 10L,
        beanNameSnapshot = "Kenya AB",
        coffeeDoseG = 15.0,
        brewWaterMl = 240.0,
        waterTempC = waterTemp,
        brewedAt = brewedAt,
        subjectiveEvaluation = SubjectiveEvaluation(
            recordId = id,
            overall = overall,
        ),
    )
}
