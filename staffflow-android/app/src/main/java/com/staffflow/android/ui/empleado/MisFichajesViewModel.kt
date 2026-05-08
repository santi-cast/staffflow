package com.staffflow.android.ui.empleado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.InformeApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.repository.InformeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ViewModel de la vista de fichajes del empleado autenticado (P10) — variante WebView.
 *
 * Endpoint: GET /api/v1/informes/me/horas?desde=&hasta=&formato=html
 *
 * Rango por defecto: semana actual de lunes a domingo.
 * El empleado puede seleccionar cualquier rango con el chip de periodo.
 */
class MisFichajesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InformeRepository(
        NetworkModule.retrofit.create(InformeApiService::class.java)
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

    private var desde: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY)
    private var hasta: LocalDate = LocalDate.now().with(DayOfWeek.SUNDAY)

    private val _rangoLabel = MutableStateFlow(calcularLabel(desde, hasta))
    val rangoLabel: StateFlow<String> = _rangoLabel.asStateFlow()

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

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getMisHorasHtml(desde.format(apiFmt), hasta.format(apiFmt)).fold(
                onSuccess = { body ->
                    val html = body.string()
                    _uiState.value = UiState.Success(html)
                },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar informe") }
            )
        }
    }

    private fun calcularLabel(desde: LocalDate, hasta: LocalDate): String =
        "${desde.format(labelFmt)} – ${hasta.format(labelFmt)}"
}
