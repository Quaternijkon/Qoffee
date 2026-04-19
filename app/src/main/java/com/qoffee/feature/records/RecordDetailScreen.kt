package com.qoffee.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.HeroCard
import com.qoffee.ui.components.LabeledValue
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    recordRepository: RecordRepository,
) : ViewModel() {
    private val recordId = checkNotNull(savedStateHandle.get<Long>(QoffeeDestinations.recordIdArg))

    val record: StateFlow<CoffeeRecord?> = recordRepository.observeRecord(recordId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
}

@Composable
fun RecordDetailRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    isReadOnlyArchive: Boolean,
    viewModel: RecordDetailViewModel = hiltViewModel(),
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    RecordDetailScreen(
        paddingValues = paddingValues,
        record = record,
        isReadOnlyArchive = isReadOnlyArchive,
        onBack = onBack,
        onEdit = onEdit,
        onDuplicate = onDuplicate,
    )
}

@Composable
private fun RecordDetailScreen(
    paddingValues: PaddingValues,
    record: CoffeeRecord?,
    isReadOnlyArchive: Boolean,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
) {
    if (record == null) {
        EmptyStateCard(
            title = "未找到这条记录",
            subtitle = "这条记录可能还在加载，或者已经不存在了。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
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
            title = record.beanNameSnapshot ?: (record.brewMethod?.displayName ?: "咖啡记录"),
            subtitle = "${record.brewMethod?.displayName ?: "未指定方式"} | ${formatter.format(Date(record.brewedAt))}",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            if (!isReadOnlyArchive) {
                Button(onClick = { onEdit(record.id) }) { Text("编辑") }
                OutlinedButton(onClick = { onDuplicate(record.id) }) { Text("复制") }
            }
        }

        SectionCard(title = "客观参数") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "制作方式", value = record.brewMethod?.displayName.orEmpty())
                LabeledValue(label = "咖啡豆", value = record.beanNameSnapshot.orEmpty())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "磨豆机", value = record.grinderNameSnapshot.orEmpty())
                LabeledValue(label = "研磨格数", value = record.grindSetting?.toString().orEmpty())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "粉量", value = "${record.coffeeDoseG ?: ""} g")
                LabeledValue(label = "冲煮水量", value = "${record.brewWaterMl ?: ""} ml")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "旁路水量", value = "${record.bypassWaterMl ?: ""} ml")
                LabeledValue(label = "水温", value = "${record.waterTempC ?: ""} °C")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                record.totalWaterMl?.let { StatChip(text = "总水量 ${it} ml") }
                record.brewRatio?.let { StatChip(text = "粉水比 ${"%.1f".format(it)}") }
                record.beanRoastLevelSnapshot?.let { StatChip(text = it.displayName) }
            }
            if (record.notes.isNotBlank()) {
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        SectionCard(title = "主观感受") {
            if (record.subjectiveEvaluation == null || record.subjectiveEvaluation.isEmpty()) {
                Text(
                    text = "这条记录还没有填写主观感受。",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                val evaluation = record.subjectiveEvaluation
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "香气", value = evaluation.aroma?.toString().orEmpty())
                    LabeledValue(label = "酸质", value = evaluation.acidity?.toString().orEmpty())
                    LabeledValue(label = "甜感", value = evaluation.sweetness?.toString().orEmpty())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "苦感", value = evaluation.bitterness?.toString().orEmpty())
                    LabeledValue(label = "醇厚", value = evaluation.body?.toString().orEmpty())
                    LabeledValue(label = "余韵", value = evaluation.aftertaste?.toString().orEmpty())
                }
                LabeledValue(label = "总体评分", value = evaluation.overall?.let { "$it / 10" }.orEmpty())
                if (evaluation.flavorTags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        evaluation.flavorTags.forEach { tag ->
                            StatChip(text = tag.name)
                        }
                    }
                }
                if (evaluation.notes.isNotBlank()) {
                    Text(
                        text = evaluation.notes,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
