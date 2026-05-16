package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.AusenciaApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.AusenciaPatchRequest
import com.staffflow.android.data.remote.dto.AusenciaRangoRequest
import com.staffflow.android.data.remote.dto.AusenciaRequest
import com.staffflow.android.data.remote.dto.PlanificacionVacApResponse
import com.staffflow.android.data.remote.repository.AusenciaRepository
import com.staffflow.android.domain.model.TipoAusencia
import com.staffflow.android.util.ApiError
import com.staffflow.android.util.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del formulario de ausencia planificada (P24).
 *
 * Modo alta   (ausenciaId = -1): llama a E30 POST /ausencias.
 * Modo edicion (ausenciaId > 0): llama a E31 PATCH /ausencias/{id}.
 * Eliminar    (ausenciaId > 0, procesado=false): llama a E32 DELETE /ausencias/{id}.
 *
 * Si procesado=true el formulario se abre en modo solo lectura.
 * La eliminacion solo esta disponible si procesado=false.
 * El dialogo de confirmacion antes de eliminar lo gestiona FormAusenciaFragment
 * con MaterialAlertDialogBuilder (Decision 26).
 *
 * empleadoId null = festivo global (afecta a todos los empleados).
 *
 * UiState:
 *   Idle    -> formulario listo
 *   Loading -> llamada al API en curso (botones deshabilitados)
 *   Success -> operacion correcta (Fragment navega atras)
 *   Deleted -> ausencia eliminada (Fragment navega atras)
 *   Error   -> mensaje de error inline
 */
class FormAusenciaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AusenciaRepository(
        NetworkModule.retrofit.create(AusenciaApiService::class.java)
    )

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        object Deleted : UiState()
        data class Error(val mensaje: String) : UiState()
        data class Conflicto(val fechas: List<String>) : UiState()
    }

    sealed class PlanificacionVacApResult {
        object Idle : PlanificacionVacApResult()
        object Loading : PlanificacionVacApResult()
        data class Success(val data: PlanificacionVacApResponse) : PlanificacionVacApResult()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _planificacionVacAp = MutableStateFlow<PlanificacionVacApResult>(PlanificacionVacApResult.Idle)
    val planificacionVacAp: StateFlow<PlanificacionVacApResult> = _planificacionVacAp.asStateFlow()

    var modoEdicion: Boolean = false
        private set

    /** true si la ausencia ya fue procesada. Impide guardar y eliminar. */
    var procesado: Boolean = false
        private set

    private var ausenciaId: Long = Long.MIN_VALUE

    /**
     * Inicializa el modo del formulario.
     * El guard evita reinicializar en rotaciones de pantalla.
     */
    fun init(ausenciaId: Long, procesado: Boolean) {
        if (this.ausenciaId != Long.MIN_VALUE) return
        this.ausenciaId = ausenciaId
        this.procesado = procesado
        modoEdicion = ausenciaId > 0L
    }

    /**
     * Guarda la ausencia.
     * Modo alta  -> E30 POST /ausencias.
     * Modo edicion -> E31 PATCH /ausencias/{id}.
     * No se puede guardar si procesado=true (validacion local).
     */
    fun guardar(
        empleadoId: Long?,
        fecha: String,
        tipoAusencia: TipoAusencia,
        observaciones: String?
    ) {
        if (procesado) {
            _uiState.value = UiState.Error("Esta ausencia ya fue procesada y no se puede modificar")
            return
        }
        if (fecha.isBlank()) {
            _uiState.value = UiState.Error("La fecha es obligatoria")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading

            if (modoEdicion) {
                val request = AusenciaPatchRequest(
                    tipoAusencia = tipoAusencia,
                    observaciones = observaciones?.ifBlank { null }
                )
                repository.actualizarAusencia(ausenciaId, request).fold(
                    onSuccess = { _uiState.value = UiState.Success },
                    onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al guardar") }
                )
            } else {
                val request = AusenciaRequest(
                    empleadoId = empleadoId?.takeIf { it > 0L },
                    fecha = fecha,
                    tipoAusencia = tipoAusencia,
                    observaciones = observaciones?.ifBlank { null }
                )
                repository.crearAusencia(request).fold(
                    onSuccess = { _uiState.value = UiState.Success },
                    onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al crear") }
                )
            }
        }
    }

    /**
     * Elimina la ausencia. Solo disponible si procesado=false.
     * El Fragment debe mostrar MaterialAlertDialogBuilder antes de llamar a este metodo.
     * Error 409 del backend si procesado=true (doble proteccion).
     */
    fun eliminar() {
        if (ausenciaId <= 0L) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.eliminarAusencia(ausenciaId).fold(
                onSuccess = { _uiState.value = UiState.Deleted },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al eliminar") }
            )
        }
    }

    /**
     * Crea ausencias en rango via POST /ausencias/rango.
     * Si el backend devuelve 409, emite UiState.Conflicto con las fechas conflictivas.
     * El Fragment muestra un dialogo y puede rellamar con sobrescribir=true.
     */
    fun guardarRango(
        empleadoId: Long?,
        fechaDesde: String,
        fechaHasta: String,
        tipoAusencia: TipoAusencia,
        observaciones: String?,
        sobrescribir: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val request = AusenciaRangoRequest(empleadoId, fechaDesde, fechaHasta, tipoAusencia, observaciones, sobrescribir)
            repository.crearAusenciaRango(request).fold(
                onSuccess = { _uiState.value = UiState.Success },
                onFailure = {
                    val error = (it as? ApiException)?.error
                    if (error is ApiError.RangoConflicto) _uiState.value = UiState.Conflicto(error.fechas)
                    else _uiState.value = UiState.Error(it.message ?: "Error al crear rango")
                }
            )
        }
    }

    /**
     * Carga los días pendientes de planificar para vacaciones y asuntos propios.
     * Solo aplica en modo rango con tipo VACACIONES o ASUNTO_PROPIO.
     * El anio se extrae de los primeros 4 caracteres de fechaDesde.
     */
    fun cargarPlanificacionVacAp(empleadoId: Long, fechaDesde: String) {
        val anio = fechaDesde.take(4).toIntOrNull() ?: return
        viewModelScope.launch {
            _planificacionVacAp.value = PlanificacionVacApResult.Loading
            repository.getPlanificacionVacAp(empleadoId, anio).fold(
                onSuccess = { _planificacionVacAp.value = PlanificacionVacApResult.Success(it) },
                onFailure = { _planificacionVacAp.value = PlanificacionVacApResult.Idle }
            )
        }
    }

    fun ocultarBannerVacAp() {
        _planificacionVacAp.value = PlanificacionVacApResult.Idle
    }

    fun limpiarError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }

    /** Resetea Success/Deleted/Conflicto a Idle. */
    fun resetUiState() {
        if (_uiState.value is UiState.Success || _uiState.value is UiState.Deleted
            || _uiState.value is UiState.Conflicto)
            _uiState.value = UiState.Idle
    }
}
