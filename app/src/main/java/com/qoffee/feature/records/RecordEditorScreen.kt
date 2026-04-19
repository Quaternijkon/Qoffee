package com.qoffee.feature.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.ObjectiveDraftUpdate
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.DashboardActionBar
import com.qoffee.ui.components.BeanIdentityCard
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.NumericStepField
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.RatingSelector
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.TagSelector
import com.qoffee.ui.navigation.QoffeeDestinations
import com.qoffee.ui.navigation.RecordEditorEntry
import com.qoffee.ui.theme.QoffeeDashboardTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class ObjectiveFormState(
    val recipeTemplateId: Long? = null,
    val recipeNameSnapshot: String? = null,
    val brewMethod: BrewMethod? = null,
    val beanProfileId: Long? = null,
    val grinderProfileId: Long? = null,
    val grindSetting: String = "",
    val coffeeDoseG: String = "",
    val brewWaterMl: String = "",
    val bypassWaterMl: String = "",
    val waterTempC: String = "",
    val notes: String = "",
)

internal data class SubjectiveFormState(
    val aroma: Int? = null,
    val acidity: Int? = null,
    val sweetness: Int? = null,
    val bitterness: Int? = null,
    val body: Int? = null,
    val aftertaste: Int? = null,
    val overall: Int? = null,
    val notes: String = "",
    val tags: List<FlavorTag> = emptyList(),
)

