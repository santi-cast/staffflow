package com.staffflow.android.ui.empleado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.AusenciaApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.AusenciaResponse
import com.staffflow.android.data.remote.repository.AusenciaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel de la lista de ausencias del empleado autenticado (P11).
 *
 * Endpoint: E34 GET /ausencias/me
 * Rango por defecto: mes actual.
 * Solo lectura — el empleado no puede crear ni editar ausencias.
 */
class MisAusenciasViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AusenciaRepository(
        NetworkModule.retrofit.create(AusenciaApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val ausencias: List<AusenciaResponse>) : UiState()
        object Empty : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var desde: String = LocalDate.now().withDayOfMonth(1).format(fmt)
    private var hasta: String = LocalDate.now().withDayOfMonth(
        LocalDate.now().lengthOfMonth()
    ).format(fmt)

    init {
        cargar()
    }

    fun reintentar() = cargar()

    fun setRangoFechas(desde: String, hasta: String) {
        this.desde = desde
        this.hasta = hasta
        cargar()
    }

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getMisAusencias(desde = desde, hasta = hasta).fold(
                onSuccess = { lista ->
                    _uiState.value = if (lista.isEmpty()) UiState.Empty
                                     else UiState.Success(lista)
                },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar ausencias") }
            )
        }
    }
}
