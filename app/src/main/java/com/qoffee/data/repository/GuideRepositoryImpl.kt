package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.BypassStage
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.GuideStageCard
import com.qoffee.core.model.GuideTemplate
import com.qoffee.core.model.GuideTemplateConfigJsonCodec
import com.qoffee.core.model.ObjectiveSnapshot
import com.qoffee.core.model.PourStage
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.WaitStage
import com.qoffee.core.model.toObjectiveSnapshot
import com.qoffee.data.local.CollectionEntity
import com.qoffee.data.local.QoffeeDatabase
import com.qoffee.domain.repository.GuideRepository
import com.qoffee.domain.repository.RecordRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private const val COLLECTION_TYPE_GUIDE_TEMPLATE = "guide_template"

@Singleton
class GuideRepositoryImpl @Inject constructor(
    private val database: QoffeeDatabase,
    private val dataStore: DataStore<Preferences>,
    private val recordRepository: RecordRepository,
    private val timeProvider: TimeProvider,
) : GuideRepository {

    private val collectionDao get() = database.collectionDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeGuides(): Flow<List<GuideTemplate>> {
        return currentArchiveIdFlow().flatMapLatest { archiveId ->
            archiveId?.let { safeArchiveId ->
                collectionDao.observeByArchiveAndType(safeArchiveId, COLLECTION_TYPE_GUIDE_TEMPLATE)
                    .onStart { seedBuiltInGuidesForArchive(safeArchiveId) }
                    .map { collections ->
                        collections.mapNotNull { decodeGuide(it) }
                            .sortedWith(compareByDescending<GuideTemplate> { it.isBuiltIn }.thenByDescending { it.updatedAt })
                    }
            } ?: flowOf(emptyList())
        }
    }

    override fun observeGuide(guideId: Long): Flow<GuideTemplate?> {
        return collectionDao.observeById(guideId).map { entity ->
            entity?.let(::decodeGuide)
        }
    }

    override suspend fun getGuide(guideId: Long): GuideTemplate? {
        return collectionDao.getById(guideId)?.let(::decodeGuide)
    }

    override suspend fun createGuideFromRecord(recordId: Long): Long {
        val archiveId = requireCurrentArchiveId()
        val record = checkNotNull(recordRepository.getRecord(recordId)) { "记录不存在。" }
        val now = timeProvider.nowMillis()
        val guide = GuideTemplate(
            archiveId = archiveId,
            title = buildGuideTitle(record),
            description = if (record.waterCurve != null) "来自记录的阶段快照" else "来自记录的简化指导",
            brewMethod = record.brewMethod,
            sourceRecordId = record.id,
            isBuiltIn = false,
            objective = record.toObjectiveSnapshot(),
            stages = buildStagesFromRecord(record),
            createdAt = now,
            updatedAt = now,
        )
        return collectionDao.insert(
            CollectionEntity(
                archiveId = archiveId,
                typeCode = COLLECTION_TYPE_GUIDE_TEMPLATE,
                title = guide.title,
                description = guide.description,
                hypothesis = "",
                configJson = GuideTemplateConfigJsonCodec.encode(guide),
                notes = "",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun seedBuiltInGuides() {
        seedBuiltInGuidesForArchive(requireCurrentArchiveId())
    }

    private fun decodeGuide(entity: CollectionEntity): GuideTemplate? {
        return GuideTemplateConfigJsonCodec.decode(
            id = entity.id,
            archiveId = entity.archiveId,
            title = entity.title,
            description = entity.description,
            hypothesis = entity.hypothesis,
            configJson = entity.configJson,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private suspend fun seedBuiltInGuidesForArchive(archiveId: Long) {
        val now = timeProvider.nowMillis()
        builtInGuides().forEach { guide ->
            if (collectionDao.findByArchiveTypeAndTitle(archiveId, COLLECTION_TYPE_GUIDE_TEMPLATE, guide.title) != null) {
                return@forEach
            }
            collectionDao.insert(
                CollectionEntity(
                    archiveId = archiveId,
                    typeCode = COLLECTION_TYPE_GUIDE_TEMPLATE,
                    title = guide.title,
                    description = guide.description,
                    hypothesis = "",
                    configJson = GuideTemplateConfigJsonCodec.encode(
                        guide.copy(
                            archiveId = archiveId,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    ),
                    notes = "",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun builtInGuides(): List<GuideTemplate> {
        return listOf(
            GuideTemplate(
                title = "V60 15g 日常两段",
                description = "手冲 · 偏日常的稳定模板",
                brewMethod = BrewMethod.POUR_OVER,
                isBuiltIn = true,
                objective = ObjectiveSnapshot(
                    brewMethod = BrewMethod.POUR_OVER,
                    coffeeDoseG = 15.0,
                    brewWaterMl = 240.0,
                    waterTempC = 92.0,
                ),
                stages = listOf(
                    GuideStageCard("prep", "准备", "称 15g 咖啡粉，润纸并预热器具。", 35, "15g / 240ml", "先把总水量固定下来。"),
                    GuideStageCard("bloom", "闷蒸", "注入约 35-40ml 热水，轻轻润湿粉床。", 30, "35-40ml", "让粉层均匀吃水。"),
                    GuideStageCard("main-pour", "主注水", "继续稳定注水到 240ml。", 75, "到 240ml", "保持水柱和流速稳定。"),
                    GuideStageCard("finish", "收尾", "等待液面下降并准备记录风味。", 40, "总时长约 2:20-2:50", "先记下你的第一感受。"),
                ),
            ),
            GuideTemplate(
                title = "V60 18g 三段练习",
                description = "手冲 · 更适合对比研磨与水温",
                brewMethod = BrewMethod.POUR_OVER,
                isBuiltIn = true,
                objective = ObjectiveSnapshot(
                    brewMethod = BrewMethod.POUR_OVER,
                    coffeeDoseG = 18.0,
                    brewWaterMl = 288.0,
                    waterTempC = 91.0,
                ),
                stages = listOf(
                    GuideStageCard("prep", "准备", "称粉、润纸、预热分享壶。", 40, "18g / 288ml", "适合做控制变量练习。"),
                    GuideStageCard("bloom", "闷蒸", "注入 45-50ml，等待排气。", 35, "45-50ml", "粉床均匀比速度更重要。"),
                    GuideStageCard("pour-2", "第二段注水", "注水到 180ml。", 40, "到 180ml", "尽量减少大幅摆动。"),
                    GuideStageCard("pour-3", "第三段注水", "补到 288ml。", 35, "到 288ml", "观察流速变化。"),
                    GuideStageCard("finish", "收尾", "等待滤尽并记录结果。", 45, "总时长约 2:40-3:10", "写下清晰度与甜感。"),
                ),
            ),
            GuideTemplate(
                title = "爱乐压 15g 反置",
                description = "爱乐压 · 日常顺手模板",
                brewMethod = BrewMethod.AEROPRESS,
                isBuiltIn = true,
                objective = ObjectiveSnapshot(
                    brewMethod = BrewMethod.AEROPRESS,
                    coffeeDoseG = 15.0,
                    brewWaterMl = 220.0,
                    waterTempC = 88.0,
                ),
                stages = listOf(
                    GuideStageCard("prep", "准备", "装好反置爱乐压，称 15g 咖啡粉。", 40, "15g / 220ml", "先固定冲法。"),
                    GuideStageCard("pour", "注水与搅拌", "快速注水并轻搅。", 25, "220ml", "不要过度搅拌。"),
                    GuideStageCard("steep", "浸泡", "静置等待。", 60, "浸泡 1:00", "观察香气变化。"),
                    GuideStageCard("press", "压滤", "稳定下压完成萃取。", 30, "20-30 秒", "阻力过大优先检查研磨。"),
                ),
            ),
            GuideTemplate(
                title = "摩卡壶 热水起壶",
                description = "摩卡壶 · 控制尾段苦感",
                brewMethod = BrewMethod.MOKA_POT,
                isBuiltIn = true,
                objective = ObjectiveSnapshot(
                    brewMethod = BrewMethod.MOKA_POT,
                    waterTempC = 92.0,
                ),
                stages = listOf(
                    GuideStageCard("prep", "准备壶体", "下壶装热水，中层装粉并抹平。", 50, "热水起壶", "减少过度加热。"),
                    GuideStageCard("extract", "观察萃取", "中小火观察稳定出液。", 90, "中小火", "声音和流速很关键。"),
                    GuideStageCard("finish", "及时离火", "颜色变浅前停止萃取并降温。", 20, "及时离火", "避免尾段过萃。"),
                ),
            ),
            GuideTemplate(
                title = "意式 18g 标准双份",
                description = "意式 · 以 1:2 为起点",
                brewMethod = BrewMethod.ESPRESSO_MACHINE,
                isBuiltIn = true,
                objective = ObjectiveSnapshot(
                    brewMethod = BrewMethod.ESPRESSO_MACHINE,
                    coffeeDoseG = 18.0,
                ),
                stages = listOf(
                    GuideStageCard("prep", "准备粉碗", "布粉、压粉并锁上把手。", 40, "18g", "先让布粉稳定。"),
                    GuideStageCard("pull", "开始萃取", "萃取到约 36g 出液。", 30, "1:2 in 25-30s", "秒数和液柱都要一起看。"),
                    GuideStageCard("note", "记录", "记录酸苦平衡与下一轮调整方向。", 25, "复盘", "一次只改一个变量。"),
                ),
            ),
            GuideTemplate(
                title = "意式 18g 偏浓缩",
                description = "意式 · 偏短比例练习模板",
                brewMethod = BrewMethod.ESPRESSO_MACHINE,
                isBuiltIn = true,
                objective = ObjectiveSnapshot(
                    brewMethod = BrewMethod.ESPRESSO_MACHINE,
                    coffeeDoseG = 18.0,
                ),
                stages = listOf(
                    GuideStageCard("prep", "准备粉碗", "完成布粉、压粉并预冲准备。", 40, "18g", "保证每次动作一致。"),
                    GuideStageCard("pull", "开始萃取", "萃取到约 30g 出液。", 28, "1:1.7 左右", "更关注甜感和厚度。"),
                    GuideStageCard("note", "记录", "记录质地、余韵和苦感变化。", 25, "复盘", "判断是否需要放粗研磨。"),
                ),
            ),
        )
    }

    private fun buildGuideTitle(record: CoffeeRecord): String {
        val bean = record.beanNameSnapshot ?: record.brewMethod?.displayName ?: "记录"
        return "$bean 指导"
    }

    private fun buildStagesFromRecord(record: CoffeeRecord): List<GuideStageCard> {
        val curve = record.waterCurve
        if (curve != null) {
            return curve.stages.mapIndexed { index, stage ->
                when (stage) {
                    is PourStage -> GuideStageCard(
                        id = "stage-$index",
                        title = if (index == 0) "开始注水" else "继续注水",
                        instruction = "在 ${stage.endTimeSeconds} 秒前把累计注水量带到 ${stage.cumulativeWaterMl}ml。",
                        targetDurationSeconds = stage.endTimeSeconds,
                        targetValueLabel = "${stage.cumulativeWaterMl}ml",
                        tip = stage.quickTemperatureC?.let { "参考水温 ${it.toInt()}°C" }.orEmpty(),
                    )

                    is WaitStage -> GuideStageCard(
                        id = "stage-$index",
                        title = "等待",
                        instruction = "保持当前状态，等待到 ${stage.endTimeSeconds} 秒。",
                        targetDurationSeconds = stage.endTimeSeconds,
                        targetValueLabel = "等待",
                        tip = "观察液面与粉床状态。",
                    )

                    is BypassStage -> GuideStageCard(
                        id = "stage-$index",
                        title = "旁路加水",
                        instruction = "加入 ${stage.waterMl}ml 旁路水并轻轻混匀。",
                        targetDurationSeconds = 20,
                        targetValueLabel = "${stage.waterMl}ml",
                        tip = "记录稀释后的口感变化。",
                    )
                }
            }
        }
        return listOf(
            GuideStageCard("prep", "准备", "准备器具、称粉并确认冲煮参数。", 45, "准备完成", "让器具和参数先稳定。"),
            GuideStageCard("brew", "冲煮", "按照当前记录的参数完成本次冲煮。", 90, "按记录执行", "侧重观察主观变化。"),
            GuideStageCard("finish", "收尾", "记录评分、风味标签和主观笔记。", 35, "完成记录", "先记第一印象再回看参数。"),
        )
    }

    private fun currentArchiveIdFlow(): Flow<Long?> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.CURRENT_ARCHIVE_ID]
    }

    private suspend fun requireCurrentArchiveId(): Long {
        return checkNotNull(dataStore.data.first()[PreferenceKeys.CURRENT_ARCHIVE_ID]) { "当前没有活动存档。" }
    }
}
