package com.qoffee.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.ArchiveSeedStatus
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.domain.repository.ArchiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val archives: List<ArchiveSummary> = emptyList(),
    val currentArchive: ArchiveSummary? = null,
    val seedStatus: ArchiveSeedStatus = ArchiveSeedStatus(),
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    private val seedStatusInternal = kotlinx.coroutines.flow.MutableStateFlow(ArchiveSeedStatus())

    val uiState: StateFlow<AppUiState> = combine(
        archiveRepository.observeArchives(),
        archiveRepository.observeCurrentArchive(),
        seedStatusInternal,
    ) { archives, currentArchive, seedStatus ->
        AppUiState(
            archives = archives,
            currentArchive = currentArchive,
            seedStatus = seedStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(),
    )

    init {
        viewModelScope.launch {
            seedStatusInternal.value = archiveRepository.seedDemoArchiveIfNeeded()
        }
    }

    fun switchArchive(id: Long) {
        viewModelScope.launch {
            archiveRepository.switchArchive(id)
        }
    }

    fun createArchive(name: String) {
        viewModelScope.launch {
            archiveRepository.createArchive(name)
        }
    }

    fun duplicateArchive(sourceArchiveId: Long, newName: String) {
        viewModelScope.launch {
            archiveRepository.duplicateArchive(sourceArchiveId, newName)
        }
    }

    fun renameArchive(id: Long, newName: String) {
        viewModelScope.launch {
            archiveRepository.renameArchive(id, newName)
        }
    }

    fun deleteArchive(id: Long) {
        viewModelScope.launch {
            archiveRepository.deleteArchive(id)
        }
    }

    fun copyDemoArchiveAsEditable(name: String) {
        viewModelScope.launch {
            archiveRepository.copyDemoArchiveAsEditable(name)
        }
    }

    fun resetDemoArchive() {
        viewModelScope.launch {
            archiveRepository.resetDemoArchive()
        }
    }
}
