@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.qoffee.feature.experiments

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.analytics.AnalyticsEngine
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.ExperimentProject
import com.qoffee.core.model.ExperimentProjectDraft
import com.qoffee.core.model.ExperimentVariableDefinition
import com.qoffee.core.model.ExperimentVariableLevel
import com.qoffee.core.model.ExperimentVariableType
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.toObjectiveSnapshot
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExperimentLabUiState(
    val projects: List<ExperimentProject> = emptyList(),
    val recentRecords: List<CoffeeRecord> = emptyList(),
    val recipes: List<RecipeTemplate> = emptyList(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
)

data class ExperimentProjectUiState(
    val project: ExperimentProject? = null,
    val records: List<CoffeeRecord> = emptyList(),
    val dashboardSampleCount: Int = 0,
    val topInsight: String? = null,
)

@HiltViewModel
class ExperimentLabViewModel @Inject constructor(
    private val experimentRepository: ExperimentRepository,
    recordRepository: RecordRepository,
    recipeRepository: RecipeRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    val uiState: StateFlow<ExperimentLabUiState> = combine(
        experimentRepository.observeProjects(),
        recordRepository.observeRecentRecords(limit = 8),
        recipeRepository.observeRecipes(),
        catalogRepository.observeBeanProfiles(),
        catalogRepository.observeGrinderProfiles(),
    ) { projects, records, recipes, beans, grinders ->
        ExperimentLabUiState(
            projects = projects,
            recentRecords = records,
            recipes = recipes,
            beans = beans,
            grinders = grinders,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExperimentLabUiState(),
    )

    fun createProject(
        draft: ExperimentProjectDraft,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            onCreated(experimentRepository.createProject(draft))
        }
    }
}

@HiltViewModel
class ExperimentProjectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val experimentRepository: ExperimentRepository,
    recordRepository: RecordRepository,
    analyticsEngine: AnalyticsEngine,
) : ViewModel() {
    private val projectId = checkNotNull(savedStateHandle.get<Long>(QoffeeDestinations.experimentProjectIdArg))

    val uiState: StateFlow<ExperimentProjectUiState> = combine(
        experimentRepository.observeProject(projectId),
        recordRepository.observeRecords(AnalysisFilter(timeRange = AnalysisTimeRange.ALL)),
    ) { project, records ->
        val scopedRecords = project?.runs?.mapNotNull { run ->
            records.firstOrNull { it.id == run.recordId }
        }.orEmpty()
        val dashboard = analyticsEngine.buildDashboard(scopedRecords, AnalysisFilter(timeRange = AnalysisTimeRange.ALL))
        ExperimentProjectUiState(
            project = project,
            records = scopedRecords,
            dashboardSampleCount = dashboard.sampleCount,
            topInsight = dashboard.insightCards.firstOrNull()?.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExperimentProjectUiState(),
    )

    fun openCellDraft(
        cellId: String,
        onOpened: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val draftId = experimentRepository.createOrOpenCellDraft(projectId, cellId)
            onOpened(draftId)
        }
    }
}

@Composable
fun ExperimentLabRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onProjectCreated: (Long) -> Unit,
    viewModel: ExperimentLabViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExperimentLabScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        onOpenProject = onOpenProject,
        onProjectCreated = onProjectCreated,
        onCreateProject = viewModel::createProject,
    )
}

@Composable
fun ExperimentProjectRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onOpenDraft: (Long) -> Unit,
    viewModel: ExperimentProjectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExperimentProjectScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        onOpenCell = { cellId ->
            viewModel.openCellDraft(cellId, onOpenDraft)
        },
    )
}

