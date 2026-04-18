package com.qoffee.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    viewModel: RecordDetailViewModel = hiltViewModel(),
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    RecordDetailScreen(
        paddingValues = paddingValues,
        record = record,
        onBack = onBack,
        onEdit = onEdit,
        onDuplicate = onDuplicate,
    )
}

@Composable
private fun RecordDetailScreen(
    paddingValues: PaddingValues,
    record: CoffeeRecord?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
) {
    if (record == null) {
        EmptyStateCard(
            title = "Record not found",
            subtitle = "The record is missing or still loading.",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
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
            title = record.beanNameSnapshot ?: (record.brewMethod?.displayName ?: "Coffee record"),
            subtitle = "${record.brewMethod?.displayName ?: "Unknown method"} | ${formatter.format(Date(record.brewedAt))}",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = { onEdit(record.id) }) { Text("Edit") }
            OutlinedButton(onClick = { onDuplicate(record.id) }) { Text("Duplicate") }
        }

        SectionCard(title = "Objective details") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "Method", value = record.brewMethod?.displayName.orEmpty())
                LabeledValue(label = "Bean", value = record.beanNameSnapshot.orEmpty())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "Grinder", value = record.grinderNameSnapshot.orEmpty())
                LabeledValue(label = "Grind", value = record.grindSetting?.toString().orEmpty())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "Dose", value = "${record.coffeeDoseG ?: ""} g")
                LabeledValue(label = "Brew water", value = "${record.brewWaterMl ?: ""} ml")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "Bypass", value = "${record.bypassWaterMl ?: ""} ml")
                LabeledValue(label = "Water temp", value = "${record.waterTempC ?: ""} C")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                record.totalWaterMl?.let { StatChip(text = "Total ${it}ml") }
                record.brewRatio?.let { StatChip(text = "Ratio ${"%.1f".format(it)}") }
                record.beanRoastLevelSnapshot?.let { StatChip(text = it.displayName) }
            }
            if (record.notes.isNotBlank()) {
                Text(text = record.notes, style = MaterialTheme.typography.bodyLarge)
            }
        }

        SectionCard(title = "Subjective details") {
            if (record.subjectiveEvaluation == null || record.subjectiveEvaluation.isEmpty()) {
                Text(
                    text = "This record does not have subjective notes yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                val evaluation = record.subjectiveEvaluation
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "Aroma", value = evaluation.aroma?.toString().orEmpty())
                    LabeledValue(label = "Acidity", value = evaluation.acidity?.toString().orEmpty())
                    LabeledValue(label = "Sweetness", value = evaluation.sweetness?.toString().orEmpty())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "Bitterness", value = evaluation.bitterness?.toString().orEmpty())
                    LabeledValue(label = "Body", value = evaluation.body?.toString().orEmpty())
                    LabeledValue(label = "Aftertaste", value = evaluation.aftertaste?.toString().orEmpty())
                }
                LabeledValue(label = "Overall", value = evaluation.overall?.let { "$it / 10" }.orEmpty())
                if (evaluation.flavorTags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        evaluation.flavorTags.forEach { tag ->
                            StatChip(text = tag.name)
                        }
                    }
                }
                if (evaluation.notes.isNotBlank()) {
                    Text(text = evaluation.notes, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
