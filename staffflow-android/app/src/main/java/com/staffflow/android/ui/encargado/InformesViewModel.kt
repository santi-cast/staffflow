package com.staffflow.android.ui.encargado

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.EmpleadoApiService
import com.staffflow.android.data.remote.api.InformeApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.repository.EmpleadoRepository
import com.staffflow.android.data.remote.repository.InformeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de informes (P28). ADMIN y ENCARGADO.
 *
 * Flujo:
 *   - HTML auto-carga (E42/E43/E44) al seleccionar empleado, fechas o año -> HtmlVistaListo.
 *   - Botones "Imprimir" piden el PDF (E45/E46/E47/E53) -> PdfListo -> Intent ACTION_VIEW.
 *
 * UiState:
 *   Idle           -> pantalla en reposo
 *   Loading        -> peticion en curso (botones deshabilitados)
 *   HtmlVistaListo -> HTML listo, Fragment lo carga en el WebView del tab activo
 *   PdfListo       -> PDF listo, Fragment lo abre con Intent ACTION_VIEW
 *   Error          -> mensaje de error
 */
/** Par id+nombre para el autocomplete de empleados en P28. */
data class EmpleadoItem(val id: Long, val nombreCompleto: String) {
    override fun toString() = nombreCompleto
}

class InformesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InformeRepository(
        NetworkModule.retrofit.create(InformeApiService::class.java)
    )
    private val empleadoRepository = EmpleadoRepository(
        NetworkModule.retrofit.create(EmpleadoApiService::class.java)
    )

    private val _empleados = MutableStateFlow<List<EmpleadoItem>>(emptyList())
    val empleados: StateFlow<List<EmpleadoItem>> = _empleados.asStateFlow()

    init {
        cargarEmpleados()
    }

    private fun cargarEmpleados() {
        viewModelScope.launch {
            empleadoRepository.listarEmpleados(activo = true).fold(
                onSuccess = { lista ->
                    _empleados.value = lista.map { e ->
                        val nombre = "${e.nombre} ${e.apellido1}${e.apellido2?.let { " $it" } ?: ""}"
                        EmpleadoItem(e.id, nombre)
                    }
                },
                onFailure = { /* silencioso: los campos de ID siguen funcionando */ }
            )
        }
    }

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class PdfListo(val bytes: ByteArray, val nombreFichero: String) : UiState()
        /** HTML para mostrar en WebView (auto-carga al seleccionar empleado/fechas/año). */
        data class HtmlVistaListo(val html: String) : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun resetear() {
        _uiState.value = UiState.Idle
    }

    /** E42 formato=html — para WebView (boton Ver informe, tab Horas individual). */
    fun verHorasEmpleado(empleadoId: Long, desde: String, hasta: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getInformeHorasHtml(empleadoId, desde, hasta).fold(
                onSuccess = { body -> _uiState.value = UiState.HtmlVistaListo(body.string()) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al obtener informe") }
            )
        }
    }

    /** E43 formato=html — para WebView (boton Ver informe, tab Horas global). */
    fun verHorasGlobal(desde: String, hasta: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getInformeHorasGlobalHtml(desde, hasta).fold(
                onSuccess = { body -> _uiState.value = UiState.HtmlVistaListo(body.string()) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al obtener informe") }
            )
        }
    }

    /** E44 formato=html — para WebView (boton Ver saldos, tab Saldos). */
    fun verSaldos(anio: Int) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getInformeSaldosHtml(anio).fold(
                onSuccess = { body -> _uiState.value = UiState.HtmlVistaListo(body.string()) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al obtener informe") }
            )
        }
    }

    /** E45 — PDF horas de un empleado. */
    fun descargarPdfHorasEmpleado(empleadoId: Long, desde: String, hasta: String) {
        val nombre = "informe_horas_${empleadoId}_${desde}_${hasta}.pdf"
        descargarPdf(nombre) { repository.getPdfHorasEmpleado(empleadoId, desde, hasta) }
    }

    /** E46 — PDF horas global de todos los empleados. */
    fun descargarPdfHorasGlobal(desde: String, hasta: String) {
        val nombre = "informe_horas_global_${desde}_${hasta}.pdf"
        descargarPdf(nombre) { repository.getPdfHorasGlobal(desde, hasta) }
    }

    /** E47 — PDF saldos anuales. */
    fun descargarPdfSaldos(anio: Int) {
        val nombre = "informe_saldos_$anio.pdf"
        descargarPdf(nombre) { repository.getPdfSaldos(anio) }
    }

    /** E53 — PDF vacaciones de un empleado. */
    fun descargarPdfVacaciones(empleadoId: Long, anio: Int) {
        val nombre = "informe_vacaciones_${empleadoId}_$anio.pdf"
        descargarPdf(nombre) { repository.getPdfVacaciones(empleadoId, anio) }
    }

    private fun descargarPdf(
        nombreFichero: String,
        llamada: suspend () -> Result<okhttp3.ResponseBody>
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            llamada().fold(
                onSuccess = { body ->
                    val bytes = body.bytes()
                    _uiState.value = UiState.PdfListo(bytes, nombreFichero)
                },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al descargar PDF") }
            )
        }
    }
}
