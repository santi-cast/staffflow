package com.staffflow.android.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.R
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
import com.staffflow.android.util.ApiError
import com.staffflow.android.util.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cabecera read-only del empleado asociado al usuario en edición.
 * Se muestra en P29 cuando el usuario cargado tiene rol distinto
 * de ADMIN. Sólo expone los campos que pinta la cabecera y el id
 * del empleado para que el botón "Editar empleado" navegue a P14.
 */
data class CabeceraEmpleado(
    val empleadoId: Long,
    val nombreCompleto: String,
    val numeroEmpleado: String
)

/**
 * ViewModel del formulario de usuario (P29). Solo ADMIN.
 *
 * Modo alta   (usuarioId = -1): E08 POST /usuarios + E13 POST /empleados
 *                               cuando rol != ADMIN.
 * Modo edicion (usuarioId > 0): pre-carga con E10 GET /usuarios/{id},
 *                               guarda con E11 PATCH /usuarios/{id}.
 * Desactivar  (usuarioId > 0): E12 DELETE /usuarios/{id} (baja logica).
 * Reactivar   (usuarioId > 0): E67 PATCH /usuarios/{id}/reactivar (deshace E12).
 *
 * UiState:
 *   Idle                    -> formulario listo
 *   Loading                 -> llamada al API en curso
 *   Cargando                -> pre-cargando datos en modo edicion
 *   Success                 -> operacion correcta (Fragment navega atras)
 *   SuccessAlta             -> usuario cargado en modo edicion (datos para pre-rellenar)
 *   Desactivado             -> baja logica OK (Fragment navega atras)
 *   Reactivado              -> E67 OK (Fragment navega atras)
 *   PasswordReseteado       -> E66 OK (Fragment muestra Snackbar, NO navega atras)
 *   Error                   -> mensaje de error inline
 *   UsernameDuplicadoEnAlta -> HTTP 409 en E08 por username ya existente.
 *                              El Fragment muestra el mensaje y vuelve a
 *                              sugerir un username libre sin perder el
 *                              resto de campos del formulario.
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
        /** Datos del usuario cargados en modo edicion para pre-rellenar el formulario. */
        data class SuccessAlta(val usuario: UsuarioResponse) : UiState()
        object Desactivado : UiState()
        /** E67 OK: usuario reactivado. El Fragment navega atras y notifica con FragmentResult. */
        object Reactivado : UiState()
        /** E66 OK: contrasena reseteada. El Fragment muestra Snackbar y vuelve a Idle. */
        object PasswordReseteado : UiState()
        data class Error(val mensaje: String) : UiState()

        /**
         * HTTP 409 en E08 POST /usuarios por username ya existente.
         * Se emite solo en modo alta y solo cuando el conflicto viene del
         * primer paso (creacion del usuario). Conflictos de E13 (DNI o NFC)
         * caen en Error porque no son resolubles automaticamente.
         *
         * El Fragment debe mostrar `mensaje` en Snackbar y volver a invocar
         * `sugerirUsername(rol)` para que el ViewModel proponga el siguiente
         * username libre. El resto del formulario se conserva intacto.
         */
        data class UsernameDuplicadoEnAlta(val mensaje: String, val rol: Rol) : UiState()
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

    private val _cabeceraEmpleado = MutableStateFlow<CabeceraEmpleado?>(null)
    /**
     * Cabecera del empleado asociado al usuario en edición (sólo modo
     * edición, sólo rol != ADMIN). null mientras no se cargue, o si la
     * llamada devuelve 404 (caso típico: usuario ADMIN sin empleado).
     * El Fragment observa este flow para mostrar/ocultar el bloque de
     * cabecera y habilitar el botón "Editar empleado".
     */
    val cabeceraEmpleado: StateFlow<CabeceraEmpleado?> = _cabeceraEmpleado.asStateFlow()

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
                onSuccess = { usuario ->
                    _uiState.value = UiState.SuccessAlta(usuario)
                    if (usuario.rol != Rol.ADMIN) {
                        cargarCabeceraEmpleado()
                    }
                },
                onFailure = {
                    _uiState.value = UiState.Error(
                        it.message ?: getApplication<Application>().getString(R.string.form_usuario_error_cargar_usuario)
                    )
                }
            )
        }
    }

    /**
     * Carga la cabecera del empleado vinculado al usuario en edición vía E68.
     * Sólo se invoca cuando rol != ADMIN. Un 404 (ApiError.NotFound) deja
     * la cabecera en null sin emitir error: el caso típico es un usuario
     * ADMIN sin empleado asociado, y otros fallos (red, 5xx) tampoco deben
     * romper el formulario porque la cabecera es un complemento informativo.
     */
    private fun cargarCabeceraEmpleado() {
        viewModelScope.launch {
            empleadoRepository.getEmpleadoPorUsuario(usuarioId).fold(
                onSuccess = { empleado ->
                    val nombreCompleto = buildString {
                        append(empleado.nombre).append(' ').append(empleado.apellido1)
                        empleado.apellido2?.takeIf { it.isNotBlank() }?.let {
                            append(' ').append(it)
                        }
                    }
                    _cabeceraEmpleado.value = CabeceraEmpleado(
                        empleadoId = empleado.id,
                        nombreCompleto = nombreCompleto,
                        numeroEmpleado = empleado.numeroEmpleado
                    )
                },
                onFailure = {
                    // Silencio deliberado: la cabecera es informativa, no debe
                    // romper el flujo. 404 es el caso esperado para usuarios
                    // sin empleado vinculado.
                    _cabeceraEmpleado.value = null
                }
            )
        }
    }

    /**
     * Sugiere el siguiente username disponible segun el rol seleccionado.
     * Llama a E09 GET /usuarios?rol= para obtener los usuarios existentes,
     * busca el numero mas alto que siga el patron del prefijo y propone el
     * siguiente con formato de 3 digitos (usu001, usu002, ... / admin001, admin002, ...).
     *
     * Reglas de prefijo:
     *   - ADMIN     → prefijo "admin", numeracion exclusiva de ADMIN.
     *   - ENCARGADO → prefijo "usu", numeracion COMPARTIDA con EMPLEADO.
     *   - EMPLEADO  → prefijo "usu", numeracion COMPARTIDA con ENCARGADO.
     *
     * ENCARGADO y EMPLEADO comparten prefijo "usu" y numeracion correlativa porque
     * el username es agnostico del rol operativo y no se regenera al cambiar de rol.
     * Asi un cambio ENCARGADO ↔ EMPLEADO no deja el username inconsistente.
     *
     * El usuario de sistema "terminal_service" no sigue este esquema: es un seed de
     * sistema creado directamente en data.sql y nunca se crea desde la UI.
     *
     * Solo relevante en modo alta. Se ignora en modo edicion.
     * Si la llamada falla, no emite nada (el campo queda libre para escribir).
     */
    fun sugerirUsername(rol: Rol) {
        if (modoEdicion) return
        val prefix: String
        val rolesAConsultar: List<Rol>
        when (rol) {
            Rol.ADMIN -> {
                prefix = "admin"
                rolesAConsultar = listOf(Rol.ADMIN)
            }
            Rol.ENCARGADO, Rol.EMPLEADO -> {
                prefix = "usu"
                // ENCARGADO y EMPLEADO comparten numeracion porque comparten prefijo "usu":
                // el username es agnostico del rol operativo y no se regenera al cambiar rol.
                rolesAConsultar = listOf(Rol.ENCARGADO, Rol.EMPLEADO)
            }
        }
        viewModelScope.launch {
            val regex = Regex("^${Regex.escape(prefix)}(\\d+)$")
            var maxNum = 0
            var huboFallo = false
            for (r in rolesAConsultar) {
                repository.listarUsuarios(rol = r.name).fold(
                    onSuccess = { lista ->
                        val maxRol = lista
                            .mapNotNull { regex.find(it.username)?.groupValues?.get(1)?.toIntOrNull() }
                            .maxOrNull() ?: 0
                        if (maxRol > maxNum) maxNum = maxRol
                    },
                    onFailure = { huboFallo = true }
                )
            }
            // Si al menos una peticion respondio con exito (o ninguna fallo), emite sugerencia.
            // Si una falla pero la otra responde, usa el dato disponible sin bloquear.
            // Solo silencioso si TODAS fallaron y maxNum quedo en 0.
            if (!huboFallo || maxNum > 0) {
                _usernameSugerido.value = "%s%03d".format(prefix, maxNum + 1)
            }
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
     * fechaAlta (String ISO-8601 "yyyy-MM-dd") es opcional: si es null, el
     * backend asigna LocalDate.now(). Si se envia, debe ser igual o posterior
     * a hoy; en caso contrario el backend devuelve HTTP 400 y emite Error.
     *
     * En exito emite UiState.Success y el Fragment navega atras.
     */
    fun crear(
        username: String, password: String, email: String, rol: Rol,
        nombre: String? = null, apellido1: String? = null, apellido2: String? = null,
        dni: String? = null, categoria: CategoriaEmpleado? = null,
        jornadaSemanalHoras: Double? = null, diasVacaciones: Int? = null,
        diasAsuntos: Int? = null, fechaAlta: String? = null
    ) {
        if (username.isBlank() || password.isBlank() || email.isBlank()) {
            _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.form_usuario_error_campos_obligatorios))
            return
        }
        if (rol != Rol.ADMIN) {
            if (nombre.isNullOrBlank() || apellido1.isNullOrBlank() ||
                dni.isNullOrBlank() || categoria == null ||
                jornadaSemanalHoras == null || diasVacaciones == null || diasAsuntos == null) {
                _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.form_usuario_error_campos_perfil_empleado))
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
                val fallo = usuarioResult.exceptionOrNull()
                // HTTP 409 en E08 = conflicto de username (es el unico campo con
                // unicidad chequeada en POST /usuarios; email no es unique en BD).
                // Se trata de forma especial para que el Fragment regenere
                // automaticamente el username sin perder el resto del formulario.
                val errorApi = (fallo as? ApiException)?.error
                if (errorApi is ApiError.Conflict) {
                    _uiState.value = UiState.UsernameDuplicadoEnAlta(
                        mensaje = errorApi.mensaje
                            ?: getApplication<Application>().getString(R.string.form_usuario_error_username_duplicado_fallback),
                        rol = rol
                    )
                } else {
                    _uiState.value = UiState.Error(
                        fallo?.message ?: getApplication<Application>().getString(R.string.form_usuario_error_crear_usuario)
                    )
                }
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
                        diasAsuntosPropiosAnuales = diasAsuntos!!,
                        fechaAlta = fechaAlta
                    )
                ).fold(
                    onSuccess = { _uiState.value = UiState.Success },
                    onFailure = {
                        _uiState.value = UiState.Error(
                            getApplication<Application>().getString(
                                R.string.form_usuario_error_perfil_empleado_fallido,
                                it.message ?: ""
                            )
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
     * Solo permite cambiar email y rol (no username, password ni activo).
     *
     * El estado activo no es modificable por esta via: el DTO
     * `UsuarioPatchRequest` no declara el campo (ni en backend ni en cliente).
     * Para desactivar se usa E12 DELETE (metodo `desactivar()`); para reactivar
     * se usa E67 PATCH /usuarios/{id}/reactivar (metodo `reactivar()`).
     */
    fun actualizar(email: String, rol: Rol) {
        if (email.isBlank()) {
            _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.form_usuario_error_email_obligatorio))
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val request = UsuarioPatchRequest(email = email, rol = rol)
            repository.actualizarUsuario(usuarioId, request).fold(
                onSuccess = { _uiState.value = UiState.Success },
                onFailure = { _uiState.value = UiState.Error(it.message ?: getApplication<Application>().getString(R.string.form_usuario_error_guardar)) }
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
                onFailure = { _uiState.value = UiState.Error(it.message ?: getApplication<Application>().getString(R.string.form_usuario_error_desactivar)) }
            )
        }
    }

    /**
     * Reactiva el usuario (E67 PATCH /usuarios/{id}/reactivar). Simetrico a
     * desactivar(): solo aplica a usuarios en edicion con activo=false.
     * El Fragment debe mostrar MaterialAlertDialogBuilder antes de llamar.
     */
    fun reactivar() {
        if (usuarioId <= 0L) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.reactivarUsuario(usuarioId).fold(
                onSuccess = { _uiState.value = UiState.Reactivado },
                onFailure = { _uiState.value = UiState.Error(it.message ?: getApplication<Application>().getString(R.string.form_usuario_error_reactivar)) }
            )
        }
    }

    /**
     * Restablece la contrasena del usuario en edicion (E66 PATCH /usuarios/{id}/password).
     *
     * Solo disponible en modo edicion (usuarioId > 0). El ADMIN proporciona la
     * nueva contrasena desde el dialogo de P29; la validacion de longitud minima
     * (8 chars) se realiza en el Fragment antes de llamar a este metodo.
     *
     * En exito emite UiState.PasswordReseteado y el Fragment muestra Snackbar
     * sin navegar atras (el formulario permanece abierto).
     *
     * @param nuevaPassword nueva contrasena en claro (minimo 8 caracteres)
     */
    fun resetearPassword(nuevaPassword: String) {
        if (!modoEdicion || usuarioId <= 0L) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.resetearPassword(usuarioId, nuevaPassword).fold(
                onSuccess = { _uiState.value = UiState.PasswordReseteado },
                onFailure = { _uiState.value = UiState.Error(it.message ?: getApplication<Application>().getString(R.string.form_usuario_error_cambiar_password)) }
            )
        }
    }

    /**
     * Limpia los estados transitorios de error para que el Fragment no los
     * reprocese tras una rotacion u otro cambio de configuracion.
     * Cubre Error generico y UsernameDuplicadoEnAlta (ambos requieren
     * Snackbar + vuelta a Idle).
     */
    fun limpiarError() {
        val actual = _uiState.value
        if (actual is UiState.Error || actual is UiState.UsernameDuplicadoEnAlta) {
            _uiState.value = UiState.Idle
        }
    }

    /**
     * Limpia el estado PasswordReseteado tras mostrar el Snackbar.
     * Vuelve a Idle para que el Fragment no reprocese el estado en rotaciones.
     */
    fun limpiarPasswordReseteado() {
        if (_uiState.value is UiState.PasswordReseteado) {
            _uiState.value = UiState.Idle
        }
    }
}
