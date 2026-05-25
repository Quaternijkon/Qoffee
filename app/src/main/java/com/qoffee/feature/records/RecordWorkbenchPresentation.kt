package com.qoffee.feature.records

import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RecipeTemplate
import java.util.Locale

internal data class WorkbenchHero(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val primaryLabel: String,
    val primaryAction: WorkbenchAction,
    val secondaryActions: List<WorkbenchSecondaryAction> = emptyList(),
)

internal data class WorkbenchSecondaryAction(
    val label: String,
    val action: WorkbenchAction,
)

internal data class WorkbenchSourceItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val action: WorkbenchAction,
)

internal data class PendingFeedbackItem(
    val recordId: Long,
    val title: String,
    val subtitle: String,
)

internal sealed interface WorkbenchAction {
    data object StartBlank : WorkbenchAction
    data object OpenAnalysis : WorkbenchAction
    data class ResumeDraft(val recordId: Long) : WorkbenchAction
    data class StartBean(val beanId: Long) : WorkbenchAction
    data class StartRecipe(val recipeId: Long) : WorkbenchAction
    data class DuplicateRecord(val recordId: Long) : WorkbenchAction
    data class OpenRecord(val recordId: Long) : WorkbenchAction
}

internal fun buildRecordWorkbenchHero(
    activeDraft: CoffeeRecord?,
    inventory: List<BeanInventory>,
    recipes: List<RecipeTemplate>,
    recentRecords: List<CoffeeRecord>,
    scoredRecords: List<CoffeeRecord>,
): WorkbenchHero {
    val secondaryActions = buildList {
        recentRecords.firstOrNull { it.status == RecordStatus.COMPLETED }?.let { record ->
            add(WorkbenchSecondaryAction("复刻上一杯", WorkbenchAction.DuplicateRecord(record.id)))
        }
        if (scoredRecords.size >= 3) {
            add(WorkbenchSecondaryAction("查看复盘", WorkbenchAction.OpenAnalysis))
        }
    }.take(2)

    activeDraft?.let { draft ->
        return WorkbenchHero(
            eyebrow = "今日行动",
            title = "继续当前草稿",
            subtitle = draft.beanNameSnapshot?.let { "已记录到 $it，可直接补全参数或感受。" }
                ?: "有一杯还没完成，继续它比新开一杯更省心。",
            primaryLabel = "继续这杯",
            primaryAction = WorkbenchAction.ResumeDraft(draft.id),
            secondaryActions = secondaryActions,
        )
    }

    inventory.firstOrNull { it.beanId != null && it.remainingStockG > 0.0 }?.let { bean ->
        return WorkbenchHero(
            eyebrow = "今日行动",
            title = if (recentRecords.isEmpty()) "从第一杯开始" else "用库存豆开始一杯",
            subtitle = "${bean.beanName} 还有 ${formatWorkbenchNumber(bean.remainingStockG)}g，点一下直接生成记录草稿。",
            primaryLabel = "用这支豆开始",
            primaryAction = WorkbenchAction.StartBean(checkNotNull(bean.beanId)),
            secondaryActions = secondaryActions,
        )
    }

    recipes.firstOrNull()?.let { recipe ->
        return WorkbenchHero(
            eyebrow = "今日行动",
            title = "复用常用配方",
            subtitle = "${recipe.name} 会预填客观参数，你只需要按本次冲煮微调。",
            primaryLabel = "从配方开始",
            primaryAction = WorkbenchAction.StartRecipe(recipe.id),
            secondaryActions = secondaryActions,
        )
    }

    recentRecords.firstOrNull { it.status == RecordStatus.COMPLETED }?.let { record ->
        return WorkbenchHero(
            eyebrow = "今日行动",
            title = "复刻最近一杯",
            subtitle = record.beanNameSnapshot?.let { "复制 $it 的参数，再按本次结果微调。" }
                ?: "复制上一杯参数，再按本次结果微调。",
            primaryLabel = "复刻下一杯",
            primaryAction = WorkbenchAction.DuplicateRecord(record.id),
            secondaryActions = secondaryActions,
        )
    }

    return WorkbenchHero(
        eyebrow = "今日行动",
        title = "记录第一杯咖啡",
        subtitle = "先保存豆子、方法和时间，感受和高级参数可以之后补全。",
        primaryLabel = "添加记录",
        primaryAction = WorkbenchAction.StartBlank,
        secondaryActions = secondaryActions,
    )
}

internal fun buildWorkbenchSourceItems(
    activeDraft: CoffeeRecord?,
    inventory: List<BeanInventory>,
    recipes: List<RecipeTemplate>,
    recentRecords: List<CoffeeRecord>,
): List<WorkbenchSourceItem> = buildList {
    activeDraft?.let { draft ->
        add(
            WorkbenchSourceItem(
                title = draft.beanNameSnapshot ?: "未完成草稿",
                subtitle = "继续填写这杯",
                badge = "草稿",
                action = WorkbenchAction.ResumeDraft(draft.id),
            ),
        )
    }
    inventory
        .filter { it.beanId != null && it.remainingStockG > 0.0 }
        .take(4)
        .forEach { bean ->
            add(
                WorkbenchSourceItem(
                    title = bean.beanName,
                    subtitle = "剩余 ${formatWorkbenchNumber(bean.remainingStockG)}g",
                    badge = "豆子",
                    action = WorkbenchAction.StartBean(checkNotNull(bean.beanId)),
                ),
            )
        }
    recipes.take(4).forEach { recipe ->
        add(
            WorkbenchSourceItem(
                title = recipe.name,
                subtitle = recipe.brewMethod?.displayName ?: "预填客观参数",
                badge = "配方",
                action = WorkbenchAction.StartRecipe(recipe.id),
            ),
        )
    }
    recentRecords
        .filter { it.status == RecordStatus.COMPLETED }
        .take(4)
        .forEach { record ->
            add(
                WorkbenchSourceItem(
                    title = record.beanNameSnapshot ?: record.brewMethod?.displayName ?: "历史记录",
                    subtitle = record.subjectiveEvaluation?.overall?.let { "总评 $it/5" } ?: "复制参数微调",
                    badge = "历史",
                    action = WorkbenchAction.DuplicateRecord(record.id),
                ),
            )
        }
}.distinctBy { "${it.badge}:${it.title}:${it.subtitle}" }.take(10)

internal fun buildPendingFeedbackItems(records: List<CoffeeRecord>): List<PendingFeedbackItem> {
    return records
        .filter { it.status == RecordStatus.COMPLETED && it.subjectiveEvaluation?.overall == null }
        .sortedByDescending { it.brewedAt }
        .take(3)
        .map { record ->
            PendingFeedbackItem(
                recordId = record.id,
                title = record.beanNameSnapshot ?: record.brewMethod?.displayName ?: "待补感受",
                subtitle = "补一个总评，就能进入复盘样本。",
            )
        }
}

private fun formatWorkbenchNumber(value: Double): String {
    return if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        String.format(Locale.CHINA, "%.1f", value)
    }
}
