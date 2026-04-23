package com.qoffee.feature.brew

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.BrewSession
import com.qoffee.domain.repository.SessionRepository
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BrewSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val requestedMethod = BrewMethod.fromCode(
        savedStateHandle.get<String>(QoffeeDestinations.sessionMethodArg)?.ifBlank { null },
    )
    private val requestedGuideId = savedStateHandle.get<Long>(QoffeeDestinations.guideIdArg)?.takeIf { it > 0L }

    val session: StateFlow<BrewSession?> = sessionRepository.observeActiveSession().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            requestedGuideId?.let { guideId ->
                val current = sessionRepository.observeActiveSession().first()
                if (current == null || current.sourceGuideId != guideId || current.isCompleted) {
                    sessionRepository.startGuideSession(guideId)
                }
            } ?: requestedMethod?.let { method ->
                val current = sessionRepository.observeActiveSession().first()
                if (current == null || current.method != method || current.isCompleted) {
                    sessionRepository.startSession(method)
                }
            }
        }
    }

    fun startSession(method: BrewMethod) {
        viewModelScope.launch {
            sessionRepository.startSession(method)
        }
    }

    fun nextStage() {
        viewModelScope.launch {
            val current = session.value ?: return@launch
            if (current.currentStageIndex >= current.stages.lastIndex) {
                sessionRepository.finishActiveSession()
            } else {
                sessionRepository.moveToNextStage()
            }
        }
    }

    fun previousStage() {
        viewModelScope.launch {
            sessionRepository.moveToPreviousStage()
        }
    }

    fun pauseSession() {
        viewModelScope.launch {
            sessionRepository.pauseActiveSession()
        }
    }

    fun resumeSession() {
        viewModelScope.launch {
            sessionRepository.resumeActiveSession()
        }
    }

    fun finishSession() {
        viewModelScope.launch {
            sessionRepository.finishActiveSession()
        }
    }

    fun discardSession() {
        viewModelScope.launch {
            sessionRepository.discardActiveSession()
        }
    }
}

@Composable
fun BrewSessionRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onOpenRecordEditor: () -> Unit,
    viewModel: BrewSessionViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    BrewSessionScreen(
        paddingValues = paddingValues,
        session = session,
        onBack = onBack,
        onStartMethod = viewModel::startSession,
        onPreviousStage = viewModel::previousStage,
        onNextStage = viewModel::nextStage,
        onPauseSession = viewModel::pauseSession,
        onResumeSession = viewModel::resumeSession,
        onFinishSession = viewModel::finishSession,
        onDiscardSession = viewModel::discardSession,
        onOpenRecordEditor = onOpenRecordEditor,
    )
}

@Composable
private fun BrewSessionScreen(
    paddingValues: PaddingValues,
    session: BrewSession?,
    onBack: () -> Unit,
    onStartMethod: (BrewMethod) -> Unit,
    onPreviousStage: () -> Unit,
    onNextStage: () -> Unit,
    onPauseSession: () -> Unit,
    onResumeSession: () -> Unit,
    onFinishSession: () -> Unit,
    onDiscardSession: () -> Unit,
    onOpenRecordEditor: () -> Unit,
) {
    if (session == null) {
        DashboardPage(paddingValues = paddingValues) {
            PageHeader(
                title = "主动冲煮会话",
                subtitle = "从方法化步骤、计时和目标值对照开始，把冲煮变成一组可执行任务。",
                eyebrow = "QOFFEE / BREW SESSION",
            )
            EmptyStateCard(
                title = "还没有活动会话",
                subtitle = "选择一种家用冲煮方式，立刻开始一次带步骤的练习。",
            )
            Button(onClick = { onStartMethod(BrewMethod.POUR_OVER) }) {
                Text("开始手冲会话")
            }
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
        }
        return
    }

    var nowMillis by remember(session.id, session.isCompleted) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id, session.isCompleted) {
        while (!session.isCompleted) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedSeconds = session.activeElapsedMillis(nowMillis).coerceAtLeast(0L) / 1_000L
    val currentStage = session.currentStage
    val stageElapsedSeconds = session.stageElapsedMillis(nowMillis).coerceAtLeast(0L) / 1_000L
    val remainingStageSeconds = currentStage?.let { (it.targetDurationSeconds - stageElapsedSeconds.toInt()).coerceAtLeast(0) } ?: 0

    LaunchedEffect(session.id, session.currentStageIndex, session.isPaused, session.isCompleted) {
        while (!session.isCompleted) {
            val stage = session.currentStage ?: break
            val liveNow = System.currentTimeMillis()
            if (!session.isPaused && session.stageElapsedMillis(liveNow) >= stage.targetDurationSeconds * 1_000L) {
                if (session.currentStageIndex >= session.stages.lastIndex) {
                    onFinishSession()
                } else {
                    onNextStage()
                }
                break
            }
            delay(250)
        }
    }

    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = session.title,
            subtitle = "${session.method.displayName} · ${session.stages.size} 个阶段",
            eyebrow = "QOFFEE / GUIDED BREW",
        )

        SectionCard(
            title = "会话概览",
            subtitle = "把当前进度、计时和阶段目标集中展示。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "阶段进度",
                    value = "${session.currentStageIndex + 1}/${session.stages.size}",
                    supporting = currentStage?.title ?: "待开始",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "已用时间",
                    value = formatElapsed(elapsedSeconds.toInt()),
                    supporting = if (session.isPaused) "当前已暂停" else "整个会话的累计时长",
                    modifier = Modifier.weight(1f),
                )
            }
            LinearProgressIndicator(
                progress = session.progressFraction,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(text = session.method.displayName, leadingIcon = Icons.Outlined.PlayArrow)
                StatChip(text = if (session.isCompleted) "已完成" else "进行中", leadingIcon = Icons.Outlined.Timer)
            }
        }

        SectionCard(
            title = "当前阶段",
            subtitle = "跟随步骤执行，不需要一边回忆一边读长段文字。",
        ) {
            AnimatedContent(
                targetState = currentStage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "stageTransition",
            ) { stage ->
                if (stage == null) {
                    Text(
                        text = "当前没有可显示的步骤。",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = stage.title, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = formatElapsed(remainingStageSeconds),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stage.instruction,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (stage.targetValueLabel.isNotBlank()) {
                            StatChip(text = stage.targetValueLabel, leadingIcon = Icons.Outlined.Timer)
                        }
                        if (stage.tip.isNotBlank()) {
                            Text(
                                text = "提示：${stage.tip}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = "建议阶段时长：${formatElapsed(stage.targetDurationSeconds)} · 已进行 ${formatElapsed(stageElapsedSeconds.toInt())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPreviousStage,
                    modifier = Modifier.weight(1f),
                    enabled = session.currentStageIndex > 0,
                ) {
                    Text("上一步")
                }
                OutlinedButton(
                    onClick = {
                        if (session.isPaused) onResumeSession() else onPauseSession()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (session.isPaused) "继续" else "暂停")
                }
                Button(
                    onClick = onNextStage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (session.currentStageIndex >= session.stages.lastIndex) "完成会话" else "下一步")
                }
            }
        }

        SectionCard(
            title = "记录与复盘",
            subtitle = "完成主动冲煮后，继续进入杯测记录和后续分析。",
        ) {
            Button(
                onClick = onOpenRecordEditor,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("记录这一杯")
            }
            OutlinedButton(
                onClick = onFinishSession,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("标记会话完成")
            }
            OutlinedButton(
                onClick = onDiscardSession,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("结束并清空会话")
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("返回")
            }
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val remaining = safeSeconds % 60
    return "%d:%02d".format(minutes, remaining)
}
