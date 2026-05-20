package com.staffflow.android.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.EmpleadoApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.EmpleadoPatchRequest
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.data.remote.repository.EmpleadoRepository
import com.staffflow.android.domain.model.CategoriaEmpleado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del formulario de empleado (P15). Solo edicion.
 *
 * Flujo:
 *   1. init(empleadoId) -> E15 GET /empleados/{id} para precargar datos.
 *   2. guardar(...) -> E16 PATCH /empleados/{id} con los campos modificables.
 *
 * El alta de empleado NO se hace aqui: vive integrada en P29
 * (FormUsuarioFragment) junto al alta del usuario. P15 solo admite
 * empleadoId > 0; recibir -1 es un error de programacion y emite Error.
 *
 * UiState:
 *   Idle    -> formulario listo para editar (datos precargados)
 *   Loading -> llamada al API en curso (boton GUARDAR deshabilitado)
 *   Success -> PATCH correcto (Fragment navega atras)
 *   Error   -> mensaje de error del backend o validacion local
 */
class FormEmpleadoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmpleadoRepository(
        NetworkModule.retrofit.create(EmpleadoApiService::class.java)
    )

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Empleado cargado. FormEmpleadoFragment lo usa para prerellenar campos. */
    private val _empleado = MutableStateFlow<EmpleadoResponse?>(null)
    val empleado: StateFlow<EmpleadoResponse?> = _empleado.asStateFlow()

    private var empleadoId: Long = Long.MIN_VALUE  // sentinel: aun no inicializado

    /**
     * Inicializa el formulario cargando el empleado a editar.
     * Llamado desde FormEmpleadoFragment.onViewCreated con el argumento de navegacion.
     *
     * @param empleadoId Id del empleado a editar. Debe ser > 0.
     */
    fun init(empleadoId: Long) {
        if (this.empleadoId == empleadoId) return  // ya inicializado (rotacion de pantalla)
        this.empleadoId = empleadoId

        if (empleadoId <= 0L) {
            _uiState.value = UiState.Error("Identificador de empleado invalido")
            return
        }
        cargarEmpleado()
    }

    private fun cargarEmpleado() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getEmpleado(empleadoId).fold(
                onSuccess = {
                    _empleado.value = it
                    _uiState.value = UiState.Idle
                },
                onFailure = {
                    _uiState.value = UiState.Error(it.message ?: "Error al cargar el empleado")
                }
            )
        }
    }

    /**
     * Guarda los cambios del empleado mediante E16 PATCH /empleados/{id}.
     * Valida los campos antes de llamar al API.
     */
    fun guardar(
        nombre: String,
        apellido1: String,
        apellido2: String?,
        categoria: CategoriaEmpleado,
        jornadaSemanalHoras: Double,
        diasVacaciones: Int,
        diasAsuntos: Int
    ) {
        if (nombre.isBlank() || apellido1.isBlank()) {
            _uiState.value = UiState.Error("Rellena todos los campos obligatorios")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val request = EmpleadoPatchRequest(
                nombre = nombre,
                apellido1 = apellido1,
                apellido2 = apellido2?.ifBlank { null },
                categoria = categoria,
                jornadaSemanalHoras = jornadaSemanalHoras,
                diasVacacionesAnuales = diasVacaciones,
                diasAsuntosPropiosAnuales = diasAsuntos
            )
            repository.actualizarEmpleado(empleadoId, request).fold(
                onSuccess = { _uiState.value = UiState.Success },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al guardar") }
            )
        }
    }

    /** Limpia el estado de error para que el Fragment no lo reprocese tras una rotacion. */
    fun limpiarError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }
}
