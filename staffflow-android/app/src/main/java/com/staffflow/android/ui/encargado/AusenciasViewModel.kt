package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.AusenciaApiService
import com.staffflow.android.data.remote.api.InformeApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.repository.AusenciaRepository
import com.staffflow.android.data.remote.repository.InformeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * ViewModel del resumen de ausencias globales (P23).
 *
 * Endpoint: E60 GET /api/v1/informes/ausencias?desde=&hasta=
 * Accesible por ADMIN y ENCARGADO.
 *
 * Rango por defecto: semana actual de lunes a domingo.
 * El usuario puede navegar con los botones < semana anterior / semana siguiente >.
 */
class AusenciasViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InformeRepository(
        NetworkModule.retrofit.create(InformeApiService::class.java)
    )
    private val ausenciaRepository = AusenciaRepository(
        NetworkModule.retrofit.create(AusenciaApiService::class.java)
    )

    sealed class UiState {
        object Loading : UiState()
        data class Success(val html: String) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    sealed class InfoSeleccionState {
        object Oculta : InfoSeleccionState()
        object Cargando : InfoSeleccionState()
        data class Visible(val vacaciones: Int, val asuntosPropios: Int, val anio: Int) : InfoSeleccionState()
    }

    private val _infoSeleccion = MutableStateFlow<InfoSeleccionState>(InfoSeleccionState.Oculta)
    val infoSeleccion: StateFlow<InfoSeleccionState> = _infoSeleccion.asStateFlow()

    private val apiFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val labelFmt = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es"))

    private var desde: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY)
    private var hasta: LocalDate = desde.plusDays(6)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _semanaLabel = MutableStateFlow(calcularLabel())
    val semanaLabel: StateFlow<String> = _semanaLabel.asStateFlow()

    /** true mientras no ha ocurrido ningún onResume posterior al init. */
    private var primeraVez = true

    init {
        cargar()
    }

    /**
     * Llamado desde onResume del Fragment. Recarga los datos si es un retorno
     * desde una pantalla hija (formulario de ausencia), ignorando el primer
     * onResume que ocurre al crear el Fragment.
     */
    fun onResumed() {
        if (primeraVez) { primeraVez = false; return }
        cargar()
    }

    fun semanaSiguiente() {
        desde = desde.plusWeeks(1)
        hasta = hasta.plusWeeks(1)
        _semanaLabel.value = calcularLabel()
        ocultarInfoSeleccion()
        cargar()
    }

    fun semanaAnterior() {
        desde = desde.minusWeeks(1)
        hasta = hasta.minusWeeks(1)
        _semanaLabel.value = calcularLabel()
        ocultarInfoSeleccion()
        cargar()
    }

    fun mesSiguiente() {
        val primerDiaMesSiguiente = desde.withDayOfMonth(1).plusMonths(1)
        desde = primerDiaMesSiguiente.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        hasta = desde.plusDays(6)
        _semanaLabel.value = calcularLabel()
        ocultarInfoSeleccion()
        cargar()
    }

    fun mesAnterior() {
        val primerDiaMesAnterior = desde.withDayOfMonth(1).minusMonths(1)
        desde = primerDiaMesAnterior.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        hasta = desde.plusDays(6)
        _semanaLabel.value = calcularLabel()
        ocultarInfoSeleccion()
        cargar()
    }

    fun cargarInfoSeleccion(empleadoId: Long, fechaDesde: String) {
        val anio = fechaDesde.take(4).toIntOrNull() ?: return
        viewModelScope.launch {
            _infoSeleccion.value = InfoSeleccionState.Cargando
            ausenciaRepository.getPlanificacionVacAp(empleadoId, anio).fold(
                onSuccess = { data ->
                    _infoSeleccion.value = InfoSeleccionState.Visible(
                        vacaciones     = data.vacaciones.pendientesPlanificar,
                        asuntosPropios = data.asuntosPropios.pendientesPlanificar,
                        anio           = anio
                    )
                },
                onFailure = { _infoSeleccion.value = InfoSeleccionState.Oculta }
            )
        }
    }

    fun ocultarInfoSeleccion() {
        _infoSeleccion.value = InfoSeleccionState.Oculta
    }

    fun reintentar() = cargar()

    private fun cargar() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getInformeAusenciasGlobal(
                desde.format(apiFmt),
                hasta.format(apiFmt)
            ).fold(
                onSuccess = { body -> _uiState.value = UiState.Success(body.string()) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar las ausencias") }
            )
        }
    }

    private fun calcularLabel(): String {
        val desdeStr = "${desde.format(labelFmt)} (${desde.year})"
        val hastaStr = "${hasta.format(labelFmt)} (${hasta.year})"
        return if (desde.year == hasta.year)
            "${desde.format(labelFmt)} – ${hasta.format(labelFmt)} (${hasta.year})"
        else
            "$desdeStr – $hastaStr"
    }
}
