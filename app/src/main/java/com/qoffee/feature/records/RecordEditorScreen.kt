package com.qoffee.feature.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.components.BeanIdentityCard
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.HeroCard
import com.qoffee.ui.components.LabeledValue
import com.qoffee.ui.components.RatingSelector
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.TagSelector
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class ObjectiveFormState(
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
    val recordId: Long? = null,
    val record: CoffeeRecord? = null,
    val objective: ObjectiveFormState = ObjectiveFormState(),
    val subjective: SubjectiveFormState = SubjectiveFormState(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val flavorTags: List<FlavorTag> = emptyList(),
    val validationErrors: List<String> = emptyList(),
)

private data class RecordEditorIntermediateState(
    val record: CoffeeRecord?,
    val beans: List<BeanProfile>,
    val grinders: List<GrinderProfile>,
)

internal sealed interface RecordEditorEvent {
    data class Completed(val recordId: Long) : RecordEditorEvent
}

@HiltViewModel
class RecordEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordRepository: RecordRepository,
    private val catalogRepository: CatalogRepository,
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
        viewModelScope.launch {
            val settings = preferenceRepository.observeSettings().first()
            val resolvedRecordId = when {
                duplicateFrom > 0L -> recordRepository.duplicateRecordAsDraft(duplicateFrom)
                requestedRecordId > 0L -> requestedRecordId
                else -> recordRepository.getOrCreateActiveDraftId(settings.autoRestoreDraft)
            }
            recordRepository.observeRecord(resolvedRecordId)
                .combine(catalogRepository.observeBeanProfiles()) { record, beans ->
                    record to beans
                }
                .combine(catalogRepository.observeGrinderProfiles()) { recordAndBeans, grinders ->
                    RecordEditorIntermediateState(
                        record = recordAndBeans.first,
                        beans = recordAndBeans.second,
                        grinders = grinders,
                    )
                }
                .combine(catalogRepository.observeFlavorTags()) { intermediate, tags ->
                    val current = uiStateInternal.value
                    RecordEditorUiState(
                        isLoading = false,
                        recordId = resolvedRecordId,
                        record = intermediate.record,
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
                        flavorTags = tags,
                        validationErrors = current.validationErrors,
                    )
                }.collect { newState ->
                    if (!initialBindingDone && newState.record != null) {
                        initialBindingDone = true
                    }
                    uiStateInternal.value = newState
                }
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
                uiStateInternal.value = uiStateInternal.value.copy(
                    validationErrors = result.errors,
                )
            }
        }
    }

    private fun updateObjective(objective: ObjectiveFormState) {
        uiStateInternal.value = uiStateInternal.value.copy(objective = objective, validationErrors = emptyList())
        persistObjective()
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

@Composable
private fun RecordEditorScreen(
    paddingValues: PaddingValues,
    uiState: RecordEditorUiState,
    onBack: () -> Unit,
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
    var customTag by remember { mutableStateOf("") }
    val selectedBean = uiState.beans.firstOrNull { it.id == uiState.objective.beanProfileId }
    val objectiveProgress = listOf(
        uiState.objective.brewMethod != null,
        uiState.objective.beanProfileId != null,
        uiState.objective.coffeeDoseG.isNotBlank(),
        uiState.objective.brewWaterMl.isNotBlank(),
        uiState.objective.waterTempC.isNotBlank() || uiState.objective.brewMethod?.isHotBrew == false,
    ).count { it } / 5f
    val subjectiveProgress = listOf(
        uiState.subjective.aroma != null,
        uiState.subjective.acidity != null,
        uiState.subjective.sweetness != null,
        uiState.subjective.bitterness != null,
        uiState.subjective.body != null,
        uiState.subjective.aftertaste != null,
        uiState.subjective.overall != null,
    ).count { it } / 7f

    if (uiState.isLoading) {
        EmptyStateCard(
            title = "正在准备草稿",
            subtitle = "Qoffee 正在恢复未完成草稿，或为你创建一条新记录。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    Scaffold(
        bottomBar = {
            SectionCard(
                title = "提交操作",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(text = "自动保存")
                    uiState.recordId?.let { StatChip(text = "记录 #$it") }
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_record_button"),
                ) {
                    Text("完成提交")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(innerPadding)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard(
                title = "在一页里完成整条记录",
                subtitle = "减少来回切换，把客观参数、豆子信息和主观感受顺手写完。",
            )

            OutlinedButton(onClick = onBack) {
                Text("返回")
            }

            SectionCard(title = "填写进度") {
                Text("客观参数", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = objectiveProgress, modifier = Modifier.fillMaxWidth())
                Text("主观感受", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = subjectiveProgress, modifier = Modifier.fillMaxWidth())
            }

            SectionCard(title = "冲煮参数") {
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
                OutlinedTextField(
                    value = uiState.objective.grindSetting,
                    onValueChange = onGrindSettingChange,
                    label = { Text("研磨格数") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.objective.coffeeDoseG,
                    onValueChange = onCoffeeDoseChange,
                    label = { Text("咖啡粉重量（g）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.objective.brewWaterMl,
                    onValueChange = onBrewWaterChange,
                    label = { Text("冲煮水量（ml）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.objective.bypassWaterMl,
                    onValueChange = onBypassWaterChange,
                    label = { Text("旁路水量（ml）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(visible = uiState.objective.brewMethod?.isHotBrew != false) {
                    OutlinedTextField(
                        value = uiState.objective.waterTempC,
                        onValueChange = onWaterTempChange,
                        label = { Text("水温（°C）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = uiState.objective.notes,
                    onValueChange = onObjectiveNotesChange,
                    label = { Text("冲煮备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "主观感受") {
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

            SectionCard(title = "提交前检查") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "制作方式", value = uiState.objective.brewMethod?.displayName.orEmpty())
                    LabeledValue(label = "咖啡豆", value = selectedBean?.name.orEmpty())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "粉量", value = "${uiState.objective.coffeeDoseG} g")
                    LabeledValue(label = "水量", value = "${uiState.objective.brewWaterMl} ml")
                }
                LabeledValue(label = "总体主观评分", value = uiState.subjective.overall?.let { "$it / 10" }.orEmpty())
                AnimatedVisibility(visible = uiState.validationErrors.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "提交前还需要补充这些内容：",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        uiState.validationErrors.forEach { error ->
                            Text(
                                text = "- $error",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                if (uiState.validationErrors.isEmpty()) {
                    Text(
                        text = "你可以随时提交。即使现在不填写主观感受，这条记录也会先完整保存下来。",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private fun CoffeeRecord.toObjectiveForm() = ObjectiveFormState(
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
