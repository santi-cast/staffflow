package com.staffflow.android.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.remote.api.EmpleadoApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.UsuarioApiService
import com.staffflow.android.data.remote.dto.EmpleadoRequest
import com.staffflow.android.data.remote.dto.UsuarioPatchRequest
import com.staffflow.android.data.remote.dto.UsuarioRequest
import com.staffflow.android.data.remote.dto.UsuarioResponse
import com.staffflow.android.data.remote.repository.EmpleadoRepository
import com.staffflow.android.data.remote.repository.UsuarioRepository
import com.staffflow.android.domain.model.CategoriaEmpleado
import com.staffflow.android.domain.model.Rol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del formulario de usuario (P29). Solo ADMIN.
 *
 * Modo alta   (usuarioId = -1): E08 POST /usuarios.
 * Modo edicion (usuarioId > 0): pre-carga con E10 GET /usuarios/{id},
 *                               guarda con E11 PATCH /usuarios/{id}.
 * Desactivar  (usuarioId > 0): E12 DELETE /usuarios/{id} (baja logica).
 *
 * UiState:
 *   Idle        -> formulario listo
 *   Loading     -> llamada al API en curso
 *   Cargando    -> pre-cargando datos en modo edicion
 *   Success     -> operacion correcta (Fragment navega atras)
 *   SuccessAlta -> usuario creado (Fragment muestra Snackbar con accion crear perfil)
 *   Desactivado -> baja logica OK (Fragment navega atras)
 *   Error       -> mensaje de error inline
 */
class FormUsuarioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsuarioRepository(
        NetworkModule.retrofit.create(UsuarioApiService::class.java)
    )
    private val empleadoRepository = EmpleadoRepository(
        NetworkModule.retrofit.create(EmpleadoApiService::class.java)
    )

    sealed class UiState {
        object Idle : UiState()
        object Cargando : UiState()
        object Loading : UiState()
        object Success : UiState()
        /** Usuario creado. empleadoId del nuevo usuario para navegar a P15. */
        data class SuccessAlta(val usuario: UsuarioResponse) : UiState()
        object Desactivado : UiState()
        data class Error(val mensaje: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _usernameSugerido = MutableStateFlow<String?>(null)
    /**
     * Sugerencia de username para el campo de alta.
     * Solo se emite en modo alta. El Fragment la aplica si el campo
     * esta vacio o si contiene la sugerencia anterior.
     */
    val usernameSugerido: StateFlow<String?> = _usernameSugerido.asStateFlow()

    var modoEdicion: Boolean = false
        private set

    private var usuarioId: Long = Long.MIN_VALUE

    /**
     * Inicializa el modo del formulario.
     * El guard evita reinicializar en rotaciones de pantalla.
     * Si usuarioId > 0, pre-carga los datos del usuario via E10.
     */
    fun init(usuarioId: Long) {
        if (this.usuarioId != Long.MIN_VALUE) return
        this.usuarioId = usuarioId
        modoEdicion = usuarioId > 0L
        if (modoEdicion) preCargarUsuario()
    }

    private fun preCargarUsuario() {
        viewModelScope.launch {
            _uiState.value = UiState.Cargando
            repository.obtenerUsuario(usuarioId).fold(
                onSuccess = { _uiState.value = UiState.SuccessAlta(it) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al cargar usuario") }
            )
        }
    }

    /**
     * Sugiere el siguiente username disponible segun el rol seleccionado.
     * Llama a E09 GET /usuarios?rol= para obtener los usuarios existentes,
     * busca el numero mas alto que siga el patron del prefijo y propone el
     * siguiente con formato de 2 digitos (emp01, emp02, ...).
     *
     * Solo relevante en modo alta. Se ignora en modo edicion.
     * Si la llamada falla, no emite nada (el campo queda como esta).
     */
    fun sugerirUsername(rol: Rol) {
        if (modoEdicion) return
        val prefix = when (rol) {
            Rol.EMPLEADO  -> "emp"
            Rol.ENCARGADO -> "encargado"
            Rol.ADMIN     -> "admin"
        }
        viewModelScope.launch {
            repository.listarUsuarios(rol = rol.name).fold(
                onSuccess = { lista ->
                    val regex = Regex("^${Regex.escape(prefix)}(\\d+)$")
                    val maxNum = lista
                        .mapNotNull { regex.find(it.username)?.groupValues?.get(1)?.toIntOrNull() }
                        .maxOrNull() ?: 0
                    _usernameSugerido.value = "%s%02d".format(prefix, maxNum + 1)
                },
                onFailure = { /* silencioso: el campo queda libre para escribir */ }
            )
        }
    }

    /**
     * Crea un nuevo usuario (E08 POST /usuarios) y, si el rol es EMPLEADO o
     * ENCARGADO, crea tambien su perfil de empleado (E13 POST /empleados)
     * en un segundo paso con el usuarioId recien generado.
     *
     * Si el segundo paso falla (error en /empleados), emite Error con mensaje
     * descriptivo para que el admin sepa que el usuario SI fue creado.
     *
     * En exito emite UiState.Success y el Fragment navega atras.
     */
    fun crear(
        username: String, password: String, email: String, rol: Rol,
        nombre: String? = null, apellido1: String? = null, apellido2: String? = null,
        dni: String? = null, categoria: CategoriaEmpleado? = null,
        jornadaSemanalHoras: Double? = null, diasVacaciones: Int? = null,
        diasAsuntos: Int? = null
    ) {
        if (username.isBlank() || password.isBlank() || email.isBlank()) {
            _uiState.value = UiState.Error("Rellena todos los campos obligatorios")
            return
        }
        if (rol != Rol.ADMIN) {
            if (nombre.isNullOrBlank() || apellido1.isNullOrBlank() ||
                dni.isNullOrBlank() || categoria == null ||
                jornadaSemanalHoras == null || diasVacaciones == null || diasAsuntos == null) {
                _uiState.value = UiState.Error("Rellena todos los campos del perfil de empleado")
                return
            }
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            // Paso 1: crear usuario
            val usuarioResult = repository.crearUsuario(
                UsuarioRequest(username = username, password = password,
                               email = email, rol = rol)
            )
            if (usuarioResult.isFailure) {
                _uiState.value = UiState.Error(
                    usuarioResult.exceptionOrNull()?.message ?: "Error al crear usuario"
                )
                return@launch
            }
            val usuario = usuarioResult.getOrThrow()
            // Paso 2: crear perfil de empleado (solo si no es ADMIN)
            if (rol != Rol.ADMIN) {
                empleadoRepository.crearEmpleado(
                    EmpleadoRequest(
                        usuarioId = usuario.id,
                        nombre = nombre!!,
                        apellido1 = apellido1!!,
                        apellido2 = apellido2?.ifBlank { null },
                        dni = dni!!,
                        categoria = categoria!!,
                        jornadaSemanalHoras = jornadaSemanalHoras!!,
                        diasVacacionesAnuales = diasVacaciones!!,
                        diasAsuntosPropiosAnuales = diasAsuntos!!
                    )
                ).fold(
                    onSuccess = { _uiState.value = UiState.Success },
                    onFailure = {
                        _uiState.value = UiState.Error(
                            "Usuario creado pero falló el perfil: ${it.message}"
                        )
                    }
                )
            } else {
                _uiState.value = UiState.Success
            }
        }
    }

    /**
     * Actualiza un usuario existente (E11 PATCH /usuarios/{id}).
     * Solo permite cambiar email, rol y activo (no username ni password).
     */
    fun actualizar(email: String, rol: Rol, activo: Boolean) {
        if (email.isBlank()) {
            _uiState.value = UiState.Error("El email es obligatorio")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val request = UsuarioPatchRequest(email = email, rol = rol, activo = activo)
            repository.actualizarUsuario(usuarioId, request).fold(
                onSuccess = { _uiState.value = UiState.Success },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al guardar") }
            )
        }
    }

    /**
     * Desactiva el usuario (E12 DELETE, baja logica).
     * El Fragment debe mostrar MaterialAlertDialogBuilder antes de llamar.
     */
    fun desactivar() {
        if (usuarioId <= 0L) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.desactivarUsuario(usuarioId).fold(
                onSuccess = { _uiState.value = UiState.Desactivado },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Error al desactivar") }
            )
        }
    }

    fun limpiarError() {
        if (_uiState.value is UiState.Error) _uiState.value = UiState.Idle
    }
}
