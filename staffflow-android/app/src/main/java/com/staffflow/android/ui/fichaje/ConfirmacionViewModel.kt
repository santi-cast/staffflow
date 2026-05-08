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
 * Estado de la UI de la pantalla de bienvenida y confirmacion (P06).
 *
 *   CargandoEstado       -> estado inicial transitorio hasta que init() recibe los datos de P01
 *   BienvenidaConOpciones-> datos recibidos de P01 (E52 ya verificado); muestra saludo + botones
 *   Loading              -> llamando a E48/E49/E50/E51; botones deshabilitados
 *   Resultado            -> accion completada; muestra datos y cuenta atras de 5s
 *   Error                -> fallo en E48/E49/E50/E51; muestra mensaje y reactiva botones
 */
sealed class ConfirmacionUiState {
    object CargandoEstado : ConfirmacionUiState()

    data class BienvenidaConOpciones(
        val nombre: String,
        val estadoDia: String,          // EstadoTerminal.name()
        val horaEntrada: String?,
        val horaSalida: String?,
        val horaInicioPausa: String?,
        val tipoPausa: String?          // TipoPausa.name() o null
    ) : ConfirmacionUiState()

    object Loading : ConfirmacionUiState()

    data class Resultado(
        val accion: AccionTerminal,
        val nombre: String,
        val horaEntrada: String? = null,
        val horaSalida: String? = null,
        val totalPausasSegundos: Int? = null,
        val numeroPausas: Int? = null,
        val jornadaEfectivaSegundos: Int? = null,
        val horaInicioPausa: String? = null,
        val horaFinPausa: String? = null,
        val duracionPausaSegundos: Int? = null,
        val tipoPausa: TipoPausa? = null
    ) : ConfirmacionUiState()

    data class Error(val mensaje: String) : ConfirmacionUiState()
}

/**
 * ViewModel de la pantalla de bienvenida y confirmacion de fichaje (P06).
 *
 * Flujo:
 *   1. init(pin) -> llama E52 -> estado BienvenidaConOpciones (o Error)
 *   2. El usuario pulsa una accion -> ejecutarAccion() -> E48/E49/E50/E51
 *   3. Exito -> estado Resultado; el Fragment inicia la cuenta atras de 5s
 *   4. Error -> estado Error; el Fragment muestra el mensaje y reactiva botones
 *
 * init() solo inicializa una vez (guard isEmpty). En config changes y
 * vuelta de P07 el pin ya esta seteado y el estado se preserva.
 */
class ConfirmacionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TerminalRepository(
        NetworkModule.retrofit.create(TerminalApiService::class.java)
    )

    private val dispositivoId: String = Settings.Secure.getString(
        application.contentResolver,
        Settings.Secure.ANDROID_ID
    )

    private var pin: String = ""

    private val _uiState = MutableStateFlow<ConfirmacionUiState>(ConfirmacionUiState.CargandoEstado)
    val uiState: StateFlow<ConfirmacionUiState> = _uiState.asStateFlow()

    /**
     * Inicializa el ViewModel con el PIN y los datos del estado ya verificados por P01 (E52).
     * Solo inicializa una vez: llamadas posteriores (config change, vuelta de P07)
     * se ignoran para preservar el estado actual.
     *
     * P01 llama a E52, obtiene los datos del estado del empleado y los pasa aqui
     * via Bundle. P06 no necesita llamar a E52 de nuevo.
     */
    fun init(
        pin: String,
        nombre: String,
        estadoDia: String,
        horaEntrada: String?,
        horaSalida: String?,
        horaInicioPausa: String?,
        tipoPausa: String?
    ) {
        if (this.pin.isEmpty()) {
            this.pin = pin
            _uiState.value = ConfirmacionUiState.BienvenidaConOpciones(
                nombre = nombre,
                estadoDia = estadoDia,
                horaEntrada = horaEntrada,
                horaSalida = horaSalida,
                horaInicioPausa = horaInicioPausa,
                tipoPausa = tipoPausa
            )
        }
    }

    /**
     * Ejecuta la accion seleccionada por el usuario (E48/E49/E50/E51).
     *
     * @param accion Accion elegida en los botones de P06.
     * @param tipoPausa Requerido solo para INICIAR_PAUSA. Viene de P07 via FragmentResult.
     */
    fun ejecutarAccion(accion: AccionTerminal, tipoPausa: TipoPausa? = null) {
        val pinRequest = TerminalPinRequest(pin = pin, dispositivoId = dispositivoId)
        viewModelScope.launch {
            _uiState.value = ConfirmacionUiState.Loading

            when (accion) {
                AccionTerminal.ENTRADA -> {
                    repository.registrarEntrada(pinRequest).fold(
                        onSuccess = { resp ->
                            _uiState.value = ConfirmacionUiState.Resultado(
                                accion = AccionTerminal.ENTRADA,
                                nombre = resp.nombre,
                                horaEntrada = resp.horaEntrada
                            )
                        },
                        onFailure = { _uiState.value = ConfirmacionUiState.Error(it.message ?: "Error") }
                    )
                }

                AccionTerminal.SALIDA -> {
                    repository.registrarSalida(pinRequest).fold(
                        onSuccess = { resp ->
                            _uiState.value = ConfirmacionUiState.Resultado(
                                accion = AccionTerminal.SALIDA,
                                nombre = resp.nombre,
                                horaEntrada = resp.horaEntrada,
                                horaSalida = resp.horaSalida,
                                totalPausasSegundos = resp.totalPausasSegundos,
                                numeroPausas = resp.numeroPausas,
                                jornadaEfectivaSegundos = resp.jornadaEfectivaSegundos
                            )
                        },
                        onFailure = { _uiState.value = ConfirmacionUiState.Error(it.message ?: "Error") }
                    )
                }

                AccionTerminal.INICIAR_PAUSA -> {
                    val tipo = tipoPausa ?: run {
                        _uiState.value = ConfirmacionUiState.Error("Selecciona el tipo de pausa primero")
                        return@launch
                    }
                    val pausaRequest = TerminalPausaRequest(
                        pin = pin,
                        tipoPausa = tipo,
                        dispositivoId = dispositivoId
                    )
                    repository.iniciarPausa(pausaRequest).fold(
                        onSuccess = { resp ->
                            _uiState.value = ConfirmacionUiState.Resultado(
                                accion = AccionTerminal.INICIAR_PAUSA,
                                nombre = resp.nombre,
                                horaInicioPausa = resp.horaInicioPausa,
                                tipoPausa = tipo
                            )
                        },
                        onFailure = { _uiState.value = ConfirmacionUiState.Error(it.message ?: "Error") }
                    )
                }

                AccionTerminal.FINALIZAR_PAUSA -> {
                    repository.finalizarPausa(pinRequest).fold(
                        onSuccess = { resp ->
                            _uiState.value = ConfirmacionUiState.Resultado(
                                accion = AccionTerminal.FINALIZAR_PAUSA,
                                nombre = resp.nombre,
                                horaInicioPausa = resp.horaInicioPausa,
                                horaFinPausa = resp.horaFinPausa,
                                duracionPausaSegundos = resp.duracionPausaSegundos
                            )
                        },
                        onFailure = { _uiState.value = ConfirmacionUiState.Error(it.message ?: "Error") }
                    )
                }
            }
        }
    }
}
