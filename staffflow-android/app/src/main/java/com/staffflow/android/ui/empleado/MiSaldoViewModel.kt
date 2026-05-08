package com.staffflow.android.ui.empleado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.SaldoApiService
import com.staffflow.android.data.remote.dto.SaldoResponse
import com.staffflow.android.data.remote.repository.SaldoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel de Mi saldo anual (P09). Destino inicial del rol EMPLEADO.
 *
 * Llama a E41 GET /saldos/me?anio= via SaldoRepository.
 * El año por defecto es el año actual (Calendar.getInstance()).
 * MiSaldoFragment actualiza el año via setAnio() desde el selector de la toolbar.
 */
class MiSaldoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SaldoRepository(
        NetworkModule.retrofit.create(SaldoApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val saldo: SaldoResponse) : UiState()
        data class Empty(val anio: Int) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _anio = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    /** Año actualmente seleccionado. MiSaldoFragment lo usa para actualizar el titulo del menu. */
    val anio: StateFlow<Int> = _anio.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    /** Estado de la UI. MiSaldoFragment observa este flow para mostrar loading/error/datos. */
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        cargarSaldo()
    }

    /**
     * Cambia el año y recarga el saldo.
     * Llamado desde MiSaldoFragment cuando el usuario selecciona un año en el dialogo.
     */
    fun setAnio(anio: Int) {
        _anio.value = anio
        cargarSaldo()
    }

    /** Recarga el saldo con el año actual. Llamado desde el boton Reintentar. */
    fun reintentar() = cargarSaldo()

    private fun cargarSaldo() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getMiSaldo(_anio.value).fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = {
                    if (it.message?.startsWith("No hay datos de saldo") == true) {
                        _uiState.value = UiState.Empty(_anio.value)
                    } else {
                        _uiState.value = UiState.Error(it.message ?: "Error al cargar el saldo")
                    }
                }
            )
        }
    }
}
