package com.qoffee.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.WaterCurveTemperatureMode
import com.qoffee.core.model.analyze
import com.qoffee.core.model.buildLegacyWaterCurveSummary
import com.qoffee.core.model.deriveValues
import com.qoffee.core.model.formatWaterCurveNumber
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.ui.components.DashboardActionBar
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.NumericStepField
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.WaterCurveEditor
import com.qoffee.ui.components.WaterCurveStageEditorState
import com.qoffee.ui.components.WaterCurveStageKind
import com.qoffee.ui.components.buildWaterCurveFormResult
import com.qoffee.ui.components.createDefaultWaterCurveStage
import com.qoffee.ui.components.toEditorStageStates
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeEditorUiState(
    val isLoading: Boolean = true,
    val recipe: RecipeTemplate? = null,
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
)

@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeRepository: RecipeRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {
    private val recipeId = savedStateHandle.get<Long>(QoffeeDestinations.recipeIdArg) ?: -1L
    private val recipeState = MutableStateFlow<RecipeTemplate?>(null)

    val uiState: StateFlow<RecipeEditorUiState> = combine(
        recipeState,
        catalogRepository.observeBeanProfiles(),
        catalogRepository.observeGrinderProfiles(),
    ) { recipe, beans, grinders ->
        RecipeEditorUiState(
            isLoading = recipeId > 0L && recipe == null,
            recipe = recipe,
            beans = beans,
            grinders = grinders,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecipeEditorUiState(),
    )

    init {
        viewModelScope.launch {
            recipeState.value = recipeId.takeIf { it > 0L }?.let { recipeRepository.getRecipe(it) }
        }
    }

    suspend fun save(template: RecipeTemplate): Long = recipeRepository.saveRecipe(template)

    suspend fun delete(id: Long) = recipeRepository.deleteRecipe(id)
}

@Composable
fun RecipeEditorRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RecipeEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecipeEditorScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        onSave = { template ->
            viewModel.viewModelScope.launch {
                viewModel.save(template)
                onSaved()
            }
        },
        onDelete = { id ->
            viewModel.viewModelScope.launch {
                viewModel.delete(id)
                onSaved()
            }
        },
    )
}

