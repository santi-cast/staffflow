package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.AusenciaApiService
import com.staffflow.android.data.remote.api.FichajeApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.PausaApiService
import com.staffflow.android.data.remote.dto.AusenciaResponse
import com.staffflow.android.data.remote.dto.FichajeResponse
import com.staffflow.android.data.remote.dto.PausaResponse
import com.staffflow.android.data.remote.repository.AusenciaRepository
import com.staffflow.android.data.remote.repository.FichajeRepository
import com.staffflow.android.data.remote.repository.PausaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del detalle de jornada de un empleado (P16).
 *
 * Carga en paralelo los fichajes, pausas y ausencias de un empleado para una fecha concreta:
 *   E24 GET /fichajes?empleadoId=X&desde=fecha&hasta=fecha   → todos los fichajes del dia
 *   E29 GET /pausas?empleadoId=X&desde=fecha&hasta=fecha     → pausas del dia
 *   E33 GET /ausencias?empleadoId=X&desde=fecha&hasta=fecha  → ausencias planificadas
 *
 * No requiere cambios en el backend: usa endpoints existentes.
 * Roles: ENCARGADO y ADMIN (mismos que el parte diario).
 */
class DetalleDiaViewModel(application: Application) : AndroidViewModel(application) {

    private val fichajeRepository = FichajeRepository(
        NetworkModule.retrofit.create(FichajeApiService::class.java)
    )
    private val pausaRepository = PausaRepository(
        NetworkModule.retrofit.create(PausaApiService::class.java)
    )
    private val ausenciaRepository = AusenciaRepository(
        NetworkModule.retrofit.create(AusenciaApiService::class.java)
    )

    data class DayDetail(
        val fichajes: List<FichajeResponse>,
        val pausas: List<PausaResponse>,
        val ausencias: List<AusenciaResponse>
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val detail: DayDetail) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var empleadoId: Long = -1L
    private var fecha: String = ""

    /**
     * Inicializa con el empleadoId y fecha. Guard contra reinicializacion en rotaciones.
     * Llamado desde DetalleDiaFragment.onViewCreated.
     */
    fun init(empleadoId: Long, fecha: String) {
        if (this.empleadoId != -1L) return
        this.empleadoId = empleadoId
        this.fecha = fecha
        cargar()
    }

    /** Recarga fichaje y pausas. Llamado desde Reintentar y onResume. */
    fun reintentar() = cargar()

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            val fichajeDeferred = async {
                fichajeRepository.listarFichajes(empleadoId = empleadoId, desde = fecha, hasta = fecha)
            }
            val pausaDeferred = async {
                pausaRepository.listarPausas(empleadoId = empleadoId, desde = fecha, hasta = fecha)
            }
            val ausenciaDeferred = async {
                ausenciaRepository.listarAusencias(empleadoId = empleadoId, desde = fecha, hasta = fecha)
            }

            val fichajeResult  = fichajeDeferred.await()
            val pausaResult    = pausaDeferred.await()
            val ausenciaResult = ausenciaDeferred.await()

            if (fichajeResult.isFailure) {
                _uiState.value = UiState.Error(
                    fichajeResult.exceptionOrNull()?.message ?: "Error al cargar el fichaje"
                )
                return@launch
            }
            if (pausaResult.isFailure) {
                _uiState.value = UiState.Error(
                    pausaResult.exceptionOrNull()?.message ?: "Error al cargar las pausas"
                )
                return@launch
            }
            if (ausenciaResult.isFailure) {
                _uiState.value = UiState.Error(
                    ausenciaResult.exceptionOrNull()?.message ?: "Error al cargar las ausencias"
                )
                return@launch
            }

            val fichajes  = fichajeResult.getOrNull()?.sortedBy { it.horaEntrada } ?: emptyList()
            val pausas    = pausaResult.getOrNull()?.sortedBy { it.horaInicio } ?: emptyList()
            val ausencias = ausenciaResult.getOrNull() ?: emptyList()

            _uiState.value = UiState.Success(DayDetail(fichajes, pausas, ausencias))
        }
    }
}
