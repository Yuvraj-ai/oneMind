package com.onemind.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryDetailViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _memory = MutableStateFlow<Memory?>(null)
    val memory: StateFlow<Memory?> = _memory.asStateFlow()

    fun loadMemory(memoryId: Long) {
        viewModelScope.launch {
            _memory.value = memoryRepository.getMemoryById(memoryId)
        }
    }
}
