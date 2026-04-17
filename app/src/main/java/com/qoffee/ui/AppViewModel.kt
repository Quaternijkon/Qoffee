package com.qoffee.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qoffee.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            catalogRepository.seedPresetFlavorTags()
        }
    }
}
