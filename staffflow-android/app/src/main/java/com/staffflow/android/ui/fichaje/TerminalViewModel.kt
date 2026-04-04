package com.staffflow.android.ui.fichaje

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.TerminalApiService
import com.staffflow.android.data.remote.dto.TerminalPausaRequest
import com.staffflow.android.data.remote.dto.TerminalPinRequest
import com.staffflow.android.data.remote.repository.TerminalRepository
import com.staffflow.android.domain.model.TipoPausa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Accion seleccionada en el terminal. Determina que endpoint se llama
 * al completar los 4 digitos del PIN.
 *
 *   ENTRADA        -> E48 POST /terminal/entrada
 *   SALIDA         -> E49 POST /terminal/salida
 *   INICIAR_PAUSA  -> E50 POST /terminal/pausa/iniciar (requiere tipoPausaSeleccionado)
 *   FINALIZAR_PAUSA-> E51 POST /terminal/pausa/finalizar
 */
enum class AccionTerminal { ENTRADA, SALIDA, INICIAR_PAUSA, FINALIZAR_PAUSA }

/**
 * Estado de la UI del terminal.
 *
 *   Idle    -> esperando interaccion del usuario
 *   Loading -> llamada de red en curso (botones deshabilitados)
 *   Exito   -> operacion completada, TerminalFragment navega a P06
 *   Error   -> mensaje de error visible en pantalla (sin salir del terminal)
 */
sealed class TerminalUiState {
    object Idle : TerminalUiState()
    object Loading : TerminalUiState()
    data class Exito(
        val accion: AccionTerminal,
        val nombre: String,
        val horaEntrada: String? = null,
        val horaSalida: String? = null,
        val jornadaEfectivaMinutos: Int? = null,
        val horaInicioPausa: String? = null,
        val duracionPausaMinutos: Int? = null,
        val tipoPausa: TipoPausa? = null
    ) : TerminalUiState()
    data class Error(val mensaje: String) : TerminalUiState()
}

