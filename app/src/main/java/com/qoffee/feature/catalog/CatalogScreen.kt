package com.qoffee.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RoastLevel
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.HeroCard
import com.qoffee.ui.components.SectionCard
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
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CatalogScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onSaveBean = viewModel::saveBean,
        onSaveGrinder = viewModel::saveGrinder,
    )
}

@Composable
private fun CatalogScreen(
    paddingValues: PaddingValues,
    uiState: CatalogUiState,
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
            title = "Build a reusable catalog",
            subtitle = "Store beans and grinders once, then reuse them in records and filters.",
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Beans") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Grinders") })
        }

        if (selectedTab == 0) {
            SectionCard(title = "Bean profiles") {
                OutlinedButton(
                    onClick = {
                        editingBean = null
                        showBeanDialog = true
                    },
                ) {
                    Text("Add bean")
                }
                if (uiState.beans.isEmpty()) {
                    EmptyStateCard(
                        title = "No bean profiles yet",
                        subtitle = "Create your first bean profile to speed up future records.",
                    )
                } else {
                    uiState.beans.forEach { bean ->
                        CatalogItemCard(
                            title = bean.name,
                            subtitle = listOf(bean.roaster, bean.roastLevel.displayName)
                                .filter { it.isNotBlank() }
                                .joinToString(" | "),
                            onEdit = {
                                editingBean = bean
                                showBeanDialog = true
                            },
                        )
                    }
                }
            }
        } else {
            SectionCard(title = "Grinder profiles") {
                OutlinedButton(
                    onClick = {
                        editingGrinder = null
                        showGrinderDialog = true
                    },
                ) {
                    Text("Add grinder")
                }
                if (uiState.grinders.isEmpty()) {
                    EmptyStateCard(
                        title = "No grinder profiles yet",
                        subtitle = "Define your grinders and ranges so record validation can follow your setup.",
                    )
                } else {
                    uiState.grinders.forEach { grinder ->
                        CatalogItemCard(
                            title = grinder.name,
                            subtitle = "${grinder.minSetting}-${grinder.maxSetting} ${grinder.unitLabel} | step ${grinder.stepSize}",
                            onEdit = {
                                editingGrinder = grinder
                                showGrinderDialog = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (showBeanDialog) {
        BeanEditorDialog(
            initialValue = editingBean,
            onDismiss = { showBeanDialog = false },
            onSave = {
                onSaveBean(it)
                showBeanDialog = false
            },
        )
    }

    if (showGrinderDialog) {
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
private fun CatalogItemCard(
    title: String,
    subtitle: String,
    onEdit: () -> Unit,
) {
    SectionCard(title = title) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onEdit) {
            Text("Edit")
        }
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
    var process by remember(initialValue) { mutableStateOf(initialValue?.process.orEmpty()) }
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
        title = { Text(if (initialValue == null) "Add bean profile" else "Edit bean profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = roaster, onValueChange = { roaster = it }, label = { Text("Roaster") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = origin, onValueChange = { origin = it }, label = { Text("Origin") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = process, onValueChange = { process = it }, label = { Text("Process") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = variety, onValueChange = { variety = it }, label = { Text("Variety") }, modifier = Modifier.fillMaxWidth())
                DropdownField(
                    label = "Roast level",
                    selectedLabel = roastLevel.displayName,
                    options = RoastLevel.entries.map { DropdownOption(it.displayName, it) },
                    onSelected = { selected -> selected?.let { roastLevel = it } },
                    allowClear = false,
                )
                OutlinedTextField(
                    value = roastDate,
                    onValueChange = { roastDate = it },
                    label = { Text("Roast date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate = try {
                        roastDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse)?.toEpochDay()
                    } catch (_: DateTimeParseException) {
                        error = "Roast date must use YYYY-MM-DD."
                        return@Button
                    }
                    if (name.isBlank()) {
                        error = "Bean name can not be blank."
                        return@Button
                    }
                    scope.launch {
                        onSave(
                            BeanProfile(
                                id = initialValue?.id ?: 0L,
                                name = name.trim(),
                                roaster = roaster.trim(),
                                origin = origin.trim(),
                                process = process.trim(),
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
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
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
        title = { Text(if (initialValue == null) "Add grinder" else "Edit grinder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = minSetting, onValueChange = { minSetting = it }, label = { Text("Min setting") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = maxSetting, onValueChange = { maxSetting = it }, label = { Text("Max setting") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = stepSize, onValueChange = { stepSize = it }, label = { Text("Step size") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unitLabel, onValueChange = { unitLabel = it }, label = { Text("Unit label") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
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
                        error = "Grinder name can not be blank."
                        return@Button
                    }
                    if (min == null || max == null || step == null) {
                        error = "Please enter a valid range and step size."
                        return@Button
                    }
                    if (min >= max) {
                        error = "Max setting must be greater than min setting."
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
                                unitLabel = unitLabel.ifBlank { "click" },
                                notes = notes.trim(),
                                createdAt = initialValue?.createdAt ?: 0L,
                            ),
                        )
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
