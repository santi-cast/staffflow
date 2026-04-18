package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.SaldoApiService
import com.staffflow.android.data.remote.dto.SaldoResponse
import com.staffflow.android.data.remote.repository.SaldoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel de Saldos globales (P27).
 *
 * Llama a E39 GET /saldos?anio= via SaldoRepository.
 * Devuelve la lista de saldos de todos los empleados activos.
 * El año por defecto es el año actual.
 *
 * UiState:
 *   Loading -> skeleton list
 *   Success -> RecyclerView con saldos
 *   Empty   -> icono + mensaje sin datos
 *   Error   -> icono nube + mensaje + Reintentar
 */
class SaldosGlobalesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SaldoRepository(
        NetworkModule.retrofit.create(SaldoApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val saldos: List<SaldoResponse>) : UiState()
        object Empty : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _anio = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val anio: StateFlow<Int> = _anio.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        cargarSaldos()
    }

    fun setAnio(anio: Int) {
        _anio.value = anio
        cargarSaldos()
    }

    fun reintentar() = cargarSaldos()

    private fun cargarSaldos() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getSaldosGlobal(_anio.value).fold(
                onSuccess = { lista ->
                    _uiState.value = if (lista.isEmpty()) UiState.Empty else UiState.Success(lista)
                },
                onFailure = {
                    _uiState.value = UiState.Error(it.message ?: "Error al cargar los saldos")
                }
            )
        }
    }
}
