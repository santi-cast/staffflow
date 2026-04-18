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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del detalle de empleado (P14).
 *
 * Llama a E15 GET /empleados/{id} via EmpleadoRepository.
 * Recibe el empleadoId desde DetalleEmpleadoFragment (argumento de navegacion).
 *
 * Expone el rol del usuario autenticado para que DetalleEmpleadoFragment
 * muestre u oculte el boton Editar de la toolbar (solo ADMIN).
 */
class DetalleEmpleadoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmpleadoRepository(
        NetworkModule.retrofit.create(EmpleadoApiService::class.java)
    )
    private val sessionManager = SessionManager.getInstance(application)

    sealed class UiState {
        object Loading : UiState()
        data class Success(val empleado: EmpleadoResponse) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    /** Estado de la UI. DetalleEmpleadoFragment observa este flow. */
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _rol = MutableStateFlow<Rol?>(null)
    /**
     * Rol del usuario autenticado. DetalleEmpleadoFragment lo usa para
     * mostrar el boton Editar de la toolbar solo cuando el rol es ADMIN.
     */
    val rol: StateFlow<Rol?> = _rol.asStateFlow()

    private var empleadoId: Long = -1L

    init {
        viewModelScope.launch { _rol.value = sessionManager.getRol() }
    }

    /**
     * Inicializa el ViewModel con el id del empleado a mostrar.
     * Llamado desde DetalleEmpleadoFragment.onViewCreated con el argumento de navegacion.
     * Si el id ya esta cargado (rotacion de pantalla) no vuelve a llamar al API.
     */
    fun init(id: Long) {
        if (empleadoId == id) return
        empleadoId = id
        cargarEmpleado()
    }

    /** Recarga el detalle del empleado. Llamado desde el boton Reintentar. */
    fun reintentar() = cargarEmpleado()

    private fun cargarEmpleado() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getEmpleado(empleadoId).fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar el empleado") }
            )
        }
    }
}
