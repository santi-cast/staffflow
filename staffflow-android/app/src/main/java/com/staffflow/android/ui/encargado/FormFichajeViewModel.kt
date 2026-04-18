package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.FichajeApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.PausaApiService
import com.staffflow.android.data.remote.dto.FichajePatchRequest
import com.staffflow.android.data.remote.dto.FichajeRequest
import com.staffflow.android.data.remote.dto.PausaPatchRequest
import com.staffflow.android.data.remote.dto.PausaRequest
import com.staffflow.android.data.remote.repository.FichajeRepository
import com.staffflow.android.data.remote.repository.PausaRepository
import com.staffflow.android.domain.model.TipoFichaje
import com.staffflow.android.domain.model.TipoPausa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del formulario de fichaje/pausa (P20).
 *
 * Variante FICHAJE:
 *   Modo alta   (fichajeId = -1): llama a E22 POST /fichajes.
 *   Modo edicion (fichajeId > 0): llama a E23 PATCH /fichajes/{id}.
 *
 * Variante PAUSA:
 *   Modo alta   (pausaId = -1): llama a E27 POST /pausas.
 *   Modo edicion (pausaId > 0): llama a E28 PATCH /pausas/{id}.
 *
 * Las observaciones son OBLIGATORIAS en todas las variantes (RNF-L02).
 * ENCARGADO solo puede gestionar registros del dia actual (D-026, validado en backend).
 *
 * UiState:
 *   Idle    -> formulario listo para rellenar
 *   Loading -> llamada al API en curso (boton GUARDAR deshabilitado)
 *   Success -> operacion correcta (Fragment navega atras)
 *   Error   -> mensaje de error inline
 */
class FormFichajeViewModel(application: Application) : AndroidViewModel(application) {

    private val fichajeRepository = FichajeRepository(
        NetworkModule.retrofit.create(FichajeApiService::class.java)
    )
    private val pausaRepository = PausaRepository(
        NetworkModule.retrofit.create(PausaApiService::class.java)
    )

    enum class Variante { FICHAJE, PAUSA }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    var variante: Variante = Variante.FICHAJE
        private set

    var modoEdicion: Boolean = false
        private set

    private var fichajeId: Long = -1L
    private var pausaId: Long = -1L

    /**
     * Inicializa el modo y la variante del formulario.
     * Llamado desde FormFichajeFragment.onViewCreated con los argumentos de navegacion.
     * El guard evita reinicializar en rotaciones de pantalla.
     */
    fun init(variante: Variante, fichajeId: Long, pausaId: Long) {
        if (this.fichajeId != -1L || this.pausaId != -1L) return
        this.variante = variante
        this.fichajeId = fichajeId
        this.pausaId = pausaId
        modoEdicion = (variante == Variante.FICHAJE && fichajeId > 0L) ||
                      (variante == Variante.PAUSA && pausaId > 0L)
    }

    /**
     * Guarda el fichaje. Variante FICHAJE, modo alta -> E22. Modo edicion -> E23.
     * observaciones es OBLIGATORIA (RNF-L02).
     */
    fun guardarFichaje(
        empleadoId: Long,
        fecha: String,
        tipo: TipoFichaje,
        horaEntrada: String?,
        horaSalida: String?,
        observaciones: String
    ) {
        if (!validarCamposComunes(empleadoId, fecha, observaciones)) return

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            if (modoEdicion) {
                val request = FichajePatchRequest(
                    tipo = tipo,
                    horaEntrada = horaEntrada?.ifBlank { null },
                    horaSalida = horaSalida?.ifBlank { null },
                    observaciones = observaciones
                )
                fichajeRepository.actualizarFichaje(fichajeId, request).fold(
                    onSuccess = { _uiState.value = UiState.Success },
                    onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al guardar") }
                )
            } else {
                val request = FichajeRequest(
                    empleadoId = empleadoId,
                    fecha = fecha,
                    tipo = tipo,
                    horaEntrada = horaEntrada?.ifBlank { null },
                    horaSalida = horaSalida?.ifBlank { null },
                    observaciones = observaciones
                )
                fichajeRepository.crearFichaje(request).fold(
                    onSuccess = { _uiState.value = UiState.Success },
                    onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al crear") }
                )
            }
        }
    }

    /**
     * Guarda la pausa. Variante PAUSA, modo alta -> E27. Modo edicion -> E28.
     * observaciones es OBLIGATORIA (RNF-L02).
     */
    fun guardarPausa(
        empleadoId: Long,
        fecha: String,
        tipoPausa: TipoPausa,
        horaInicio: String,
        horaFin: String?,
        observaciones: String
    ) {
        if (!validarCamposComunes(empleadoId, fecha, observaciones)) return
        if (horaInicio.isBlank()) {
            _uiState.value = UiState.Error("La hora de inicio es obligatoria")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            if (modoEdicion) {
                val request = PausaPatchRequest(
                    tipoPausa = tipoPausa,
                    horaInicio = horaInicio,
                    horaFin = horaFin?.ifBlank { null },
                    observaciones = observaciones
                )
                pausaRepository.actualizarPausa(pausaId, request).fold(
                    onSuccess = { _uiState.value = UiState.Success },
                    onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al guardar") }
                )
            } else {
                val request = PausaRequest(
                    empleadoId = empleadoId,
                    fecha = fecha,
                    horaInicio = horaInicio,
                    horaFin = horaFin?.ifBlank { null },
                    tipoPausa = tipoPausa,
                    observaciones = observaciones
                )
                pausaRepository.crearPausa(request).fold(
                    onSuccess = { _uiState.value = UiState.Success },
                    onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al crear") }
                )
            }
        }
    }

    /** Limpia el estado de error para que el Fragment no lo reprocese tras una rotacion. */
    fun limpiarError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }

    /** Resetea Success a Idle tras mostrar el dialogo de recalcular saldo. */
    fun resetUiState() {
        if (_uiState.value is UiState.Success) _uiState.value = UiState.Idle
    }

    private fun validarCamposComunes(empleadoId: Long, fecha: String, observaciones: String): Boolean {
        return when {
            empleadoId <= 0L -> {
                _uiState.value = UiState.Error("Introduce un ID de empleado válido")
                false
            }
            fecha.isBlank() -> {
                _uiState.value = UiState.Error("La fecha es obligatoria")
                false
            }
            observaciones.isBlank() -> {
                _uiState.value = UiState.Error("Las observaciones son obligatorias (RNF-L02)")
                false
            }
            else -> true
        }
    }
}
