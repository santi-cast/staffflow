package com.staffflow.android.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.EmpleadoApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.EmpleadoPatchRequest
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.data.remote.repository.EmpleadoRepository
import com.staffflow.android.domain.model.CategoriaEmpleado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel del formulario de empleado (P15). Solo edicion.
 *
 * Flujo:
 *   1. init(empleadoId) -> E15 GET /empleados/{id} para precargar datos.
 *   2. guardar(...) -> E16 PATCH /empleados/{id} con los campos modificables.
 *
 * El alta de empleado NO se hace aqui: vive integrada en P29
 * (FormUsuarioFragment) junto al alta del usuario. P15 solo admite
 * empleadoId > 0; recibir -1 es un error de programacion y emite Error.
 *
 * UiState:
 *   Idle    -> formulario listo para editar (datos precargados)
 *   Loading -> llamada al API en curso (boton GUARDAR deshabilitado)
 *   Success -> PATCH correcto (Fragment navega atras)
 *   Error   -> mensaje de error del backend o validacion local
 */
class FormEmpleadoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmpleadoRepository(
        NetworkModule.retrofit.create(EmpleadoApiService::class.java)
    )

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Empleado cargado. FormEmpleadoFragment lo usa para prerellenar campos. */
    private val _empleado = MutableStateFlow<EmpleadoResponse?>(null)
    val empleado: StateFlow<EmpleadoResponse?> = _empleado.asStateFlow()

    private var empleadoId: Long = Long.MIN_VALUE  // sentinel: aun no inicializado

    /**
     * Fecha de alta original del empleado (la que llego de E15). Se usa para
     * decidir si hay que enviarla en el PATCH: solo se envia cuando difiere
     * del valor original. null = sin cambio respecto a lo precargado.
     */
    private var fechaAltaOriginal: LocalDate? = null

    /**
     * DNI original del empleado (el que llego de E15). Se compara normalizado
     * (trim + uppercase) para evitar PATCH "fantasma" si el ADMIN reescribe el
     * mismo valor con espacios o minusculas distintas.
     */
    private var dniOriginal: String? = null

    /** Formato ISO-8601 (yyyy-MM-dd) que espera el backend en fechaAlta. */
    private val fmtFechaAltaIso = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Inicializa el formulario cargando el empleado a editar.
     * Llamado desde FormEmpleadoFragment.onViewCreated con el argumento de navegacion.
     *
     * @param empleadoId Id del empleado a editar. Debe ser > 0.
     */
    fun init(empleadoId: Long) {
        if (this.empleadoId == empleadoId) return  // ya inicializado (rotacion de pantalla)
        this.empleadoId = empleadoId

        if (empleadoId <= 0L) {
            _uiState.value = UiState.Error("Identificador de empleado invalido")
            return
        }
        cargarEmpleado()
    }

    private fun cargarEmpleado() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getEmpleado(empleadoId).fold(
                onSuccess = {
                    _empleado.value = it
                    fechaAltaOriginal = parsearFechaAltaIso(it.fechaAlta)
                    dniOriginal = it.dni
                    _uiState.value = UiState.Idle
                },
                onFailure = {
                    _uiState.value = UiState.Error(it.message ?: "Error al cargar el empleado")
                }
            )
        }
    }

    /**
     * Parsea la fechaAlta tal como llega del backend (ISO-8601 yyyy-MM-dd).
     * Devuelve null si el valor es nulo, blanco o no se puede parsear; en ese
     * caso la edicion de fechaAlta queda implicitamente deshabilitada (el
     * Fragment usara LocalDate.now() como base del picker).
     */
    private fun parsearFechaAltaIso(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return try {
            LocalDate.parse(iso)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Guarda los cambios del empleado mediante E16 PATCH /empleados/{id}.
     * Valida los campos antes de llamar al API.
     *
     * fechaAlta y dni solo viajan en el PATCH cuando difieren de los valores
     * originales cargados de E15 (semantica del PATCH: null = sin cambio). Asi
     * evitamos marcar el row como modificado en el backend cuando el ADMIN no
     * toco esos campos y simplificamos la auditoria de cambios.
     *
     * El dni se compara normalizado (trim + uppercase): si el ADMIN reescribe
     * el mismo valor con espacios o mayusculas distintas, no se envia.
     *
     * Errores tipicos esperados:
     *   - 409 DNI duplicado (otro empleado ya lo tiene)
     *   - 409 dni con formato invalido o letra de control incorrecta (validacion backend)
     */
    fun guardar(
        nombre: String,
        apellido1: String,
        apellido2: String?,
        dni: String,
        categoria: CategoriaEmpleado,
        jornadaSemanalHoras: Double,
        diasVacaciones: Int,
        diasAsuntos: Int,
        fechaAlta: LocalDate
    ) {
        if (nombre.isBlank() || apellido1.isBlank() || dni.isBlank()) {
            _uiState.value = UiState.Error("Rellena todos los campos obligatorios")
            return
        }

        val dniNormalizado = dni.trim().uppercase()
        if (dniNormalizado.length != 9) {
            _uiState.value = UiState.Error("El DNI debe tener 9 caracteres")
            return
        }

        val fechaAltaIso = if (fechaAlta != fechaAltaOriginal) {
            fechaAlta.format(fmtFechaAltaIso)
        } else {
            null
        }

        val dniNuevo = if (dniNormalizado != dniOriginal?.trim()?.uppercase()) {
            dniNormalizado
        } else {
            null
        }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val request = EmpleadoPatchRequest(
                nombre = nombre,
                apellido1 = apellido1,
                apellido2 = apellido2?.ifBlank { null },
                dni = dniNuevo,
                categoria = categoria,
                jornadaSemanalHoras = jornadaSemanalHoras,
                diasVacacionesAnuales = diasVacaciones,
                diasAsuntosPropiosAnuales = diasAsuntos,
                fechaAlta = fechaAltaIso
            )
            repository.actualizarEmpleado(empleadoId, request).fold(
                onSuccess = { _uiState.value = UiState.Success },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al guardar") }
            )
        }
    }

    /** Limpia el estado de error para que el Fragment no lo reprocese tras una rotacion. */
    fun limpiarError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }
}
