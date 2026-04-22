package com.qoffee.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.UserSettings
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = preferenceRepository.observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings(),
    )

    val beans: StateFlow<List<BeanProfile>> = catalogRepository.observeBeanProfiles().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val grinders: StateFlow<List<GrinderProfile>> = catalogRepository.observeGrinderProfiles().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
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

    fun setShowLearnInDock(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setShowLearnInDock(enabled)
        }
    }

    fun setDefaultBean(beanId: Long?) {
        viewModelScope.launch {
            preferenceRepository.setDefaultBeanProfile(beanId)
        }
    }

    fun setDefaultGrinder(grinderId: Long?) {
        viewModelScope.launch {
            preferenceRepository.setDefaultGrinderProfile(grinderId)
        }
    }
}

@Composable
fun SettingsMenuRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onOpenRecordSettings: () -> Unit,
    onOpenAnalysisSettings: () -> Unit,
    onOpenNavigationSettings: () -> Unit,
) {
    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = "设置",
            subtitle = null,
            eyebrow = "QOFFEE / MINE / SETTINGS",
        )
        OutlinedButton(onClick = onBack) {
            Text("返回")
        }
        SectionCard(title = "目录") {
            SettingsMenuItem(
                title = "记录设置",
                subtitle = "草稿恢复与录入偏好",
                icon = Icons.Outlined.Restore,
                onClick = onOpenRecordSettings,
            )
            SettingsMenuItem(
                title = "分析设置",
                subtitle = "默认时间范围与洞察显示",
                icon = Icons.Outlined.AutoGraph,
                onClick = onOpenAnalysisSettings,
            )
            SettingsMenuItem(
                title = "导航设置",
                subtitle = "学习页在底栏中的显示",
                icon = Icons.Outlined.ViewDay,
                onClick = onOpenNavigationSettings,
            )
        }
    }
}

@Composable
fun RecordSettingsRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val beans by viewModel.beans.collectAsStateWithLifecycle()
    val grinders by viewModel.grinders.collectAsStateWithLifecycle()
    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = "记录设置",
            subtitle = null,
            eyebrow = "QOFFEE / SETTINGS / RECORD",
        )
        OutlinedButton(onClick = onBack) {
            Text("返回")
        }
        SectionCard(title = "草稿") {
            SettingToggleCard(
                title = "自动恢复活动草稿",
                subtitle = "再次打开记录编辑页时，优先接着填写未完成的草稿。",
                checked = settings.autoRestoreDraft,
                onCheckedChange = viewModel::setAutoRestoreDraft,
            )
            DropdownField(
                label = "Default Bean",
                selectedLabel = beans.firstOrNull { it.id == settings.defaultBeanProfileId }?.name,
                options = beans.map { DropdownOption(it.name, it.id) },
                onSelected = viewModel::setDefaultBean,
            )
            DropdownField(
                label = "Default Grinder",
                selectedLabel = grinders.firstOrNull { it.id == settings.defaultGrinderProfileId }?.name,
                options = grinders.map { DropdownOption(it.name, it.id) },
                onSelected = viewModel::setDefaultGrinder,
            )
        }
    }
}

@Composable
fun AnalysisSettingsRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = "分析设置",
            subtitle = null,
            eyebrow = "QOFFEE / SETTINGS / ANALYSIS",
        )
        OutlinedButton(onClick = onBack) {
            Text("返回")
        }
        SectionCard(title = "分析偏好") {
            SettingToggleCard(
                title = "显示洞察置信度",
                subtitle = "在分析卡片里显示置信度，帮助判断结论是否稳妥。",
                checked = settings.showInsightConfidence,
                onCheckedChange = viewModel::setShowInsightConfidence,
            )
            DropdownField(
                label = "默认分析时间范围",
                selectedLabel = settings.defaultAnalysisTimeRange.displayName,
                options = AnalysisTimeRange.entries.map { DropdownOption(it.displayName, it) },
                onSelected = { selected -> selected?.let(viewModel::setDefaultAnalysisRange) },
                allowClear = false,
            )
        }
    }
}

@Composable
fun NavigationSettingsRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = "导航设置",
            subtitle = null,
            eyebrow = "QOFFEE / SETTINGS / NAVIGATION",
        )
        OutlinedButton(onClick = onBack) {
            Text("返回")
        }
        SectionCard(title = "底栏") {
            SettingToggleCard(
                title = "在底栏显示学习页",
                subtitle = "关闭后，学习页会从底栏移除，但仍可以从“我的”进入。",
                checked = settings.showLearnInDock,
                onCheckedChange = viewModel::setShowLearnInDock,
            )
        }
    }
}

@Composable
private fun SettingsMenuItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = icon, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
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
}
