package com.qoffee.feature.records

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.SubjectiveEvaluation
import org.junit.Test

class RecordReportPresentationTest {

    @Test
    fun buildRecordReportHeaderShowsScoreAndCoreParameters() {
        val record = CoffeeRecord(
            id = 1L,
            status = RecordStatus.COMPLETED,
            brewMethod = BrewMethod.POUR_OVER,
            beanNameSnapshot = "Kenya AB",
            coffeeDoseG = 15.0,
            brewWaterMl = 240.0,
            waterTempC = 92.0,
            brewDurationSeconds = 150,
            grindSetting = 18.0,
            brewedAt = 1_000L,
            subjectiveEvaluation = SubjectiveEvaluation(recordId = 1L, overall = 4),
        )

        val report = buildRecordReport(record)

        assertThat(report.title).isEqualTo("Kenya AB")
        assertThat(report.scoreText).isEqualTo("4/5")
        assertThat(report.parameters).contains("粉量" to "15g")
        assertThat(report.parameters).contains("萃取水量" to "240ml")
        assertThat(report.parameters).contains("水温" to "92°C")
        assertThat(report.parameters).contains("时长" to "2:30")
    }

    @Test
    fun buildRecordReportUsesMissingScoreFallback() {
        val report = buildRecordReport(
            CoffeeRecord(id = 1L, status = RecordStatus.COMPLETED, brewedAt = 1_000L),
        )

        assertThat(report.scoreText).isEqualTo("未评分")
    }
}
