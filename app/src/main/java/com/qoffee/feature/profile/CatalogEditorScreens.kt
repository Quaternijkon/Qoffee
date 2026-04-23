package com.qoffee.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.qoffee.core.model.BeanProcessMethod
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.GrindCalibrationRange
import com.qoffee.core.model.GrindNormalizationProfile
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RoastLevel
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.ui.components.DashboardActionBar
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.DatePickerField
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.GrindNormalizationChart
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.RoastLevelSelector
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.SingleChoiceChipGroup
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BeanEditorUiState(
    val isLoading: Boolean = true,
    val bean: BeanProfile? = null,
    val nameSuggestions: List<String> = emptyList(),
)

data class GrinderEditorUiState(
    val isLoading: Boolean = true,
    val grinder: GrinderProfile? = null,
)

@HiltViewModel
class BeanEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val beanId = savedStateHandle.get<Long>(QoffeeDestinations.beanIdArg) ?: -1L
    private val uiStateInternal = MutableStateFlow(BeanEditorUiState())
    val uiState: StateFlow<BeanEditorUiState> = uiStateInternal

    init {
        viewModelScope.launch {
            val suggestions = catalogRepository.observeBeanProfiles()
                .first()
                .mapNotNull { it.name.trim().takeIf(String::isNotBlank) }
                .distinct()
            uiStateInternal.value = BeanEditorUiState(
                isLoading = false,
                bean = beanId.takeIf { it > 0L }?.let { catalogRepository.getBeanProfile(it) },
                nameSuggestions = suggestions,
            )
        }
    }

    suspend fun save(profile: BeanProfile): Long = catalogRepository.saveBeanProfile(profile)
}

@HiltViewModel
class GrinderEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val grinderId = savedStateHandle.get<Long>(QoffeeDestinations.grinderIdArg) ?: -1L
    private val uiStateInternal = MutableStateFlow(GrinderEditorUiState())
    val uiState: StateFlow<GrinderEditorUiState> = uiStateInternal

    init {
        viewModelScope.launch {
            uiStateInternal.value = GrinderEditorUiState(
                isLoading = false,
                grinder = grinderId.takeIf { it > 0L }?.let { catalogRepository.getGrinderProfile(it) },
            )
        }
    }

    suspend fun save(profile: GrinderProfile): Long = catalogRepository.saveGrinderProfile(profile)

    suspend fun delete(grinderId: Long) = catalogRepository.deleteGrinderProfile(grinderId)
}

@Composable
fun BeanEditorRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BeanEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BeanEditorScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        onSave = { profile ->
            viewModel.viewModelScope.launch {
                viewModel.save(profile)
                onSaved()
            }
        },
    )
}

