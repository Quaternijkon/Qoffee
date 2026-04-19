package com.qoffee.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.BeanProcessMethod
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RoastLevel
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.ui.components.BeanIdentityCard
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.HeroCard
import com.qoffee.ui.components.RoastLevelSelector
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.SingleChoiceChipGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogUiState(
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    val uiState: StateFlow<CatalogUiState> = combine(
        catalogRepository.observeBeanProfiles(),
        catalogRepository.observeGrinderProfiles(),
    ) { beans, grinders ->
        CatalogUiState(beans = beans, grinders = grinders)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CatalogUiState(),
    )

    suspend fun saveBean(profile: BeanProfile): Long = catalogRepository.saveBeanProfile(profile)

    suspend fun saveGrinder(profile: GrinderProfile): Long = catalogRepository.saveGrinderProfile(profile)
}

@Composable
fun CatalogRoute(
    paddingValues: PaddingValues,
    isReadOnlyArchive: Boolean,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CatalogScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        isReadOnlyArchive = isReadOnlyArchive,
        onSaveBean = viewModel::saveBean,
        onSaveGrinder = viewModel::saveGrinder,
    )
}

@Composable
private fun CatalogScreen(
    paddingValues: PaddingValues,
    uiState: CatalogUiState,
    isReadOnlyArchive: Boolean,
    onSaveBean: suspend (BeanProfile) -> Long,
    onSaveGrinder: suspend (GrinderProfile) -> Long,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingBean by remember { mutableStateOf<BeanProfile?>(null) }
    var editingGrinder by remember { mutableStateOf<GrinderProfile?>(null) }
    var showBeanDialog by remember { mutableStateOf(false) }
    var showGrinderDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(
            title = "维护你的资料库",
            subtitle = "先录入咖啡豆和磨豆机，记录时就能快速复用，也更方便后续分析。",
        )

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("咖啡豆") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("磨豆机") })
        }

        if (selectedTab == 0) {
            SectionCard(title = "咖啡豆资料") {
                if (!isReadOnlyArchive) {
                    OutlinedButton(
                        onClick = {
                            editingBean = null
                            showBeanDialog = true
                        },
                    ) {
                        Text("新增咖啡豆")
                    }
                }
                if (uiState.beans.isEmpty()) {
                    EmptyStateCard(
                        title = "还没有咖啡豆资料",
                        subtitle = if (isReadOnlyArchive) {
                            "当前示范存档为只读模式，可以先浏览结构化资料，再复制出自己的存档。"
                        } else {
                            "先创建第一条咖啡豆资料，之后记录时就能直接选择。"
                        },
                    )
                } else {
                    uiState.beans.forEach { bean ->
                        BeanIdentityCard(
                            name = bean.name,
                            roastLevel = bean.roastLevel,
                            processMethod = bean.processMethod,
                            roaster = bean.roaster,
                        )
                        if (!isReadOnlyArchive) {
                            OutlinedButton(onClick = {
                                editingBean = bean
                                showBeanDialog = true
                            }) {
                                Text("编辑")
                            }
                        }
                    }
                }
            }
        } else {
            SectionCard(title = "磨豆机资料") {
                if (!isReadOnlyArchive) {
                    OutlinedButton(
                        onClick = {
                            editingGrinder = null
                            showGrinderDialog = true
                        },
                    ) {
                        Text("新增磨豆机")
                    }
                }
                if (uiState.grinders.isEmpty()) {
                    EmptyStateCard(
                        title = "还没有磨豆机资料",
                        subtitle = if (isReadOnlyArchive) {
                            "当前示范存档为只读模式，可以先查看示例器材配置。"
                        } else {
                            "先定义设备和格数范围，记录时就能按你的器材配置校验。"
                        },
                    )
                } else {
                    uiState.grinders.forEach { grinder ->
                        SectionCard(title = grinder.name) {
                            Text(
                                text = "${grinder.minSetting}-${grinder.maxSetting} ${grinder.unitLabel} | 步进 ${grinder.stepSize}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (grinder.notes.isNotBlank()) {
                                Text(
                                    text = grinder.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!isReadOnlyArchive) {
                                OutlinedButton(onClick = {
                                    editingGrinder = grinder
                                    showGrinderDialog = true
                                }) {
                                    Text("编辑")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBeanDialog && !isReadOnlyArchive) {
        BeanEditorDialog(
            initialValue = editingBean,
            onDismiss = { showBeanDialog = false },
            onSave = {
                onSaveBean(it)
                showBeanDialog = false
            },
        )
    }

    if (showGrinderDialog && !isReadOnlyArchive) {
        GrinderEditorDialog(
            initialValue = editingGrinder,
            onDismiss = { showGrinderDialog = false },
            onSave = {
                onSaveGrinder(it)
                showGrinderDialog = false
            },
        )
    }
}

@Composable
private fun BeanEditorDialog(
    initialValue: BeanProfile?,
    onDismiss: () -> Unit,
    onSave: suspend (BeanProfile) -> Unit,
) {
    var name by remember(initialValue) { mutableStateOf(initialValue?.name.orEmpty()) }
    var roaster by remember(initialValue) { mutableStateOf(initialValue?.roaster.orEmpty()) }
    var origin by remember(initialValue) { mutableStateOf(initialValue?.origin.orEmpty()) }
    var processMethod by remember(initialValue) { mutableStateOf(initialValue?.processMethod ?: BeanProcessMethod.WASHED) }
    var variety by remember(initialValue) { mutableStateOf(initialValue?.variety.orEmpty()) }
    var roastLevel by remember(initialValue) { mutableStateOf(initialValue?.roastLevel ?: RoastLevel.MEDIUM) }
    var roastDate by remember(initialValue) {
        mutableStateOf(initialValue?.roastDateEpochDay?.let(LocalDate::ofEpochDay)?.toString().orEmpty())
    }
    var notes by remember(initialValue) { mutableStateOf(initialValue?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialValue == null) "新增咖啡豆资料" else "编辑咖啡豆资料") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                OutlinedTextField(
                    value = roastDate,
                    onValueChange = { roastDate = it },
                    label = { Text("烘焙日期（YYYY-MM-DD）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate = try {
                        roastDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse)?.toEpochDay()
                    } catch (_: DateTimeParseException) {
                        error = "烘焙日期请使用 YYYY-MM-DD 格式。"
                        return@Button
                    }
                    if (name.isBlank()) {
                        error = "名称不能为空。"
                        return@Button
                    }
                    scope.launch {
                        onSave(
                            BeanProfile(
                                id = initialValue?.id ?: 0L,
                                name = name.trim(),
                                roaster = roaster.trim(),
                                origin = origin.trim(),
                                processMethod = processMethod,
                                variety = variety.trim(),
                                roastLevel = roastLevel,
                                roastDateEpochDay = parsedDate,
                                notes = notes.trim(),
                                createdAt = initialValue?.createdAt ?: 0L,
                            ),
                        )
                    }
                },
            ) {
                Text("保存")
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
private fun GrinderEditorDialog(
    initialValue: GrinderProfile?,
    onDismiss: () -> Unit,
    onSave: suspend (GrinderProfile) -> Unit,
) {
    var name by remember(initialValue) { mutableStateOf(initialValue?.name.orEmpty()) }
    var minSetting by remember(initialValue) { mutableStateOf(initialValue?.minSetting?.toString().orEmpty()) }
    var maxSetting by remember(initialValue) { mutableStateOf(initialValue?.maxSetting?.toString().orEmpty()) }
    var stepSize by remember(initialValue) { mutableStateOf(initialValue?.stepSize?.toString().orEmpty()) }
    var unitLabel by remember(initialValue) { mutableStateOf(initialValue?.unitLabel.orEmpty()) }
    var notes by remember(initialValue) { mutableStateOf(initialValue?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialValue == null) "新增磨豆机" else "编辑磨豆机") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = minSetting, onValueChange = { minSetting = it }, label = { Text("最小格数") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = maxSetting, onValueChange = { maxSetting = it }, label = { Text("最大格数") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = stepSize, onValueChange = { stepSize = it }, label = { Text("步进") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unitLabel, onValueChange = { unitLabel = it }, label = { Text("单位标签") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
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
                    scope.launch {
                        onSave(
                            GrinderProfile(
                                id = initialValue?.id ?: 0L,
                                name = name.trim(),
                                minSetting = min,
                                maxSetting = max,
                                stepSize = step,
                                unitLabel = unitLabel.ifBlank { "格" },
                                notes = notes.trim(),
                                createdAt = initialValue?.createdAt ?: 0L,
                            ),
                        )
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
