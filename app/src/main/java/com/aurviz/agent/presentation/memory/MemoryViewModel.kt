package com.aurviz.agent.presentation.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurviz.agent.domain.model.Memory
import com.aurviz.agent.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val memories: StateFlow<List<Memory>> = memoryRepository.getAllMemories()
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter {
                it.key.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun updateMemory(memory: Memory, key: String, content: String, category: String) {
        viewModelScope.launch {
            // Same id + REPLACE conflict strategy updates the row in place.
            memoryRepository.insertMemory(
                memory.copy(key = key.trim(), content = content.trim(), category = category)
            )
        }
    }

    fun addManualMemory(key: String, content: String, category: String) {
        viewModelScope.launch {
            memoryRepository.insertMemory(
                Memory(key = key, content = content, category = category)
            )
        }
    }
}
