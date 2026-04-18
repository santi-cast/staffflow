package com.staffflow.android.ui.shared

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.local.SessionManager
import com.staffflow.android.data.remote.api.EmpleadoApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.data.remote.repository.EmpleadoRepository
import com.staffflow.android.domain.model.Rol
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de la lista de empleados (P13).
 *
 * Llama a E14 GET /empleados?q= via EmpleadoRepository.
 * La busqueda se debouncea 300ms para evitar llamadas excesivas al API.
 *
 * Expone el rol del usuario autenticado para que EmpleadosFragment
 * muestre u oculte el FAB de alta (solo ADMIN).
 */
class EmpleadosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmpleadoRepository(
        NetworkModule.retrofit.create(EmpleadoApiService::class.java)
    )
    private val sessionManager = SessionManager.getInstance(application)

    sealed class UiState {
        object Loading : UiState()
        data class Success(val empleados: List<EmpleadoResponse>) : UiState()
        object Empty : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    /** Estado de la UI. EmpleadosFragment observa este flow. */
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _rol = MutableStateFlow<Rol?>(null)
    /**
     * Rol del usuario autenticado. EmpleadosFragment lo usa para
     * mostrar el FAB de alta solo cuando el rol es ADMIN.
     */
    val rol: StateFlow<Rol?> = _rol.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch { _rol.value = sessionManager.getRol() }
        cargarEmpleados()
    }

    /**
     * Recarga la lista completa sin filtro.
     * Llamado desde el boton Reintentar y pull-to-refresh.
     */
    fun reintentar() = cargarEmpleados()

    /**
     * Busqueda con debounce de 300ms.
     * Si el texto esta vacio recarga la lista completa.
     * Llamado desde el SearchView de la toolbar en cada cambio de texto.
     */
    fun buscar(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            cargarEmpleados(q = query.ifBlank { null })
        }
    }

    private fun cargarEmpleados(q: String? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.listarEmpleados(q = q).fold(
                onSuccess = { lista ->
                    _uiState.value = if (lista.isEmpty()) UiState.Empty
                                     else UiState.Success(lista)
                },
                onFailure = {
                    _uiState.value = UiState.Error(it.message ?: "Error al cargar empleados")
                }
            )
        }
    }
}
