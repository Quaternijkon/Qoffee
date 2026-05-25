package com.qoffee.data.repository

import com.qoffee.core.model.AnalyticsReport
import com.qoffee.core.model.FileExportPayload
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.StatisticalEvidence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object AnalyticsMarkdownExporter {
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)

    fun export(report: AnalyticsReport): FileExportPayload {
        return FileExportPayload(
            fileName = defaultAnalysisReportFileName(report.generatedAt),
            mimeType = "text/markdown",
            content = buildMarkdown(report),
        )
    }

    fun buildMarkdown(report: AnalyticsReport): String {
        return buildString {
            appendLine("# Qoffee 冲煮质量分析报告")
            appendLine()
            appendLine("- 生成时间：${dateTimeFormat.format(Date(report.generatedAt))}")
            appendLine("- 筛选范围：${report.filterContext.ifBlank { "全部样本" }}")
            appendLine("- 样本质量：${report.sampleQuality.displayName}（${report.sampleQuality.description}）")
            appendLine("- 有效评分样本：${report.summary.sampleCount}")
            appendLine()

            appendLine("## 报告摘要")
            appendLine()
            appendLine(report.executiveSummary.headline.asMarkdownText())
            appendLine()
            appendLine(report.executiveSummary.supportingText.asMarkdownText())
            appendLine()

            appendLine("## 样本质量")
            appendLine()
            appendLine("- 当前筛选记录：${report.dataQuality.totalRecordCount}")
            appendLine("- 完成记录：${report.dataQuality.completedRecordCount}")
            appendLine("- 带总评分记录：${report.dataQuality.scoredRecordCount}")
            appendLine("- 缺少总评分记录：${report.dataQuality.missingScoreCount}")
            appendLine("- 达到可检验门槛的参数：${report.dataQuality.testableParameterCount}")
            if (report.dataQuality.missingParameterCounts.isNotEmpty()) {
                appendLine("- 参数缺失：${report.dataQuality.missingParameterCounts.toReadableMissingParameters()}")
            }
            report.dataQuality.notes.forEach { note ->
                appendLine("- ${note.asMarkdownText()}")
            }
            appendLine()

            appendLine("## 评分与趋势")
            appendLine()
            val score = report.scoreDistribution
            appendLine("- 均分：${score.mean.formatScoreOrDash()}/5")
            appendLine("- 中位数：${score.median.formatScoreOrDash()}/5")
            appendLine("- 标准差：${score.standardDeviation.formatScoreOrDash()}")
            appendLine("- IQR：${score.interquartileRange.formatScoreOrDash()}")
            appendLine("- 最高/最低：${score.maxScore ?: "--"}/${score.minScore ?: "--"}")
            appendLine("- 近 5 杯均分：${score.recentAverage.formatScoreOrDash()}/5")
            report.trendFinding?.let { finding ->
                appendLine()
                appendLine("### 趋势检验")
                appendLine()
                appendLine(finding.summary.asMarkdownText())
                appendLine()
                appendEvidence(finding.evidence)
            }
            appendLine()

            appendLine("## 品鉴图谱")
            appendLine()
            val charts = report.tastingCharts
            appendLine("- 带总评品鉴样本：${charts.scoredSampleCount}")
            appendLine("- 带感官维度样本：${charts.dimensionSampleCount}")
            appendLine("- 图谱摘要：${charts.insight.asMarkdownText()}")
            appendLine()
            appendLine("### 评分直方图")
            appendLine()
            appendLine("| 评分 | 样本 | 占比 |")
            appendLine("| --- | ---: | ---: |")
            charts.scoreBuckets.forEach { bucket ->
                appendLine("| ${bucket.label} | ${bucket.count} | ${bucket.share.formatPercent()} |")
            }
            if (charts.dimensionMatrix.isNotEmpty()) {
                appendLine()
                appendLine("### 感官热力矩阵")
                appendLine()
                appendLine("| 分组 | 维度 | 均值 | 样本 |")
                appendLine("| --- | --- | ---: | ---: |")
                charts.dimensionMatrix.forEach { cell ->
                    appendLine("| ${cell.groupType.asMarkdownText()} ${cell.groupName.asMarkdownText()} | ${cell.dimensionLabel.asMarkdownText()} | ${cell.averageScore.formatScore()} | ${cell.sampleCount} |")
                }
            }
            if (charts.dimensionSignals.isNotEmpty()) {
                appendLine()
                appendLine("### 感官维度信号")
                appendLine()
                charts.dimensionSignals.forEach { signal ->
                    appendLine("- ${signal.finding.summary.asMarkdownText()}")
                    appendEvidence(signal.finding.evidence)
                }
            }
            if (charts.parameterResponse.isNotEmpty()) {
                appendLine()
                appendLine("### 参数响应矩阵")
                appendLine()
                appendLine("| 参数 | 分区 | 范围 | 样本 | 总评 | 甜感 | 酸质 |")
                appendLine("| --- | --- | --- | ---: | ---: | ---: | ---: |")
                charts.parameterResponse.forEach { cell ->
                    appendLine("| ${cell.parameter.displayName.asMarkdownText()} | ${cell.zoneLabel} | ${cell.minValue.formatScore()}-${cell.maxValue.formatScore()}${cell.parameter.unitLabel} | ${cell.sampleCount} | ${cell.averageOverall.formatScore()} | ${cell.averageSweetness.formatScoreOrDash()} | ${cell.averageAcidity.formatScoreOrDash()} |")
                }
            }
            if (charts.concentrationCurve.isNotEmpty()) {
                appendLine()
                appendLine("### 高分集中度")
                appendLine()
                charts.concentrationCurve.forEach { point ->
                    appendLine("- 前 ${point.sampleShare.formatPercent()} 高分样本贡献 ${point.cumulativeScoreShare.formatPercent()} 的总评分。")
                }
            }
            appendLine()

            appendLine("## Sweet Point")
            appendLine()
            val sweetPoint = report.sweetPoint
            appendLine("- 样本：${sweetPoint.sampleCount}")
            appendLine("- 条件：${sweetPoint.targetDescription.asMarkdownText()}")
            appendLine("- 甜感均值：${sweetPoint.averageSweetness.formatScoreOrDash()}/5")
            appendLine("- 总评均值：${sweetPoint.averageOverall.formatScoreOrDash()}/5")
            appendLine("- 结论：${sweetPoint.insight.asMarkdownText()}")
            if (sweetPoint.parameterAverages.isNotEmpty()) {
                appendLine()
                appendLine("| 参数 | 中心值 | 样本 |")
                appendLine("| --- | ---: | ---: |")
                sweetPoint.parameterAverages.forEach { parameter ->
                    appendLine("| ${parameter.label.asMarkdownText()} | ${parameter.value.formatScore()}${parameter.unitLabel.asMarkdownText()} | ${parameter.sampleCount} |")
                }
            }
            if (sweetPoint.sourceRecords.isNotEmpty()) {
                appendLine()
                appendLine("| 参考记录 | 日期 | 评分 | 参数 |")
                appendLine("| --- | --- | ---: | --- |")
                sweetPoint.sourceRecords.forEach { record ->
                    appendLine("| #${record.recordId} ${record.label.asMarkdownText()} | ${dateTimeFormat.format(Date(record.brewedAt))} | ${record.score}/5 | ${record.parameterSummary.asMarkdownText()} |")
                }
            }
            appendLine()

            appendLine("## 消耗与摄入图谱")
            appendLine()
            val consumption = report.consumptionCharts
            appendLine("- 样本：${consumption.recordCount}")
            appendLine("- 咖啡豆消耗：${consumption.totalDoseG.formatScore()}g")
            appendLine("- 咖啡因估算：${consumption.estimatedCaffeineMg.formatScore()}mg")
            appendLine("- 单杯均粉量：${consumption.averageDoseG.formatScoreOrDash()}g")
            appendLine("- 单杯咖啡因估算：${consumption.averageCaffeineMg.formatScoreOrDash()}mg")
            appendLine("- 高频时段：${consumption.peakHourLabel ?: "--"}")
            appendLine("- 结论：${consumption.insight.asMarkdownText()}")
            if (consumption.beanBuckets.isNotEmpty()) {
                appendLine()
                appendLine("### 豆子消耗")
                appendLine()
                appendLine("| 豆子 | 消耗 | 杯数 | 占比 |")
                appendLine("| --- | ---: | ---: | ---: |")
                consumption.beanBuckets.forEach { bucket ->
                    appendLine("| ${bucket.beanName.asMarkdownText()} | ${bucket.doseG.formatScore()}g | ${bucket.recordCount} | ${bucket.share.formatPercent()} |")
                }
            }
            if (consumption.hourlySpectrum.any { it.recordCount > 0 }) {
                appendLine()
                appendLine("### 饮用时间光谱")
                appendLine()
                appendLine("| 小时 | 杯数 | 粉量 | 咖啡因估算 |")
                appendLine("| ---: | ---: | ---: | ---: |")
                consumption.hourlySpectrum.filter { it.recordCount > 0 }.forEach { cell ->
                    appendLine("| ${cell.hour.toString().padStart(2, '0')}:00 | ${cell.recordCount} | ${cell.doseG.formatScore()}g | ${cell.caffeineMg.formatScore()}mg |")
                }
            }
            appendLine()

            appendLine("## 质量管理曲线")
            appendLine()
            val form = report.qualityForm
            if (form.points.isEmpty()) {
                appendLine("暂无可生成长期质量曲线的评分样本。")
            } else {
                appendLine("- 近期质量 7d EWMA：${form.shortTermQuality.formatScoreOrDash()}/5")
                appendLine("- 长期质量 42d EWMA：${form.longTermQuality.formatScoreOrDash()}/5")
                appendLine("- Form：${form.formDelta.formatSignedScoreOrDash()}")
                appendLine("- 近期记录负荷：${form.shortTermLoad.formatScoreOrDash()}")
                appendLine("- 长期记录负荷：${form.longTermLoad.formatScoreOrDash()}")
                appendLine("- 负荷差：${form.loadDelta.formatSignedScoreOrDash()}")
            }
            report.periodComparison?.let { comparison ->
                appendLine()
                appendLine("### 周期对比")
                appendLine()
                appendLine("- ${comparison.currentLabel}：${comparison.currentSampleCount} 杯，均分 ${comparison.currentAverageScore.formatScoreOrDash()}/5，标准差 ${comparison.currentConsistency.formatScoreOrDash()}")
                appendLine("- ${comparison.previousLabel}：${comparison.previousSampleCount} 杯，均分 ${comparison.previousAverageScore.formatScoreOrDash()}/5，标准差 ${comparison.previousConsistency.formatScoreOrDash()}")
                appendLine("- 均分变化：${comparison.averageScoreDelta.formatSignedScoreOrDash()}")
                appendLine("- 稳定性变化：${comparison.consistencyDelta.formatSignedScoreOrDash()}")
                appendLine("- 结论：${comparison.finding.asMarkdownText()}")
            }
            appendLine()

            appendLine("## 最佳可复做曲线")
            appendLine()
            if (report.qualityCurve.isEmpty()) {
                appendLine("暂无足够样本生成复做窗口表现曲线。")
            } else {
                appendLine("| 窗口 | 最佳均分 | 起止时间 | 样本 |")
                appendLine("| --- | ---: | --- | --- |")
                report.qualityCurve.forEach { point ->
                    appendLine("| ${point.label.asMarkdownText()} | ${point.bestAverageScore.formatScore()}/5 | ${dateTimeFormat.format(Date(point.startAt))} - ${dateTimeFormat.format(Date(point.endAt))} | ${point.recordIds.joinToString { "#$it" }} |")
                }
            }
            appendLine()

            appendLine("## 感官画像")
            appendLine()
            if (report.sensoryProfile.isEmpty()) {
                appendLine("暂无维度评分。")
            } else {
                appendLine("| 维度 | 均值 | 标准差 | 样本 |")
                appendLine("| --- | ---: | ---: | ---: |")
                report.sensoryProfile.forEach { dimension ->
                    appendLine("| ${dimension.label.asMarkdownText()} | ${dimension.average.formatScore()} | ${dimension.standardDeviation.formatScore()} | ${dimension.sampleCount} |")
                }
            }
            appendLine()

            appendLine("## 关键参数")
            appendLine()
            if (report.parameterFindings.isEmpty()) {
                appendLine("当前没有达到检验门槛的参数窗口。")
            } else {
                report.parameterFindings.forEach { window ->
                    appendLine("### ${window.finding.title.asMarkdownText()}")
                    appendLine()
                    appendLine("- 推荐窗口：${window.preferredRangeLabel.asMarkdownText()}")
                    appendLine("- 对比方式：${window.comparisonLabel.asMarkdownText()}")
                    appendLine("- 结论：${window.finding.summary.asMarkdownText()}")
                    window.finding.action?.let { appendLine("- 动作：${it.asMarkdownText()}") }
                    appendEvidence(window.finding.evidence)
                    appendLine()
                }
            }

            appendLine("## 参数分区")
            appendLine()
            if (report.parameterZones.isEmpty()) {
                appendLine("当前没有足够样本生成参数分区。")
            } else {
                report.parameterZones.forEach { zoneReport ->
                    appendLine("### ${zoneReport.parameter.displayName.asMarkdownText()}")
                    appendLine()
                    appendLine(zoneReport.insight.asMarkdownText())
                    appendLine()
                    appendLine("| 分区 | 范围 | 样本 | 均分 | 标准差 | 最佳 |")
                    appendLine("| --- | --- | ---: | ---: | ---: | ---: |")
                    zoneReport.zones.forEach { zone ->
                        appendLine("| ${zone.label} | ${zone.minValue.formatScore()}-${zone.maxValue.formatScore()}${zoneReport.parameter.unitLabel} | ${zone.sampleCount} | ${zone.averageScore.formatScore()} | ${zone.standardDeviation.formatScore()} | ${zone.bestScore}/5 |")
                    }
                    appendLine()
                }
            }

            appendLine("## 分组对比")
            appendLine()
            if (report.segmentFindings.isEmpty()) {
                appendLine("当前没有达到检验门槛的分组差异。")
            } else {
                report.segmentFindings.forEach { segment ->
                    appendLine("### ${segment.finding.title.asMarkdownText()}")
                    appendLine()
                    appendLine(segment.finding.summary.asMarkdownText())
                    segment.finding.action?.let { appendLine("- 动作：${it.asMarkdownText()}") }
                    appendEvidence(segment.finding.evidence)
                    appendLine()
                }
            }

            appendLine("## 离群杯与参考杯")
            appendLine()
            if (report.outliers.isEmpty()) {
                appendLine("当前没有可展示的离群或参考样本。")
            } else {
                report.outliers.forEach { item ->
                    appendLine("- #${item.recordId} ${item.title.asMarkdownText()}：${item.subtitle.asMarkdownText()}，${item.reason.asMarkdownText()}")
                }
            }
            appendLine()

            appendLine("## 下一步建议")
            appendLine()
            if (report.executiveSummary.nextActions.isEmpty()) {
                appendLine("- 继续补齐完成记录与评分，保持一次只改一个变量。")
            } else {
                report.executiveSummary.nextActions.forEach { action ->
                    appendLine("- ${action.asMarkdownText()}")
                }
            }
            appendLine()

            appendLine("## 样本引用")
            appendLine()
            if (report.sourceRecords.isEmpty()) {
                appendLine("暂无可引用样本。")
            } else {
                appendLine("| 记录 | 日期 | 评分 | 方式 | 参数 |")
                appendLine("| --- | --- | ---: | --- | --- |")
                report.sourceRecords.forEach { record ->
                    appendLine(
                        "| #${record.recordId} ${record.label.asMarkdownText()} | ${dateTimeFormat.format(Date(record.brewedAt))} | ${record.score}/5 | ${record.brewMethodLabel.asMarkdownText()} | ${record.parameterSummary.asMarkdownText()} |",
                    )
                }
            }
            appendLine()

            appendLine("## 统计口径说明")
            appendLine()
            appendLine("- 本报告只使用当前筛选内已完成且有总评分的 `CoffeeRecord`。")
            appendLine("- 评分为 1-5 主观评分，统计结果只代表个人记录内的经验模式，不宣称普适因果关系。")
            appendLine("- 参数窗口至少需要 12 对样本且每段不少于 3 条，使用三分段均值差与确定性 bootstrap 95% CI。")
            appendLine("- 分组对比要求目标组和对照组各不少于 4 条样本。")
            appendLine("- 品鉴图谱参考直方图、热力矩阵、散点和累计曲线等图表形式，但统计对象始终是杯感评分、冲煮参数、豆子和制作方式。")
            appendLine("- 消耗和咖啡因图谱只使用记录内粉量估算，咖啡因按 10mg/g 粗估，未区分豆种、烘焙、萃取率和饮用残留。")
            appendLine("- p 值与置信区间用于辅助判断，不替代单变量复测。")
        }
    }

    private fun StringBuilder.appendEvidence(evidence: StatisticalEvidence) {
        appendLine("- 样本：${evidence.sampleCount}${evidence.referenceSampleCount?.let { " vs $it" }.orEmpty()}")
        appendLine("- 效应量：${evidence.effectSize.formatSignedScoreOrDash()}")
        appendLine("- 95% CI：${evidence.confidenceLow.formatSignedScoreOrDash()} 到 ${evidence.confidenceHigh.formatSignedScoreOrDash()}")
        appendLine("- p 值：${evidence.pValue.formatPValueOrDash()}（${evidence.significance.displayName}）")
        appendLine("- 方法：${evidence.method.asMarkdownText()}")
        appendLine("- 限制：${evidence.limitation.asMarkdownText()}")
    }

    private fun Map<NumericParameter, Int>.toReadableMissingParameters(): String {
        return entries
            .filter { it.value > 0 }
            .joinToString("；") { "${it.key.displayName} ${it.value}" }
            .ifBlank { "无" }
    }

    private fun String.asMarkdownText(): String {
        return replace("|", "\\|")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
    }

    private fun Double?.formatScoreOrDash(): String = this?.formatScore() ?: "--"

    private fun Double.formatScore(): String = String.format(Locale.CHINA, "%.2f", this).trimEnd('0').trimEnd('.')

    private fun Double.formatPercent(): String = String.format(Locale.CHINA, "%.0f%%", this * 100.0)

    private fun Double?.formatSignedScoreOrDash(): String {
        val value = this ?: return "--"
        return String.format(Locale.CHINA, "%+.2f", value)
    }

    private fun Double?.formatPValueOrDash(): String {
        val value = this ?: return "--"
        return if (value < 0.001) "<0.001" else String.format(Locale.CHINA, "%.3f", value)
    }

    private fun defaultAnalysisReportFileName(generatedAt: Long): String {
        return "qoffee-analysis-report-${fileNameFormat.format(Date(generatedAt))}.md"
    }
}
