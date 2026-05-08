package com.staffflow.android.ui.encargado

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
 * ViewModel del informe de ausencias de un empleado específico (WebView).
 *
 * Endpoint: GET /api/v1/ausencias/{empleadoId}/informe?desde=&hasta=&filtro=
 * Accesible por ADMIN y ENCARGADO. Desde P14 chip "Ver ausencias".
 *
 * Rango por defecto: año en curso completo.
 */
class InformeAusenciasEmpleadoViewModel(application: Application) : AndroidViewModel(application) {

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
    private var empleadoId: Long = -1L
    private var desde: LocalDate = LocalDate.of(anioActual, 1, 1)
    private var hasta: LocalDate = LocalDate.of(anioActual, 12, 31)
    private var filtro: Filtro = Filtro.TODAS

    private val _rangoLabel = MutableStateFlow(calcularLabel(desde, hasta))
    val rangoLabel: StateFlow<String> = _rangoLabel.asStateFlow()

    private val _filtroActivo = MutableStateFlow(filtro)
    val filtroActivo: StateFlow<Filtro> = _filtroActivo.asStateFlow()

    fun init(id: Long) {
        if (empleadoId == id) return
        empleadoId = id
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
        if (empleadoId < 0) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getInformeAusenciasEmpleado(
                empleadoId = empleadoId,
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
