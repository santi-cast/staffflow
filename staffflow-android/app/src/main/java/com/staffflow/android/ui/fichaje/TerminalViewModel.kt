package com.staffflow.android.ui.fichaje

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.local.SessionManager
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.TerminalApiService
import com.staffflow.android.data.remote.dto.TerminalPinRequest
import com.staffflow.android.data.remote.repository.TerminalRepository
import com.staffflow.android.util.ApiError
import com.staffflow.android.util.ApiException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de la UI del terminal de fichaje (P01).
 *
 *   EsperandoPin   -> numpad habilitado (estado inicial y tras resetear)
 *   VerificandoPin -> llamando a E52; spinner visible, numpad deshabilitado
 *   Error          -> E52 fallo (PIN incorrecto u otro error); mensaje visible,
 *                     numpad deshabilitado; se resetea automaticamente tras 2s
 *   PinVerificado  -> E52 OK; el Fragment navega a P06 con todos los datos del estado
 */
sealed class TerminalUiState {
    object EsperandoPin : TerminalUiState()
    object VerificandoPin : TerminalUiState()
    data class Error(val mensaje: String) : TerminalUiState()
    data class Bloqueado(val mensaje: String) : TerminalUiState()
    data class ErrorConexion(val pin: String) : TerminalUiState()
    data class PinVerificado(
        val pin: String,
        val nombre: String,
        val estadoDia: String,
        val horaEntrada: String?,
        val horaSalida: String?,
        val horaInicioPausa: String?,
        val tipoPausa: String?
    ) : TerminalUiState()
}

/**
 * ViewModel del terminal de fichaje (P01).
 *
 * Gestiona la entrada del PIN y verifica su validez contra el backend (E52)
 * antes de navegar a P06. De este modo, P06 recibe los datos del estado del
 * empleado ya cargados y no necesita llamar a E52 de nuevo.
 *
 * Flujo:
 *   1. El usuario introduce 4 digitos -> appendDigito() -> verificarPin()
 *   2. verificarPin() llama a E52:
 *      - Exito  -> PinVerificado; el Fragment navega a P06 con los datos del estado
 *      - Error  -> Error(mensaje); el Fragment muestra el error y resetea tras 2s
 *   3. TerminalFragment llama a resetEstado() tras navegar a P06 para limpiar el PIN
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager.getInstance(application)

    private val dispositivoId: String = Settings.Secure.getString(
        application.contentResolver,
        Settings.Secure.ANDROID_ID
    )

    private val _pin = MutableStateFlow("")
    /** PIN introducido hasta ahora (0-4 digitos). TerminalFragment actualiza el display. */
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _uiState = MutableStateFlow<TerminalUiState>(TerminalUiState.EsperandoPin)
    /** Estado de la UI. TerminalFragment observa este flow. */
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    /**
     * Aniade un digito al PIN.
     * Al completar los 4 digitos llama automaticamente a E52 para verificar el PIN.
     * Ignora la pulsacion si ya se esta verificando o si el PIN ya fue verificado.
     */
    fun appendDigito(digito: Int) {
        val estado = _uiState.value
        if (estado is TerminalUiState.VerificandoPin || estado is TerminalUiState.PinVerificado || estado is TerminalUiState.Bloqueado) return
        if (_pin.value.length >= 4) return
        val nuevo = _pin.value + digito.toString()
        _pin.value = nuevo
        if (nuevo.length == 4) {
            verificarPin(nuevo)
        }
    }

    /**
     * Borra el ultimo digito del PIN.
     * Ignorado si se esta verificando el PIN.
     */
    fun borrarDigito() {
        if (_uiState.value is TerminalUiState.VerificandoPin) return
        if (_uiState.value is TerminalUiState.PinVerificado) return
        if (_uiState.value is TerminalUiState.Bloqueado) return
        if (_pin.value.isNotEmpty()) _pin.value = _pin.value.dropLast(1)
    }

    /**
     * Persiste la nueva IP en DataStore, reconstruye el cliente Retrofit
     * y reintenta la verificacion del PIN.
     */
    fun guardarIpYReintentarPin(ip: String, pin: String) {
        viewModelScope.launch {
            val baseUrl = "http://$ip:8080/api/v1/"
            sessionManager.saveBaseUrl(baseUrl)
            NetworkModule.init(baseUrl)
            verificarPin(pin)
        }
    }

    /**
     * Resetea el estado al inicial: PIN vacio, estado EsperandoPin.
     * Llamado desde TerminalFragment tras navegar a P06.
     */
    fun resetEstado() {
        _pin.value = ""
        _uiState.value = TerminalUiState.EsperandoPin
    }

    /**
     * Llama a E52 para verificar el PIN y obtener el estado del empleado.
     * En exito emite PinVerificado con todos los datos de la respuesta.
     * En error emite Error(mensaje) y resetea automaticamente tras 2 segundos.
     */
    private fun verificarPin(pin: String) {
        viewModelScope.launch {
            _uiState.value = TerminalUiState.VerificandoPin
            val request = TerminalPinRequest(pin = pin, dispositivoId = dispositivoId)
            val repo = TerminalRepository(NetworkModule.retrofit.create(TerminalApiService::class.java))
            repo.obtenerEstado(request).fold(
                onSuccess = { resp ->
                    _uiState.value = TerminalUiState.PinVerificado(
                        pin = pin,
                        nombre = resp.nombre,
                        estadoDia = resp.estado,
                        horaEntrada = resp.horaEntrada,
                        horaSalida = resp.horaSalida,
                        horaInicioPausa = resp.horaInicioPausa,
                        tipoPausa = resp.tipoPausa
                    )
                },
                onFailure = { error ->
                    val apiError = (error as? ApiException)?.error
                    when (apiError) {
                        is ApiError.Network, ApiError.Timeout -> {
                            _uiState.value = TerminalUiState.ErrorConexion(pin)
                        }
                        is ApiError.PinBloqueado -> {
                            _uiState.value = TerminalUiState.Bloqueado(apiError.mensaje ?: "")
                        }
                        is ApiError.NotFound, is ApiError.Validation -> {
                            _uiState.value = TerminalUiState.Error("PIN incorrecto")
                            delay(2000)
                            resetEstado()
                        }
                        is ApiError.Server -> {
                            _uiState.value = TerminalUiState.Error("Error del servidor (${apiError.code})")
                            delay(2000)
                            resetEstado()
                        }
                        else -> {
                            _uiState.value = TerminalUiState.Error("Error inesperado")
                            delay(2000)
                            resetEstado()
                        }
                    }
                }
            )
        }
    }
}
