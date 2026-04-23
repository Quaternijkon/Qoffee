package com.qoffee.data.repository

import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.BrewSession
import com.qoffee.core.model.BrewStage
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.EntitlementTier
import com.qoffee.core.model.Experiment
import com.qoffee.core.model.ExperimentRun
import com.qoffee.core.model.ExperimentStatus
import com.qoffee.core.model.GuideTemplate
import com.qoffee.core.model.GlossaryTerm
import com.qoffee.core.model.LearningTrack
import com.qoffee.core.model.Lesson
import com.qoffee.core.model.LessonType
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.PracticeBlock
import com.qoffee.core.model.RecipeVersion
import com.qoffee.core.model.ShareCard
import com.qoffee.core.model.SkillLevel
import com.qoffee.core.model.TroubleshootingItem
import com.qoffee.core.model.UserEntitlements
import com.qoffee.domain.repository.EntitlementRepository
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.GuideRepository
import com.qoffee.domain.repository.LearningRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.domain.repository.SessionRepository
import com.qoffee.domain.repository.ShareRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val timeProvider: TimeProvider,
    private val guideRepository: GuideRepository,
) : SessionRepository {

    private val activeSession = MutableStateFlow<BrewSession?>(null)

    override fun observeActiveSession(): Flow<BrewSession?> = activeSession

    override suspend fun startSession(method: BrewMethod, practiceBlockId: String?): BrewSession {
        val now = timeProvider.nowMillis()
        val session = BrewSession(
            id = "session-${method.code}-$now",
            method = method,
            title = "${method.displayName} 主动冲煮会话",
            practiceBlockId = practiceBlockId,
            startedAt = now,
            currentStageStartedAt = now,
            stages = buildStages(method),
        )
        activeSession.value = session
        return session
    }

    override suspend fun startGuideSession(guideId: Long): BrewSession {
        val guide = checkNotNull(guideRepository.getGuide(guideId)) { "指导不存在。" }
        val now = timeProvider.nowMillis()
        val session = BrewSession(
            id = "guide-$guideId-$now",
            method = guide.brewMethod ?: BrewMethod.POUR_OVER,
            title = guide.title,
            sourceGuideId = guide.id,
            startedAt = now,
            currentStageStartedAt = now,
            stages = guide.stages.map { stage ->
                com.qoffee.core.model.BrewStage(
                    id = stage.id,
                    title = stage.title,
                    instruction = stage.instruction,
                    targetDurationSeconds = stage.targetDurationSeconds,
                    targetValueLabel = stage.targetValueLabel,
                    tip = stage.tip,
                )
            },
        )
        activeSession.value = session
        return session
    }

    override suspend fun moveToNextStage() {
        val current = activeSession.value ?: return
        if (current.isCompleted) return
        val nextIndex = (current.currentStageIndex + 1).coerceAtMost(current.stages.lastIndex)
        activeSession.value = current.copy(
            currentStageIndex = nextIndex,
            currentStageStartedAt = timeProvider.nowMillis(),
            isPaused = false,
            pausedAt = null,
        )
    }

    override suspend fun moveToPreviousStage() {
        val current = activeSession.value ?: return
        activeSession.value = current.copy(
            currentStageIndex = (current.currentStageIndex - 1).coerceAtLeast(0),
            currentStageStartedAt = timeProvider.nowMillis(),
            isPaused = false,
            pausedAt = null,
        )
    }

    override suspend fun pauseActiveSession() {
        val current = activeSession.value ?: return
        if (current.isPaused || current.isCompleted) return
        activeSession.value = current.copy(
            isPaused = true,
            pausedAt = timeProvider.nowMillis(),
        )
    }

    override suspend fun resumeActiveSession() {
        val current = activeSession.value ?: return
        if (!current.isPaused || current.isCompleted) return
        val now = timeProvider.nowMillis()
        val pausedDuration = (now - (current.pausedAt ?: now)).coerceAtLeast(0L)
        activeSession.value = current.copy(
            isPaused = false,
            pausedAt = null,
            currentStageStartedAt = current.currentStageStartedAt + pausedDuration,
            accumulatedPauseMillis = current.accumulatedPauseMillis + pausedDuration,
        )
    }

    override suspend fun finishActiveSession() {
        val current = activeSession.value ?: return
        activeSession.value = current.copy(
            isCompleted = true,
            currentStageIndex = current.stages.lastIndex.coerceAtLeast(0),
            isPaused = false,
            pausedAt = null,
            completedAt = timeProvider.nowMillis(),
        )
    }

    override suspend fun discardActiveSession() {
        activeSession.value = null
    }

    private fun buildStages(method: BrewMethod): List<BrewStage> {
        return when (method) {
            BrewMethod.POUR_OVER -> listOf(
                BrewStage("prep", "准备器具", "确认滤杯、滤纸、电子秤和热水都已就位。", 45, "15-18g", "新手优先固定豆量和总水量。"),
                BrewStage("bloom", "闷蒸", "注入少量热水均匀润湿粉床，等待排气。", 30, "2-3 倍粉量", "如果粉床不均匀，后续流速也会不稳定。"),
                BrewStage("main-pour", "主注水", "按目标流速注水到设定总水量。", 75, "240-260ml", "观察流速和水柱高度，尽量减少大幅摆动。"),
                BrewStage("drawdown", "收尾萃取", "等待液面下降并记录总时长与风味预期。", 45, "总时长 2:20-3:00", "时长只是结果，需要和风味一起看。"),
            )
            BrewMethod.AEROPRESS -> listOf(
                BrewStage("prep", "准备", "准备爱乐压、滤纸、研磨和水温。", 45, "15g / 220ml", "先固定倒置或正置法，再做变量调整。"),
                BrewStage("bloom", "注水与搅拌", "快速完成注水并进行初次搅拌。", 30, "10-15 秒搅拌", "不要在同一杯里同时改注水量和搅拌强度。"),
                BrewStage("steep", "浸泡", "等待浸泡结束。", 60, "总浸泡 1:00", "如果风味发闷，可缩短浸泡或调整研磨。"),
                BrewStage("press", "压滤", "稳定下压，记录总时长和阻力感。", 30, "20-30 秒", "阻力过大优先检查研磨是否太细。"),
            )
            BrewMethod.CLEVER_DRIPPER -> listOf(
                BrewStage("prep", "准备", "放置滤纸并预热器具。", 45, "18g / 270ml", "聪明杯适合用来练习浸泡转滤滴的差异。"),
                BrewStage("immersion", "浸泡", "完成主注水并短暂搅拌。", 90, "浸泡 1:30", "如甜感不足，尝试微调浸泡时间。"),
                BrewStage("release", "释放滤滴", "将杯体放到分享壶上开始出液。", 45, "释放后 45 秒", "观察液面下降速度，判断研磨是否偏细。"),
            )
            BrewMethod.MOKA_POT -> listOf(
                BrewStage("prep", "准备壶体", "装水、装粉并确认火力。", 60, "下壶热水", "先用热水起始可以减少过度加热。"),
                BrewStage("extract", "观察萃取", "等待咖啡液稳定流出。", 90, "中小火", "声音和流速变化比时间更重要。"),
                BrewStage("finish", "及时离火", "在颜色变浅前停止萃取。", 20, "及时降温", "尾段过萃很容易让整壶变苦。"),
            )
            BrewMethod.COLD_BREW -> listOf(
                BrewStage("prep", "准备冷萃", "确认粉量、浸泡容器和冷藏空间。", 60, "1:10-1:14", "冷萃建议先固定比例和时间。"),
                BrewStage("steep", "冷藏浸泡", "设置浸泡时长与取出提醒。", 28_800, "8-12 小时", "风味太闷时通常不是继续浸更久。"),
                BrewStage("filter", "过滤与记录", "完成过滤并记录浓度与稀释方案。", 180, "记录稀释比", "原液与饮用比例都应记录。"),
            )
            BrewMethod.ESPRESSO_MACHINE -> listOf(
                BrewStage("prep", "准备粉碗", "确认剂量、分布和压粉。", 45, "18g", "先稳定粉量，再调研磨和时间。"),
                BrewStage("pull", "正式萃取", "记录出液重量、时间与流速变化。", 30, "1:2 in 25-30s", "不要只看秒数，液柱颜色同样重要。"),
                BrewStage("dial-in", "复盘修正", "对照目标杯感记录下一轮调整方向。", 45, "酸/苦/薄/闷", "一次只改一个变量最容易形成结论。"),
            )
        }
    }
}

