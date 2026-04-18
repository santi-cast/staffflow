package com.staffflow.android.ui.empleado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.PresenciaApiService
import com.staffflow.android.data.remote.dto.DetallePresenciaResponse
import com.staffflow.android.data.remote.repository.PresenciaRepository
import com.staffflow.android.domain.model.EstadoPresencia
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel de Mi hoy (P12). Destino inicial del rol EMPLEADO.
 *
 * Llama a E37 GET /presencia/parte-diario/me via PresenciaRepository.
 * Cuando el estado es JORNADA_INICIADA o EN_PAUSA, arranca un ticker
 * que recarga los datos cada 60 segundos para actualizar el tiempo
 * transcurrido sin que el usuario tenga que refrescar manualmente.
 *
 * UiState:
 *   Loading -> CircularProgressIndicator
 *   Success -> card con estado y datos de la jornada
 *   Error   -> icono nube + mensaje + Reintentar
 */
class MiHoyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PresenciaRepository(
        NetworkModule.retrofit.create(PresenciaApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val presencia: DetallePresenciaResponse) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        cargarPresencia()
    }

    /** Recarga los datos. Llamado desde onResume y desde el boton Reintentar. */
    fun reintentar() = cargarPresencia()

    private fun cargarPresencia() {
        tickerJob?.cancel()
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getMiPresencia().fold(
                onSuccess = { presencia ->
                    _uiState.value = UiState.Success(presencia)
                    if (presencia.estado == EstadoPresencia.JORNADA_INICIADA ||
                        presencia.estado == EstadoPresencia.EN_PAUSA) {
                        iniciarTicker()
                    }
                },
                onFailure = {
                    _uiState.value = UiState.Error(it.message ?: "Error al cargar el estado de hoy")
                }
            )
        }
    }

    /**
     * Ticker silencioso cada 60 segundos para refrescar el tiempo transcurrido.
     * Solo activo mientras la jornada esta en curso o en pausa.
     * Los errores del ticker se ignoran para no interrumpir la UI.
     */
    private fun iniciarTicker() {
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                repository.getMiPresencia().fold(
                    onSuccess = { _uiState.value = UiState.Success(it) },
                    onFailure = { /* silencioso — no sustituir un exito previo por un error de red */ }
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}