@Composable
private fun RecipeEditorScreen(
    paddingValues: PaddingValues,
    uiState: RecipeEditorUiState,
    onBack: () -> Unit,
    onSave: (RecipeTemplate) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (uiState.isLoading) {
        EmptyStateCard(
            title = "正在准备配方",
            subtitle = "稍等一下，Qoffee 正在读取当前配方内容。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val initialValue = uiState.recipe
    val initialCurve = initialValue?.waterCurve
    var name by remember(initialValue) { mutableStateOf(initialValue?.name.orEmpty()) }
    var brewMethod by remember(initialValue) { mutableStateOf(initialValue?.brewMethod) }
    var beanId by remember(initialValue) { mutableStateOf(initialValue?.beanProfileId) }
    var grinderId by remember(initialValue) { mutableStateOf(initialValue?.grinderProfileId) }
    var grindSetting by remember(initialValue) { mutableStateOf(initialValue?.grindSetting?.toString().orEmpty()) }
    var coffeeDose by remember(initialValue) { mutableStateOf(initialValue?.coffeeDoseG?.toString().orEmpty()) }
    var brewWater by remember(initialValue) { mutableStateOf(initialValue?.brewWaterMl?.toString().orEmpty()) }
    var bypassWater by remember(initialValue) { mutableStateOf(initialValue?.bypassWaterMl?.toString().orEmpty()) }
    var waterTemp by remember(initialValue) { mutableStateOf(initialValue?.waterTempC?.toString().orEmpty()) }
    var waterCurveTemperatureMode by remember(initialValue) {
        mutableStateOf(initialCurve?.temperatureMode ?: WaterCurveTemperatureMode.POUR_WATER)
    }
    var waterCurveAmbientTemp by remember(initialValue) {
        mutableStateOf(initialCurve?.ambientTempC?.let(::formatWaterCurveNumber).orEmpty())
    }
    var waterCurveContainerType by remember(initialValue) { mutableStateOf(initialCurve?.containerType) }
    var waterCurveStages by remember(initialValue) { mutableStateOf(initialCurve.toEditorStageStates()) }
    var legacyWaterCurveSummary by remember(initialValue) {
        mutableStateOf(
            if (initialValue?.waterCurve == null) {
                buildLegacyWaterCurveSummary(
                    brewWaterMl = initialValue?.brewWaterMl,
                    bypassWaterMl = initialValue?.bypassWaterMl,
                    waterTempC = initialValue?.waterTempC,
                    brewDurationSeconds = null,
                )
            } else {
                null
            },
        )
    }
    var notes by remember(initialValue) { mutableStateOf(initialValue?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val waterCurveResult = buildWaterCurveFormResult(
        temperatureMode = waterCurveTemperatureMode,
        ambientTempText = waterCurveAmbientTemp,
        containerType = waterCurveContainerType,
        stages = waterCurveStages,
        brewMethod = brewMethod,
    )
    val waterCurveDerivedValues = waterCurveResult.curve?.deriveValues(coffeeDose.toDoubleOrNull())
    val selectedBean = uiState.beans.firstOrNull { it.id == beanId }
    val selectedGrinder = uiState.grinders.firstOrNull { it.id == grinderId }
    val waterCurveAnalysis = waterCurveResult.curve?.analyze(
        coffeeDoseG = coffeeDose.toDoubleOrNull(),
        grindSetting = grindSetting.toDoubleOrNull(),
        grinderProfile = selectedGrinder,
        roastLevel = selectedBean?.roastLevel,
        brewMethod = brewMethod,
    )

    fun clearCurveFallbackIfNeeded() {
        if (waterCurveStages.isEmpty() && legacyWaterCurveSummary == null) {
            brewWater = ""
            bypassWater = ""
            waterTemp = ""
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            DashboardActionBar(
                title = if (initialValue == null) "保存新配方" else "保存配方修改",
                subtitle = "配方只保存客观参数，适合在记录页快速复用。",
            ) {
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            error = "配方名称不能为空。"
                            return@Button
                        }
                        if (waterCurveStages.isNotEmpty() && waterCurveResult.errors.isNotEmpty()) {
                            error = waterCurveResult.errors.first()
                            return@Button
                        }
                        error = null
                        onSave(
                            RecipeTemplate(
                                id = initialValue?.id ?: 0L,
                                archiveId = initialValue?.archiveId ?: 0L,
                                name = name.trim(),
                                brewMethod = brewMethod,
                                beanProfileId = beanId,
                                beanNameSnapshot = selectedBean?.name ?: initialValue?.beanNameSnapshot,
                                grinderProfileId = grinderId,
                                grinderNameSnapshot = selectedGrinder?.name ?: initialValue?.grinderNameSnapshot,
                                grindSetting = grindSetting.toDoubleOrNull(),
                                coffeeDoseG = coffeeDose.toDoubleOrNull(),
                                brewWaterMl = waterCurveDerivedValues?.brewWaterMl ?: brewWater.toDoubleOrNull(),
                                bypassWaterMl = waterCurveDerivedValues?.bypassWaterMl ?: bypassWater.toDoubleOrNull(),
                                waterTempC = waterCurveDerivedValues?.waterTempC ?: waterTemp.toDoubleOrNull(),
                                waterCurve = waterCurveResult.curve,
                                notes = notes.trim(),
                                createdAt = initialValue?.createdAt ?: 0L,
                                updatedAt = initialValue?.updatedAt ?: 0L,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存")
                }
                initialValue?.let {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除配方")
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PageHeader(
                title = if (initialValue == null) "新增配方" else "编辑配方",
                subtitle = "只保存客观参数模板，便于在记录页一键采用。",
                eyebrow = "QOFFEE / RECIPE",
            )
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            SectionCard(
                title = "基本信息",
                subtitle = "为配方补齐命名、制作方式和绑定的豆子/磨豆机信息。",
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配方名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = "制作方式",
                    selectedLabel = brewMethod?.displayName,
                    options = BrewMethod.entries.map { DropdownOption(it.displayName, it) },
                    onSelected = { brewMethod = it },
                )
                DropdownField(
                    label = "咖啡豆",
                    selectedLabel = uiState.beans.firstOrNull { it.id == beanId }?.name,
                    options = uiState.beans.map { DropdownOption(it.name, it.id) },
                    onSelected = { beanId = it },
                )
                DropdownField(
                    label = "磨豆机",
                    selectedLabel = uiState.grinders.firstOrNull { it.id == grinderId }?.name,
                    options = uiState.grinders.map { DropdownOption(it.name, it.id) },
                    onSelected = { grinderId = it },
                )
            }
            SectionCard(
                title = "客观参数",
                subtitle = "配方现在也可以直接编辑注水、等待和旁路的完整节奏。",
            ) {
                NumericStepField(
                    label = "研磨格数",
                    value = grindSetting,
                    onValueChange = { grindSetting = it },
                    step = 1.0,
                    decimals = 1,
                )
                NumericStepField(
                    label = "咖啡粉重量（g）",
                    value = coffeeDose,
                    onValueChange = { coffeeDose = it },
                    step = 0.5,
                    decimals = 1,
                    quickValues = listOf("15", "18", "20"),
                )
                WaterCurveEditor(
                    temperatureMode = waterCurveTemperatureMode,
                    onTemperatureModeChange = {
                        waterCurveTemperatureMode = it
                        legacyWaterCurveSummary = null
                    },
                    ambientTempText = waterCurveAmbientTemp,
                    onAmbientTempChange = {
                        waterCurveAmbientTemp = it
                        legacyWaterCurveSummary = null
                    },
                    containerType = waterCurveContainerType,
                    onContainerTypeChange = {
                        waterCurveContainerType = it
                        legacyWaterCurveSummary = null
                    },
                    stages = waterCurveStages,
                    onStageChange = { index, stage ->
                        waterCurveStages = waterCurveStages.mapIndexed { stageIndex, current ->
                            if (stageIndex == index) stage else current
                        }
                        legacyWaterCurveSummary = null
                        clearCurveFallbackIfNeeded()
                    },
                    onAddStage = { kind ->
                        waterCurveStages = waterCurveStages + createDefaultWaterCurveStage(kind, waterCurveStages)
                        legacyWaterCurveSummary = null
                    },
                    onMoveStageUp = { index ->
                        if (index > 0) {
                            waterCurveStages = waterCurveStages.toMutableList().apply {
                                val item = removeAt(index)
                                add(index - 1, item)
                            }
                        }
                    },
                    onMoveStageDown = { index ->
                        if (index < waterCurveStages.lastIndex) {
                            waterCurveStages = waterCurveStages.toMutableList().apply {
                                val item = removeAt(index)
                                add(index + 1, item)
                            }
                        }
                    },
                    onRemoveStage = { index ->
                        waterCurveStages = waterCurveStages.filterIndexed { stageIndex, _ -> stageIndex != index }
                        legacyWaterCurveSummary = null
                        clearCurveFallbackIfNeeded()
                    },
                    previewCurve = waterCurveResult.curve,
                    derivedValues = waterCurveDerivedValues,
                    analysis = waterCurveAnalysis,
                    legacySummary = legacyWaterCurveSummary,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除配方") },
            text = { Text("这个配方会从当前存档中移除。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        initialValue?.let { onDelete(it.id) }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RecipeEditorScreenLegacy(
    paddingValues: PaddingValues,
    uiState: RecipeEditorUiState,
    onBack: () -> Unit,
    onSave: (RecipeTemplate) -> Unit,
    onDelete: (Long) -> Unit,
) {
    RecipeEditorScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        onSave = onSave,
        onDelete = onDelete,
    )
    return
    /*
    if (uiState.isLoading) {
        EmptyStateCard(
            title = "正在准备配方",
            subtitle = "稍等一下，Qoffee 正在读取当前配方内容。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val initialValue = uiState.recipe
    var name by remember(initialValue) { mutableStateOf(initialValue?.name.orEmpty()) }
    var brewMethod by remember(initialValue) { mutableStateOf(initialValue?.brewMethod) }
    var beanId by remember(initialValue) { mutableStateOf(initialValue?.beanProfileId) }
    var grinderId by remember(initialValue) { mutableStateOf(initialValue?.grinderProfileId) }
    var grindSetting by remember(initialValue) { mutableStateOf(initialValue?.grindSetting?.toString().orEmpty()) }
    var coffeeDose by remember(initialValue) { mutableStateOf(initialValue?.coffeeDoseG?.toString().orEmpty()) }
    var brewWater by remember(initialValue) { mutableStateOf(initialValue?.brewWaterMl?.toString().orEmpty()) }
    var bypassWater by remember(initialValue) { mutableStateOf(initialValue?.bypassWaterMl?.toString().orEmpty()) }
    var waterTemp by remember(initialValue) { mutableStateOf(initialValue?.waterTempC?.toString().orEmpty()) }
    var notes by remember(initialValue) { mutableStateOf(initialValue?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            DashboardActionBar(
                title = if (initialValue == null) "保存新配方" else "保存配方修改",
                subtitle = "配方只保存客观参数，适合在记录页快速复用。",
            ) {
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            error = "配方名称不能为空。"
                            return@Button
                        }
                        error = null
                        val selectedBean = uiState.beans.firstOrNull { it.id == beanId }
                        val selectedGrinder = uiState.grinders.firstOrNull { it.id == grinderId }
                        onSave(
                            RecipeTemplate(
                                id = initialValue?.id ?: 0L,
                                archiveId = initialValue?.archiveId ?: 0L,
                                name = name.trim(),
                                brewMethod = brewMethod,
                                beanProfileId = beanId,
                                beanNameSnapshot = selectedBean?.name ?: initialValue?.beanNameSnapshot,
                                grinderProfileId = grinderId,
                                grinderNameSnapshot = selectedGrinder?.name ?: initialValue?.grinderNameSnapshot,
                                grindSetting = grindSetting.toDoubleOrNull(),
                                coffeeDoseG = coffeeDose.toDoubleOrNull(),
                                brewWaterMl = brewWater.toDoubleOrNull(),
                                bypassWaterMl = bypassWater.toDoubleOrNull(),
                                waterTempC = waterTemp.toDoubleOrNull(),
                                notes = notes.trim(),
                                createdAt = initialValue?.createdAt ?: 0L,
                                updatedAt = initialValue?.updatedAt ?: 0L,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存")
                }
                initialValue?.let {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除配方")
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PageHeader(
                title = if (initialValue == null) "新增配方" else "编辑配方",
                subtitle = "只保存客观参数模板，便于在记录页一键采用。",
                eyebrow = "QOFFEE / RECIPE",
            )
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            SectionCard(
                title = "基本信息",
                subtitle = "为配方补齐命名、制作方式和绑定的豆子/磨豆机信息。",
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配方名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownField(
                    label = "制作方式",
                    selectedLabel = brewMethod?.displayName,
                    options = BrewMethod.entries.map { DropdownOption(it.displayName, it) },
                    onSelected = { brewMethod = it },
                )
                DropdownField(
                    label = "咖啡豆",
                    selectedLabel = uiState.beans.firstOrNull { it.id == beanId }?.name,
                    options = uiState.beans.map { DropdownOption(it.name, it.id) },
                    onSelected = { beanId = it },
                )
                DropdownField(
                    label = "磨豆机",
                    selectedLabel = uiState.grinders.firstOrNull { it.id == grinderId }?.name,
                    options = uiState.grinders.map { DropdownOption(it.name, it.id) },
                    onSelected = { grinderId = it },
                )
            }
            SectionCard(
                title = "客观参数",
                subtitle = "这些参数会在新建记录时作为模板直接套用。",
            ) {
                NumericStepField(
                    label = "研磨格数",
                    value = grindSetting,
                    onValueChange = { grindSetting = it },
                    step = 1.0,
                    decimals = 1,
                )
                NumericStepField(
                    label = "咖啡粉重量（g）",
                    value = coffeeDose,
                    onValueChange = { coffeeDose = it },
                    step = 0.5,
                    decimals = 1,
                    quickValues = listOf("15", "18", "20"),
                )
                NumericStepField(
                    label = "冲煮水量（ml）",
                    value = brewWater,
                    onValueChange = { brewWater = it },
                    step = 10.0,
                    decimals = 0,
                    quickValues = listOf("200", "240", "280"),
                )
                NumericStepField(
                    label = "旁路水量（ml）",
                    value = bypassWater,
                    onValueChange = { bypassWater = it },
                    step = 10.0,
                    decimals = 0,
                    quickValues = listOf("0", "40", "60"),
                )
                if (brewMethod?.isHotBrew != false) {
                    NumericStepField(
                        label = "水温（°C）",
                        value = waterTemp,
                        onValueChange = { waterTemp = it },
                        step = 1.0,
                        decimals = 0,
                        quickValues = listOf("90", "92", "94"),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Recipe") },
            text = { Text("This recipe will be removed from the current archive.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        initialValue?.let { onDelete(it.id) }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    */
}