@Singleton
class LearningRepositoryImpl @Inject constructor() : LearningRepository {

    private val tracks = listOf(
        LearningTrack("track-pour-over-foundations", "7 天手冲入门", "从器具准备、术语理解到第一杯稳定复现。", SkillLevel.BEGINNER, lessonCount = 6, estimatedMinutes = 55),
        LearningTrack("track-home-brew-lab", "家庭咖啡实验室", "把配方、变量和记录串起来，建立自己的实验方法。", SkillLevel.INTERMEDIATE, lessonCount = 5, estimatedMinutes = 48, proOnly = true),
        LearningTrack("track-espresso-dial-in", "家用意式校准", "围绕粉量、时间、出液比和风味诊断建立意式调整框架。", SkillLevel.ADVANCED, lessonCount = 5, estimatedMinutes = 52, proOnly = true),
    )

    private val lessons = listOf(
        Lesson(
            id = "lesson-pour-over-what",
            trackId = "track-pour-over-foundations",
            title = "手冲到底在控制什么",
            summary = "理解粉水比、流速、时长和研磨在一杯手冲里的角色分工。",
            level = SkillLevel.BEGINNER,
            type = LessonType.FOUNDATIONS,
            estimatedMinutes = 8,
            keyPoints = listOf("不要在一杯里同时调整多个核心变量。", "时间只是结果，不是单独的目标。"),
            steps = listOf("先固定豆量和总水量。", "只调整一个变量并记录结果。"),
            glossaryTerms = listOf("粉水比", "闷蒸", "萃取"),
        ),
        Lesson(
            id = "lesson-bloom-why",
            trackId = "track-pour-over-foundations",
            title = "为什么要闷蒸",
            summary = "理解闷蒸对排气、均匀润湿和后续流速的影响。",
            level = SkillLevel.BEGINNER,
            type = LessonType.METHOD,
            estimatedMinutes = 6,
            keyPoints = listOf("闷蒸不是形式动作，它直接影响后续萃取均匀度。"),
            steps = listOf("注入 2-3 倍粉量热水。", "让粉床完全润湿但不要剧烈冲刷。"),
            glossaryTerms = listOf("闷蒸", "流速"),
        ),
        Lesson(
            id = "lesson-grind-calibration",
            trackId = "track-home-brew-lab",
            title = "研磨校准实验怎么做",
            summary = "用三档研磨设计一个可复盘、可下结论的小实验。",
            level = SkillLevel.INTERMEDIATE,
            type = LessonType.FOUNDATIONS,
            estimatedMinutes = 10,
            keyPoints = listOf("同豆、同方法、同水温，只改研磨。", "连续三杯实验优先看趋势，不急着求最佳。"),
            steps = listOf("选一支熟悉的豆子作为实验对象。", "设定细、中、粗三档研磨。", "在实验工作台里比较风味与时长。"),
            glossaryTerms = listOf("研磨", "变量控制"),
            proOnly = true,
        ),
        Lesson(
            id = "lesson-espresso-ratio",
            trackId = "track-espresso-dial-in",
            title = "出液比与秒数怎么配合看",
            summary = "学习在家用意式场景下同时观察时间、出液比和液柱状态。",
            level = SkillLevel.ADVANCED,
            type = LessonType.DEVICE,
            estimatedMinutes = 12,
            keyPoints = listOf("只看秒数很容易误判。", "风味、液柱颜色和出液比必须一起看。"),
            steps = listOf("先稳定粉量。", "观察 1:2 出液比附近的杯感变化。"),
            glossaryTerms = listOf("出液比", "过萃", "欠萃"),
            proOnly = true,
        ),
    )