/**
 * ViewModel del terminal de fichaje (P01).
 *
 * Gestiona el PIN en curso, la accion activa y el estado de la UI.
 * Al completar los 4 digitos llama automaticamente al endpoint correspondiente
 * segun la accion activa (E48/E49/E50/E51).
 *
 * Usa AndroidViewModel para obtener el Application context necesario para
 * leer Settings.Secure.ANDROID_ID sin pasar Context al ViewModel.
 *
 * Instanciacion manual del repositorio (sin Hilt -- D-B2-03):
 *   NetworkModule.retrofit.create(TerminalApiService::class.java)
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TerminalRepository(
        NetworkModule.retrofit.create(TerminalApiService::class.java)
    )

    /**
     * ID unico del dispositivo Android. Se envia en todas las peticiones
     * al terminal para el control de bloqueo por intentos (HTTP 423).
     */
    private val dispositivoId: String = Settings.Secure.getString(
        application.contentResolver,
        Settings.Secure.ANDROID_ID
    )

    private val _pin = MutableStateFlow("")
    /** PIN introducido hasta ahora (0-4 digitos). TerminalFragment actualiza el display. */
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _accionActiva = MutableStateFlow(AccionTerminal.ENTRADA)
    /** Accion seleccionada. Determina el endpoint y el boton activo en la UI. */
    val accionActiva: StateFlow<AccionTerminal> = _accionActiva.asStateFlow()

    private val _uiState = MutableStateFlow<TerminalUiState>(TerminalUiState.Idle)
    /** Estado de la UI. TerminalFragment observa este flow para actualizar la pantalla. */
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    /**
     * Tipo de pausa seleccionado en P07 (TipoPausaFragment) via FragmentResult.
     * Solo es relevante cuando accionActiva == INICIAR_PAUSA.
     */
    private var tipoPausaSeleccionado: TipoPausa? = null

    // ------------------------------------------------------------------
    // Gestion de acciones
    // ------------------------------------------------------------------

    /** Establece la accion activa y limpia el PIN y el estado. */
    fun setAccion(accion: AccionTerminal) {
        _accionActiva.value = accion
        resetPin()
        _uiState.value = TerminalUiState.Idle
    }

    /**
     * Establece INICIAR_PAUSA como accion activa y guarda el tipo de pausa
     * recibido desde P07 via FragmentResult("tipoPausa").
     */
    fun setAccionConPausa(tipoPausa: TipoPausa) {
        tipoPausaSeleccionado = tipoPausa
        _accionActiva.value = AccionTerminal.INICIAR_PAUSA
        resetPin()
        _uiState.value = TerminalUiState.Idle
    }

    // ------------------------------------------------------------------
    // Gestion del PIN
    // ------------------------------------------------------------------

    /**
     * Aniade un digito al PIN. Si es el cuarto, lanza la llamada de red.
     * No hace nada si ya hay 4 digitos o si hay una llamada en curso.
     */
    fun appendDigito(digito: Int) {
        if (_pin.value.length >= 4 || _uiState.value is TerminalUiState.Loading) return
        val nuevo = _pin.value + digito.toString()
        _pin.value = nuevo
        if (nuevo.length == 4) ejecutarAccion(nuevo)
    }

    /**
     * Borra el ultimo digito del PIN.
     * Si habia un error visible, lo limpia al empezar a escribir de nuevo.
     */
    fun borrarDigito() {
        if (_uiState.value is TerminalUiState.Loading) return
        if (_pin.value.isNotEmpty()) _pin.value = _pin.value.dropLast(1)
        if (_uiState.value is TerminalUiState.Error) _uiState.value = TerminalUiState.Idle
    }

    /**
     * Resetea el PIN y el estado a Idle. Llamado desde TerminalFragment
     * justo antes de navegar a ConfirmacionFragment para que al volver
     * el terminal quede limpio.
     */
    fun resetEstado() {
        _uiState.value = TerminalUiState.Idle
        resetPin()
    }

    private fun resetPin() { _pin.value = "" }

    // ------------------------------------------------------------------
    // Llamada de red
    // ------------------------------------------------------------------

    private fun ejecutarAccion(pin: String) {
        viewModelScope.launch {
            _uiState.value = TerminalUiState.Loading
            val pinRequest = TerminalPinRequest(pin = pin, dispositivoId = dispositivoId)

            when (_accionActiva.value) {

                AccionTerminal.ENTRADA -> {
                    repository.registrarEntrada(pinRequest).fold(
                        onSuccess = { resp ->
                            _uiState.value = TerminalUiState.Exito(
                                accion = AccionTerminal.ENTRADA,
                                nombre = resp.nombre,
                                horaEntrada = resp.horaEntrada
                            )
                        },
                        onFailure = { _uiState.value = TerminalUiState.Error(it.message ?: "Error") }
                    )
                }

                AccionTerminal.SALIDA -> {
                    repository.registrarSalida(pinRequest).fold(
                        onSuccess = { resp ->
                            _uiState.value = TerminalUiState.Exito(
                                accion = AccionTerminal.SALIDA,
                                nombre = resp.nombre,
                                horaSalida = resp.horaSalida,
                                jornadaEfectivaMinutos = resp.jornadaEfectivaMinutos
                            )
                        },
                        onFailure = { _uiState.value = TerminalUiState.Error(it.message ?: "Error") }
                    )
                }

                AccionTerminal.INICIAR_PAUSA -> {
                    val tipo = tipoPausaSeleccionado ?: run {
                        _uiState.value = TerminalUiState.Error("Selecciona el tipo de pausa primero")
                        return@launch
                    }
                    val pausaRequest = TerminalPausaRequest(
                        pin = pin,
                        tipoPausa = tipo,
                        dispositivoId = dispositivoId
                    )
                    repository.iniciarPausa(pausaRequest).fold(
                        onSuccess = { resp ->
                            _uiState.value = TerminalUiState.Exito(
                                accion = AccionTerminal.INICIAR_PAUSA,
                                nombre = resp.nombre,
                                horaInicioPausa = resp.horaInicioPausa,
                                tipoPausa = tipo
                            )
                        },
                        onFailure = { _uiState.value = TerminalUiState.Error(it.message ?: "Error") }
                    )
                }

                AccionTerminal.FINALIZAR_PAUSA -> {
                    repository.finalizarPausa(pinRequest).fold(
                        onSuccess = { resp ->
                            _uiState.value = TerminalUiState.Exito(
                                accion = AccionTerminal.FINALIZAR_PAUSA,
                                nombre = resp.nombre,
                                duracionPausaMinutos = resp.duracionPausaMinutos
                            )
                        },
                        onFailure = { _uiState.value = TerminalUiState.Error(it.message ?: "Error") }
                    )
                }
            }
        }
    }
}
