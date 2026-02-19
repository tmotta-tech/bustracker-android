package com.example.bustrackernativo.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bustrackernativo.data.BusInfo
import com.example.bustrackernativo.data.BusRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Intervalo de atualização em milissegundos (ex: 15 segundos)
private const val REFRESH_INTERVAL_MS = 15_000L

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BusRepository.getInstance(application)
    private var updateJob: Job? = null

    // Lista de linhas que o usuário está monitorando
    // Por enquanto, vamos deixar uma fixa para testar
    private val _activeLines = MutableStateFlow<List<String>>(listOf("100", "455", "SP455"))
    val activeLines = _activeLines.asStateFlow()

    // A lista de ônibus que a UI vai observar para se redesenhar
    private val _busState = MutableStateFlow<List<BusInfo>>(emptyList())
    val busState = _busState.asStateFlow()

    init {
        // Começa a buscar por atualizações assim que o ViewModel é criado
        startBusUpdates()
    }

    private fun startBusUpdates() {
        // Cancela qualquer job anterior para evitar múltiplas buscas simultâneas
        updateJob?.cancel()
        
        // Inicia uma nova coroutine que vai rodar em loop
        updateJob = viewModelScope.launch {
            while (true) {
                // Pega as linhas ativas no momento da busca
                val lines = _activeLines.value
                if (lines.isNotEmpty()) {
                    // Chama o repositório para buscar os dados das APIs
                    val buses = repository.fetchBuses(lines)
                    _busState.value = buses // Atualiza o estado com os novos dados
                } else {
                    _busState.value = emptyList() // Limpa o mapa se não houver linhas
                }
                // Espera o intervalo definido antes de buscar novamente
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    // Função para a UI chamar quando o usuário adicionar uma nova linha (usaremos no futuro)
    fun addLine(line: String) {
        if (!_activeLines.value.contains(line)) {
            _activeLines.value = _activeLines.value + line
        }
    }
    
    // Função para a UI chamar quando o usuário remover uma linha (usaremos no futuro)
    fun removeLine(line: String) {
        _activeLines.value = _activeLines.value - line
    }
    
    override fun onCleared() {
        super.onCleared()
        // Garante que o loop de busca pare quando o ViewModel for destruído
        updateJob?.cancel()
    }
}