@Composable
private fun ExperimentLabScreen(
    paddingValues: PaddingValues,
    uiState: ExperimentLabUiState,
    onBack: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onProjectCreated: (Long) -> Unit,
    onCreateProject: (ExperimentProjectDraft, (Long) -> Unit) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = "实验工作台",
            subtitle = "先选基线，再填写变量水平，最后按格子逐杯记录。",
            eyebrow = "QOFFEE / LAB",
        )

        SectionCard(title = "怎么开始", subtitle = "首版按“基线优先”的流程来创建项目。") {
            Text("1. 先选一个基线记录或配方。", style = MaterialTheme.typography.bodyMedium)
            Text("2. 再填写要比较的变量水平，支持豆子、磨豆机、研磨、水温、粉量和萃取水。", style = MaterialTheme.typography.bodyMedium)
            Text("3. 建好网格后，点任意格子就会进入该组合的记录草稿。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("新建实验")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        }

        SectionCard(title = "已有项目") {
            if (uiState.projects.isEmpty()) {
                EmptyStateCard(
                    title = "还没有实验项目",
                    subtitle = "先从一条熟悉的记录开始，手动填几个变量水平，Qoffee 就会帮你生成实验网格。",
                )
            } else {
                uiState.projects.forEach { project ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        onClick = { onOpenProject(project.id) },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(project.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = project.hypothesis.ifBlank { "还没有填写假设" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatChip(text = "${project.variables.size} 个变量")
                                StatChip(text = "${project.cells.size} 个格子")
                                StatChip(text = "${project.runs.size} 条记录")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateExperimentDialog(
            uiState = uiState,
            onDismiss = { showCreateDialog = false },
            onCreateProject = onCreateProject,
            onCreated = {
                showCreateDialog = false
                onProjectCreated(it)
            },
        )
    }
}

@Composable
private fun CreateExperimentDialog(
    uiState: ExperimentLabUiState,
    onDismiss: () -> Unit,
    onCreateProject: (ExperimentProjectDraft, (Long) -> Unit) -> Unit,
    onCreated: (Long) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var hypothesis by remember { mutableStateOf("") }
    var selectedRecordId by remember { mutableLongStateOf(-1L) }
    var selectedRecipeId by remember { mutableLongStateOf(-1L) }
    var beanLevels by remember { mutableStateOf("") }
    var grinderLevels by remember { mutableStateOf("") }
    var grindLevels by remember { mutableStateOf("") }
    var tempLevels by remember { mutableStateOf("") }
    var doseLevels by remember { mutableStateOf("") }
    var waterLevels by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedRecord = uiState.recentRecords.firstOrNull { it.id == selectedRecordId }
    val selectedRecipe = uiState.recipes.firstOrNull { it.id == selectedRecipeId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建实验") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "先选择基线，再按需要填写变量水平。留空的变量不会进入本次实验。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("项目名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = hypothesis, onValueChange = { hypothesis = it }, label = { Text("实验假设") }, modifier = Modifier.fillMaxWidth())
                DropdownField(
                    label = "基线记录",
                    selectedLabel = selectedRecord?.let { it.recipeNameSnapshot ?: it.beanNameSnapshot ?: "记录 ${it.id}" },
                    options = uiState.recentRecords.map { record ->
                        DropdownOption(record.recipeNameSnapshot ?: record.beanNameSnapshot ?: "记录 ${record.id}", record.id)
                    },
                    onSelected = {
                        selectedRecordId = (it ?: -1L)
                        if (it != null) selectedRecipeId = -1L
                    },
                    allowClear = true,
                )
                DropdownField(
                    label = "基线配方",
                    selectedLabel = selectedRecipe?.name,
                    options = uiState.recipes.map { recipe -> DropdownOption(recipe.name, recipe.id) },
                    onSelected = {
                        selectedRecipeId = (it ?: -1L)
                        if (it != null) selectedRecordId = -1L
                    },
                    allowClear = true,
                )
                OutlinedTextField(value = beanLevels, onValueChange = { beanLevels = it }, label = { Text("豆子名称，用逗号分隔") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = grinderLevels, onValueChange = { grinderLevels = it }, label = { Text("磨豆机名称，用逗号分隔") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = grindLevels, onValueChange = { grindLevels = it }, label = { Text("研磨格数，用逗号分隔") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tempLevels, onValueChange = { tempLevels = it }, label = { Text("水温，用逗号分隔") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = doseLevels, onValueChange = { doseLevels = it }, label = { Text("粉量，用逗号分隔") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = waterLevels, onValueChange = { waterLevels = it }, label = { Text("萃取水量，用逗号分隔") }, modifier = Modifier.fillMaxWidth())
                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val baseline = selectedRecord?.toObjectiveSnapshot() ?: selectedRecipe?.toObjectiveSnapshot()
                    if (title.isBlank()) {
                        error = "请先填写项目名称。"
                        return@Button
                    }
                    if (baseline == null) {
                        error = "请先选择一条基线记录或基线配方。"
                        return@Button
                    }
                    val variables = buildVariableDefinitions(
                        beanLevels = beanLevels,
                        grinderLevels = grinderLevels,
                        grindLevels = grindLevels,
                        tempLevels = tempLevels,
                        doseLevels = doseLevels,
                        waterLevels = waterLevels,
                        beans = uiState.beans,
                        grinders = uiState.grinders,
                    )
                    if (variables.isEmpty()) {
                        error = "至少要填写一个变量的水平。"
                        return@Button
                    }
                    error = null
                    onCreateProject(
                        ExperimentProjectDraft(
                            title = title.trim(),
                            hypothesis = hypothesis.trim(),
                            baseRecordId = selectedRecord?.id,
                            baseRecipeId = selectedRecipe?.id,
                            baseline = baseline,
                            variables = variables,
                        ),
                    ) { projectId ->
                        onCreated(projectId)
                    }
                },
            ) {
                Text("生成网格")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ExperimentProjectScreen(
    paddingValues: PaddingValues,
    uiState: ExperimentProjectUiState,
    onBack: () -> Unit,
    onOpenCell: (String) -> Unit,
) {
    val project = uiState.project
    if (project == null) {
        EmptyStateCard(
            title = "未找到实验项目",
            subtitle = "这个项目可能正在加载，或者已经被删除。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }
    var selectedScenarioId by remember(project.id) { mutableStateOf(project.scenarioTabs.firstOrNull()?.first ?: "default") }
    val scenarioCells = project.cells.filter { it.scenarioId == selectedScenarioId }
    val rowLabels = scenarioCells.map { it.yLabel }.distinct()
    val columnLabels = scenarioCells.map { it.xLabel }.distinct()

    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = project.title,
            subtitle = project.hypothesis.ifBlank { "控制变量实验项目" },
            eyebrow = "QOFFEE / LAB / PROJECT",
        )

        SectionCard(title = "项目概览") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip(text = "${project.variables.size} 个变量")
                StatChip(text = "${project.cells.size} 个格子")
                StatChip(text = "${uiState.dashboardSampleCount} 条有效样本")
            }
            uiState.topInsight?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        }

        if (project.scenarioTabs.size > 1) {
            SectionCard(title = "场景切换") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    project.scenarioTabs.forEach { (scenarioId, label) ->
                        FilterChip(
                            selected = scenarioId == selectedScenarioId,
                            onClick = { selectedScenarioId = scenarioId },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        SectionCard(title = "实验网格", subtitle = "点击任意格子，直接进入这组参数的实验记录。") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rowLabels.forEach { rowLabel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        columnLabels.forEach { columnLabel ->
                            val cell = scenarioCells.firstOrNull { it.xLabel == columnLabel && it.yLabel == rowLabel } ?: return@forEach
                            val run = project.runs.lastOrNull { it.cellId == cell.id }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.large,
                                onClick = { onOpenCell(cell.id) },
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(cell.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${columnLabel} / ${rowLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = run?.let { "${it.score ?: "--"}/5${if (it.isOffPlan) " · 已偏离" else ""}" } ?: "点击开始",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionCard(title = "最近样本") {
            if (project.runs.isEmpty()) {
                EmptyStateCard(
                    title = "项目里还没有样本",
                    subtitle = "从上面的格子进入记录后，这里会开始积累你的实验样本。",
                )
            } else {
                project.runs.takeLast(6).reversed().forEach { run ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(run.recordTitle, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = listOfNotNull(run.deltaSummary, if (run.isOffPlan) "偏离计划" else null).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildVariableDefinitions(
    beanLevels: String,
    grinderLevels: String,
    grindLevels: String,
    tempLevels: String,
    doseLevels: String,
    waterLevels: String,
    beans: List<BeanProfile>,
    grinders: List<GrinderProfile>,
): List<ExperimentVariableDefinition> {
    val definitions = mutableListOf<ExperimentVariableDefinition>()
    parseNameLevels(beanLevels, beans.associateBy { it.name }) { index, name, bean ->
        ExperimentVariableLevel(
            id = "bean-$index",
            label = name,
            beanId = bean.id,
        )
    }?.let { definitions += ExperimentVariableDefinition(ExperimentVariableType.BEAN, it) }
    parseNameLevels(grinderLevels, grinders.associateBy { it.name }) { index, name, grinder ->
        ExperimentVariableLevel(
            id = "grinder-$index",
            label = name,
            grinderId = grinder.id,
        )
    }?.let { definitions += ExperimentVariableDefinition(ExperimentVariableType.GRINDER, it) }
    parseNumericLevels(grindLevels, "grind").takeIf { it.isNotEmpty() }?.let {
        definitions += ExperimentVariableDefinition(ExperimentVariableType.GRIND_SETTING, it)
    }
    parseNumericLevels(tempLevels, "temp").takeIf { it.isNotEmpty() }?.let {
        definitions += ExperimentVariableDefinition(ExperimentVariableType.WATER_TEMP, it)
    }
    parseNumericLevels(doseLevels, "dose").takeIf { it.isNotEmpty() }?.let {
        definitions += ExperimentVariableDefinition(ExperimentVariableType.COFFEE_DOSE, it)
    }
    parseNumericLevels(waterLevels, "water").takeIf { it.isNotEmpty() }?.let {
        definitions += ExperimentVariableDefinition(ExperimentVariableType.BREW_WATER, it)
    }
    return definitions
}

private fun <T> parseNameLevels(
    raw: String,
    source: Map<String, T>,
    builder: (Int, String, T) -> ExperimentVariableLevel,
): List<ExperimentVariableLevel>? {
    if (raw.isBlank()) return null
    return raw.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapIndexedNotNull { index, name ->
            source[name]?.let { builder(index, name, it) }
        }
        .takeIf { it.isNotEmpty() }
}

private fun parseNumericLevels(
    raw: String,
    prefix: String,
): List<ExperimentVariableLevel> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { it.toDoubleOrNull()?.let { value -> it to value } }
        .mapIndexed { index, (label, value) ->
            ExperimentVariableLevel(
                id = "$prefix-$index",
                label = label,
                numericValue = value,
            )
        }
}
