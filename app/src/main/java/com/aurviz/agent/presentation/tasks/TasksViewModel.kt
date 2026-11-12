package com.aurviz.agent.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurviz.agent.domain.model.ScheduledTask
import com.aurviz.agent.domain.repository.TaskSchedulerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(
    private val schedulerRepository: TaskSchedulerRepository
) : ViewModel() {

    val tasks: StateFlow<List<ScheduledTask>> = schedulerRepository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateTask(task: ScheduledTask) {
        viewModelScope.launch { schedulerRepository.updateTask(task) }
    }

    fun cancelTask(id: String) {
        viewModelScope.launch {
            schedulerRepository.cancelTask(id)
        }
    }

    fun triggerTaskNow(id: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val result = schedulerRepository.triggerTaskNow(id)
            onComplete(result)
        }
    }
}
