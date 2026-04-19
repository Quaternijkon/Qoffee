package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.Archive
import com.qoffee.core.model.ArchiveSeedStatus
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.core.model.ArchiveType
import com.qoffee.core.model.BeanProcessMethod
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RoastLevel
import com.qoffee.data.local.ArchiveDao
import com.qoffee.data.local.ArchiveEntity
import com.qoffee.data.local.BeanProfileDao
import com.qoffee.data.local.BeanProfileEntity
import com.qoffee.data.local.BrewRecordDao
import com.qoffee.data.local.BrewRecordEntity
import com.qoffee.data.local.FlavorTagDao
import com.qoffee.data.local.FlavorTagEntity
import com.qoffee.data.local.GrinderProfileDao
import com.qoffee.data.local.GrinderProfileEntity
import com.qoffee.data.local.QoffeeDatabase
import com.qoffee.data.local.RecipeTemplateDao
import com.qoffee.data.local.RecipeTemplateEntity
import com.qoffee.data.local.RecordFlavorTagCrossRef
import com.qoffee.data.local.RecordFlavorTagDao
import com.qoffee.data.local.SubjectiveEvaluationDao
import com.qoffee.data.local.SubjectiveEvaluationEntity
import com.qoffee.data.mapper.toDomain
import com.qoffee.domain.repository.ArchiveRepository
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.absoluteValue
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class ArchiveRepositoryImpl @Inject constructor(
    private val database: QoffeeDatabase,
    private val archiveDao: ArchiveDao,
    private val beanProfileDao: BeanProfileDao,
    private val grinderProfileDao: GrinderProfileDao,
    private val recipeTemplateDao: RecipeTemplateDao,
    private val brewRecordDao: BrewRecordDao,
    private val subjectiveEvaluationDao: SubjectiveEvaluationDao,
    private val flavorTagDao: FlavorTagDao,
    private val recordFlavorTagDao: RecordFlavorTagDao,
    private val dataStore: DataStore<Preferences>,
    private val timeProvider: TimeProvider,
) : ArchiveRepository {

    override fun observeArchives(): Flow<List<ArchiveSummary>> =
        archiveDao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeCurrentArchive(): Flow<ArchiveSummary?> =
        observeArchives().combine(currentArchiveIdFlow()) { archives, currentId ->
            archives.firstOrNull { it.archive.id == currentId }
        }

    override suspend fun getCurrentArchiveId(): Long? = dataStore.data.first()[PreferenceKeys.CURRENT_ARCHIVE_ID]

    override suspend fun switchArchive(id: Long) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.CURRENT_ARCHIVE_ID] = id
            prefs[PreferenceKeys.LAST_OPENED_ARCHIVE_ID] = id
        }
    }

    override suspend fun createArchive(name: String): Long = database.withTransaction {
        val now = timeProvider.nowMillis()
        val sortOrder = (archiveDao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val id = archiveDao.insert(
            ArchiveEntity(
                name = name,
                typeCode = ArchiveType.NORMAL.code,
                isReadOnly = false,
                createdAt = now,
                updatedAt = now,
                sortOrder = sortOrder,
            ),
        )
        seedPresetFlavorTagsForArchive(id)
        switchArchive(id)
        id
    }

    override suspend fun duplicateArchive(sourceArchiveId: Long, newName: String, switchToNew: Boolean): Long = database.withTransaction {
        val sourceArchive = checkNotNull(archiveDao.getById(sourceArchiveId)) { "源存档不存在。" }
        val now = timeProvider.nowMillis()
        val sortOrder = (archiveDao.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val newArchiveId = archiveDao.insert(
            ArchiveEntity(
                name = newName,
                typeCode = ArchiveType.NORMAL.code,
                isReadOnly = false,
                createdAt = now,
                updatedAt = now,
                sortOrder = sortOrder,
            ),
        )

        val tagIdMap = mutableMapOf<Long, Long>()
        flavorTagDao.getAllByArchive(sourceArchiveId).forEach { tag ->
            val newTagId = flavorTagDao.insert(
                tag.copy(id = 0L, archiveId = newArchiveId),
            )
            tagIdMap[tag.id] = newTagId
        }

        val beanIdMap = mutableMapOf<Long, Long>()
        beanProfileDao.getAllByArchive(sourceArchiveId).forEach { bean ->
            val newBeanId = beanProfileDao.insert(
                bean.copy(id = 0L, archiveId = newArchiveId),
            )
            beanIdMap[bean.id] = newBeanId
        }

        val grinderIdMap = mutableMapOf<Long, Long>()
        grinderProfileDao.getAllByArchive(sourceArchiveId).forEach { grinder ->
            val newGrinderId = grinderProfileDao.insert(
                grinder.copy(id = 0L, archiveId = newArchiveId),
            )
            grinderIdMap[grinder.id] = newGrinderId
        }

        val recipeIdMap = mutableMapOf<Long, Long>()
        recipeTemplateDao.getAllByArchive(sourceArchiveId).forEach { recipe ->
            val newRecipeId = recipeTemplateDao.insert(
                recipe.copy(
                    id = 0L,
                    archiveId = newArchiveId,
                    beanId = recipe.beanId?.let(beanIdMap::get),
                    grinderId = recipe.grinderId?.let(grinderIdMap::get),
                ),
            )
            recipeIdMap[recipe.id] = newRecipeId
        }

        brewRecordDao.getAllByArchive(sourceArchiveId).forEach { row ->
            val sourceRecord = row.record
            val newRecordId = brewRecordDao.insert(
                sourceRecord.copy(
                    id = 0L,
                    archiveId = newArchiveId,
                    beanId = sourceRecord.beanId?.let(beanIdMap::get),
                    recipeTemplateId = sourceRecord.recipeTemplateId?.let(recipeIdMap::get),
                    grinderId = sourceRecord.grinderId?.let(grinderIdMap::get),
                    status = if (sourceRecord.status == RecordStatus.DRAFT.code) RecordStatus.COMPLETED.code else sourceRecord.status,
                    updatedAt = now,
                ),
            )
            row.subjectiveEvaluation?.let { evaluation ->
                subjectiveEvaluationDao.upsert(
                    evaluation.evaluation.copy(recordId = newRecordId),
                )
                recordFlavorTagDao.insertAll(
                    evaluation.flavorTags.mapNotNull { tag ->
                        tagIdMap[tag.id]?.let { newTagId ->
                            RecordFlavorTagCrossRef(recordId = newRecordId, flavorTagId = newTagId)
                        }
                    },
                )
            }
        }

        archiveDao.update(
            sourceArchive.copy(updatedAt = now),
        )

        if (switchToNew) {
            switchArchive(newArchiveId)
        }
        newArchiveId
    }

    override suspend fun renameArchive(id: Long, newName: String) {
        val archive = checkNotNull(archiveDao.getById(id)) { "存档不存在。" }
        require(!archive.isReadOnly) { "示范存档不可重命名。" }
        archiveDao.update(
            archive.copy(
                name = newName,
                updatedAt = timeProvider.nowMillis(),
            ),
        )
    }

    override suspend fun deleteArchive(id: Long) {
        val archive = checkNotNull(archiveDao.getById(id)) { "存档不存在。" }
        require(!archive.isReadOnly) { "示范存档不可删除。" }
        archiveDao.deleteById(id)
        val fallback = archiveDao.findByType(ArchiveType.DEMO.code) ?: archiveDao.getAll().firstOrNull()
        fallback?.let { switchArchive(it.id) }
    }

    override suspend fun copyDemoArchiveAsEditable(name: String): Long {
        val demo = checkNotNull(archiveDao.findByType(ArchiveType.DEMO.code)) { "示范存档不存在。" }
        return duplicateArchive(demo.id, name, switchToNew = true)
    }

    override suspend fun resetDemoArchive(): Long = database.withTransaction {
        archiveDao.findByType(ArchiveType.DEMO.code)?.let { archiveDao.deleteById(it.id) }
        val demoArchiveId = createDemoArchive()
        switchArchive(demoArchiveId)
        demoArchiveId
    }

    override suspend fun seedDemoArchiveIfNeeded(): ArchiveSeedStatus = database.withTransaction {
        val existingDemo = archiveDao.findByType(ArchiveType.DEMO.code)
        val demoArchiveId = existingDemo?.id ?: createDemoArchive()
        val currentArchiveId = getCurrentArchiveId()
        if (currentArchiveId == null) {
            switchArchive(demoArchiveId)
        }
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.HAS_SEEDED_DEMO_ARCHIVE] = true
            prefs[PreferenceKeys.LAST_OPENED_ARCHIVE_ID] = prefs[PreferenceKeys.LAST_OPENED_ARCHIVE_ID] ?: demoArchiveId
            if (prefs[PreferenceKeys.CURRENT_ARCHIVE_ID] == null) {
                prefs[PreferenceKeys.CURRENT_ARCHIVE_ID] = demoArchiveId
            }
        }
        ArchiveSeedStatus(
            hasSeededDemoArchive = true,
            demoArchiveId = demoArchiveId,
            currentArchiveId = getCurrentArchiveId(),
        )
    }

    private fun currentArchiveIdFlow(): Flow<Long?> {
        return dataStore.data.map { prefs -> prefs[PreferenceKeys.CURRENT_ARCHIVE_ID] }
    }

    private suspend fun createDemoArchive(): Long {
        val now = timeProvider.nowMillis()
        val demoArchiveId = archiveDao.insert(
            ArchiveEntity(
                name = "示范存档",
                typeCode = ArchiveType.DEMO.code,
                isReadOnly = true,
                createdAt = now,
                updatedAt = now,
                sortOrder = 0,
            ),
        )
        val tagIds = seedPresetFlavorTagsForArchive(demoArchiveId)
        val beanIds = seedDemoBeans(demoArchiveId, now)
        val grinderIds = seedDemoGrinders(demoArchiveId, now)
        seedDemoRecipes(
            archiveId = demoArchiveId,
            beanIds = beanIds,
            grinderIds = grinderIds,
            now = now,
        )
        seedDemoRecords(
            archiveId = demoArchiveId,
            beanIds = beanIds,
            grinderIds = grinderIds,
            tagIds = tagIds,
            now = now,
        )
        return demoArchiveId
    }

    private suspend fun seedPresetFlavorTagsForArchive(archiveId: Long): Map<String, Long> {
        val names = listOf("花香", "柑橘", "莓果", "热带水果", "茶感", "焦糖", "可可", "巧克力", "坚果", "香料")
        val ids = mutableMapOf<String, Long>()
        names.forEach { name ->
            val id = flavorTagDao.insert(
                FlavorTagEntity(
                    archiveId = archiveId,
                    name = name,
                    isPreset = true,
                ),
            )
            ids[name] = if (id > 0L) id else checkNotNull(flavorTagDao.findByName(archiveId, name)).id
        }
        return ids
    }

    private suspend fun seedDemoBeans(archiveId: Long, now: Long): Map<String, Long> {
        val beans = listOf(
            BeanProfileEntity(archiveId = archiveId, name = "埃塞俄比亚 花魁", roaster = "Qoffee Lab", origin = "耶加雪菲", processValue = BeanProcessMethod.NATURAL.storageValue, variety = "74110", roastLevelValue = RoastLevel.LIGHT_MEDIUM.storageValue, roastDateEpochDay = null, notes = "花果香型示范豆", createdAt = now),
            BeanProfileEntity(archiveId = archiveId, name = "肯尼亚 AA", roaster = "Qoffee Lab", origin = "涅里", processValue = BeanProcessMethod.WASHED.storageValue, variety = "SL28", roastLevelValue = RoastLevel.LIGHT.storageValue, roastDateEpochDay = null, notes = "高酸质示范豆", createdAt = now),
            BeanProfileEntity(archiveId = archiveId, name = "巴拿马 波奎特", roaster = "Qoffee Lab", origin = "波奎特", processValue = BeanProcessMethod.HONEY.storageValue, variety = "卡杜艾", roastLevelValue = RoastLevel.MEDIUM.storageValue, roastDateEpochDay = null, notes = "蜜处理示范豆", createdAt = now),
            BeanProfileEntity(archiveId = archiveId, name = "哥伦比亚 慧兰", roaster = "Qoffee Lab", origin = "慧兰", processValue = BeanProcessMethod.WASHED.storageValue, variety = "卡斯蒂略", roastLevelValue = RoastLevel.MEDIUM_DARK.storageValue, roastDateEpochDay = null, notes = "均衡型示范豆", createdAt = now),
            BeanProfileEntity(archiveId = archiveId, name = "巴西 皇后庄园", roaster = "Qoffee Lab", origin = "米纳斯", processValue = BeanProcessMethod.NATURAL.storageValue, variety = "黄波旁", roastLevelValue = RoastLevel.DARK.storageValue, roastDateEpochDay = null, notes = "坚果可可示范豆", createdAt = now),
        )
        return beans.associate { bean ->
            bean.name to beanProfileDao.insert(bean)
        }
    }

    private suspend fun seedDemoGrinders(archiveId: Long, now: Long): Map<String, Long> {
        val grinders = listOf(
            GrinderProfileEntity(archiveId = archiveId, name = "Comandante C40", minSetting = 14.0, maxSetting = 36.0, stepSize = 1.0, unitLabel = "格", notes = "手冲常用", createdAt = now),
            GrinderProfileEntity(archiveId = archiveId, name = "Niche Zero", minSetting = 5.0, maxSetting = 28.0, stepSize = 0.5, unitLabel = "格", notes = "意式与手冲兼顾", createdAt = now),
            GrinderProfileEntity(archiveId = archiveId, name = "EK43S", minSetting = 3.0, maxSetting = 14.0, stepSize = 0.5, unitLabel = "格", notes = "高均匀度参考", createdAt = now),
        )
        return grinders.associate { grinder ->
            grinder.name to grinderProfileDao.insert(grinder)
        }
    }

    private suspend fun seedDemoRecipes(
        archiveId: Long,
        beanIds: Map<String, Long>,
        grinderIds: Map<String, Long>,
        now: Long,
    ) {
        val recipes = listOf(
            RecipeTemplateEntity(
                archiveId = archiveId,
                name = "花魁手冲基准",
                brewMethodCode = BrewMethod.POUR_OVER.code,
                beanId = beanIds["埃塞俄比亚 花魁"],
                beanNameSnapshot = "埃塞俄比亚 花魁",
                grinderId = grinderIds["Comandante C40"],
                grinderNameSnapshot = "Comandante C40",
                grindSetting = 21.0,
                coffeeDoseG = 15.0,
                brewWaterMl = 240.0,
                bypassWaterMl = 0.0,
                waterTempC = 91.0,
                notes = "适合作为花果香浅烘豆的起始参数。",
                createdAt = now,
                updatedAt = now,
            ),
            RecipeTemplateEntity(
                archiveId = archiveId,
                name = "巴西意式基准",
                brewMethodCode = BrewMethod.ESPRESSO_MACHINE.code,
                beanId = beanIds["巴西 皇后庄园"],
                beanNameSnapshot = "巴西 皇后庄园",
                grinderId = grinderIds["Niche Zero"],
                grinderNameSnapshot = "Niche Zero",
                grindSetting = 11.0,
                coffeeDoseG = 18.0,
                brewWaterMl = 38.0,
                bypassWaterMl = 0.0,
                waterTempC = 92.0,
                notes = "作为坚果可可取向意式的常用起始点。",
                createdAt = now,
                updatedAt = now,
            ),
            RecipeTemplateEntity(
                archiveId = archiveId,
                name = "肯尼亚爱乐压",
                brewMethodCode = BrewMethod.AEROPRESS.code,
                beanId = beanIds["肯尼亚 AA"],
                beanNameSnapshot = "肯尼亚 AA",
                grinderId = grinderIds["EK43S"],
                grinderNameSnapshot = "EK43S",
                grindSetting = 7.5,
                coffeeDoseG = 15.0,
                brewWaterMl = 220.0,
                bypassWaterMl = 40.0,
                waterTempC = 86.0,
                notes = "更强调酸甜与茶感的轻萃方案。",
                createdAt = now,
                updatedAt = now,
            ),
        )
        recipes.forEach { recipeTemplateDao.insert(it) }
    }

    private suspend fun seedDemoRecords(
        archiveId: Long,
        beanIds: Map<String, Long>,
        grinderIds: Map<String, Long>,
        tagIds: Map<String, Long>,
        now: Long,
    ) {
        data class Scenario(
            val beanName: String,
            val grinderName: String,
            val method: BrewMethod,
            val temp: Double,
            val ratio: Double,
            val grind: Double,
            val process: BeanProcessMethod,
            val roast: RoastLevel,
            val tags: List<String>,
            val dayOffset: Int,
        )

        val scenarios = mutableListOf<Scenario>()
        repeat(12) { index ->
            scenarios += Scenario("埃塞俄比亚 花魁", "Comandante C40", BrewMethod.POUR_OVER, 89.0 + (index % 5), 15.0 + (index % 4) * 0.5, 20.0 + (index % 4), BeanProcessMethod.NATURAL, RoastLevel.LIGHT_MEDIUM, listOf("花香", "莓果", "热带水果"), 70 - index)
            scenarios += Scenario("肯尼亚 AA", "EK43S", BrewMethod.POUR_OVER, 88.0 + (index % 4), 15.5 + (index % 3) * 0.4, 7.0 + (index % 3) * 0.5, BeanProcessMethod.WASHED, RoastLevel.LIGHT, listOf("柑橘", "莓果", "茶感"), 55 - index)
            scenarios += Scenario("巴拿马 波奎特", "Comandante C40", BrewMethod.AEROPRESS, 83.0 + (index % 5), 13.0 + (index % 4) * 0.4, 16.0 + (index % 4), BeanProcessMethod.HONEY, RoastLevel.MEDIUM, listOf("焦糖", "热带水果", "花香"), 40 - index)
            scenarios += Scenario("哥伦比亚 慧兰", "Niche Zero", BrewMethod.CLEVER_DRIPPER, 90.0 + (index % 3), 14.5 + (index % 4) * 0.5, 18.0 + (index % 3), BeanProcessMethod.WASHED, RoastLevel.MEDIUM_DARK, listOf("焦糖", "可可", "坚果"), 25 - index)
        }
        repeat(6) { index ->
            scenarios += Scenario("巴西 皇后庄园", "Niche Zero", BrewMethod.ESPRESSO_MACHINE, 92.0, 2.1 + index * 0.08, 10.0 + (index % 3), BeanProcessMethod.NATURAL, RoastLevel.DARK, listOf("巧克力", "坚果", "可可"), 10 - index)
            scenarios += Scenario("巴西 皇后庄园", "Niche Zero", BrewMethod.COLD_BREW, 20.0, 11.0, 22.0, BeanProcessMethod.NATURAL, RoastLevel.DARK, listOf("巧克力", "可可"), 4 - index)
        }

        scenarios.forEachIndexed { index, scenario ->
            val beanId = checkNotNull(beanIds[scenario.beanName])
            val grinderId = checkNotNull(grinderIds[scenario.grinderName])
            val dose = when (scenario.method) {
                BrewMethod.ESPRESSO_MACHINE -> 18.0
                BrewMethod.COLD_BREW -> 55.0
                else -> 15.0
            }
            val brewWater = when (scenario.method) {
                BrewMethod.ESPRESSO_MACHINE -> 38.0
                BrewMethod.COLD_BREW -> 500.0
                BrewMethod.AEROPRESS -> 220.0
                else -> 240.0
            }
            val bypass = when (scenario.method) {
                BrewMethod.AEROPRESS -> 30.0 + (index % 2) * 20.0
                else -> 0.0
            }
            val totalWater = brewWater + bypass
            val brewRatio = totalWater / dose
            val score = computeDemoScore(scenario.method, scenario.temp, scenario.ratio, scenario.grind, scenario.roast, index)
            val recordId = brewRecordDao.insert(
                BrewRecordEntity(
                    archiveId = archiveId,
                    status = RecordStatus.COMPLETED.code,
                    brewMethodCode = scenario.method.code,
                    beanId = beanId,
                    beanNameSnapshot = scenario.beanName,
                    beanRoastLevelSnapshotValue = scenario.roast.storageValue,
                    beanProcessMethodSnapshotValue = scenario.process.storageValue,
                    grinderId = grinderId,
                    grinderNameSnapshot = scenario.grinderName,
                    grindSetting = scenario.grind,
                    coffeeDoseG = dose,
                    brewWaterMl = brewWater,
                    bypassWaterMl = bypass,
                    waterTempC = if (scenario.method.isHotBrew) scenario.temp else null,
                    notes = buildDemoNote(score, scenario.method, scenario.tags),
                    brewedAt = now - scenario.dayOffset * 24L * 60L * 60L * 1000L,
                    createdAt = now,
                    updatedAt = now,
                    totalWaterMl = totalWater,
                    brewRatio = brewRatio,
                ),
            )
            val aroma = ((score - 4).coerceAtLeast(1)).coerceAtMost(5)
            val acidity = when (scenario.beanName) {
                "肯尼亚 AA" -> 5
                "埃塞俄比亚 花魁" -> 4
                else -> (aroma - 1).coerceAtLeast(1)
            }
            val sweetness = (score - 3).coerceIn(1, 5)
            val bitterness = if (scenario.roast.storageValue >= RoastLevel.DARK.storageValue) 4 else 2
            val body = if (scenario.method == BrewMethod.ESPRESSO_MACHINE || scenario.method == BrewMethod.COLD_BREW) 5 else 3
            val aftertaste = (score - 4).coerceIn(1, 5)
            subjectiveEvaluationDao.upsert(
                SubjectiveEvaluationEntity(
                    recordId = recordId,
                    aroma = aroma,
                    acidity = acidity,
                    sweetness = sweetness,
                    bitterness = bitterness,
                    body = body,
                    aftertaste = aftertaste,
                    overall = score,
                    notes = buildDemoCupNote(score, scenario.method),
                ),
            )
            recordFlavorTagDao.insertAll(
                scenario.tags.mapNotNull { tag ->
                    tagIds[tag]?.let { tagId -> RecordFlavorTagCrossRef(recordId = recordId, flavorTagId = tagId) }
                },
            )
        }
    }

    private fun computeDemoScore(
        method: BrewMethod,
        temp: Double,
        ratio: Double,
        grind: Double,
        roast: RoastLevel,
        index: Int,
    ): Int {
        val targetTemp = when (method) {
            BrewMethod.POUR_OVER -> 91.0
            BrewMethod.AEROPRESS -> 86.0
            BrewMethod.CLEVER_DRIPPER -> 91.0
            BrewMethod.ESPRESSO_MACHINE -> 92.0
            BrewMethod.COLD_BREW -> 20.0
            BrewMethod.MOKA_POT -> 93.0
        }
        val targetRatio = when (method) {
            BrewMethod.ESPRESSO_MACHINE -> 2.3
            BrewMethod.COLD_BREW -> 10.0
            BrewMethod.AEROPRESS -> 16.5
            else -> 16.0
        }
        val tempPenalty = ((temp - targetTemp).absoluteValue / 2.0)
        val ratioPenalty = ((ratio - targetRatio).absoluteValue / 0.6)
        val grindPenalty = when (method) {
            BrewMethod.ESPRESSO_MACHINE -> ((grind - 11.0).absoluteValue / 1.2)
            BrewMethod.POUR_OVER -> ((grind - 21.0).absoluteValue / 2.4)
            else -> ((grind - 18.0).absoluteValue / 3.0)
        }
        val roastBonus = when (roast) {
            RoastLevel.LIGHT, RoastLevel.LIGHT_MEDIUM -> 0.6
            RoastLevel.MEDIUM -> 0.4
            RoastLevel.MEDIUM_DARK -> 0.2
            RoastLevel.DARK -> -0.2
            RoastLevel.EXTREME_DARK -> -0.5
            RoastLevel.EXTREME_LIGHT -> -0.4
        }
        val variation = ((index % 5) - 2) * 0.15
        val score = 9.2 - tempPenalty - ratioPenalty - grindPenalty + roastBonus + variation
        return score.roundToInt().coerceIn(4, 10)
    }

    private fun buildDemoNote(score: Int, method: BrewMethod, tags: List<String>): String {
        val methodText = method.displayName
        val flavorText = tags.joinToString("、")
        return if (score >= 9) {
            "$methodText 下表现非常稳定，风味集中在 $flavorText。"
        } else if (score <= 6) {
            "$methodText 参数偏离最佳区间，整体显得略失衡。"
        } else {
            "$methodText 保持了不错的均衡感，主体风味是 $flavorText。"
        }
    }

    private fun buildDemoCupNote(score: Int, method: BrewMethod): String {
        return when {
            score >= 9 -> "这杯在 ${method.displayName} 场景下表现非常优秀，适合作为参考样本。"
            score >= 7 -> "整体平衡，属于稳定发挥。"
            else -> "作为低分样本保留，用来对比参数偏差对结果的影响。"
        }
    }
}
