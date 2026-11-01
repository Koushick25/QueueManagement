package com.example.adaptivequeue.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adaptivequeue.data.model.QueueToken
import com.example.adaptivequeue.data.repository.QueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val repository: QueueRepository
) : ViewModel() {

    val allTokens = repository.getAllTokens()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val waitingTokens = repository.getWaitingTokens()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentServingToken = repository.getCurrentServingToken()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val averageServiceTimeMinutes = repository.getAverageServiceTimeMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7.0)

    fun serveNext() {
        viewModelScope.launch {
            val current = currentServingToken.value
            if (current != null) {
                repository.markAsCompleted(current)
            }
            val next = waitingTokens.value.firstOrNull()
            if (next != null) {
                repository.markAsServing(next)
            }
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            repository.clearQueue()
        }
    }

    fun addMockServiceRecord(durationMinutes: Long) {
        viewModelScope.launch {
            repository.addMockServiceRecord(durationMinutes)
        }
    }
}
