@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.qoffee.feature.guides

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.GuideTemplate
import com.qoffee.domain.repository.GuideRepository
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class GuideLibraryViewModel @Inject constructor(
    guideRepository: GuideRepository,
) : ViewModel() {
    val guides: StateFlow<List<GuideTemplate>> = guideRepository.observeGuides().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}

@HiltViewModel
class GuideDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    guideRepository: GuideRepository,
) : ViewModel() {
    private val guideId = checkNotNull(savedStateHandle.get<Long>(QoffeeDestinations.guideIdArg))
    val guide: StateFlow<GuideTemplate?> = guideRepository.observeGuide(guideId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
}

@Composable
fun GuideLibraryRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onOpenGuide: (Long) -> Unit,
    viewModel: GuideLibraryViewModel = hiltViewModel(),
) {
    val guides by viewModel.guides.collectAsStateWithLifecycle()
    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = "指导库",
            subtitle = "先预览，再开始；内置模板和你自己的指导会放在一起。",
            eyebrow = "QOFFEE / GUIDE",
        )
        SectionCard(title = "怎么使用") {
            Text("1. 先打开一条指导，完整浏览阶段卡片。", style = MaterialTheme.typography.bodyMedium)
            Text("2. 熟悉后点击开始，会进入全屏计时卡片。", style = MaterialTheme.typography.bodyMedium)
            Text("3. 结束后回到记录页，重点补主观感受。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        }
        SectionCard(title = "全部指导") {
            if (guides.isEmpty()) {
                EmptyStateCard(
                    title = "还没有指导",
                    subtitle = "你可以先用内置模板，也可以从任意一条记录生成自己的指导。",
                )
            } else {
                guides.forEach { guide ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        onClick = { onOpenGuide(guide.id) },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(guide.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = guide.description.ifBlank { guide.brewMethod?.displayName ?: "自定义指导" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                guide.brewMethod?.let { StatChip(text = it.displayName) }
                                StatChip(text = "${guide.stages.size} 个阶段")
                                if (guide.isBuiltIn) StatChip(text = "内置")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GuideDetailRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onStart: (Long) -> Unit,
    viewModel: GuideDetailViewModel = hiltViewModel(),
) {
    val guide by viewModel.guide.collectAsStateWithLifecycle()
    val safeGuide = guide
    if (safeGuide == null) {
        EmptyStateCard(
            title = "未找到指导",
            subtitle = "这个指导可能正在加载，或者已经被删除。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }
    var expandAll by remember { mutableStateOf(true) }
    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = safeGuide.title,
            subtitle = safeGuide.description.ifBlank { safeGuide.brewMethod?.displayName ?: "指导模板" },
            eyebrow = "QOFFEE / GUIDE / DETAIL",
        )
        SectionCard(title = "预览") {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                safeGuide.brewMethod?.let { StatChip(text = it.displayName) }
                StatChip(text = "${safeGuide.stages.size} 个阶段")
                if (safeGuide.isBuiltIn) StatChip(text = "内置")
                safeGuide.sourceRecordId?.let { StatChip(text = "来源记录 $it") }
            }
            Button(onClick = { onStart(safeGuide.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("开始指导")
            }
            OutlinedButton(onClick = { expandAll = !expandAll }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expandAll) "收起阶段卡片" else "展开全部阶段")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        }
        if (expandAll) {
            SectionCard(title = "阶段卡片") {
                safeGuide.stages.forEachIndexed { index, stage ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("${index + 1}. ${stage.title}", style = MaterialTheme.typography.titleSmall)
                            Text(stage.instruction, style = MaterialTheme.typography.bodyMedium)
                            if (stage.targetValueLabel.isNotBlank()) {
                                StatChip(text = stage.targetValueLabel)
                            }
                            if (stage.tip.isNotBlank()) {
                                Text(
                                    text = stage.tip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