    private val glossaryTerms = listOf(
        GlossaryTerm("term-bloom", "闷蒸", "在主注水前用少量热水润湿咖啡粉并释放二氧化碳。", "闷蒸有助于粉床均匀受水，减少后续通道效应。", relatedTerms = listOf("流速", "萃取")),
        GlossaryTerm("term-ratio", "粉水比", "咖啡粉与总水量之间的比例。", "它决定杯子整体强度，也是最适合新手先固定的变量。", relatedTerms = listOf("总水量", "浓度")),
        GlossaryTerm("term-extraction", "萃取", "水从咖啡粉中带出可溶物的过程。", "萃取不是越多越好，关键是平衡与目标风味。", relatedTerms = listOf("过萃", "欠萃")),
        GlossaryTerm("term-bypass", "旁路水", "在主萃取后额外加入的稀释水。", "常见于意式美式、部分爱乐压和冷萃浓缩液饮用。", relatedTerms = listOf("总水量")),
        GlossaryTerm("term-body", "醇厚", "入口时对稠度、厚实感和质感的主观感受。", "醇厚不等于苦重，和萃取率、滤纸与方法都相关。", relatedTerms = listOf("甜感", "余韵")),
        GlossaryTerm("term-aftertaste", "余韵", "咽下后仍停留在口中的风味印象。", "稳定的高分杯通常余韵更干净、持续更长。", relatedTerms = listOf("甜感", "酸质")),
    )

