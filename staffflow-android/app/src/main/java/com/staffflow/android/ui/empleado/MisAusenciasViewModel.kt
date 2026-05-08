package com.staffflow.android.ui.empleado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.AusenciaApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.repository.AusenciaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ViewModel de la vista de ausencias del empleado autenticado (P11) — variante WebView.
 *
 * Endpoint: GET /api/v1/ausencias/me/informe?desde=&hasta=&filtro=
 *
 * Rango por defecto: año en curso completo (1 enero – 31 diciembre).
 * Filtro por defecto: TODAS (excluye festivos siempre).
 * El empleado puede alternar entre TODAS y VACACIONES_AP con el chip de filtro.
 */
class MisAusenciasViewModel(application: Application) : AndroidViewModel(application) {

    enum class Filtro { TODAS, VACACIONES_AP }

    private val repository = AusenciaRepository(
        NetworkModule.retrofit.create(AusenciaApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val html: String) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val apiFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val labelFmt = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es"))

    private val anioActual = LocalDate.now().year
    private var desde: LocalDate = LocalDate.of(anioActual, 1, 1)
    private var hasta: LocalDate = LocalDate.of(anioActual, 12, 31)
    private var filtro: Filtro = Filtro.TODAS

    private val _rangoLabel = MutableStateFlow(calcularLabel(desde, hasta))
    val rangoLabel: StateFlow<String> = _rangoLabel.asStateFlow()

    private val _filtroActivo = MutableStateFlow(filtro)
    val filtroActivo: StateFlow<Filtro> = _filtroActivo.asStateFlow()

    init {
        cargar()
    }

    fun reintentar() = cargar()

    fun setRangoFechas(desdeStr: String, hastaStr: String) {
        desde = LocalDate.parse(desdeStr, apiFmt)
        hasta = LocalDate.parse(hastaStr, apiFmt)
        _rangoLabel.value = calcularLabel(desde, hasta)
        cargar()
    }

    fun toggleFiltro() {
        filtro = if (filtro == Filtro.TODAS) Filtro.VACACIONES_AP else Filtro.TODAS
        _filtroActivo.value = filtro
        cargar()
    }

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getMisAusenciasInforme(
                desde = desde.format(apiFmt),
                hasta = hasta.format(apiFmt),
                filtro = filtro.name
            ).fold(
                onSuccess = { body -> _uiState.value = UiState.Success(body.string()) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar ausencias") }
            )
        }
    }

    private fun calcularLabel(desde: LocalDate, hasta: LocalDate): String =
        "${desde.format(labelFmt)} – ${hasta.format(labelFmt)}"
}
