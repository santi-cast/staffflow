package com.staffflow.android.ui.encargado

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
 * ViewModel de la lista de ausencias planificadas (P23).
 *
 * Llama a E33 GET /ausencias?empleadoId=&desde=&hasta=&procesado= via AusenciaRepository.
 * Si se recibe empleadoId como argumento de navegacion (desde P14), filtra por ese empleado.
 * Por defecto muestra todas las ausencias del mes actual sin filtro de procesado.
 *
 * UiState:
 *   Loading -> skeleton list
 *   Success -> RecyclerView con ausencias
 *   Empty   -> icono + mensaje sin datos
 *   Error   -> icono nube + mensaje + Reintentar
 */
class AusenciasViewModel(application: Application) : AndroidViewModel(application) {

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

    /** Empleado filtrado. -1 = todos. Se establece una sola vez desde el Fragment. */
    private var empleadoId: Long = -1L
    private var desde: String = primeroDeMesActual()
    private var hasta: String = ultimoDeMesActual()

    /**
     * Inicializa el filtro de empleado.
     * El guard evita reinicializar en rotaciones de pantalla.
     */
    fun init(empleadoId: Long) {
        if (this.empleadoId != -1L) return
        this.empleadoId = empleadoId
        cargarAusencias()
    }

    fun reintentar() = cargarAusencias()

    fun setRangoFechas(desde: String, hasta: String) {
        this.desde = desde
        this.hasta = hasta
        cargarAusencias()
    }

    private fun cargarAusencias() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val idFiltro = empleadoId.takeIf { it > 0L }
            repository.listarAusencias(
                empleadoId = idFiltro,
                desde = desde,
                hasta = hasta
            ).fold(
                onSuccess = { lista ->
                    _uiState.value = if (lista.isEmpty()) UiState.Empty
                                     else UiState.Success(lista)
                },
                onFailure = {
                    _uiState.value = UiState.Error(it.message ?: "Error al cargar las ausencias")
                }
            )
        }
    }

    companion object {
        private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        fun primeroDeMesActual(): String =
            LocalDate.now().withDayOfMonth(1).format(fmt)
        fun ultimoDeMesActual(): String =
            LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).format(fmt)
    }
}
