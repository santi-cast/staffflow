package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.FichajeApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.FichajeResponse
import com.staffflow.android.data.remote.repository.FichajeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel de la lista de fichajes (P19).
 *
 * Llama a E24 GET /fichajes?empleadoId=&desde=&hasta=&tipo= via FichajeRepository.
 * Si se recibe empleadoId como argumento de navegacion (desde P14), filtra por ese empleado.
 * Si no, muestra todos los fichajes del mes actual.
 *
 * UiState:
 *   Loading -> skeleton list
 *   Success -> RecyclerView con fichajes
 *   Empty   -> icono + mensaje sin datos
 *   Error   -> icono nube + mensaje + Reintentar
 */
class FichajesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FichajeRepository(
        NetworkModule.retrofit.create(FichajeApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val fichajes: List<FichajeResponse>) : UiState()
        object Empty : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Empleado filtrado. -1 = todos. Se establece una sola vez desde el Fragment. */
    private var empleadoId: Long = -1L

    /** Fecha inicio del filtro activo en formato "yyyy-MM-dd". */
    private var desde: String = primeroDeMesActual()

    /** Fecha fin del filtro activo en formato "yyyy-MM-dd". */
    private var hasta: String = ultimoDeMesActual()

    /**
     * Inicializa el filtro de empleado.
     * Llamado desde FichajesFragment.onViewCreated con el argumento de navegacion.
     * Si no viene argumento (acceso desde Drawer), empleadoId = -1 (todos).
     * El guard evita reinicializar en rotaciones de pantalla.
     */
    fun init(empleadoId: Long) {
        if (this.empleadoId != -1L) return
        this.empleadoId = empleadoId
        cargarFichajes()
    }

    /** Recarga con los filtros actuales. Llamado desde Reintentar y pull-to-refresh. */
    fun reintentar() = cargarFichajes()

    /**
     * Actualiza el rango de fechas y recarga.
     * @param desde Fecha inicio "yyyy-MM-dd"
     * @param hasta Fecha fin "yyyy-MM-dd"
     */
    fun setRangoFechas(desde: String, hasta: String) {
        this.desde = desde
        this.hasta = hasta
        cargarFichajes()
    }

    private fun cargarFichajes() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val idFiltro = empleadoId.takeIf { it > 0L }
            repository.listarFichajes(
                empleadoId = idFiltro,
                desde = desde,
                hasta = hasta
            ).fold(
                onSuccess = { lista ->
                    _uiState.value = if (lista.isEmpty()) UiState.Empty
                                     else UiState.Success(lista)
                },
                onFailure = {
                    _uiState.value = UiState.Error(it.message ?: "Error al cargar los fichajes")
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
