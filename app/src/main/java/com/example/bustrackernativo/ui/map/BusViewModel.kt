package com.example.bustrackernativo.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.bustrackernativo.data.BusInfo
import com.example.bustrackernativo.data.BusRepository
import com.example.bustrackernativo.data.NetworkHelper
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BusRepository.getInstance(application)

    private val _buses = MutableLiveData<List<BusInfo>>()
    val buses: LiveData<List<BusInfo>> = _buses

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _activeLines = MutableLiveData<List<String>>(emptyList())
    val activeLines: LiveData<List<String>> = _activeLines

    private val _suggestions = MutableLiveData<List<LineSuggestion>>()
    val suggestions: LiveData<List<LineSuggestion>> = _suggestions
    
    // Official route shapes for active lines (from bundled GTFS data)
    private val _routeShapes = MutableLiveData<Map<String, List<List<Double>>>>(emptyMap())
    val routeShapes: LiveData<Map<String, List<List<Double>>>> = _routeShapes

    private var refreshJob: Job? = null
    
    // Cache of previous positions for bearing calculation (bus rotation)
    private val previousPositions = mutableMapOf<String, LatLng>()
    
    // Lifecycle-aware control - stops updates when app is in background
    private var isPaused = false
    
    fun pauseTracking() {
        isPaused = true
        refreshJob?.cancel()
    }
    
    fun resumeTracking() {
        if (isPaused) {
            isPaused = false
            val lines = _activeLines.value ?: emptyList()
            if (lines.isNotEmpty()) {
                restartTracking()
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.loadDatabase()
            // Start prefetching ALL Rio buses in background for INSTANT searches
            repository.prefetchAllRioBuses()
        }
    }

    fun search(query: String) {
        if (query.length < 2) {
            _suggestions.value = emptyList()
            return
        }

        viewModelScope.launch {
            val db = repository.getDatabase() ?: return@launch
            val result = mutableListOf<LineSuggestion>()
            
            // Smart Filter Regex:
            // Match the query if it is a whole word or a suffix/prefix separated by non-digits.
            // Ex matches: "343", "SP343", "LECD343", "343A"
            // Ex non-matches: "2343", "1343"
            val q = Regex.escape(query)
            val regex = Regex("(^|\\D)$q($|\\D)", RegexOption.IGNORE_CASE)

            // Search Detro
            db.detro.forEach { (key, value) ->
                if (regex.containsMatchIn(key) || value.nome.contains(query, ignoreCase = true)) {
                    result.add(LineSuggestion(key, "${value.nome} (Intermunicipal)"))
                }
            }

            // Search Rio
            db.rio.forEach { (key, value) ->
                if (regex.containsMatchIn(key) || value.nome.contains(query, ignoreCase = true)) {
                    result.add(LineSuggestion(key, "Municipal do Rio"))
                }
            }

            _suggestions.postValue(result.take(50))
        }
    }

    fun addLine(line: String) {
        val current = _activeLines.value?.toMutableList() ?: mutableListOf()
        if (!current.contains(line)) {
            current.add(line)
            _activeLines.value = current
            restartTracking()
            
            // Load official route shape for this line
            viewModelScope.launch {
                val shape = repository.getRouteShape(line)
                if (shape != null) {
                    val currentShapes = _routeShapes.value?.toMutableMap() ?: mutableMapOf()
                    currentShapes[line] = shape
                    _routeShapes.postValue(currentShapes)
                }
            }
        }
        clearSuggestions()
    }

    fun removeLine(line: String) {
        val current = _activeLines.value?.toMutableList() ?: return
        current.remove(line)
        _activeLines.value = current
        restartTracking()
        
        // Remove route shape for this line
        val currentShapes = _routeShapes.value?.toMutableMap() ?: return
        currentShapes.remove(line)
        _routeShapes.value = currentShapes
    }

    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }
    
    /**
     * Calculate bearing (rotation) for a bus based on its previous position.
     * Returns 0f if no previous position is available.
     */
    fun calculateBearing(bus: BusInfo): Float {
        val current = LatLng(bus.lat, bus.lng)
        val prev = previousPositions[bus.id]
        previousPositions[bus.id] = current
        
        return if (prev != null && prev != current) {
            // Compute heading from previous to current position
            SphericalUtil.computeHeading(prev, current).toFloat()
        } else {
            0f // No rotation if no previous position
        }
    }

    private fun restartTracking() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Initial fetch
            fetchOnce()
            
            // Loop with DYNAMIC interval based on network quality
            while (isActive) {
                val quality = NetworkHelper.getNetworkQuality(getApplication())
                val interval = NetworkHelper.getRecommendedInterval(quality)
                delay(interval)
                fetchOnce()
            }
        }
    }

    private suspend fun fetchOnce() {
        val lines = _activeLines.value ?: emptyList()
        if (lines.isNotEmpty()) {
            _loading.postValue(true)
            val result = repository.fetchBuses(lines)
            // IMPORTANT: Only update if we got results
            // This prevents clearing the map when API returns 503 error
            if (result.isNotEmpty()) {
                // Clean up previousPositions to only keep current bus IDs
                // This prevents memory leaks and stale position data
                val currentBusIds = result.map { it.id }.toSet()
                previousPositions.keys.retainAll(currentBusIds)
                
                _buses.postValue(result)
            }
            // If result is empty but we had buses before, keep them (API error)
            _loading.postValue(false)
        } else {
            // Clear everything when no lines are selected
            previousPositions.clear()
            _buses.postValue(emptyList())
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