    private val troubleshooting = listOf(
        TroubleshootingItem("trouble-sour", "太酸 / 尖锐", likelyCauses = listOf("研磨偏粗", "总时长过短", "水温偏低"), adjustments = listOf("微调更细一档研磨。", "保持其他参数不变，略延长接触时间。", "确认热冲方式的目标水温。"), relatedLessonId = "lesson-grind-calibration"),
        TroubleshootingItem("trouble-bitter", "太苦 / 发涩", likelyCauses = listOf("研磨偏细", "尾段过长", "总萃取过度"), adjustments = listOf("先尝试粗一档研磨。", "收窄尾段时间。", "不要同时增加水温和细度。")),
        TroubleshootingItem("trouble-thin", "太薄 / 没有存在感", likelyCauses = listOf("粉量偏少", "比例过稀", "豆子状态衰退"), adjustments = listOf("先固定总水量，再提高粉量。", "检查豆子开封时间与剩余量。")),
        TroubleshootingItem("trouble-muddy", "发闷 / 杂味重", likelyCauses = listOf("注水扰动过强", "流速波动大", "冷却或过滤不稳定"), adjustments = listOf("减少大幅绕圈和粉床冲刷。", "观察液柱和流速的连续性。")),
    )

    override fun observeTracks(): Flow<List<LearningTrack>> = flowOf(tracks)

    override fun observeLessons(): Flow<List<Lesson>> = flowOf(lessons)

    override fun observeGlossaryTerms(): Flow<List<GlossaryTerm>> = flowOf(glossaryTerms)

    override fun observeTroubleshootingItems(): Flow<List<TroubleshootingItem>> = flowOf(troubleshooting)
}

