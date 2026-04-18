package com.qoffee.feature.records

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

internal enum class EditorStep(val label: String) {
    OBJECTIVE("Objective"),
    SUBJECTIVE("Subjective"),
    CONFIRM("Confirm"),
}

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
    val step: EditorStep = EditorStep.OBJECTIVE,
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
                    step = current.step,
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

    fun updateStep(stepIndex: Int) {
        uiStateInternal.value = uiStateInternal.value.copy(
            step = EditorStep.entries[stepIndex.coerceIn(0, EditorStep.entries.lastIndex)],
            validationErrors = emptyList(),
        )
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
                    step = EditorStep.CONFIRM,
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
        onStepChange = viewModel::updateStep,
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
    onStepChange: (Int) -> Unit,
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

    if (uiState.isLoading) {
        EmptyStateCard(
            title = "Preparing draft",
            subtitle = "Qoffee is restoring the active draft or creating a new one.",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val selectedStepIndex = uiState.step.ordinal
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
            title = "One record, multiple passes",
            subtitle = "Objective inputs save as draft instantly. Subjective notes can be added now or later.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorStep.entries.forEachIndexed { index, step ->
                OutlinedButton(onClick = { onStepChange(index) }) {
                    Text(step.label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(text = "Auto saved")
            uiState.recordId?.let { StatChip(text = "Record #$it") }
            uiState.record?.status?.let { status ->
                StatChip(text = if (status.name == "DRAFT") "Draft" else "Completed")
            }
        }

        when (uiState.step) {
            EditorStep.OBJECTIVE -> {
                ObjectiveStep(
                    uiState = uiState,
                    onMethodChange = onMethodChange,
                    onBeanChange = onBeanChange,
                    onGrinderChange = onGrinderChange,
                    onGrindSettingChange = onGrindSettingChange,
                    onCoffeeDoseChange = onCoffeeDoseChange,
                    onBrewWaterChange = onBrewWaterChange,
                    onBypassWaterChange = onBypassWaterChange,
                    onWaterTempChange = onWaterTempChange,
                    onObjectiveNotesChange = onObjectiveNotesChange,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Button(onClick = { onStepChange(selectedStepIndex + 1) }) { Text("Next") }
                }
            }

            EditorStep.SUBJECTIVE -> {
                SubjectiveStep(
                    uiState = uiState,
                    customTag = customTag,
                    onCustomTagChange = { customTag = it },
                    onAddCustomTag = {
                        onAddCustomTag(customTag)
                        customTag = ""
                    },
                    onAromaChange = onAromaChange,
                    onAcidityChange = onAcidityChange,
                    onSweetnessChange = onSweetnessChange,
                    onBitternessChange = onBitternessChange,
                    onBodyChange = onBodyChange,
                    onAftertasteChange = onAftertasteChange,
                    onOverallChange = onOverallChange,
                    onSubjectiveNotesChange = onSubjectiveNotesChange,
                    onToggleTag = onToggleTag,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onStepChange(selectedStepIndex - 1) }) { Text("Back") }
                    Button(onClick = { onStepChange(selectedStepIndex + 1) }) { Text("Confirm") }
                }
            }

            EditorStep.CONFIRM -> {
                ConfirmStep(
                    record = uiState.record,
                    objective = uiState.objective,
                    subjective = uiState.subjective,
                    validationErrors = uiState.validationErrors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onStepChange(selectedStepIndex - 1) }) { Text("Back") }
                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.testTag("submit_record_button"),
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

@Composable
private fun ObjectiveStep(
    uiState: RecordEditorUiState,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (Long?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onGrindSettingChange: (String) -> Unit,
    onCoffeeDoseChange: (String) -> Unit,
    onBrewWaterChange: (String) -> Unit,
    onBypassWaterChange: (String) -> Unit,
    onWaterTempChange: (String) -> Unit,
    onObjectiveNotesChange: (String) -> Unit,
) {
    SectionCard(title = "Objective inputs") {
        DropdownField(
            label = "Brew method",
            selectedLabel = uiState.objective.brewMethod?.displayName,
            options = BrewMethod.entries.map { DropdownOption(it.displayName, it) },
            onSelected = onMethodChange,
            modifier = Modifier.testTag("brew_method_dropdown"),
        )
        DropdownField(
            label = "Bean",
            selectedLabel = uiState.beans.firstOrNull { it.id == uiState.objective.beanProfileId }?.name,
            options = uiState.beans.map { DropdownOption(it.name, it.id) },
            onSelected = onBeanChange,
            modifier = Modifier.testTag("bean_dropdown"),
        )
        DropdownField(
            label = "Grinder",
            selectedLabel = uiState.grinders.firstOrNull { it.id == uiState.objective.grinderProfileId }?.name,
            options = uiState.grinders.map { DropdownOption(it.name, it.id) },
            onSelected = onGrinderChange,
        )
        OutlinedTextField(
            value = uiState.objective.grindSetting,
            onValueChange = onGrindSettingChange,
            label = { Text("Grind setting") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.objective.coffeeDoseG,
            onValueChange = onCoffeeDoseChange,
            label = { Text("Coffee dose (g)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.objective.brewWaterMl,
            onValueChange = onBrewWaterChange,
            label = { Text("Brew water (ml)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.objective.bypassWaterMl,
            onValueChange = onBypassWaterChange,
            label = { Text("Bypass water (ml)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.objective.waterTempC,
            onValueChange = onWaterTempChange,
            label = { Text("Water temp (C)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.objective.notes,
            onValueChange = onObjectiveNotesChange,
            label = { Text("Objective notes") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SubjectiveStep(
    uiState: RecordEditorUiState,
    customTag: String,
    onCustomTagChange: (String) -> Unit,
    onAddCustomTag: () -> Unit,
    onAromaChange: (Int?) -> Unit,
    onAcidityChange: (Int?) -> Unit,
    onSweetnessChange: (Int?) -> Unit,
    onBitternessChange: (Int?) -> Unit,
    onBodyChange: (Int?) -> Unit,
    onAftertasteChange: (Int?) -> Unit,
    onOverallChange: (Int?) -> Unit,
    onSubjectiveNotesChange: (String) -> Unit,
    onToggleTag: (FlavorTag) -> Unit,
) {
    SectionCard(title = "Subjective inputs") {
        RatingSelector(label = "Aroma", value = uiState.subjective.aroma, range = 1..5, onSelected = onAromaChange)
        RatingSelector(label = "Acidity", value = uiState.subjective.acidity, range = 1..5, onSelected = onAcidityChange)
        RatingSelector(label = "Sweetness", value = uiState.subjective.sweetness, range = 1..5, onSelected = onSweetnessChange)
        RatingSelector(label = "Bitterness", value = uiState.subjective.bitterness, range = 1..5, onSelected = onBitternessChange)
        RatingSelector(label = "Body", value = uiState.subjective.body, range = 1..5, onSelected = onBodyChange)
        RatingSelector(label = "Aftertaste", value = uiState.subjective.aftertaste, range = 1..5, onSelected = onAftertasteChange)
        RatingSelector(label = "Overall", value = uiState.subjective.overall, range = 1..10, onSelected = onOverallChange)
        TagSelector(
            tags = uiState.flavorTags.map { it.name },
            selected = uiState.subjective.tags.map { it.name }.toSet(),
            onToggle = { name ->
                uiState.flavorTags.firstOrNull { it.name == name }?.let(onToggleTag)
            },
        )
        OutlinedTextField(
            value = customTag,
            onValueChange = onCustomTagChange,
            label = { Text("Custom flavor tag") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onAddCustomTag) {
            Text("Add")
        }
        OutlinedTextField(
            value = uiState.subjective.notes,
            onValueChange = onSubjectiveNotesChange,
            label = { Text("Subjective notes") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConfirmStep(
    record: CoffeeRecord?,
    objective: ObjectiveFormState,
    subjective: SubjectiveFormState,
    validationErrors: List<String>,
) {
    SectionCard(title = "Confirm submission") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledValue(label = "Method", value = objective.brewMethod?.displayName.orEmpty())
            LabeledValue(label = "Bean", value = record?.beanNameSnapshot.orEmpty())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledValue(label = "Dose", value = "${objective.coffeeDoseG} g")
            LabeledValue(label = "Brew water", value = "${objective.brewWaterMl} ml")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledValue(label = "Bypass", value = "${objective.bypassWaterMl} ml")
            LabeledValue(label = "Water temp", value = "${objective.waterTempC} C")
        }
        LabeledValue(label = "Overall subjective", value = subjective.overall?.let { "$it / 10" }.orEmpty())
        if (validationErrors.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "The following fields still need attention before submission:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                validationErrors.forEach { error ->
                    Text(
                        text = "- $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else {
            Text(
                text = "Objective parameters are already saved. Subjective input remains optional for submission.",
                style = MaterialTheme.typography.bodyLarge,
            )
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
