package com.qoffee.feature.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.UserSettings
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.HeroCard
import com.qoffee.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = preferenceRepository.observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings(),
    )

    fun setAutoRestoreDraft(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setAutoRestoreDraft(enabled)
        }
    }

    fun setShowInsightConfidence(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setShowInsightConfidence(enabled)
        }
    }

    fun setDefaultAnalysisRange(range: AnalysisTimeRange) {
        viewModelScope.launch {
            preferenceRepository.setDefaultAnalysisTimeRange(range)
        }
    }
}

@Composable
fun SettingsRoute(
    paddingValues: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScreen(
        paddingValues = paddingValues,
        settings = settings,
        onAutoRestoreChange = viewModel::setAutoRestoreDraft,
        onShowConfidenceChange = viewModel::setShowInsightConfidence,
        onDefaultRangeChange = viewModel::setDefaultAnalysisRange,
    )
}

@Composable
private fun SettingsScreen(
    paddingValues: PaddingValues,
    settings: UserSettings,
    onAutoRestoreChange: (Boolean) -> Unit,
    onShowConfidenceChange: (Boolean) -> Unit,
    onDefaultRangeChange: (AnalysisTimeRange) -> Unit,
) {
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
            title = "Tune the local experience",
            subtitle = "These settings are stored on device and only affect offline use.",
        )
        SectionCard(title = "Record settings") {
            SettingToggle(
                title = "Auto restore active draft",
                subtitle = "When you open the editor again, continue the unfinished draft first.",
                checked = settings.autoRestoreDraft,
                onCheckedChange = onAutoRestoreChange,
            )
        }
        SectionCard(title = "Analysis settings") {
            SettingToggle(
                title = "Show insight confidence",
                subtitle = "Display confidence strength alongside insight cards.",
                checked = settings.showInsightConfidence,
                onCheckedChange = onShowConfidenceChange,
            )
            DropdownField(
                label = "Default analysis range",
                selectedLabel = settings.defaultAnalysisTimeRange.displayName,
                options = AnalysisTimeRange.entries.map { DropdownOption(it.displayName, it) },
                onSelected = { selected -> selected?.let(onDefaultRangeChange) },
                allowClear = false,
            )
        }
        Text(
            text = "Qoffee v1 stores data locally with Room and DataStore. No account or upload is enabled.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
