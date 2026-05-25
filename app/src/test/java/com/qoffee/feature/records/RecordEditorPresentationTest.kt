package com.qoffee.feature.records

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BrewMethod
import org.junit.Test

class RecordEditorPresentationTest {

    @Test
    fun buildEditorProgressCountsCoreObjectiveFields() {
        val progress = buildEditorProgress(
            objective = ObjectiveFormState(
                brewMethod = BrewMethod.POUR_OVER,
                beanProfileId = 10L,
                coffeeDoseG = "15",
                brewWaterMl = "240",
                brewedAtMillis = 1_000L,
            ),
            subjective = SubjectiveFormState(overall = 4),
        )

        assertThat(progress.completedCount).isGreaterThan(4)
        assertThat(progress.label).contains("已完成")
    }

    @Test
    fun buildParameterSummaryUsesCompactUnits() {
        val summary = buildEditorParameterSummary(
            ObjectiveFormState(
                coffeeDoseG = "15",
                brewWaterMl = "240",
                waterTempC = "92",
                brewDurationSeconds = "150",
                grindSetting = "18",
            ),
        )

        assertThat(summary).contains("粉量" to "15g")
        assertThat(summary).contains("萃取水" to "240ml")
        assertThat(summary).contains("水温" to "92°C")
        assertThat(summary).contains("时长" to "2:30")
        assertThat(summary).contains("研磨" to "18")
    }

    @Test
    fun buildMissingFieldsAllowsMinimalRecordButReportsHelpfulGaps() {
        val gaps = buildEditorMissingFields(
            objective = ObjectiveFormState(),
            subjective = SubjectiveFormState(),
        )

        assertThat(gaps).contains("记录时间")
        assertThat(gaps).contains("总评")
    }
}