internal data class RecordEditorUiState(
    val isLoading: Boolean = true,
    val entry: RecordEditorEntry = RecordEditorEntry.NEW,
    val recordId: Long? = null,
    val record: CoffeeRecord? = null,
    val sourceRecord: CoffeeRecord? = null,
    val referenceRecord: CoffeeRecord? = null,
    val objective: ObjectiveFormState = ObjectiveFormState(),
    val subjective: SubjectiveFormState = SubjectiveFormState(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val recipes: List<RecipeTemplate> = emptyList(),
    val flavorTags: List<FlavorTag> = emptyList(),
    val validationErrors: List<String> = emptyList(),
)

private data class RecordEditorIntermediateState(
    val record: CoffeeRecord?,
    val beans: List<BeanProfile>,
    val grinders: List<GrinderProfile>,
    val recipes: List<RecipeTemplate>,
)

internal sealed interface RecordEditorEvent {
    data class Completed(val recordId: Long) : RecordEditorEvent
}

@HiltViewModel
class RecordEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordRepository: RecordRepository,
    private val catalogRepository: CatalogRepository,
    private val recipeRepository: RecipeRepository,
    preferenceRepository: PreferenceRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(RecordEditorUiState())
    internal val uiState: StateFlow<RecordEditorUiState> = uiStateInternal

    private val eventsInternal = MutableSharedFlow<RecordEditorEvent>()
    internal val events: SharedFlow<RecordEditorEvent> = eventsInternal.asSharedFlow()

    private var initialBindingDone = false

    init {
        val requestedRecordId = savedStateHandle.get<Long>(QoffeeDestinations.recordIdArg) ?: -1L
        val duplicateFrom = savedStateHandle.get<Long>(QoffeeDestinations.duplicateFromArg) ?: -1L
        val recipeId = savedStateHandle.get<Long>(QoffeeDestinations.recipeIdArg) ?: -1L
        val entryValue = savedStateHandle.get<String>(QoffeeDestinations.recordEntryArg)
        val entry = RecordEditorEntry.entries.firstOrNull { it.value == entryValue } ?: RecordEditorEntry.NEW

        viewModelScope.launch {
            val settings = preferenceRepository.observeSettings().first()
            val sourceRecord = duplicateFrom.takeIf { it > 0L }?.let { recordRepository.getRecord(it) }
            val resolvedRecordId = when {
                entry == RecordEditorEntry.RECIPE && recipeId > 0L -> {
                    recordRepository.createDraftFromRecipe(recipeId = recipeId, replaceActiveDraft = true)
                }
                duplicateFrom > 0L -> recordRepository.duplicateRecordAsDraft(duplicateFrom)
                entry == RecordEditorEntry.NEW -> recordRepository.createEmptyDraft(replaceActiveDraft = true)
                requestedRecordId > 0L -> requestedRecordId
                else -> recordRepository.getOrCreateActiveDraftId(settings.autoRestoreDraft)
            }

            recordRepository.observeRecord(resolvedRecordId)
                .combine(catalogRepository.observeBeanProfiles()) { record, beans ->
                    record to beans
                }
                .combine(catalogRepository.observeGrinderProfiles()) { recordAndBeans, grinders ->
                    Triple(recordAndBeans.first, recordAndBeans.second, grinders)
                }
                .combine(recipeRepository.observeRecipes()) { recordBeansAndGrinders, recipes ->
                    RecordEditorIntermediateState(
                        record = recordBeansAndGrinders.first,
                        beans = recordBeansAndGrinders.second,
                        grinders = recordBeansAndGrinders.third,
                        recipes = recipes,
                    )
                }
                .combine(catalogRepository.observeFlavorTags()) { intermediate, tags ->
                    val current = uiStateInternal.value
                    RecordEditorUiState(
                        isLoading = false,
                        entry = entry,
                        recordId = resolvedRecordId,
                        record = intermediate.record,
                        sourceRecord = sourceRecord,
                        referenceRecord = current.referenceRecord,
                        objective = if (!initialBindingDone && intermediate.record != null) {
                            intermediate.record.toObjectiveForm()
                        } else {
                            current.objective
                        },
                        subjective = if (!initialBindingDone && intermediate.record != null) {
                            intermediate.record.toSubjectiveForm()
                        } else {
                            current.subjective
                        },
                        beans = intermediate.beans,
                        grinders = intermediate.grinders,
                        recipes = intermediate.recipes,
                        flavorTags = tags,
                        validationErrors = current.validationErrors,
                    )
                }
                .collect { newState ->
                    if (!initialBindingDone && newState.record != null) {
                        initialBindingDone = true
                    }
                    uiStateInternal.value = newState
                    refreshReferenceRecord(
                        beanId = newState.objective.beanProfileId,
                        brewMethod = newState.objective.brewMethod,
                        recordId = newState.recordId,
                        sourceRecord = newState.sourceRecord,
                    )
                }
        }
    }

    fun applyRecipe(recipeId: Long?) {
        val safeRecipeId = recipeId ?: return
        val recordId = uiStateInternal.value.recordId ?: return
        viewModelScope.launch {
            recordRepository.applyRecipeToDraft(recordId, safeRecipeId)
            recipeRepository.getRecipe(safeRecipeId)?.let { recipe ->
                uiStateInternal.value = uiStateInternal.value.copy(
                    objective = recipe.toObjectiveForm(),
                    validationErrors = emptyList(),
                )
                refreshReferenceRecord(
                    beanId = recipe.beanProfileId,
                    brewMethod = recipe.brewMethod,
                    recordId = recordId,
                    sourceRecord = null,
                )
            }
        }
    }

    fun saveCurrentAsRecipe(name: String) {
        val normalized = name.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            val objective = uiStateInternal.value.objective
            val bean = uiStateInternal.value.beans.firstOrNull { it.id == objective.beanProfileId }
            val grinder = uiStateInternal.value.grinders.firstOrNull { it.id == objective.grinderProfileId }
            val recipeId = recipeRepository.saveRecipe(
                RecipeTemplate(
                    name = normalized,
                    brewMethod = objective.brewMethod,
                    beanProfileId = objective.beanProfileId,
                    beanNameSnapshot = bean?.name,
                    grinderProfileId = objective.grinderProfileId,
                    grinderNameSnapshot = grinder?.name,
                    grindSetting = objective.grindSetting.toDoubleOrNull(),
                    coffeeDoseG = objective.coffeeDoseG.toDoubleOrNull(),
                    brewWaterMl = objective.brewWaterMl.toDoubleOrNull(),
                    bypassWaterMl = objective.bypassWaterMl.toDoubleOrNull(),
                    waterTempC = objective.waterTempC.toDoubleOrNull(),
                    notes = objective.notes,
                ),
            )
            val recipe = recipeRepository.getRecipe(recipeId) ?: return@launch
            updateObjective(recipe.toObjectiveForm())
        }
    }

    fun updateBrewMethod(method: BrewMethod?) = updateObjective(uiStateInternal.value.objective.copy(brewMethod = method))

    fun updateBean(beanId: Long?) = updateObjective(uiStateInternal.value.objective.copy(beanProfileId = beanId))

    fun updateGrinder(grinderId: Long?) = updateObjective(uiStateInternal.value.objective.copy(grinderProfileId = grinderId))

    fun updateGrindSetting(text: String) = updateObjective(uiStateInternal.value.objective.copy(grindSetting = text))

    fun updateCoffeeDose(text: String) = updateObjective(uiStateInternal.value.objective.copy(coffeeDoseG = text))

    fun updateBrewWater(text: String) = updateObjective(uiStateInternal.value.objective.copy(brewWaterMl = text))

    fun updateBypassWater(text: String) = updateObjective(uiStateInternal.value.objective.copy(bypassWaterMl = text))

    fun updateWaterTemp(text: String) = updateObjective(uiStateInternal.value.objective.copy(waterTempC = text))

    fun updateObjectiveNotes(text: String) = updateObjective(uiStateInternal.value.objective.copy(notes = text))

    fun updateAroma(value: Int?) = updateSubjective(uiStateInternal.value.subjective.copy(aroma = value))

    fun updateAcidity(value: Int?) = updateSubjective(uiStateInternal.value.subjective.copy(acidity = value))

    fun updateSweetness(value: Int?) = updateSubjective(uiStateInternal.value.subjective.copy(sweetness = value))

    fun updateBitterness(value: Int?) = updateSubjective(uiStateInternal.value.subjective.copy(bitterness = value))

    fun updateBody(value: Int?) = updateSubjective(uiStateInternal.value.subjective.copy(body = value))

    fun updateAftertaste(value: Int?) = updateSubjective(uiStateInternal.value.subjective.copy(aftertaste = value))

    fun updateOverall(value: Int?) = updateSubjective(uiStateInternal.value.subjective.copy(overall = value))

    fun updateSubjectiveNotes(text: String) = updateSubjective(uiStateInternal.value.subjective.copy(notes = text))

    fun toggleFlavorTag(tag: FlavorTag) {
        val currentTags = uiStateInternal.value.subjective.tags
        val updated = if (currentTags.any { it.id == tag.id }) {
            currentTags.filterNot { it.id == tag.id }
        } else {
            currentTags + tag
        }
        updateSubjective(uiStateInternal.value.subjective.copy(tags = updated))
    }

    fun addCustomTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val tag = catalogRepository.ensureFlavorTag(trimmed)
            val updated = (uiStateInternal.value.subjective.tags + tag).distinctBy { it.id to it.name }
            updateSubjective(uiStateInternal.value.subjective.copy(tags = updated))
        }
    }

    fun submit() {
        val recordId = uiStateInternal.value.recordId ?: return
        viewModelScope.launch {
            val result = recordRepository.completeRecord(recordId)
            if (result.isValid) {
                eventsInternal.emit(RecordEditorEvent.Completed(recordId))
            } else {
                uiStateInternal.value = uiStateInternal.value.copy(validationErrors = result.errors)
            }
        }
    }

    private fun updateObjective(objective: ObjectiveFormState) {
        uiStateInternal.value = uiStateInternal.value.copy(objective = objective, validationErrors = emptyList())
        persistObjective()
        refreshReferenceAsync(beanId = objective.beanProfileId, brewMethod = objective.brewMethod)
    }

    private fun updateSubjective(subjective: SubjectiveFormState) {
        uiStateInternal.value = uiStateInternal.value.copy(subjective = subjective)
        persistSubjective()
    }

    private fun persistObjective() {
        val recordId = uiStateInternal.value.recordId ?: return
        val objective = uiStateInternal.value.objective
        viewModelScope.launch {
            recordRepository.updateObjective(
                recordId = recordId,
                update = ObjectiveDraftUpdate(
                    recipeTemplateId = objective.recipeTemplateId,
                    recipeNameSnapshot = objective.recipeNameSnapshot,
                    brewMethod = objective.brewMethod,
                    beanProfileId = objective.beanProfileId,
                    grinderProfileId = objective.grinderProfileId,
                    grindSetting = objective.grindSetting.toDoubleOrNull(),
                    coffeeDoseG = objective.coffeeDoseG.toDoubleOrNull(),
                    brewWaterMl = objective.brewWaterMl.toDoubleOrNull(),
                    bypassWaterMl = objective.bypassWaterMl.toDoubleOrNull(),
                    waterTempC = objective.waterTempC.toDoubleOrNull(),
                    notes = objective.notes,
                ),
            )
        }
    }

    private fun persistSubjective() {
        val recordId = uiStateInternal.value.recordId ?: return
        val subjective = uiStateInternal.value.subjective
        viewModelScope.launch {
            recordRepository.updateSubjective(
                recordId = recordId,
                evaluation = SubjectiveEvaluation(
                    recordId = recordId,
                    aroma = subjective.aroma,
                    acidity = subjective.acidity,
                    sweetness = subjective.sweetness,
                    bitterness = subjective.bitterness,
                    body = subjective.body,
                    aftertaste = subjective.aftertaste,
                    overall = subjective.overall,
                    notes = subjective.notes,
                    flavorTags = subjective.tags,
                ),
            )
        }
    }

    private fun refreshReferenceAsync(beanId: Long?, brewMethod: BrewMethod?) {
        viewModelScope.launch {
            refreshReferenceRecord(
                beanId = beanId,
                brewMethod = brewMethod,
                recordId = uiStateInternal.value.recordId,
                sourceRecord = uiStateInternal.value.sourceRecord,
            )
        }
    }

    private suspend fun refreshReferenceRecord(
        beanId: Long?,
        brewMethod: BrewMethod?,
        recordId: Long?,
        sourceRecord: CoffeeRecord?,
    ) {
        val reference = sourceRecord ?: recordRepository.getLatestComparableRecord(
            beanId = beanId,
            brewMethod = brewMethod,
            excludingRecordId = recordId,
        )
        if (uiStateInternal.value.referenceRecord?.id != reference?.id) {
            uiStateInternal.value = uiStateInternal.value.copy(referenceRecord = reference)
        }
    }
}

