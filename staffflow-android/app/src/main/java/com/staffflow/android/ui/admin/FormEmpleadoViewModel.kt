package com.staffflow.android.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.EmpleadoApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.EmpleadoPatchRequest
import com.staffflow.android.data.remote.dto.EmpleadoRequest
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.data.remote.repository.EmpleadoRepository
import com.staffflow.android.domain.model.CategoriaEmpleado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del formulario de empleado (P15).
 *
 * Modo alta   (empleadoId = -1): llama a E13 POST /empleados.
 * Modo edicion (empleadoId > 0): llama a E15 para precargar datos,
 *                                luego a E16 PATCH /empleados/{id}.
 *
 * UiState:
 *   Idle    -> formulario listo para rellenar (datos precargados en edicion)
 *   Loading -> llamada al API en curso (boton GUARDAR deshabilitado)
 *   Success -> operacion correcta (Fragment navega atras)
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

    /** Empleado cargado en modo edicion. FormEmpleadoFragment lo usa para prerellenar campos. */
    private val _empleado = MutableStateFlow<EmpleadoResponse?>(null)
    val empleado: StateFlow<EmpleadoResponse?> = _empleado.asStateFlow()

    /** true = modo edicion | false = modo alta */
    var modoEdicion: Boolean = false
        private set

    private var empleadoId: Long = Long.MIN_VALUE  // sentinel: aun no inicializado
    private var usuarioId: Long = -1L

    /**
     * Inicializa el modo del formulario.
     * Llamado desde FormEmpleadoFragment.onViewCreated con los argumentos de navegacion.
     *
     * @param empleadoId Id del empleado a editar, o -1 para modo alta.
     * @param usuarioId  Id del usuario al que vincular el empleado (modo alta desde P32).
     *                   -1 si el ADMIN lo introduce manualmente.
     */
    fun init(empleadoId: Long, usuarioId: Long) {
        if (this.empleadoId == empleadoId) return  // ya inicializado (rotacion de pantalla)
        this.empleadoId = empleadoId
        this.usuarioId  = usuarioId
        modoEdicion = empleadoId > 0L

        if (modoEdicion) {
            cargarEmpleado()
        } else {
            _uiState.value = UiState.Idle
        }
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
     * Guarda el empleado.
     * En modo alta llama a E13 POST, en modo edicion a E16 PATCH.
     * Valida los campos antes de llamar al API.
     */
    fun guardar(
        usuarioIdInput: Long?,       // solo modo alta
        nombre: String,
        apellido1: String,
        apellido2: String?,
        dni: String,                 // solo modo alta
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

            if (modoEdicion) {
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
            } else {
                val idUsuario = usuarioId.takeIf { it > 0L } ?: usuarioIdInput ?: run {
                    _uiState.value = UiState.Error("Introduce el ID de usuario")
                    return@launch
                }
                if (dni.isBlank()) {
                    _uiState.value = UiState.Error("Rellena todos los campos obligatorios")
                    return@launch
                }
                val request = EmpleadoRequest(
                    usuarioId = idUsuario,
                    nombre = nombre,
                    apellido1 = apellido1,
                    apellido2 = apellido2?.ifBlank { null },
                    dni = dni,
                    categoria = categoria,
                    jornadaSemanalHoras = jornadaSemanalHoras,
                    diasVacacionesAnuales = diasVacaciones,
                    diasAsuntosPropiosAnuales = diasAsuntos
                )
                repository.crearEmpleado(request).fold(
                    onSuccess = { _uiState.value = UiState.Success },
                    onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al crear el empleado") }
                )
            }
        }
    }

    /** Limpia el estado de error para que el Fragment no lo reprocese tras una rotacion. */
    fun limpiarError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }
}
