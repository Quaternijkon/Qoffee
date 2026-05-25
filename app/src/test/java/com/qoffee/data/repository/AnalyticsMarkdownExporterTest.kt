package com.qoffee.data.repository

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.analytics.AnalyticsEngine
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.SubjectiveEvaluation
import org.junit.Test

class AnalyticsMarkdownExporterTest {

    private val now = 1_700_000_000_000L
    private val engine = AnalyticsEngine(
        timeProvider = object : TimeProvider {
            override fun nowMillis(): Long = now
        },
    )

    @Test
    fun exportBuildsMarkdownPayloadWithStableFileMetadata() {
        val report = engine.buildReport(
            records = listOf(record(id = 1L, beanName = "Kenya AB", overall = 4)),
            filter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL),
        )

        val payload = AnalyticsMarkdownExporter.export(report)

        assertThat(payload.fileName).startsWith("qoffee-analysis-report-")
        assertThat(payload.fileName).endsWith(".md")
        assertThat(payload.mimeType).isEqualTo("text/markdown")
        assertThat(payload.content).contains("# Qoffee 冲煮质量分析报告")
        assertThat(payload.content).contains("样本不足")
        assertThat(payload.content).contains("品鉴图谱")
        assertThat(payload.content).contains("评分直方图")
        assertThat(payload.content).contains("质量管理曲线")
        assertThat(payload.content).contains("最佳可复做曲线")
        assertThat(payload.content).contains("参数分区")
        assertThat(payload.content).contains("统计口径说明")
        assertThat(payload.content).contains("#1 Kenya AB")
    }

    @Test
    fun markdownEscapesTableBreakingCharactersInSourceRecords() {
        val report = engine.buildReport(
            records = listOf(record(id = 1L, beanName = "Kenya | AB\nWashed", overall = 4)),
            filter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL),
        )

        val markdown = AnalyticsMarkdownExporter.buildMarkdown(report)

        assertThat(markdown).contains("Kenya \\| AB Washed")
        assertThat(markdown).doesNotContain("Kenya | AB\nWashed")
    }

    private fun record(
        id: Long,
        beanName: String,
        overall: Int,
    ) = CoffeeRecord(
        id = id,
        status = RecordStatus.COMPLETED,
        brewMethod = BrewMethod.POUR_OVER,
        beanProfileId = 10L,
        beanNameSnapshot = beanName,
        coffeeDoseG = 15.0,
        brewWaterMl = 240.0,
        waterTempC = 92.0,
        totalWaterMl = 240.0,
        brewRatio = 16.0,
        brewDurationSeconds = 150,
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