@Composable
fun GrinderEditorRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: GrinderEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GrinderEditorScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        onSave = { profile ->
            viewModel.viewModelScope.launch {
                viewModel.save(profile)
                onSaved()
            }
        },
        onDelete = { grinderId ->
            viewModel.viewModelScope.launch {
                viewModel.delete(grinderId)
                onSaved()
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeanEditorScreen(
    paddingValues: PaddingValues,
    uiState: BeanEditorUiState,
    onBack: () -> Unit,
    onSave: (BeanProfile) -> Unit,
) {
    if (uiState.isLoading) {
        EmptyStateCard(
            title = "正在准备咖啡豆资料",
            subtitle = "稍等一下，Qoffee 正在读取当前内容。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val initialValue = uiState.bean
    var name by remember(initialValue) { mutableStateOf(initialValue?.name.orEmpty()) }
    var roaster by remember(initialValue) { mutableStateOf(initialValue?.roaster.orEmpty()) }
    var origin by remember(initialValue) { mutableStateOf(initialValue?.origin.orEmpty()) }
    var processMethod by remember(initialValue) { mutableStateOf(initialValue?.processMethod ?: BeanProcessMethod.WASHED) }
    var variety by remember(initialValue) { mutableStateOf(initialValue?.variety.orEmpty()) }
    var roastLevel by remember(initialValue) { mutableStateOf(initialValue?.roastLevel ?: RoastLevel.MEDIUM) }
    var roastDateEpochDay by remember(initialValue) { mutableStateOf(initialValue?.roastDateEpochDay) }
    var initialStock by remember(initialValue) { mutableStateOf(initialValue?.initialStockG?.toString().orEmpty()) }
    var notes by remember(initialValue) { mutableStateOf(initialValue?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val matchingSuggestions = remember(name, uiState.nameSuggestions) {
        uiState.nameSuggestions
            .filter { suggestion ->
                suggestion != name.trim() &&
                    (name.isBlank() || suggestion.contains(name.trim(), ignoreCase = true))
            }
            .take(5)
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            DashboardActionBar(
                title = "保存咖啡豆资料",
                subtitle = "整理好的豆子资料会直接服务记录页和分析页。",
            ) {
                if (error != null) {
                    Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            error = "名称不能为空。"
                            return@Button
                        }
                        val parsedInitialStock = initialStock.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                        if (initialStock.isNotBlank() && parsedInitialStock == null) {
                            error = "库存克重请输入有效数字。"
                            return@Button
                        }
                        error = null
                        onSave(
                            BeanProfile(
                                id = initialValue?.id ?: 0L,
                                archiveId = initialValue?.archiveId ?: 0L,
                                name = name.trim(),
                                roaster = roaster.trim(),
                                origin = origin.trim(),
                                processMethod = processMethod,
                                variety = variety.trim(),
                                roastLevel = roastLevel,
                                roastDateEpochDay = roastDateEpochDay,
                                initialStockG = parsedInitialStock,
                                notes = notes.trim(),
                                createdAt = initialValue?.createdAt ?: 0L,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存")
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
                title = if (initialValue == null) "新增咖啡豆" else "编辑咖啡豆",
                subtitle = "把咖啡豆资料整理好，记录和复盘都会更顺手。",
                eyebrow = "QOFFEE / BEANS",
            )
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            SectionCard(
                title = "基础信息",
                subtitle = "补齐名称、产地、处理法和烘焙信息，形成完整的豆子档案。",
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = roaster, onValueChange = { roaster = it }, label = { Text("烘焙商") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = origin, onValueChange = { origin = it }, label = { Text("产地") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = variety, onValueChange = { variety = it }, label = { Text("品种") }, modifier = Modifier.fillMaxWidth())
                SingleChoiceChipGroup(
                    title = "处理法",
                    options = BeanProcessMethod.entries.map { DropdownOption(it.displayName, it) },
                    selected = processMethod,
                    onSelected = { processMethod = it },
                )
                RoastLevelSelector(
                    selected = roastLevel,
                    onSelected = { roastLevel = it },
                )
                DatePickerField(
                    label = "烘焙日期",
                    valueEpochDay = roastDateEpochDay,
                    onValueChange = { roastDateEpochDay = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GrinderEditorScreen(
    paddingValues: PaddingValues,
    uiState: GrinderEditorUiState,
    onBack: () -> Unit,
    onSave: (GrinderProfile) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (uiState.isLoading) {
        EmptyStateCard(
            title = "正在准备磨豆机资料",
            subtitle = "稍等一下，Qoffee 正在读取当前内容。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val initialValue = uiState.grinder
    var name by remember(initialValue) { mutableStateOf(initialValue?.name.orEmpty()) }
    var minSetting by remember(initialValue) { mutableStateOf(initialValue?.minSetting?.toString().orEmpty()) }
    var maxSetting by remember(initialValue) { mutableStateOf(initialValue?.maxSetting?.toString().orEmpty()) }
    var stepSize by remember(initialValue) { mutableStateOf(initialValue?.stepSize?.toString().orEmpty()) }
    var unitLabel by remember(initialValue) { mutableStateOf(initialValue?.unitLabel.orEmpty()) }
    var espressoStart by remember(initialValue) { mutableStateOf(initialValue?.normalization?.espressoRange?.start?.toString().orEmpty()) }
    var espressoEnd by remember(initialValue) { mutableStateOf(initialValue?.normalization?.espressoRange?.end?.toString().orEmpty()) }
    var mokaStart by remember(initialValue) { mutableStateOf(initialValue?.normalization?.mokaRange?.start?.toString().orEmpty()) }
    var mokaEnd by remember(initialValue) { mutableStateOf(initialValue?.normalization?.mokaRange?.end?.toString().orEmpty()) }
    var pourStart by remember(initialValue) { mutableStateOf(initialValue?.normalization?.pourOverRange?.start?.toString().orEmpty()) }
    var pourEnd by remember(initialValue) { mutableStateOf(initialValue?.normalization?.pourOverRange?.end?.toString().orEmpty()) }
    var coldBrewPoint by remember(initialValue) { mutableStateOf(initialValue?.normalization?.coldBrewPoint?.toString().orEmpty()) }
    var notes by remember(initialValue) { mutableStateOf(initialValue?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val minValue = minSetting.toDoubleOrNull()
    val maxValue = maxSetting.toDoubleOrNull()
    val normalizationCandidate = runCatching {
        if (
            espressoStart.isBlank() &&
            espressoEnd.isBlank() &&
            mokaStart.isBlank() &&
            mokaEnd.isBlank() &&
            pourStart.isBlank() &&
            pourEnd.isBlank() &&
            coldBrewPoint.isBlank()
        ) {
            null
        } else {
            GrindNormalizationProfile(
                espressoRange = GrindCalibrationRange(
                    start = espressoStart.toDouble(),
                    end = espressoEnd.toDouble(),
                ),
                mokaRange = GrindCalibrationRange(
                    start = mokaStart.toDouble(),
                    end = mokaEnd.toDouble(),
                ),
                pourOverRange = GrindCalibrationRange(
                    start = pourStart.toDouble(),
                    end = pourEnd.toDouble(),
                ),
                coldBrewPoint = coldBrewPoint.toDouble(),
            )
        }
    }.getOrNull()
    val normalizationErrors = normalizationCandidate?.validationErrors(
        minSetting = minValue,
        maxSetting = maxValue,
    ).orEmpty()
    val normalizationCurve = normalizationCandidate?.buildCurve(
        minSetting = minValue ?: 0.0,
        maxSetting = maxValue ?: 0.0,
    )

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            DashboardActionBar(
                title = "保存磨豆机资料",
                subtitle = "设备范围和步进会在记录编辑时直接参与参数录入与校验。",
            ) {
                if (error != null) {
                    Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        val min = minSetting.toDoubleOrNull()
                        val max = maxSetting.toDoubleOrNull()
                        val step = stepSize.toDoubleOrNull()
                        if (name.isBlank()) {
                            error = "名称不能为空。"
                            return@Button
                        }
                        if (min == null || max == null || step == null) {
                            error = "请填写有效的格数范围和步进。"
                            return@Button
                        }
                        if (min >= max) {
                            error = "最大格数必须大于最小格数。"
                            return@Button
                        }
                        if (normalizationCandidate != null && normalizationErrors.isNotEmpty()) {
                            error = normalizationErrors.joinToString(" ")
                            return@Button
                        }
                        error = null
                        onSave(
                            GrinderProfile(
                                id = initialValue?.id ?: 0L,
                                archiveId = initialValue?.archiveId ?: 0L,
                                name = name.trim(),
                                minSetting = min,
                                maxSetting = max,
                                stepSize = step,
                                unitLabel = unitLabel.ifBlank { "格" },
                                normalization = normalizationCandidate,
                                notes = notes.trim(),
                                createdAt = initialValue?.createdAt ?: 0L,
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
                        Text("Delete Grinder")
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
                title = if (initialValue == null) "新增磨豆机" else "编辑磨豆机",
                subtitle = "把设备范围和步进写清楚，后续记录会更稳定。",
                eyebrow = "QOFFEE / GRINDER",
            )
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            SectionCard(
                title = "设备信息",
                subtitle = "维护设备名称、格数范围与步进，让记录页可以更准确地给出输入参考。",
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = minSetting, onValueChange = { minSetting = it }, label = { Text("最小格数") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = maxSetting, onValueChange = { maxSetting = it }, label = { Text("最大格数") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = stepSize, onValueChange = { stepSize = it }, label = { Text("步进") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unitLabel, onValueChange = { unitLabel = it }, label = { Text("单位标签") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
            }
            SectionCard(
                title = "归一化标定",
                subtitle = "输入商家建议的刻度区间，Qoffee 会把原始格数拟合到统一的 0~1 坐标系。",
            ) {
                OutlinedTextField(value = espressoStart, onValueChange = { espressoStart = it }, label = { Text("意式起点") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = espressoEnd, onValueChange = { espressoEnd = it }, label = { Text("意式终点") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mokaStart, onValueChange = { mokaStart = it }, label = { Text("摩卡壶起点") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mokaEnd, onValueChange = { mokaEnd = it }, label = { Text("摩卡壶终点") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pourStart, onValueChange = { pourStart = it }, label = { Text("手冲起点") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pourEnd, onValueChange = { pourEnd = it }, label = { Text("手冲终点") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = coldBrewPoint, onValueChange = { coldBrewPoint = it }, label = { Text("冷萃上限单点") }, modifier = Modifier.fillMaxWidth())
                if (normalizationErrors.isNotEmpty()) {
                    normalizationErrors.forEach { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Text(
                        text = "锚点常数固定为 Espresso 300/1400、Moka 500/1400、Pour-over 800/1400、Cold Brew 1.0。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GrindNormalizationChart(curve = normalizationCurve)
            }
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Grinder") },
            text = { Text("This grinder will be removed from the current archive.") },
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
}