@Composable
fun RecordEditorRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onCompleted: (Long) -> Unit,
    viewModel: RecordEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordEditorEvent.Completed -> onCompleted(event.recordId)
            }
        }
    }
    RecordEditorScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        onRecipeSelected = viewModel::applyRecipe,
        onSaveCurrentAsRecipe = viewModel::saveCurrentAsRecipe,
        onMethodChange = viewModel::updateBrewMethod,
        onBeanChange = viewModel::updateBean,
        onGrinderChange = viewModel::updateGrinder,
        onGrindSettingChange = viewModel::updateGrindSetting,
        onCoffeeDoseChange = viewModel::updateCoffeeDose,
        onBrewWaterChange = viewModel::updateBrewWater,
        onBypassWaterChange = viewModel::updateBypassWater,
        onWaterTempChange = viewModel::updateWaterTemp,
        onObjectiveNotesChange = viewModel::updateObjectiveNotes,
        onAromaChange = viewModel::updateAroma,
        onAcidityChange = viewModel::updateAcidity,
        onSweetnessChange = viewModel::updateSweetness,
        onBitternessChange = viewModel::updateBitterness,
        onBodyChange = viewModel::updateBody,
        onAftertasteChange = viewModel::updateAftertaste,
        onOverallChange = viewModel::updateOverall,
        onSubjectiveNotesChange = viewModel::updateSubjectiveNotes,
        onToggleTag = viewModel::toggleFlavorTag,
        onAddCustomTag = viewModel::addCustomTag,
        onSubmit = viewModel::submit,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordEditorScreen(
    paddingValues: PaddingValues,
    uiState: RecordEditorUiState,
    onBack: () -> Unit,
    onRecipeSelected: (Long?) -> Unit,
    onSaveCurrentAsRecipe: (String) -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (Long?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onGrindSettingChange: (String) -> Unit,
    onCoffeeDoseChange: (String) -> Unit,
    onBrewWaterChange: (String) -> Unit,
    onBypassWaterChange: (String) -> Unit,
    onWaterTempChange: (String) -> Unit,
    onObjectiveNotesChange: (String) -> Unit,
    onAromaChange: (Int?) -> Unit,
    onAcidityChange: (Int?) -> Unit,
    onSweetnessChange: (Int?) -> Unit,
    onBitternessChange: (Int?) -> Unit,
    onBodyChange: (Int?) -> Unit,
    onAftertasteChange: (Int?) -> Unit,
    onOverallChange: (Int?) -> Unit,
    onSubjectiveNotesChange: (String) -> Unit,
    onToggleTag: (FlavorTag) -> Unit,
    onAddCustomTag: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    if (uiState.isLoading) {
        EmptyStateCard(
            title = "正在准备记录",
            subtitle = "Qoffee 正在恢复当前草稿或创建一条新的记录。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    var currentStep by remember { mutableStateOf(0) }
    var customTag by remember { mutableStateOf("") }
    var showSaveRecipeDialog by remember { mutableStateOf(false) }
    val selectedBean = uiState.beans.firstOrNull { it.id == uiState.objective.beanProfileId }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            DashboardActionBar(
                title = if (currentStep == 0) "步骤 1 / 2 · 客观参数" else "步骤 2 / 2 · 主观感受",
                subtitle = "自动保存已开启。确认无误后即可提交本次记录。",
                testTag = QoffeeTestTags.RECORD_EDITOR_ACTION_BAR,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(text = "自动保存")
                    uiState.recordId?.let { StatChip(text = "记录 #$it") }
                    StatChip(text = when (uiState.entry) {
                        RecordEditorEntry.NEW -> "空白新建"
                        RecordEditorEntry.DRAFT -> "继续草稿"
                        RecordEditorEntry.DUPLICATE -> "复制上一杯"
                        RecordEditorEntry.RECIPE -> "从配方开始"
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (currentStep == 0) onBack() else currentStep = 0
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (currentStep == 0) "返回" else "上一步")
                    }
                    Button(
                        onClick = {
                            if (currentStep == 0) currentStep = 1 else onSubmit()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_record_button"),
                    ) {
                        Text(if (currentStep == 0) "继续到主观感受" else "完成提交")
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
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .testTag(QoffeeTestTags.RECORD_EDITOR_SCREEN),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PageHeader(
                title = editorTitle(uiState),
                subtitle = editorSubtitle(uiState),
                eyebrow = "QOFFEE / EDITOR",
            )

            SectionCard(
                title = "当前来源",
                subtitle = "确认这一杯从哪里开始，以及当前最接近的参考样本。",
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatChip(text = when (uiState.entry) {
                        RecordEditorEntry.NEW -> "空白新建"
                        RecordEditorEntry.DRAFT -> "继续草稿"
                        RecordEditorEntry.DUPLICATE -> "复制上一杯"
                        RecordEditorEntry.RECIPE -> "从配方开始"
                    })
                    uiState.objective.recipeNameSnapshot?.let { StatChip(text = "配方 $it") }
                }
                uiState.referenceRecord?.let { reference ->
                    Text(
                        text = "可参考最近同类记录：${reference.beanNameSnapshot ?: "未命名咖啡豆"} · ${reference.brewMethod?.displayName ?: "未指定方式"} · ${formatDateTime(reference.brewedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(
                title = "流程切换",
                subtitle = "先完成客观参数，再进入主观感受与提交检查。",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepChip(label = "1. 客观参数", selected = currentStep == 0, onClick = { currentStep = 0 })
                    StepChip(label = "2. 主观感受", selected = currentStep == 1, onClick = { currentStep = 1 })
                }
            }

            if (currentStep == 0) {
                SectionCard(
                    title = "配方模板",
                    subtitle = "可以套用已有模板，也可以把当前参数另存为新的模板。",
                ) {
                    DropdownField(
                        label = "采用配方",
                        selectedLabel = uiState.recipes.firstOrNull { it.id == uiState.objective.recipeTemplateId }?.name
                            ?: uiState.objective.recipeNameSnapshot,
                        options = uiState.recipes.map { DropdownOption(it.name, it.id) },
                        onSelected = onRecipeSelected,
                        allowClear = false,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showSaveRecipeDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("将当前参数另存为配方")
                        }
                    }
                }

                SectionCard(
                    title = "客观参数",
                    subtitle = "把制作方式、豆子、设备与冲煮参数完整录入。",
                ) {
                    DropdownField(
                        label = "制作方式",
                        selectedLabel = uiState.objective.brewMethod?.displayName,
                        options = BrewMethod.entries.map { DropdownOption(it.displayName, it) },
                        onSelected = onMethodChange,
                        modifier = Modifier.testTag("brew_method_dropdown"),
                    )
                    DropdownField(
                        label = "咖啡豆",
                        selectedLabel = selectedBean?.name,
                        options = uiState.beans.map { DropdownOption(it.name, it.id) },
                        onSelected = onBeanChange,
                        modifier = Modifier.testTag("bean_dropdown"),
                    )
                    AnimatedVisibility(visible = selectedBean != null) {
                        selectedBean?.let { bean ->
                            BeanIdentityCard(
                                name = bean.name,
                                roastLevel = bean.roastLevel,
                                processMethod = bean.processMethod,
                                roaster = bean.roaster,
                            )
                        }
                    }
                    DropdownField(
                        label = "磨豆机",
                        selectedLabel = uiState.grinders.firstOrNull { it.id == uiState.objective.grinderProfileId }?.name,
                        options = uiState.grinders.map { DropdownOption(it.name, it.id) },
                        onSelected = onGrinderChange,
                    )
                    NumericStepField(
                        label = "研磨格数",
                        value = uiState.objective.grindSetting,
                        onValueChange = onGrindSettingChange,
                        step = 1.0,
                        decimals = 1,
                        referenceValue = uiState.referenceRecord?.grindSetting?.let(::formatNumber),
                    )
                    NumericStepField(
                        label = "咖啡粉重量（g）",
                        value = uiState.objective.coffeeDoseG,
                        onValueChange = onCoffeeDoseChange,
                        step = 0.5,
                        decimals = 1,
                        quickValues = listOf("15", "18", "20"),
                        referenceValue = uiState.referenceRecord?.coffeeDoseG?.let { "${formatNumber(it)} g" },
                    )
                    NumericStepField(
                        label = "冲煮水量（ml）",
                        value = uiState.objective.brewWaterMl,
                        onValueChange = onBrewWaterChange,
                        step = 10.0,
                        decimals = 0,
                        quickValues = listOf("200", "240", "280"),
                        referenceValue = uiState.referenceRecord?.brewWaterMl?.let { "${formatNumber(it)} ml" },
                    )
                    NumericStepField(
                        label = "旁路水量（ml）",
                        value = uiState.objective.bypassWaterMl,
                        onValueChange = onBypassWaterChange,
                        step = 10.0,
                        decimals = 0,
                        quickValues = listOf("0", "40", "60"),
                        referenceValue = uiState.referenceRecord?.bypassWaterMl?.let { "${formatNumber(it)} ml" },
                    )
                    AnimatedVisibility(visible = uiState.objective.brewMethod?.isHotBrew != false) {
                        NumericStepField(
                            label = "水温（°C）",
                            value = uiState.objective.waterTempC,
                            onValueChange = onWaterTempChange,
                            step = 1.0,
                            decimals = 0,
                            quickValues = listOf("90", "92", "94"),
                            referenceValue = uiState.referenceRecord?.waterTempC?.let { "${formatNumber(it)} °C" },
                        )
                    }
                    OutlinedTextField(
                        value = uiState.objective.notes,
                        onValueChange = onObjectiveNotesChange,
                        label = { Text("客观备注") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                SectionCard(
                    title = "主观感受",
                    subtitle = "补充风味、评分和备注，让这条记录可以进入复盘分析。",
                ) {
                    RatingSelector(label = "香气", value = uiState.subjective.aroma, range = 1..5, onSelected = onAromaChange)
                    RatingSelector(label = "酸质", value = uiState.subjective.acidity, range = 1..5, onSelected = onAcidityChange)
                    RatingSelector(label = "甜感", value = uiState.subjective.sweetness, range = 1..5, onSelected = onSweetnessChange)
                    RatingSelector(label = "苦感", value = uiState.subjective.bitterness, range = 1..5, onSelected = onBitternessChange)
                    RatingSelector(label = "醇厚", value = uiState.subjective.body, range = 1..5, onSelected = onBodyChange)
                    RatingSelector(label = "余韵", value = uiState.subjective.aftertaste, range = 1..5, onSelected = onAftertasteChange)
                    RatingSelector(label = "总体评分", value = uiState.subjective.overall, range = 1..10, onSelected = onOverallChange)
                    TagSelector(
                        tags = uiState.flavorTags.map { it.name },
                        selected = uiState.subjective.tags.map { it.name }.toSet(),
                        onToggle = { name ->
                            uiState.flavorTags.firstOrNull { it.name == name }?.let(onToggleTag)
                        },
                    )
                    OutlinedTextField(
                        value = customTag,
                        onValueChange = { customTag = it },
                        label = { Text("自定义风味标签") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        onAddCustomTag(customTag)
                        customTag = ""
                    }) {
                        Text("添加标签")
                    }
                    OutlinedTextField(
                        value = uiState.subjective.notes,
                        onValueChange = onSubjectiveNotesChange,
                        label = { Text("主观备注") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard(
                    title = "提交前检查",
                    subtitle = "确认与参考记录的差异，并补齐提交所需字段。",
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        uiState.objective.recipeNameSnapshot?.let {
                            StatChip(text = "来源配方 $it")
                        }
                        uiState.subjective.overall?.let {
                            StatChip(text = "总体评分 $it / 10")
                        }
                    }
                    uiState.referenceRecord?.let { reference ->
                        val comparison = buildComparisonSummary(uiState.record ?: reference, reference)
                        Text(text = comparison.headline, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = comparison.details.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (uiState.validationErrors.isNotEmpty()) {
                        Text(
                            text = "提交前还需要补充这些内容：",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        uiState.validationErrors.forEach { error ->
                            Text(
                                text = "• $error",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveRecipeDialog) {
        SaveRecipeDialog(
            onDismiss = { showSaveRecipeDialog = false },
            onConfirm = { name ->
                onSaveCurrentAsRecipe(name)
                showSaveRecipeDialog = false
            },
        )
    }
}

@Composable
private fun StepChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = QoffeeDashboardTheme.colors.panelMuted,
            selectedContainerColor = QoffeeDashboardTheme.colors.accentSoft,
        ),
    )
}

@Composable
private fun SaveRecipeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("另存为配方") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("配方名称") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }) {
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

private fun editorTitle(uiState: RecordEditorUiState): String {
    return when (uiState.entry) {
        RecordEditorEntry.NEW -> "新建记录"
        RecordEditorEntry.DRAFT -> "继续填写记录"
        RecordEditorEntry.DUPLICATE -> "复制上一杯"
        RecordEditorEntry.RECIPE -> "从配方开始"
    }
}

private fun editorSubtitle(uiState: RecordEditorUiState): String {
    return when {
        uiState.entry == RecordEditorEntry.RECIPE && !uiState.objective.recipeNameSnapshot.isNullOrBlank() ->
            "已从配方 ${uiState.objective.recipeNameSnapshot} 预填客观参数。"
        uiState.entry == RecordEditorEntry.DUPLICATE && uiState.sourceRecord != null ->
            "已复制 ${uiState.sourceRecord.beanNameSnapshot ?: "上一杯"} 的参数与标签，可在此基础上微调。"
        uiState.entry == RecordEditorEntry.DRAFT ->
            "回到未完成的草稿，继续补完整体客观与主观信息。"
        else -> "先完成客观参数，再补主观感受，整个过程都会自动保存。"
    }
}

private fun formatDateTime(timestampMillis: Long): String {
    return SimpleDateFormat("M/d HH:mm", Locale.CHINA).format(Date(timestampMillis))
}

private fun formatNumber(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}

private fun CoffeeRecord.toObjectiveForm() = ObjectiveFormState(
    recipeTemplateId = recipeTemplateId,
    recipeNameSnapshot = recipeNameSnapshot,
    brewMethod = brewMethod,
    beanProfileId = beanProfileId,
    grinderProfileId = grinderProfileId,
    grindSetting = grindSetting?.toString().orEmpty(),
    coffeeDoseG = coffeeDoseG?.toString().orEmpty(),
    brewWaterMl = brewWaterMl?.toString().orEmpty(),
    bypassWaterMl = bypassWaterMl?.toString().orEmpty(),
    waterTempC = waterTempC?.toString().orEmpty(),
    notes = notes,
)

private fun RecipeTemplate.toObjectiveForm() = ObjectiveFormState(
    recipeTemplateId = id,
    recipeNameSnapshot = name,
    brewMethod = brewMethod,
    beanProfileId = beanProfileId,
    grinderProfileId = grinderProfileId,
    grindSetting = grindSetting?.toString().orEmpty(),
    coffeeDoseG = coffeeDoseG?.toString().orEmpty(),
    brewWaterMl = brewWaterMl?.toString().orEmpty(),
    bypassWaterMl = bypassWaterMl?.toString().orEmpty(),
    waterTempC = waterTempC?.toString().orEmpty(),
    notes = notes,
)

private fun CoffeeRecord.toSubjectiveForm() = SubjectiveFormState(
    aroma = subjectiveEvaluation?.aroma,
    acidity = subjectiveEvaluation?.acidity,
    sweetness = subjectiveEvaluation?.sweetness,
    bitterness = subjectiveEvaluation?.bitterness,
    body = subjectiveEvaluation?.body,
    aftertaste = subjectiveEvaluation?.aftertaste,
    overall = subjectiveEvaluation?.overall,
    notes = subjectiveEvaluation?.notes.orEmpty(),
    tags = subjectiveEvaluation?.flavorTags.orEmpty(),
)
