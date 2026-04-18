package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.PresenciaApiService
import com.staffflow.android.data.remote.dto.SinJustificarResponse
import com.staffflow.android.data.remote.repository.PresenciaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de la lista de empleados sin justificar (P18).
 *
 * E36 GET /presencia/sin-justificar?fecha=
 * Acceso: P17 (ParteDiarioFragment) -> chip "Sin justificar: N".
 * La fecha se pasa desde P17 via Bundle. Si no hay fecha se usa hoy (null).
 */
class SinJustificarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PresenciaRepository(
        NetworkModule.retrofit.create(PresenciaApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val lista: List<SinJustificarResponse>) : UiState()
        object Empty : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var fechaCargada: String? = null
    private var inicializado = false

    fun init(fecha: String?) {
        if (inicializado) return
        inicializado = true
        fechaCargada = fecha
        cargar()
    }

    fun reintentar() = cargar()

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getSinJustificar(fechaCargada).fold(
                onSuccess = { lista ->
                    _uiState.value = if (lista.isEmpty()) UiState.Empty else UiState.Success(lista)
                },
                onFailure = {
                    _uiState.value = UiState.Error(it.message ?: "Error al cargar empleados sin justificar")
                }
            )
        }
    }
}
