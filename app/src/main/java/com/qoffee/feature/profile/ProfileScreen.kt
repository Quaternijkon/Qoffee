package com.qoffee.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.UserSettings
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val recipes: List<RecipeTemplate> = emptyList(),
    val settings: UserSettings = UserSettings(),
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    recipeRepository: RecipeRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        catalogRepository.observeBeanProfiles(),
        catalogRepository.observeGrinderProfiles(),
        recipeRepository.observeRecipes(),
        preferenceRepository.observeSettings(),
    ) { beans, grinders, recipes, settings ->
        ProfileUiState(
            beans = beans,
            grinders = grinders,
            recipes = recipes,
            settings = settings,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
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
fun ProfileRoute(
    paddingValues: PaddingValues,
    currentArchive: ArchiveSummary?,
    isReadOnlyArchive: Boolean,
    onOpenArchiveSheet: () -> Unit,
    onOpenBeanEditor: (Long?) -> Unit,
    onOpenGrinderEditor: (Long?) -> Unit,
    onOpenRecipeEditor: (Long?) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        paddingValues = paddingValues,
        currentArchive = currentArchive,
        isReadOnlyArchive = isReadOnlyArchive,
        uiState = uiState,
        onOpenArchiveSheet = onOpenArchiveSheet,
        onOpenBeanEditor = onOpenBeanEditor,
        onOpenGrinderEditor = onOpenGrinderEditor,
        onOpenRecipeEditor = onOpenRecipeEditor,
        onAutoRestoreChange = viewModel::setAutoRestoreDraft,
        onShowConfidenceChange = viewModel::setShowInsightConfidence,
        onDefaultRangeChange = viewModel::setDefaultAnalysisRange,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileScreen(
    paddingValues: PaddingValues,
    currentArchive: ArchiveSummary?,
    isReadOnlyArchive: Boolean,
    uiState: ProfileUiState,
    onOpenArchiveSheet: () -> Unit,
    onOpenBeanEditor: (Long?) -> Unit,
    onOpenGrinderEditor: (Long?) -> Unit,
    onOpenRecipeEditor: (Long?) -> Unit,
    onAutoRestoreChange: (Boolean) -> Unit,
    onShowConfidenceChange: (Boolean) -> Unit,
    onDefaultRangeChange: (AnalysisTimeRange) -> Unit,
) {
    DashboardPage(
        paddingValues = paddingValues,
        testTag = QoffeeTestTags.PROFILE_SCREEN,
    ) {
        PageHeader(
            title = "资产与偏好",
            subtitle = "资料、偏好设置和存档管理集中在这里。",
            eyebrow = "QOFFEE / PROFILE",
            trailing = {
                OutlinedButton(onClick = onOpenArchiveSheet) {
                    Text("存档管理")
                }
            },
        )

        SectionCard(
            title = "当前存档",
            subtitle = "查看当前工作区规模，并在只读状态下快速识别限制。",
        ) {
            Text(
                text = currentArchive?.archive?.name ?: "正在准备存档",
                style = MaterialTheme.typography.headlineMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip(text = currentArchive?.archive?.type?.displayName ?: "普通存档")
                StatChip(text = "记录 ${currentArchive?.recordCount ?: 0}")
                StatChip(text = "豆子 ${currentArchive?.beanCount ?: 0}")
                StatChip(text = "磨豆机 ${currentArchive?.grinderCount ?: 0}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "豆子资产",
                    value = uiState.beans.size.toString(),
                    supporting = "归档你的豆子信息",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "配方模板",
                    value = uiState.recipes.size.toString(),
                    supporting = "沉淀可复用客观参数",
                    modifier = Modifier.weight(1f),
                )
            }
            if (isReadOnlyArchive) {
                Text(
                    text = "示范存档只读，适合浏览结构和复盘方式；如需修改资料，请先复制一个自己的存档。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            title = "资料管理",
            subtitle = "把豆子、磨豆机和配方整理成长期可复用的个人资产。",
            modifier = Modifier.testTag(QoffeeTestTags.PROFILE_ASSETS),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onOpenBeanEditor(null) },
                    enabled = !isReadOnlyArchive,
                ) {
                    Text("新增咖啡豆")
                }
                OutlinedButton(
                    onClick = { onOpenGrinderEditor(null) },
                    enabled = !isReadOnlyArchive,
                ) {
                    Text("新增磨豆机")
                }
                OutlinedButton(
                    onClick = { onOpenRecipeEditor(null) },
                    enabled = !isReadOnlyArchive,
                ) {
                    Text("新增配方")
                }
            }

            Text(text = "咖啡豆", style = MaterialTheme.typography.titleMedium)
            if (uiState.beans.isEmpty()) {
                EmptyStateCard(
                    title = "还没有咖啡豆资料",
                    subtitle = "录入豆子资料后，记录页会更快，复盘维度也更完整。",
                )
            } else {
                uiState.beans.forEach { bean ->
                    ProfileListRow(
                        title = bean.name,
                        subtitle = buildString {
                            append(bean.processMethod.displayName)
                            append(" · ")
                            append(bean.roastLevel.displayName)
                            if (bean.roaster.isNotBlank()) {
                                append(" · ")
                                append(bean.roaster)
                            }
                        },
                        actionLabel = if (isReadOnlyArchive) null else "编辑",
                        onAction = { onOpenBeanEditor(bean.id) },
                    )
                }
            }

            Text(text = "磨豆机", style = MaterialTheme.typography.titleMedium)
            if (uiState.grinders.isEmpty()) {
                EmptyStateCard(
                    title = "还没有磨豆机资料",
                    subtitle = "录入设备和格数范围后，记录页可以更快填写和校验参数。",
                )
            } else {
                uiState.grinders.forEach { grinder ->
                    ProfileListRow(
                        title = grinder.name,
                        subtitle = "${grinder.minSetting}-${grinder.maxSetting} ${grinder.unitLabel} · 步进 ${grinder.stepSize}",
                        actionLabel = if (isReadOnlyArchive) null else "编辑",
                        onAction = { onOpenGrinderEditor(grinder.id) },
                    )
                }
            }

            Text(text = "配方模板", style = MaterialTheme.typography.titleMedium)
            if (uiState.recipes.isEmpty()) {
                EmptyStateCard(
                    title = "还没有配方模板",
                    subtitle = "把常用客观参数存成配方后，在记录页就能一键采用。",
                )
            } else {
                uiState.recipes.forEach { recipe ->
                    ProfileListRow(
                        title = recipe.name,
                        subtitle = buildString {
                            append(recipe.brewMethod?.displayName ?: "未指定方式")
                            recipe.beanNameSnapshot?.let {
                                append(" · ")
                                append(it)
                            }
                            recipe.grinderNameSnapshot?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        actionLabel = if (isReadOnlyArchive) null else "编辑",
                        onAction = { onOpenRecipeEditor(recipe.id) },
                    )
                }
            }
        }

        SectionCard(
            title = "记录与复盘偏好",
            subtitle = "控制草稿恢复逻辑、洞察展示方式和默认复盘窗口。",
        ) {
            SettingToggle(
                title = "自动恢复活动草稿",
                subtitle = "再次进入编辑页时，优先续写未完成草稿。",
                checked = uiState.settings.autoRestoreDraft,
                onCheckedChange = onAutoRestoreChange,
            )
            SettingToggle(
                title = "显示洞察置信度",
                subtitle = "在复盘卡片中标记结论的可靠性强弱。",
                checked = uiState.settings.showInsightConfidence,
                onCheckedChange = onShowConfidenceChange,
            )
            DropdownField(
                label = "默认复盘时间范围",
                selectedLabel = uiState.settings.defaultAnalysisTimeRange.displayName,
                options = AnalysisTimeRange.entries.map { DropdownOption(it.displayName, it) },
                onSelected = { selected -> selected?.let(onDefaultRangeChange) },
                allowClear = false,
            )
        }

        SectionCard(
            title = "本地优先",
            subtitle = "Qoffee 当前不依赖账号体系，默认把你的记录与资料保存在本机。",
        ) {
            Text(
                text = "这让它更适合作为长期个人咖啡日志：打开就用、离线可看、资产随存档组织，不必担心云端账户流程打断记录节奏。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileListRow(
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actionLabel != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
}
