package com.qoffee.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.GlossaryTerm
import com.qoffee.core.model.LearningTrack
import com.qoffee.core.model.Lesson
import com.qoffee.core.model.TroubleshootingItem
import com.qoffee.core.model.UserEntitlements
import com.qoffee.domain.repository.EntitlementRepository
import com.qoffee.domain.repository.LearningRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.FeatureEntryCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LearnUiState(
    val tracks: List<LearningTrack> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val glossaryTerms: List<GlossaryTerm> = emptyList(),
    val troubleshootingItems: List<TroubleshootingItem> = emptyList(),
    val entitlements: UserEntitlements = UserEntitlements(),
)

@HiltViewModel
class LearnViewModel @Inject constructor(
    learningRepository: LearningRepository,
    entitlementRepository: EntitlementRepository,
) : ViewModel() {

    val uiState: StateFlow<LearnUiState> = combine(
        learningRepository.observeTracks(),
        learningRepository.observeLessons(),
        learningRepository.observeGlossaryTerms(),
        learningRepository.observeTroubleshootingItems(),
        entitlementRepository.observeEntitlements(),
    ) { tracks, lessons, terms, troubleshooting, entitlements ->
        LearnUiState(
            tracks = tracks,
            lessons = lessons,
            glossaryTerms = terms,
            troubleshootingItems = troubleshooting,
            entitlements = entitlements,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LearnUiState(),
    )
}

@Composable
fun LearnRoute(
    paddingValues: PaddingValues,
    onBack: (() -> Unit)? = null,
    eyebrow: String = "QOFFEE / LEARN",
    title: String = "学习中心",
    viewModel: LearnViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LearnScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onBack = onBack,
        eyebrow = eyebrow,
        title = title,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearnScreen(
    paddingValues: PaddingValues,
    uiState: LearnUiState,
    onBack: (() -> Unit)?,
    eyebrow: String,
    title: String,
) {
    var selectedSection by remember { mutableStateOf(LearnSection.TRACKS) }

    DashboardPage(
        paddingValues = paddingValues,
        testTag = QoffeeTestTags.LEARN_SCREEN,
    ) {
        PageHeader(
            title = title,
            subtitle = null,
            eyebrow = eyebrow,
        )
        if (onBack != null) {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
        }

        SectionCard(title = "入口") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureEntryCard(
                    title = "课程",
                    hint = "分级路线",
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    onClick = { selectedSection = LearnSection.TRACKS },
                    modifier = Modifier.weight(1f),
                    selected = selectedSection == LearnSection.TRACKS,
                )
                FeatureEntryCard(
                    title = "术语",
                    hint = "快速查阅",
                    icon = Icons.Outlined.PlayArrow,
                    onClick = { selectedSection = LearnSection.GLOSSARY },
                    modifier = Modifier.weight(1f),
                    selected = selectedSection == LearnSection.GLOSSARY,
                )
                FeatureEntryCard(
                    title = "排查",
                    hint = "问题定位",
                    icon = Icons.Outlined.AutoGraph,
                    onClick = { selectedSection = LearnSection.TROUBLESHOOT },
                    modifier = Modifier.weight(1f),
                    selected = selectedSection == LearnSection.TROUBLESHOOT,
                )
            }
        }

        when (selectedSection) {
            LearnSection.TRACKS -> SectionCard(title = "课程") {
                uiState.tracks.forEach { track ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = track.title, style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatChip(text = track.level.displayName, leadingIcon = Icons.AutoMirrored.Outlined.MenuBook)
                            StatChip(text = "${track.lessonCount} 节")
                            if (track.proOnly) {
                                StatChip(text = "Pro")
                            }
                        }
                    }
                }
                uiState.lessons.take(4).forEach { lesson ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = lesson.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${lesson.type.displayName} · ${lesson.estimatedMinutes} 分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            LearnSection.GLOSSARY -> SectionCard(title = "术语") {
                uiState.glossaryTerms.forEach { term ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = term.term, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = term.shortDefinition,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (term.relatedTerms.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                term.relatedTerms.forEach { related ->
                                    StatChip(text = related)
                                }
                            }
                        }
                    }
                }
            }

            LearnSection.TROUBLESHOOT -> SectionCard(title = "故障排查") {
                uiState.troubleshootingItems.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = item.symptom, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = item.likelyCauses.firstOrNull() ?: "待补充",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        item.adjustments.firstOrNull()?.let { adjustment ->
                            Text(
                                text = "• $adjustment",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        SectionCard(title = "Pro") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.entitlements.proHighlights.forEach { feature ->
                    StatChip(text = feature)
                }
            }
        }
    }
}

private enum class LearnSection {
    TRACKS,
    GLOSSARY,
    TROUBLESHOOT,
}
