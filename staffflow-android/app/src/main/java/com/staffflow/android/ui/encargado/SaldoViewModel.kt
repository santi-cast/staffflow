package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.SaldoApiService
import com.staffflow.android.data.remote.dto.SaldoResponse
import com.staffflow.android.data.remote.repository.SaldoRepository
import com.staffflow.android.util.ApiError
import com.staffflow.android.util.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel de Saldo individual (P25).
 *
 * Llama a E38 GET /saldos/{empleadoId}?anio= via SaldoRepository.
 * Recibe el empleadoId via init() desde el Fragment (argumentos de navegacion).
 * El año por defecto es el año actual.
 *
 * Mismo patron que MiSaldoViewModel (P09) pero para un empleado concreto.
 * SaldoIndividualFragment.init() guarda el empleadoId en el ViewModel para
 * no recargar datos en rotaciones de pantalla.
 *
 * UiState:
 *   Loading -> CircularProgressIndicator
 *   Success -> 3 cards (vacaciones, asuntos propios, horas)
 *   Error   -> icono nube + mensaje + Reintentar
 */
class SaldoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SaldoRepository(
        NetworkModule.retrofit.create(SaldoApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val saldo: SaldoResponse) : UiState()
        data class Empty(val anio: Int) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    sealed class RecalcularState {
        object Idle : RecalcularState()
        object Loading : RecalcularState()
        object Success : RecalcularState()
        data class Error(val mensaje: String) : RecalcularState()
    }

    private val _anio = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val anio: StateFlow<Int> = _anio.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _recalcularState = MutableStateFlow<RecalcularState>(RecalcularState.Idle)
    val recalcularState: StateFlow<RecalcularState> = _recalcularState.asStateFlow()

    private var empleadoId: Long = -1L

    /**
     * Inicializa el ViewModel con el empleadoId recibido como argumento de navegacion.
     * El guard evita recargar en rotaciones de pantalla.
     */
    fun init(empleadoId: Long) {
        if (this.empleadoId == empleadoId) return
        this.empleadoId = empleadoId
        cargarSaldo()
    }

    fun setAnio(anio: Int) {
        _anio.value = anio
        cargarSaldo()
    }

    fun reintentar() = cargarSaldo()

    fun recalcular() {
        if (empleadoId <= 0L) return
        viewModelScope.launch {
            _recalcularState.value = RecalcularState.Loading
            repository.recalcularSaldo(empleadoId, _anio.value).fold(
                onSuccess = {
                    _recalcularState.value = RecalcularState.Success
                    cargarSaldo()
                },
                onFailure = {
                    _recalcularState.value = RecalcularState.Error(
                        it.message ?: "Error al recalcular el saldo"
                    )
                }
            )
        }
    }

    fun resetRecalcularState() {
        _recalcularState.value = RecalcularState.Idle
    }

    private fun cargarSaldo() {
        if (empleadoId <= 0L) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getSaldoEmpleado(empleadoId, _anio.value).fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = {
                    if ((it as? ApiException)?.error is ApiError.NotFound) {
                        _uiState.value = UiState.Empty(_anio.value)
                    } else {
                        _uiState.value = UiState.Error(it.message ?: "Error al cargar el saldo")
                    }
                }
            )
        }
    }
}
