package com.example.bustracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.bustracker.data.Bus
import com.example.bustracker.repository.BusRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BusRepository(application)
    
    // UI State
    private val _buses = MutableLiveData<List<Bus>>()
    val buses: LiveData<List<Bus>> = _buses

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _activeLines = MutableLiveData<List<String>>(emptyList())
    val activeLines: LiveData<List<String>> = _activeLines

    // Refresh Job
    private var refreshJob: Job? = null

    init {
        // Load the database immediately
        viewModelScope.launch {
            repository.loadDatabase()
        }
    }

    fun addLine(line: String) {
        val current = _activeLines.value?.toMutableList() ?: mutableListOf()
        if (!current.contains(line)) {
            current.add(line)
            _activeLines.value = current
            restartTracking()
        }
    }

    fun removeLine(line: String) {
        val current = _activeLines.value?.toMutableList() ?: return
        current.remove(line)
        _activeLines.value = current
        restartTracking()
    }

    private fun restartTracking() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                val lines = _activeLines.value ?: emptyList()
                if (lines.isNotEmpty()) {
                    _loading.postValue(true)
                    val result = repository.fetchBuses(lines)
                    _buses.postValue(result)
                    _loading.postValue(false)
                }
                delay(5000) // 5 seconds
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