@Singleton
class ExperimentRepositoryImpl @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val recordRepository: RecordRepository,
    private val timeProvider: TimeProvider,
) {

    private val practiceBlocks = listOf(
        PracticeBlock("block-pour-over-7-day", "7 天手冲入门", "以固定比例和固定豆量建立第一套稳定的手冲节奏。", BrewMethod.POUR_OVER, "建立基础手冲框架", 7, SkillLevel.BEGINNER),
        PracticeBlock("block-grind-ladder", "研磨校准练习", "通过同豆三档研磨找到你设备的工作区间。", BrewMethod.POUR_OVER, "研磨对风味与流速的影响", 3, SkillLevel.INTERMEDIATE, proOnly = true),
        PracticeBlock("block-temp-ab", "水温 AB Test", "同配方对比不同水温下的杯感差异。", BrewMethod.POUR_OVER, "建立参数实验思路", 4, SkillLevel.ADVANCED, proOnly = true),
    )

    fun observePracticeBlocks(): Flow<List<PracticeBlock>> = flowOf(practiceBlocks)

    fun observeRecipeVersions(): Flow<List<RecipeVersion>> = combine(
        recipeRepository.observeRecipes(),
        recordRepository.observeRecentRecords(limit = 6),
    ) { recipes, recentRecords ->
        buildList {
            recipes.sortedByDescending { it.updatedAt }.take(4).forEach { recipe ->
                add(
                    RecipeVersion(
                        id = "recipe-${recipe.id}-v1",
                        baseRecipeId = recipe.id,
                        name = recipe.name,
                        summary = buildString {
                            append(recipe.brewMethod?.displayName ?: "未指定方式")
                            recipe.beanNameSnapshot?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        brewMethod = recipe.brewMethod,
                        versionNumber = 1,
                    ),
                )
            }
            recentRecords
                .filter { it.recipeTemplateId != null || it.recipeNameSnapshot != null }
                .take(4)
                .forEachIndexed { index, record ->
                    add(
                        RecipeVersion(
                            id = "record-${record.id}",
                            baseRecipeId = record.recipeTemplateId,
                            name = record.recipeNameSnapshot ?: "${record.brewMethod?.displayName ?: "记录"} 变体",
                            summary = "来自最近记录 · ${record.beanNameSnapshot ?: "未命名豆子"}",
                            brewMethod = record.brewMethod,
                            sourceRecordId = record.id,
                            versionNumber = index + 2,
                            proOnly = true,
                        ),
                    )
                }
        }.distinctBy { it.id }
    }

    fun observeExperiments(): Flow<List<Experiment>> = combine(
        observePracticeBlocks(),
        observeRecipeVersions(),
    ) { blocks, versions ->
        buildList {
            add(
                Experiment(
                    id = "exp-grind-calibration",
                    title = "同豆三档研磨对比",
                    hypothesis = "更细的研磨能提升甜感，但可能压缩风味清晰度。",
                    brewMethod = BrewMethod.POUR_OVER,
                    comparedParameter = NumericParameter.GRIND_SETTING,
                    status = ExperimentStatus.ACTIVE,
                    practiceBlockId = blocks.firstOrNull { it.id == "block-grind-ladder" }?.id,
                ),
            )
            if (versions.size >= 2) {
                add(
                    Experiment(
                        id = "exp-recipe-variant",
                        title = "配方版本稳定性回看",
                        hypothesis = "当前配方版本是否能在不同批次和不同日期保持稳定。",
                        brewMethod = versions.first().brewMethod,
                        comparedParameter = NumericParameter.BREW_RATIO,
                        status = ExperimentStatus.REVIEW,
                    ),
                )
            }
        }
    }

    fun observeExperimentRuns(): Flow<List<ExperimentRun>> =
        recordRepository.observeRecentRecords(limit = 6).map { records ->
            records.take(6).mapIndexed { index, record ->
                ExperimentRun(
                    id = "run-${record.id}",
                    experimentId = if (index % 2 == 0) "exp-grind-calibration" else "exp-recipe-variant",
                    label = record.recipeNameSnapshot ?: record.beanNameSnapshot ?: "最近记录",
                    recordId = record.id,
                    notes = record.notes.take(40),
                    score = record.subjectiveEvaluation?.overall,
                    deltaSummary = buildRunDeltaSummary(record),
                )
            }
        }

    fun observeBeanInventory(): Flow<List<BeanInventory>> =
        recordRepository.observeRecords().map { records ->
            val usageByBean = records
                .filter { it.beanProfileId != null }
                .groupBy { it.beanProfileId }
            usageByBean.entries.take(5).mapIndexed { index, entry ->
                val latest = entry.value.maxByOrNull { it.brewedAt }
                BeanInventory(
                    id = "inventory-${entry.key ?: index}",
                    beanId = entry.key,
                    beanName = latest?.beanNameSnapshot ?: "未命名咖啡豆",
                    batchLabel = "当前批次",
                    purchasedAt = latest?.createdAt,
                    openedAt = latest?.brewedAt,
                    gramsRemaining = (250 - entry.value.size * 18).coerceAtLeast(0),
                    costAmount = null,
                    recommendedWindow = "建议在开封后 2-4 周内重点观察变化",
                    averageWeeklyUsageG = entry.value.size * 18.0 / 4.0,
                )
            }
        }

    private fun buildRunDeltaSummary(record: CoffeeRecord): String? {
        val score = record.subjectiveEvaluation?.overall ?: return null
        val method = record.brewMethod?.displayName ?: "未指定方式"
        return "$method · 总分 $score / 5 · ${record.brewRatio?.let { "粉水比 %.1f".format(it) } ?: "待补参数"}"
    }
}

@Singleton
class EntitlementRepositoryImpl @Inject constructor() : EntitlementRepository {
    private val entitlements = UserEntitlements(
        tier = EntitlementTier.FREE,
        unlockedFeatures = listOf("basic_records", "basic_recipes", "starter_lessons", "glossary"),
        proHighlights = listOf("完整课程库", "实验工作台", "配方版本对比", "高级导出与分享卡"),
    )

    override fun observeEntitlements(): Flow<UserEntitlements> = flowOf(entitlements)
}

@Singleton
class ShareRepositoryImpl @Inject constructor(
    private val learningRepository: LearningRepository,
    private val experimentRepository: ExperimentRepository,
) : ShareRepository {

    override fun observeShareCards(): Flow<List<ShareCard>> = combine(
        learningRepository.observeLessons(),
        experimentRepository.observePracticeBlocks(),
    ) { lessons: List<Lesson>, practiceBlocks: List<PracticeBlock> ->
        buildList<ShareCard> {
            lessons.take(2).forEach { lesson ->
                add(
                    ShareCard(
                        id = "share-lesson-${lesson.id}",
                        title = lesson.title,
                        subtitle = "${lesson.type.displayName} · ${lesson.estimatedMinutes} 分钟",
                        body = lesson.summary,
                        badge = if (lesson.proOnly) "PRO" else "LEARN",
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
                    ),
                )
            }
        }
    }
}
