package com.staffflow.android.ui.encargado

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
 * ViewModel del resumen semanal de todos los empleados activos (WebView).
 *
 * Endpoint: E59 GET /api/v1/informes/semana?desde=&hasta=
 * Accesible por ADMIN y ENCARGADO. Destino del drawer en group_encargado.
 *
 * Rango por defecto: semana actual de lunes a domingo.
 * El usuario puede navegar con los botones < semana anterior / semana siguiente >.
 */
class ResumenSemanalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InformeRepository(
        NetworkModule.retrofit.create(InformeApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val html: String) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val apiFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val labelFmt = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es"))

    private var desde: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY)
    private var hasta: LocalDate = desde.plusDays(6)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _semanaLabel = MutableStateFlow(calcularLabel())
    val semanaLabel: StateFlow<String> = _semanaLabel.asStateFlow()

    /** true mientras no ha ocurrido ningun onResume posterior al init. */
    private var primeraVez = true

    init {
        cargar()
    }

    /**
     * Llamado desde onResume del Fragment. Recarga los datos si es un retorno
     * desde una pantalla hija (formulario de fichaje/ausencia), ignorando el
     * primer onResume que ocurre al crear el Fragment.
     */
    fun onResumed() {
        if (primeraVez) { primeraVez = false; return }
        cargar()
    }

    fun semanaSiguiente() {
        desde = desde.plusWeeks(1)
        hasta = hasta.plusWeeks(1)
        _semanaLabel.value = calcularLabel()
        cargar()
    }

    fun semanaAnterior() {
        desde = desde.minusWeeks(1)
        hasta = hasta.minusWeeks(1)
        _semanaLabel.value = calcularLabel()
        cargar()
    }

    fun reintentar() = cargar()

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getInformeSemana(desde.format(apiFmt), hasta.format(apiFmt)).fold(
                onSuccess = { body -> _uiState.value = UiState.Success(body.string()) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar resumen semanal") }
            )
        }
    }

    private fun calcularLabel(): String =
        "${desde.format(labelFmt)} – ${hasta.format(labelFmt)}"
}
