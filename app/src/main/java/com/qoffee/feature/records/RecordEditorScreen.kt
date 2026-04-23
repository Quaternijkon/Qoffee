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
import com.qoffee.core.model.DraftReplacePolicy
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.ObjectiveDraftUpdate
import com.qoffee.core.model.RecordPrefillSource
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.core.model.ThermalContainerType
import com.qoffee.core.model.WaterCurveTemperatureMode
import com.qoffee.core.model.analyze
import com.qoffee.core.model.buildLegacyWaterCurve
import com.qoffee.core.model.buildLegacyWaterCurveSummary
import com.qoffee.core.model.deriveValues
import com.qoffee.core.model.formatNormalizedGrind
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.BeanIdentityCard
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.GrindDialField
import com.qoffee.ui.components.InlineRulerField
import com.qoffee.ui.components.NumericStepField
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.MovieRatingSelector
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.TagSelector
import com.qoffee.ui.components.DateTimePickerField
import com.qoffee.ui.components.WaterCurveEditor
import com.qoffee.ui.components.WaterCurveFormResult
import com.qoffee.ui.components.WaterCurveStageEditorState
import com.qoffee.ui.components.WaterCurveStageKind
import com.qoffee.ui.components.buildWaterCurveFormResult
import com.qoffee.ui.components.createDefaultWaterCurveStage
import com.qoffee.ui.components.toEditorStageStates
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
    val brewedAtMillis: Long? = null,
    val grindSetting: String = "",
    val coffeeDoseG: String = "",
    val brewWaterMl: String = "",
    val bypassWaterMl: String = "",
    val waterTempC: String = "",
    val brewDurationSeconds: String = "",
    val waterCurveTemperatureMode: WaterCurveTemperatureMode = WaterCurveTemperatureMode.POUR_WATER,
    val waterCurveAmbientTempC: String = "",
    val waterCurveContainerType: ThermalContainerType? = null,
    val waterCurveStages: List<WaterCurveStageEditorState> = emptyList(),
    val legacyWaterCurveSummary: String? = null,
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
        val beanId = savedStateHandle.get<Long>(QoffeeDestinations.beanIdArg) ?: -1L
        val entryValue = savedStateHandle.get<String>(QoffeeDestinations.recordEntryArg)
        val entry = RecordEditorEntry.entries.firstOrNull { it.value == entryValue } ?: RecordEditorEntry.NEW

        viewModelScope.launch {
            val settings = preferenceRepository.observeSettings().first()
            val sourceRecord = duplicateFrom.takeIf { it > 0L }?.let { recordRepository.getRecord(it) }
            val resolvedRecordId = when {
                entry == RecordEditorEntry.RECIPE && recipeId > 0L -> {
                    recordRepository.createDraft(
                        prefillSource = RecordPrefillSource.Recipe(recipeId),
                        replacePolicy = DraftReplacePolicy.REPLACE_CURRENT,
                    )
                }
                entry == RecordEditorEntry.BEAN && beanId > 0L -> {
                    recordRepository.createDraft(
                        prefillSource = RecordPrefillSource.Bean(beanId),
                        replacePolicy = DraftReplacePolicy.REPLACE_CURRENT,
                    )
                }
                duplicateFrom > 0L -> {
                    recordRepository.createDraft(
                        prefillSource = RecordPrefillSource.Record(duplicateFrom),
                        replacePolicy = DraftReplacePolicy.REPLACE_CURRENT,
                    )
                }
                entry == RecordEditorEntry.NEW -> {
                    recordRepository.createDraft(
                        prefillSource = RecordPrefillSource.Blank,
                        replacePolicy = DraftReplacePolicy.REPLACE_CURRENT,
                    )
                }
                requestedRecordId > 0L -> requestedRecordId
                else -> {
                    recordRepository.createDraft(
                        prefillSource = RecordPrefillSource.Draft,
                        replacePolicy = if (settings.autoRestoreDraft) {
                            DraftReplacePolicy.KEEP_CURRENT
                        } else {
                            DraftReplacePolicy.REPLACE_CURRENT
                        },
                    )
                }
            }

            val shouldSeedDefaults = entry == RecordEditorEntry.NEW &&
                requestedRecordId <= 0L &&
                duplicateFrom <= 0L &&
                recipeId <= 0L &&
                beanId <= 0L

            if (shouldSeedDefaults) {
                val currentDraft = recordRepository.getRecord(resolvedRecordId)
                if (
                    currentDraft != null &&
                    currentDraft.beanProfileId == null &&
                    currentDraft.grinderProfileId == null &&
                    (settings.defaultBeanProfileId != null || settings.defaultGrinderProfileId != null)
                ) {
                    recordRepository.updateObjective(
                        recordId = resolvedRecordId,
                        update = ObjectiveDraftUpdate(
                            beanProfileId = settings.defaultBeanProfileId,
                            grinderProfileId = settings.defaultGrinderProfileId,
                        ),
                    )
                }
            }

            if (entry == RecordEditorEntry.BEAN && beanId > 0L) {
                val currentDraft = recordRepository.getRecord(resolvedRecordId)
                if (currentDraft != null && currentDraft.grinderProfileId == null && settings.defaultGrinderProfileId != null) {
                    recordRepository.updateObjective(
                        recordId = resolvedRecordId,
                        update = ObjectiveDraftUpdate(
                            recipeTemplateId = currentDraft.recipeTemplateId,
                            recipeNameSnapshot = currentDraft.recipeNameSnapshot,
                            brewMethod = currentDraft.brewMethod,
                            beanProfileId = currentDraft.beanProfileId,
                            grinderProfileId = settings.defaultGrinderProfileId,
                            grindSetting = currentDraft.grindSetting,
                            coffeeDoseG = currentDraft.coffeeDoseG,
                            brewWaterMl = currentDraft.brewWaterMl,
                            bypassWaterMl = currentDraft.bypassWaterMl,
                            waterTempC = currentDraft.waterTempC,
                            waterCurve = currentDraft.waterCurve,
                            brewedAt = currentDraft.brewedAt,
                            brewDurationSeconds = currentDraft.brewDurationSeconds,
                            notes = currentDraft.notes,
                        ),
                    )
                }
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
                val currentObjective = uiStateInternal.value.objective
                uiStateInternal.value = uiStateInternal.value.copy(
                    objective = recipe.toObjectiveForm(
                        brewedAtMillis = currentObjective.brewedAtMillis,
                    ),
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
        val recordId = uiStateInternal.value.recordId ?: return
        viewModelScope.launch {
            recordRepository.updateObjective(recordId, uiStateInternal.value.objective.toDraftUpdate())
            val recipeId = recordRepository.saveRecordAsRecipe(recordId = recordId, name = normalized)
            val recipe = recipeRepository.getRecipe(recipeId) ?: return@launch
            val currentObjective = uiStateInternal.value.objective
            updateObjective(
                recipe.toObjectiveForm(
                    brewedAtMillis = currentObjective.brewedAtMillis,
                ),
            )
        }
    }

    fun overwriteCurrentRecipe() {
        val recordId = uiStateInternal.value.recordId ?: return
        val recipeId = uiStateInternal.value.objective.recipeTemplateId ?: return
        val recipeName = uiStateInternal.value.objective.recipeNameSnapshot ?: return
        viewModelScope.launch {
            recordRepository.updateObjective(recordId, uiStateInternal.value.objective.toDraftUpdate())
            recordRepository.saveRecordAsRecipe(
                recordId = recordId,
                name = recipeName,
                targetRecipeId = recipeId,
            )
        }
    }

    fun updateBrewMethod(method: BrewMethod?) = updateObjective(uiStateInternal.value.objective.copy(brewMethod = method))

    fun updateBean(beanId: Long?) = updateObjective(uiStateInternal.value.objective.copy(beanProfileId = beanId))

    fun updateGrinder(grinderId: Long?) = updateObjective(uiStateInternal.value.objective.copy(grinderProfileId = grinderId))

    fun updateBrewedAt(timestampMillis: Long?) = updateObjective(uiStateInternal.value.objective.copy(brewedAtMillis = timestampMillis))

    fun updateGrindSetting(text: String) = updateObjective(uiStateInternal.value.objective.copy(grindSetting = text))

    fun updateCoffeeDose(text: String) = updateObjective(uiStateInternal.value.objective.copy(coffeeDoseG = text))

    fun updateBrewWater(text: String) = updateObjective(uiStateInternal.value.objective.copy(brewWaterMl = text))

    fun updateBypassWater(text: String) = updateObjective(uiStateInternal.value.objective.copy(bypassWaterMl = text))

    fun updateWaterTemp(text: String) = updateObjective(uiStateInternal.value.objective.copy(waterTempC = text))

    fun updateBrewDuration(text: String) = updateObjective(uiStateInternal.value.objective.copy(brewDurationSeconds = text))

    fun updateWaterCurveTemperatureMode(mode: WaterCurveTemperatureMode) =
        updateObjective(uiStateInternal.value.objective.copy(waterCurveTemperatureMode = mode))

    fun updateWaterCurveAmbientTemp(text: String) =
        updateObjective(uiStateInternal.value.objective.copy(waterCurveAmbientTempC = text))

    fun updateWaterCurveContainerType(containerType: ThermalContainerType?) =
        updateObjective(uiStateInternal.value.objective.copy(waterCurveContainerType = containerType))

    fun addWaterCurveStage(kind: WaterCurveStageKind) {
        val current = uiStateInternal.value.objective
        updateObjective(
            current.copy(
                waterCurveStages = current.waterCurveStages + createDefaultWaterCurveStage(kind, current.waterCurveStages),
                legacyWaterCurveSummary = null,
            ),
        )
    }

    fun updateWaterCurveStage(index: Int, stage: WaterCurveStageEditorState) {
        val current = uiStateInternal.value.objective
        updateObjective(
            current.copy(
                waterCurveStages = current.waterCurveStages.mapIndexed { stageIndex, currentStage ->
                    if (stageIndex == index) stage else currentStage
                },
                legacyWaterCurveSummary = null,
            ),
        )
    }

    fun moveWaterCurveStageUp(index: Int) {
        if (index <= 0) return
        val updated = uiStateInternal.value.objective.waterCurveStages.toMutableList().apply {
            val stage = removeAt(index)
            add(index - 1, stage)
        }
        updateObjective(uiStateInternal.value.objective.copy(waterCurveStages = updated))
    }

    fun moveWaterCurveStageDown(index: Int) {
        val stages = uiStateInternal.value.objective.waterCurveStages
        if (index >= stages.lastIndex) return
        val updated = stages.toMutableList().apply {
            val stage = removeAt(index)
            add(index + 1, stage)
        }
        updateObjective(uiStateInternal.value.objective.copy(waterCurveStages = updated))
    }

    fun removeWaterCurveStage(index: Int) {
        val updated = uiStateInternal.value.objective.waterCurveStages.filterIndexed { stageIndex, _ -> stageIndex != index }
        updateObjective(
            uiStateInternal.value.objective.copy(
                waterCurveStages = updated,
                legacyWaterCurveSummary = null,
            ),
        )
    }

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
        val objectiveErrors = validateObjectiveInput(uiStateInternal.value.objective)
        if (objectiveErrors.isNotEmpty()) {
            uiStateInternal.value = uiStateInternal.value.copy(validationErrors = objectiveErrors)
            return
        }
        viewModelScope.launch {
            recordRepository.updateObjective(recordId, uiStateInternal.value.objective.toDraftUpdate())
            val result = recordRepository.completeRecord(recordId)
            if (result.isValid) {
                eventsInternal.emit(RecordEditorEvent.Completed(recordId))
            } else {
                uiStateInternal.value = uiStateInternal.value.copy(validationErrors = result.errors)
            }
        }
    }

    private fun updateObjective(objective: ObjectiveFormState) {
        val normalized = normalizeObjectiveWaterFields(objective)
        uiStateInternal.value = uiStateInternal.value.copy(objective = normalized, validationErrors = emptyList())
        persistObjective()
        refreshReferenceAsync(beanId = normalized.beanProfileId, brewMethod = normalized.brewMethod)
    }

    private fun updateSubjective(subjective: SubjectiveFormState) {
        uiStateInternal.value = uiStateInternal.value.copy(subjective = subjective)
        persistSubjective()
    }

    private fun persistObjective() {
        val recordId = uiStateInternal.value.recordId ?: return
        viewModelScope.launch {
            recordRepository.updateObjective(recordId = recordId, update = uiStateInternal.value.objective.toDraftUpdate())
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
        onOverwriteCurrentRecipe = viewModel::overwriteCurrentRecipe,
        onMethodChange = viewModel::updateBrewMethod,
        onBeanChange = viewModel::updateBean,
        onGrinderChange = viewModel::updateGrinder,
        onBrewedAtChange = viewModel::updateBrewedAt,
        onGrindSettingChange = viewModel::updateGrindSetting,
        onCoffeeDoseChange = viewModel::updateCoffeeDose,
        onWaterCurveTemperatureModeChange = viewModel::updateWaterCurveTemperatureMode,
        onWaterCurveAmbientTempChange = viewModel::updateWaterCurveAmbientTemp,
        onWaterCurveContainerTypeChange = viewModel::updateWaterCurveContainerType,
        onAddWaterCurveStage = viewModel::addWaterCurveStage,
        onWaterCurveStageChange = viewModel::updateWaterCurveStage,
        onMoveWaterCurveStageUp = viewModel::moveWaterCurveStageUp,
        onMoveWaterCurveStageDown = viewModel::moveWaterCurveStageDown,
        onRemoveWaterCurveStage = viewModel::removeWaterCurveStage,
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
private fun RecordEditorScreenLegacy(
    paddingValues: PaddingValues,
    uiState: RecordEditorUiState,
    onBack: () -> Unit,
    onRecipeSelected: (Long?) -> Unit,
    onSaveCurrentAsRecipe: (String) -> Unit,
    onOverwriteCurrentRecipe: () -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (Long?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onBrewedAtChange: (Long?) -> Unit,
    onGrindSettingChange: (String) -> Unit,
    onCoffeeDoseChange: (String) -> Unit,
    onWaterCurveTemperatureModeChange: (WaterCurveTemperatureMode) -> Unit,
    onWaterCurveAmbientTempChange: (String) -> Unit,
    onWaterCurveContainerTypeChange: (ThermalContainerType?) -> Unit,
    onAddWaterCurveStage: (WaterCurveStageKind) -> Unit,
    onWaterCurveStageChange: (Int, WaterCurveStageEditorState) -> Unit,
    onMoveWaterCurveStageUp: (Int) -> Unit,
    onMoveWaterCurveStageDown: (Int) -> Unit,
    onRemoveWaterCurveStage: (Int) -> Unit,
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
    RecordEditorScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        onRecipeSelected = onRecipeSelected,
        onSaveCurrentAsRecipe = onSaveCurrentAsRecipe,
        onOverwriteCurrentRecipe = onOverwriteCurrentRecipe,
        onMethodChange = onMethodChange,
        onBeanChange = onBeanChange,
        onGrinderChange = onGrinderChange,
        onBrewedAtChange = onBrewedAtChange,
        onGrindSettingChange = onGrindSettingChange,
        onCoffeeDoseChange = onCoffeeDoseChange,
        onWaterCurveTemperatureModeChange = onWaterCurveTemperatureModeChange,
        onWaterCurveAmbientTempChange = onWaterCurveAmbientTempChange,
        onWaterCurveContainerTypeChange = onWaterCurveContainerTypeChange,
        onAddWaterCurveStage = onAddWaterCurveStage,
        onWaterCurveStageChange = onWaterCurveStageChange,
        onMoveWaterCurveStageUp = onMoveWaterCurveStageUp,
        onMoveWaterCurveStageDown = onMoveWaterCurveStageDown,
        onRemoveWaterCurveStage = onRemoveWaterCurveStage,
        onObjectiveNotesChange = onObjectiveNotesChange,
        onAromaChange = onAromaChange,
        onAcidityChange = onAcidityChange,
        onSweetnessChange = onSweetnessChange,
        onBitternessChange = onBitternessChange,
        onBodyChange = onBodyChange,
        onAftertasteChange = onAftertasteChange,
        onOverallChange = onOverallChange,
        onSubjectiveNotesChange = onSubjectiveNotesChange,
        onToggleTag = onToggleTag,
        onAddCustomTag = onAddCustomTag,
        onSubmit = onSubmit,
    )
    return
    /*
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
    val waterCurveResult = buildWaterCurveFormResult(
        temperatureMode = uiState.objective.waterCurveTemperatureMode,
        stages = uiState.objective.waterCurveStages,
        brewMethod = uiState.objective.brewMethod,
    )
    val waterCurveDerivedValues = waterCurveResult.curve?.deriveValues(uiState.objective.coffeeDoseG.toDoubleOrNull())
    val onBrewWaterChange: (String) -> Unit = {}
    val onBypassWaterChange: (String) -> Unit = {}
    val onWaterTempChange: (String) -> Unit = {}
    val onBrewDurationChange: (String) -> Unit = {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag(QoffeeTestTags.RECORD_EDITOR_SCREEN),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageHeader(
            title = editorTitle(uiState),
            subtitle = editorSubtitle(uiState),
            eyebrow = "QOFFEE / RECORD",
        )

        SectionCard(title = "来源") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip(text = when (uiState.entry) {
                    RecordEditorEntry.NEW -> "空白新建"
                    RecordEditorEntry.DRAFT -> "继续草稿"
                    RecordEditorEntry.DUPLICATE -> "复制上一杯"
                    RecordEditorEntry.RECIPE -> "从配方开始"
                    RecordEditorEntry.BEAN -> "从豆子开始"
                })
                uiState.objective.recipeNameSnapshot?.let { StatChip(text = "配方 $it") }
                if (uiState.entry == RecordEditorEntry.BEAN) {
                    uiState.beans.firstOrNull { it.id == uiState.objective.beanProfileId }?.let { bean ->
                        StatChip(text = bean.name)
                    }
                }
            }
            uiState.referenceRecord?.let { reference ->
                Text(
                    text = "${reference.beanNameSnapshot ?: "未命名咖啡豆"} · ${reference.brewMethod?.displayName ?: "未指定方式"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "步骤") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepChip(label = "客观", selected = currentStep == 0, onClick = { currentStep = 0 })
                StepChip(label = "主观", selected = currentStep == 1, onClick = { currentStep = 1 })
            }
        }

        if (currentStep == 0) {
            SectionCard(title = "配方", subtitle = "配方就是可复用的记录客观参数，可以直接从这条记录生成。") {
                DropdownField(
                    label = "采用配方",
                    selectedLabel = uiState.recipes.firstOrNull { it.id == uiState.objective.recipeTemplateId }?.name
                        ?: uiState.objective.recipeNameSnapshot,
                    options = uiState.recipes.map { DropdownOption(it.name, it.id) },
                    onSelected = onRecipeSelected,
                    allowClear = false,
                )
                OutlinedButton(
                    onClick = { showSaveRecipeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为配方")
                }
                if (uiState.objective.recipeTemplateId != null && !uiState.objective.recipeNameSnapshot.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onOverwriteCurrentRecipe,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("覆盖原配方")
                    }
                }
            }

            SectionCard(title = "参数") {
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
                DateTimePickerField(
                    label = "记录时间",
                    valueMillis = uiState.objective.brewedAtMillis,
                    onValueChange = { onBrewedAtChange(it) },
                    modifier = Modifier.fillMaxWidth(),
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
                NumericStepField(
                    label = "Brew Time (s)",
                    value = uiState.objective.brewDurationSeconds,
                    onValueChange = onBrewDurationChange,
                    step = 5.0,
                    decimals = 0,
                    quickValues = listOf("30", "120", "180"),
                    referenceValue = uiState.referenceRecord?.brewDurationSeconds?.let { "$it s" },
                )
                OutlinedTextField(
                    value = uiState.objective.notes,
                    onValueChange = onObjectiveNotesChange,
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            SectionCard(title = "主观感受") {
                MovieRatingSelector(label = "香气", value = uiState.subjective.aroma, range = 1..5, onSelected = onAromaChange)
                MovieRatingSelector(label = "酸质", value = uiState.subjective.acidity, range = 1..5, onSelected = onAcidityChange)
                MovieRatingSelector(label = "甜感", value = uiState.subjective.sweetness, range = 1..5, onSelected = onSweetnessChange)
                MovieRatingSelector(label = "苦感", value = uiState.subjective.bitterness, range = 1..5, onSelected = onBitternessChange)
                MovieRatingSelector(label = "醇厚", value = uiState.subjective.body, range = 1..5, onSelected = onBodyChange)
                MovieRatingSelector(label = "余韵", value = uiState.subjective.aftertaste, range = 1..5, onSelected = onAftertasteChange)
                MovieRatingSelector(label = "总体评分", value = uiState.subjective.overall, range = 1..5, onSelected = onOverallChange)
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

            SectionCard(title = "检查") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.objective.recipeNameSnapshot?.let {
                        StatChip(text = "配方 $it")
                    }
                    uiState.subjective.overall?.let {
                        StatChip(text = "评分 $it / 5")
                    }
                }
                uiState.referenceRecord?.let { reference ->
                    val comparison = buildComparisonSummary(uiState.record ?: reference, reference)
                    Text(text = comparison.headline, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = comparison.details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.validationErrors.isNotEmpty()) {
                    uiState.validationErrors.forEach { error ->
                        Text(
                            text = "• $error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        SectionCard(title = "操作") {
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
                    Text(if (currentStep == 0) "下一步" else "提交")
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
    */
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordEditorScreen(
    paddingValues: PaddingValues,
    uiState: RecordEditorUiState,
    onBack: () -> Unit,
    onRecipeSelected: (Long?) -> Unit,
    onSaveCurrentAsRecipe: (String) -> Unit,
    onOverwriteCurrentRecipe: () -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (Long?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onBrewedAtChange: (Long?) -> Unit,
    onGrindSettingChange: (String) -> Unit,
    onCoffeeDoseChange: (String) -> Unit,
    onWaterCurveTemperatureModeChange: (WaterCurveTemperatureMode) -> Unit,
    onWaterCurveAmbientTempChange: (String) -> Unit,
    onWaterCurveContainerTypeChange: (ThermalContainerType?) -> Unit,
    onAddWaterCurveStage: (WaterCurveStageKind) -> Unit,
    onWaterCurveStageChange: (Int, WaterCurveStageEditorState) -> Unit,
    onMoveWaterCurveStageUp: (Int) -> Unit,
    onMoveWaterCurveStageDown: (Int) -> Unit,
    onRemoveWaterCurveStage: (Int) -> Unit,
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
    val selectedGrinder = uiState.grinders.firstOrNull { it.id == uiState.objective.grinderProfileId }
    val normalizedGrindValue = uiState.objective.grindSetting.toDoubleOrNull()
        ?.let { raw -> selectedGrinder?.normalization?.normalize(raw) }
    val waterCurveResult = buildWaterCurveFormResult(
        temperatureMode = uiState.objective.waterCurveTemperatureMode,
        ambientTempText = uiState.objective.waterCurveAmbientTempC,
        containerType = uiState.objective.waterCurveContainerType,
        stages = uiState.objective.waterCurveStages,
        brewMethod = uiState.objective.brewMethod,
    )
    val waterCurveDerivedValues = waterCurveResult.curve?.deriveValues(uiState.objective.coffeeDoseG.toDoubleOrNull())
    val waterCurveAnalysis = waterCurveResult.curve?.analyze(
        coffeeDoseG = uiState.objective.coffeeDoseG.toDoubleOrNull(),
        grindSetting = uiState.objective.grindSetting.toDoubleOrNull(),
        grinderProfile = selectedGrinder,
        roastLevel = selectedBean?.roastLevel ?: uiState.record?.beanRoastLevelSnapshot,
        brewMethod = uiState.objective.brewMethod,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag(QoffeeTestTags.RECORD_EDITOR_SCREEN),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageHeader(
            title = editorTitle(uiState),
            subtitle = editorSubtitle(uiState),
            eyebrow = "QOFFEE / RECORD",
        )

        SectionCard(title = "来源") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip(text = when (uiState.entry) {
                    RecordEditorEntry.NEW -> "空白新建"
                    RecordEditorEntry.DRAFT -> "继续草稿"
                    RecordEditorEntry.DUPLICATE -> "复制上一杯"
                    RecordEditorEntry.RECIPE -> "从配方开始"
                    RecordEditorEntry.BEAN -> "从豆子开始"
                })
                uiState.objective.recipeNameSnapshot?.let { StatChip(text = "配方 $it") }
                if (uiState.entry == RecordEditorEntry.BEAN) {
                    uiState.beans.firstOrNull { it.id == uiState.objective.beanProfileId }?.let { bean ->
                        StatChip(text = bean.name)
                    }
                }
            }
            uiState.referenceRecord?.let { reference ->
                Text(
                    text = "${reference.beanNameSnapshot ?: "未命名豆子"} · ${reference.brewMethod?.displayName ?: "未指定方式"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "步骤") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepChip(label = "客观", selected = currentStep == 0, onClick = { currentStep = 0 })
                StepChip(label = "主观", selected = currentStep == 1, onClick = { currentStep = 1 })
            }
        }

        if (currentStep == 0) {
            SectionCard(title = "配方", subtitle = "配方就是可复用的记录客观参数，可以直接从这条记录生成。") {
                DropdownField(
                    label = "采用配方",
                    selectedLabel = uiState.recipes.firstOrNull { it.id == uiState.objective.recipeTemplateId }?.name
                        ?: uiState.objective.recipeNameSnapshot,
                    options = uiState.recipes.map { DropdownOption(it.name, it.id) },
                    onSelected = onRecipeSelected,
                    allowClear = false,
                )
                OutlinedButton(
                    onClick = { showSaveRecipeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为配方")
                }
                if (uiState.objective.recipeTemplateId != null && !uiState.objective.recipeNameSnapshot.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onOverwriteCurrentRecipe,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("覆盖原配方")
                    }
                }
            }

            SectionCard(title = "参数") {
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
                DateTimePickerField(
                    label = "记录时间",
                    valueMillis = uiState.objective.brewedAtMillis,
                    onValueChange = { onBrewedAtChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                GrindDialField(
                    label = selectedGrinder?.let { "${it.name} 刻度" } ?: "研磨格数",
                    value = uiState.objective.grindSetting,
                    onValueChange = onGrindSettingChange,
                    minValue = selectedGrinder?.minSetting ?: 0.0,
                    maxValue = selectedGrinder?.maxSetting ?: 40.0,
                    step = selectedGrinder?.stepSize ?: 1.0,
                    decimals = 1,
                    referenceValue = uiState.referenceRecord?.grindSetting?.let(::formatNumber),
                    normalizedValueText = normalizedGrindValue?.let(::formatNormalizedGrind),
                )
                InlineRulerField(
                    label = "咖啡粉重量",
                    value = uiState.objective.coffeeDoseG,
                    onValueChange = onCoffeeDoseChange,
                    minValue = 0.0,
                    maxValue = 60.0,
                    step = 0.5,
                    decimals = 1,
                    unit = "g",
                    referenceValue = uiState.referenceRecord?.coffeeDoseG?.let { "${formatNumber(it)} g" },
                )
                if (waterCurveDerivedValues != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        waterCurveDerivedValues.brewWaterMl?.let { StatChip(text = "萃取水 ${formatNumber(it)}ml") }
                        waterCurveDerivedValues.bypassWaterMl?.let { StatChip(text = "旁路 ${formatNumber(it)}ml") }
                        waterCurveDerivedValues.brewDurationSeconds?.let { StatChip(text = "时长 ${it}s") }
                        normalizedGrindValue?.let { StatChip(text = "归一化 ${formatNormalizedGrind(it)}") }
                    }
                }
                WaterCurveEditor(
                    temperatureMode = uiState.objective.waterCurveTemperatureMode,
                    onTemperatureModeChange = onWaterCurveTemperatureModeChange,
                    ambientTempText = uiState.objective.waterCurveAmbientTempC,
                    onAmbientTempChange = onWaterCurveAmbientTempChange,
                    containerType = uiState.objective.waterCurveContainerType,
                    onContainerTypeChange = onWaterCurveContainerTypeChange,
                    stages = uiState.objective.waterCurveStages,
                    onStageChange = onWaterCurveStageChange,
                    onAddStage = onAddWaterCurveStage,
                    onMoveStageUp = onMoveWaterCurveStageUp,
                    onMoveStageDown = onMoveWaterCurveStageDown,
                    onRemoveStage = onRemoveWaterCurveStage,
                    previewCurve = waterCurveResult.curve,
                    derivedValues = waterCurveDerivedValues,
                    analysis = waterCurveAnalysis,
                    legacySummary = uiState.objective.legacyWaterCurveSummary,
                )
                OutlinedTextField(
                    value = uiState.objective.notes,
                    onValueChange = onObjectiveNotesChange,
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            SectionCard(title = "主观感受") {
                MovieRatingSelector(label = "香气", value = uiState.subjective.aroma, range = 1..5, onSelected = onAromaChange)
                MovieRatingSelector(label = "酸质", value = uiState.subjective.acidity, range = 1..5, onSelected = onAcidityChange)
                MovieRatingSelector(label = "甜感", value = uiState.subjective.sweetness, range = 1..5, onSelected = onSweetnessChange)
                MovieRatingSelector(label = "苦感", value = uiState.subjective.bitterness, range = 1..5, onSelected = onBitternessChange)
                MovieRatingSelector(label = "醇厚", value = uiState.subjective.body, range = 1..5, onSelected = onBodyChange)
                MovieRatingSelector(label = "余韵", value = uiState.subjective.aftertaste, range = 1..5, onSelected = onAftertasteChange)
                MovieRatingSelector(label = "总体评分", value = uiState.subjective.overall, range = 1..5, onSelected = onOverallChange)
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

            SectionCard(title = "检查") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.objective.recipeNameSnapshot?.let {
                        StatChip(text = "配方 $it")
                    }
                    uiState.subjective.overall?.let {
                        StatChip(text = "评分 $it / 5")
                    }
                }
                uiState.referenceRecord?.let { reference ->
                    val comparison = buildComparisonSummary(uiState.record ?: reference, reference)
                    Text(text = comparison.headline, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = comparison.details.joinToString(" 路 "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.validationErrors.isNotEmpty()) {
                    uiState.validationErrors.forEach { error ->
                        Text(
                            text = "• $error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        SectionCard(title = "操作") {
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
                    Text(if (currentStep == 0) "下一步" else "提交")
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
        title = { Text("设为配方") },
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
        RecordEditorEntry.BEAN -> "从豆子开始"
    }
}

private fun editorSubtitle(uiState: RecordEditorUiState): String {
    val beanName = uiState.record?.beanNameSnapshot
    return when {
        uiState.entry == RecordEditorEntry.RECIPE && !uiState.objective.recipeNameSnapshot.isNullOrBlank() ->
            "已从配方 ${uiState.objective.recipeNameSnapshot} 预填客观参数。"
        uiState.entry == RecordEditorEntry.DUPLICATE && uiState.sourceRecord != null ->
            "已复制 ${uiState.sourceRecord.beanNameSnapshot ?: "上一杯"} 的参数与标签，可在此基础上微调。"
        uiState.entry == RecordEditorEntry.BEAN && !beanName.isNullOrBlank() ->
            "已为你预填豆子 $beanName，现在可以直接补齐其余客观参数。"
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

private fun validateObjectiveInput(objective: ObjectiveFormState): List<String> {
    val errors = mutableListOf<String>()
    if (objective.brewedAtMillis == null) {
        errors += "请选择有效的记录时间。"
    }
    val waterCurveErrors = objective.buildWaterCurveFormResult().errors
    if (objective.waterCurveStages.isNotEmpty()) {
        errors += waterCurveErrors
    }
    return errors
}

private fun ObjectiveFormState.toDraftUpdate(): ObjectiveDraftUpdate {
    val result = buildWaterCurveFormResult()
    val derived = result.curve?.deriveValues(coffeeDoseG.toDoubleOrNull())
    return ObjectiveDraftUpdate(
        recipeTemplateId = recipeTemplateId,
        recipeNameSnapshot = recipeNameSnapshot,
        brewMethod = brewMethod,
        beanProfileId = beanProfileId,
        grinderProfileId = grinderProfileId,
        grindSetting = grindSetting.toDoubleOrNull(),
        coffeeDoseG = coffeeDoseG.toDoubleOrNull(),
        brewWaterMl = derived?.brewWaterMl ?: brewWaterMl.toDoubleOrNull(),
        bypassWaterMl = derived?.bypassWaterMl ?: bypassWaterMl.toDoubleOrNull(),
        waterTempC = derived?.waterTempC ?: waterTempC.toDoubleOrNull(),
        waterCurve = result.curve,
        brewedAt = brewedAtMillis,
        brewDurationSeconds = derived?.brewDurationSeconds ?: brewDurationSeconds.toIntOrNull(),
        notes = notes,
    )
}

private fun CoffeeRecord.toObjectiveForm(): ObjectiveFormState {
    val editableCurve = waterCurve ?: buildLegacyWaterCurve(
        brewWaterMl = brewWaterMl,
        bypassWaterMl = bypassWaterMl,
        waterTempC = waterTempC,
        brewDurationSeconds = brewDurationSeconds,
    )
    return ObjectiveFormState(
        recipeTemplateId = recipeTemplateId,
        recipeNameSnapshot = recipeNameSnapshot,
        brewMethod = brewMethod,
        beanProfileId = beanProfileId,
        grinderProfileId = grinderProfileId,
        brewedAtMillis = brewedAt,
        grindSetting = grindSetting?.toString().orEmpty(),
        coffeeDoseG = coffeeDoseG?.toString().orEmpty(),
        brewWaterMl = brewWaterMl?.toString().orEmpty(),
        bypassWaterMl = bypassWaterMl?.toString().orEmpty(),
        waterTempC = waterTempC?.toString().orEmpty(),
        brewDurationSeconds = brewDurationSeconds?.toString().orEmpty(),
        waterCurveTemperatureMode = editableCurve?.temperatureMode ?: WaterCurveTemperatureMode.POUR_WATER,
        waterCurveAmbientTempC = editableCurve?.ambientTempC?.let(::formatNumber).orEmpty(),
        waterCurveContainerType = editableCurve?.containerType,
        waterCurveStages = editableCurve.toEditorStageStates(),
        legacyWaterCurveSummary = if (waterCurve == null && editableCurve == null) {
            buildLegacyWaterCurveSummary(
                brewWaterMl = brewWaterMl,
                bypassWaterMl = bypassWaterMl,
                waterTempC = waterTempC,
                brewDurationSeconds = brewDurationSeconds,
            )
        } else {
            null
        },
        notes = notes,
    )
}

private fun RecipeTemplate.toObjectiveForm(
    brewedAtMillis: Long? = System.currentTimeMillis(),
): ObjectiveFormState {
    return ObjectiveFormState(
        recipeTemplateId = id,
        recipeNameSnapshot = name,
        brewMethod = brewMethod,
        beanProfileId = beanProfileId,
        grinderProfileId = grinderProfileId,
        brewedAtMillis = brewedAtMillis,
        grindSetting = grindSetting?.toString().orEmpty(),
        coffeeDoseG = coffeeDoseG?.toString().orEmpty(),
        brewWaterMl = brewWaterMl?.toString().orEmpty(),
        bypassWaterMl = bypassWaterMl?.toString().orEmpty(),
        waterTempC = waterTempC?.toString().orEmpty(),
        waterCurveTemperatureMode = waterCurve?.temperatureMode ?: WaterCurveTemperatureMode.POUR_WATER,
        waterCurveAmbientTempC = waterCurve?.ambientTempC?.let(::formatNumber).orEmpty(),
        waterCurveContainerType = waterCurve?.containerType,
        waterCurveStages = waterCurve.toEditorStageStates(),
        legacyWaterCurveSummary = if (waterCurve == null) {
            buildLegacyWaterCurveSummary(
                brewWaterMl = brewWaterMl,
                bypassWaterMl = bypassWaterMl,
                waterTempC = waterTempC,
                brewDurationSeconds = null,
            )
        } else {
            null
        },
        notes = notes,
    )
}

private fun ObjectiveFormState.buildWaterCurveFormResult(): WaterCurveFormResult {
    return buildWaterCurveFormResult(
        temperatureMode = waterCurveTemperatureMode,
        ambientTempText = waterCurveAmbientTempC,
        containerType = waterCurveContainerType,
        stages = waterCurveStages,
        brewMethod = brewMethod,
    )
}

private fun normalizeObjectiveWaterFields(objective: ObjectiveFormState): ObjectiveFormState {
    if (objective.waterCurveStages.isEmpty()) {
        return if (objective.legacyWaterCurveSummary == null) {
            objective.copy(
                brewWaterMl = "",
                bypassWaterMl = "",
                waterTempC = "",
                brewDurationSeconds = "",
            )
        } else {
            objective
        }
    }
    val result = objective.buildWaterCurveFormResult()
    val derived = result.curve?.deriveValues(objective.coffeeDoseG.toDoubleOrNull()) ?: return objective
    return objective.copy(
        brewWaterMl = derived.brewWaterMl?.let(::formatNumber).orEmpty(),
        bypassWaterMl = derived.bypassWaterMl?.let(::formatNumber).orEmpty(),
        waterTempC = derived.waterTempC?.let(::formatNumber).orEmpty(),
        brewDurationSeconds = derived.brewDurationSeconds?.toString().orEmpty(),
    )
}

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
