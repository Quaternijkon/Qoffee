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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.BeanProcessMethod
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.RoastLevel
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.ui.components.DashboardActionBar
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.RoastLevelSelector
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.SingleChoiceChipGroup
import com.qoffee.ui.components.DatePickerField
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BeanAssetEditorUiState(
    val isLoading: Boolean = true,
    val bean: BeanProfile? = null,
    val nameSuggestions: List<String> = emptyList(),
)

@HiltViewModel
class BeanAssetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val beanId = savedStateHandle.get<Long>(QoffeeDestinations.beanIdArg) ?: -1L
    private val uiStateInternal = MutableStateFlow(BeanAssetEditorUiState())
    val uiState: StateFlow<BeanAssetEditorUiState> = uiStateInternal

    init {
        viewModelScope.launch {
            val suggestions = catalogRepository.observeBeanProfiles()
                .first()
                .mapNotNull { it.name.trim().takeIf(String::isNotBlank) }
                .distinct()
            uiStateInternal.value = BeanAssetEditorUiState(
                isLoading = false,
                bean = beanId.takeIf { it > 0L }?.let { catalogRepository.getBeanProfile(it) },
                nameSuggestions = suggestions,
            )
        }
    }

    suspend fun save(profile: BeanProfile): Long = catalogRepository.saveBeanProfile(profile)

    suspend fun delete(beanId: Long) = catalogRepository.deleteBeanProfile(beanId)
}

@Composable
fun BeanAssetEditorRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BeanAssetEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BeanAssetEditorScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        onSave = { profile ->
            viewModel.viewModelScope.launch {
                viewModel.save(profile)
                onSaved()
            }
        },
        onDelete = { beanId ->
            viewModel.viewModelScope.launch {
                viewModel.delete(beanId)
                onSaved()
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeanAssetEditorScreen(
    paddingValues: PaddingValues,
    uiState: BeanAssetEditorUiState,
    onBack: () -> Unit,
    onSave: (BeanProfile) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (uiState.isLoading) {
        EmptyStateCard(
            title = "正在准备咖啡豆资产",
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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val matchingSuggestions = remember(name, uiState.nameSuggestions) {
        uiState.nameSuggestions
            .filter { suggestion ->
                suggestion != name.trim() &&
                    (name.isBlank() || suggestion.contains(name.trim(), ignoreCase = true))
            }
            .take(5)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            DashboardActionBar(
                title = "保存咖啡豆资产",
                subtitle = "名称候选有助于稳定分析 key，库存克重会用于库存计算和冲煮页展示。",
            ) {
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
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
                initialValue?.let {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除咖啡豆")
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
                title = if (initialValue == null) "新增咖啡豆" else "编辑咖啡豆",
                subtitle = "库存按当前资产实例单独计算，不会和同名但不同烘焙日期的豆子混在一起。",
                eyebrow = "QOFFEE / BEANS",
            )
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            SectionCard(
                title = "基础信息",
                subtitle = "把名称、产地、处理法、烘焙信息和库存录完整，后续记录与分析会更稳定。",
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (matchingSuggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        matchingSuggestions.forEach { suggestion ->
                            OutlinedButton(onClick = { name = suggestion }) {
                                Text(suggestion)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = roaster,
                    onValueChange = { roaster = it },
                    label = { Text("烘焙商") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = origin,
                    onValueChange = { origin = it },
                    label = { Text("产地") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = variety,
                    onValueChange = { variety = it },
                    label = { Text("品种") },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                OutlinedTextField(
                    value = initialStock,
                    onValueChange = { initialStock = it },
                    label = { Text("初始库存（g）") },
                    modifier = Modifier.fillMaxWidth(),
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
            title = { Text("删除咖啡豆") },
            text = { Text("这个咖啡豆会从当前存档中移除。") },
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
