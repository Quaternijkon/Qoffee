package com.qoffee.feature.analytics

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalyticsDashboard
import org.junit.Test

class ReviewInsightPresentationTest {

    @Test
    fun buildReviewInsightsShowsEmptyStateWhenSampleCountIsZero() {
        val insights = buildReviewInsights(
            dashboard = AnalyticsDashboard(filter = AnalysisFilter()),
            recordsCount = 0,
        )

        assertThat(insights.first().title).contains("先积累")
        assertThat(insights.first().primaryAction).isEqualTo(ReviewInsightAction.StartRecord)
    }

    @Test
    fun buildReviewInsightsOffersSampleReviewWhenRecordsExist() {
        val insights = buildReviewInsights(
            dashboard = AnalyticsDashboard(filter = AnalysisFilter()),
            recordsCount = 4,
        )

        assertThat(insights.map { it.primaryAction }).contains(ReviewInsightAction.OpenSamples)
    }
}
