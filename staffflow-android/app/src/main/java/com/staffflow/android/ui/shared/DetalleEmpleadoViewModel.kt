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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Eventos one-shot de la operacion "Regenerar PIN" (E65).
 *
 * El Fragment los consume con SharedFlow + repeatOnLifecycle para
 * deshabilitar el chip durante la llamada, mostrar el dialog con el
 * PIN nuevo en exito o un Snackbar en error.
 *
 * El campo codigo de Error es una de: "404" | "red" | "generico".
 * El Fragment lo mapea al string correspondiente.
 */
sealed class RegenerarPinEvento {
    object Cargando : RegenerarPinEvento()
    data class Exito(val pin: String) : RegenerarPinEvento()
    data class Error(val codigo: String) : RegenerarPinEvento()
}

/**
 * ViewModel del detalle de empleado (P14).
 *
 * Llama a E15 GET /empleados/{id} via EmpleadoRepository.
 * Recibe el empleadoId desde DetalleEmpleadoFragment (argumento de navegacion).
 *
 * Expone el rol del usuario autenticado para que DetalleEmpleadoFragment
 * muestre u oculte los chips de accion segun rol (Editar y Regenerar PIN).
 *
 * Tambien expone eventoRegenerarPin (SharedFlow) para la accion E65.
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
     * mostrar los chips Editar (ADMIN) y Regenerar PIN (ADMIN o ENCARGADO).
     */
    val rol: StateFlow<Rol?> = _rol.asStateFlow()

    private val _eventoRegenerarPin = MutableSharedFlow<RegenerarPinEvento>()
    /**
     * Eventos one-shot de la operacion E65 "Regenerar PIN".
     * Cargando -> Exito(pin) | Error(codigo).
     */
    val eventoRegenerarPin: SharedFlow<RegenerarPinEvento> = _eventoRegenerarPin.asSharedFlow()

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

    /**
     * Regenera el PIN del empleado (E65 POST /empleados/{id}/regenerar-pin).
     *
     * Emite Cargando -> Exito(pin) en 2xx; en error mapea el throwable
     * a un codigo simple ("404" | "red" | "generico") que el Fragment
     * resuelve a string. Se basa en el mensaje del throwable porque
     * safeApiCall ya colapsa HttpException/IOException a Exception plana.
     */
    fun regenerarPin(empleadoId: Long) {
        viewModelScope.launch {
            _eventoRegenerarPin.emit(RegenerarPinEvento.Cargando)
            repository.regenerarPin(empleadoId).fold(
                onSuccess = { _eventoRegenerarPin.emit(RegenerarPinEvento.Exito(it.pinTerminal)) },
                onFailure = { _eventoRegenerarPin.emit(RegenerarPinEvento.Error(mapearError(it))) }
            )
        }
    }

    private fun mapearError(t: Throwable): String {
        val msg = t.message.orEmpty()
        return when {
            msg.contains("Sin conexion", ignoreCase = true) -> "red"
            msg.contains("404") || msg.contains("no encontrado", ignoreCase = true) -> "404"
            else -> "generico"
        }
    }

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
