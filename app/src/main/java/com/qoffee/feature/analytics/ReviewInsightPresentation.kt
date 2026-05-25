package com.qoffee.feature.analytics

import com.qoffee.core.model.AnalyticsDashboard

internal data class ReviewInsight(
    val title: String,
    val evidence: String,
    val primaryLabel: String,
    val primaryAction: ReviewInsightAction,
    val secondaryLabel: String? = null,
    val secondaryAction: ReviewInsightAction? = null,
)

internal sealed interface ReviewInsightAction {
    data object StartRecord : ReviewInsightAction
    data object OpenSamples : ReviewInsightAction
    data object OpenTrends : ReviewInsightAction
    data object OpenExperiments : ReviewInsightAction
}

internal fun buildReviewInsights(
    dashboard: AnalyticsDashboard,
    recordsCount: Int,
): List<ReviewInsight> {
    val sampleCount = recordsCount.coerceAtLeast(dashboard.sampleCount)
    if (sampleCount <= 0) {
        return listOf(
            ReviewInsight(
                title = "先积累可复盘样本",
                evidence = "还没有足够记录形成稳定结论。先完成一杯，Qoffee 会从记录里生成复盘线索。",
                primaryLabel = "开始记录",
                primaryAction = ReviewInsightAction.StartRecord,
            ),
        )
    }

    return buildList {
        add(
            ReviewInsight(
                title = "查看可复用样本",
                evidence = "当前筛选下有 $sampleCount 条记录，可先从高分或低分样本进入下一杯行动。",
                primaryLabel = "查看样本",
                primaryAction = ReviewInsightAction.OpenSamples,
                secondaryLabel = "看趋势",
                secondaryAction = ReviewInsightAction.OpenTrends,
            ),
        )
        if (sampleCount >= 3) {
            add(
                ReviewInsight(
                    title = "把复盘变成实验",
                    evidence = "样本数量已经足够形成初步假设，可以创建对照实验验证参数变化。",
                    primaryLabel = "进入实验",
                    primaryAction = ReviewInsightAction.OpenExperiments,
                    secondaryLabel = "查看趋势",
                    secondaryAction = ReviewInsightAction.OpenTrends,
                ),
            )
        }
    }
}
