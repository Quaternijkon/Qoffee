package com.qoffee.data.repository

import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.ExperimentProject
import com.qoffee.core.model.Lesson
import com.qoffee.core.model.PracticeBlock
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.ShareCard
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.LearningRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.domain.repository.ShareRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class ShareRepositoryRecordImpl @Inject constructor(
    private val learningRepository: LearningRepository,
    private val experimentRepository: ExperimentRepository,
    private val recordRepository: RecordRepository,
    private val recipeRepository: RecipeRepository,
) : ShareRepository {

    override fun observeShareCards(): Flow<List<ShareCard>> {
        return combine(
            learningRepository.observeLessons(),
            experimentRepository.observePracticeBlocks(),
            recordRepository.observeRecentRecords(limit = 6),
            recipeRepository.observeRecipes(),
            experimentRepository.observeProjects(),
        ) { lessons: List<Lesson>,
            practiceBlocks: List<PracticeBlock>,
            records: List<CoffeeRecord>,
            recipes: List<RecipeTemplate>,
            projects: List<ExperimentProject>,
            ->
            buildShareCards(
                lessons = lessons,
                practiceBlocks = practiceBlocks,
                records = records,
                recipes = recipes,
                projects = projects,
            )
        }
    }
}

internal fun buildShareCards(
    lessons: List<Lesson>,
    practiceBlocks: List<PracticeBlock>,
    records: List<CoffeeRecord>,
    recipes: List<RecipeTemplate>,
    projects: List<ExperimentProject>,
): List<ShareCard> {
    return buildList {
        records
            .filter { (it.subjectiveEvaluation?.overall ?: 0) >= 4 }
            .take(2)
            .forEach { record ->
                add(
                    ShareCard(
                        id = "share-record-${record.id}",
                        title = record.beanNameSnapshot ?: record.brewMethod?.displayName ?: "高分记录",
                        subtitle = "评分 ${record.subjectiveEvaluation?.overall}/5 · 可撤回分享卡",
                        body = listOfNotNull(
                            record.brewMethod?.displayName,
                            record.recipeNameSnapshot,
                            record.grindSetting?.let { "研磨 ${formatShareNumber(it)}" },
                            record.waterTempC?.let { "水温 ${formatShareNumber(it)}C" },
                        ).joinToString(" / "),
                        badge = "RECORD",
                        sourceType = "record",
                        importHint = "分享后只导入客观参数，不公开个人档案。",
                    ),
                )
            }

        recipes.take(2).forEach { recipe ->
            add(
                ShareCard(
                    id = "share-recipe-${recipe.id}",
                    title = recipe.name,
                    subtitle = recipe.brewMethod?.displayName ?: "配方导入链接",
                    body = listOfNotNull(
                        recipe.beanNameSnapshot,
                        recipe.grindSetting?.let { "研磨 ${formatShareNumber(it)}" },
                        recipe.coffeeDoseG?.let { "粉量 ${formatShareNumber(it)}g" },
                        recipe.brewWaterMl?.let { "水量 ${formatShareNumber(it)}ml" },
                    ).joinToString(" / "),
                    badge = "RECIPE",
                    sourceType = "recipe",
                    importHint = "链接导入后会生成本地配方草稿。",
                ),
            )
        }

        projects.take(1).forEach { project ->
            add(
                ShareCard(
                    id = "share-experiment-${project.id}",
                    title = project.title,
                    subtitle = "实验摘要 · ${project.runs.size} 条记录",
                    body = project.hypothesis.ifBlank { "控制变量实验项目" },
                    badge = "LAB",
                    sourceType = "experiment",
                    importHint = "只分享实验摘要，不公开原始记录。",
                ),
            )
        }

        lessons.take(2).forEach { lesson ->
            add(
                ShareCard(
                    id = "share-lesson-${lesson.id}",
                    title = lesson.title,
                    subtitle = "${lesson.type.displayName} · ${lesson.estimatedMinutes} 分钟",
                    body = lesson.summary,
                    badge = if (lesson.proOnly) "PRO" else "LEARN",
                    sourceType = "lesson",
                ),
            )
        }

        practiceBlocks.take(2).forEach { block ->
            add(
                ShareCard(
                    id = "share-block-${block.id}",
                    title = block.title,
                    subtitle = "${block.focus} · ${block.sessionTarget} 次练习",
                    body = block.description,
                    badge = if (block.proOnly) "PRO" else "PRACTICE",
                    sourceType = "practice",
                ),
            )
        }
    }.distinctBy { it.id }.take(6)
}

private fun formatShareNumber(value: Double): String {
    return String.format(java.util.Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}
