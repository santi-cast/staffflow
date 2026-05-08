package com.staffflow.android.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.staffflow.android.data.local.SessionManager
import com.staffflow.android.data.remote.api.AuthApiService
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.dto.LoginRequest
import com.staffflow.android.data.remote.repository.AuthRepository
import com.staffflow.android.domain.model.Rol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de la UI del login.
 *
 *   Idle          -> formulario en espera
 *   Loading       -> llamada E01 en curso (boton deshabilitado, spinner visible)
 *   Exito         -> sesion iniciada; LoginFragment ejecuta la secuencia post-login en MainActivity
 *   Error         -> mensaje de error mostrado en tilPassword.error
 *   ErrorConexion -> no se pudo conectar al servidor; LoginFragment muestra el dialogo de cambio de IP
 */
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Exito(val rol: Rol) : LoginUiState()
    data class Error(val mensaje: String) : LoginUiState()
    data class ErrorConexion(val username: String, val password: String) : LoginUiState()
}

/**
 * ViewModel del login JWT (P02).
 *
 * Responsabilidades:
 *   - Llama a E01 POST /auth/login con AuthRepository.
 *   - En exito: persiste la sesion en DataStore via SessionManager
 *     y actualiza NetworkModule.authToken para que AuthInterceptor
 *     use el nuevo token inmediatamente.
 *   - Emite LoginUiState.Exito(rol) para que LoginFragment
 *     llame a MainActivity.refreshDrawerMenu() y navigateToInitialDestination(rol).
 *
 * Instanciacion manual del repositorio (sin Hilt -- D-B2-03).
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private var repository = AuthRepository(
        NetworkModule.retrofit.create(AuthApiService::class.java)
    )
    private val sessionManager = SessionManager.getInstance(application)

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    /** Estado de la UI. LoginFragment observa este flow. */
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Ejecuta el login con E01 POST /auth/login.
     * En exito persiste la sesion y actualiza authToken antes de emitir Exito.
     *
     * @param username Nombre de usuario introducido en el formulario.
     * @param password Contrasena introducida en el formulario.
     */
    fun login(username: String, password: String) {
        if (_uiState.value is LoginUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            repository.login(LoginRequest(username = username, password = password)).fold(
                onSuccess = { resp ->
                    // 1. Persistir sesion en DataStore
                    sessionManager.saveSession(
                        token = resp.token,
                        rol = resp.rol,
                        username = resp.username,
                        empleadoId = resp.empleadoId,
                        nombre = resp.nombre
                    )
                    // 2. Actualizar authToken en memoria para el interceptor
                    NetworkModule.authToken = resp.token
                    // 3. Notificar a LoginFragment para que coordine la navegacion
                    _uiState.value = LoginUiState.Exito(resp.rol)
                },
                onFailure = { e ->
                    if (e.message == "Sin conexion con el servidor") {
                        _uiState.value = LoginUiState.ErrorConexion(username, password)
                    } else {
                        _uiState.value = LoginUiState.Error(e.message ?: "Error de autenticacion")
                    }
                }
            )
        }
    }

    /** Limpia el estado de error para que el usuario pueda reintentar. */
    fun resetEstado() {
        _uiState.value = LoginUiState.Idle
    }

    /**
     * Persiste la nueva IP en DataStore, reconstruye el cliente Retrofit
     * y reintenta el login con las mismas credenciales.
     *
     * @param ip   Solo la parte de host, ej: "192.168.1.107"
     * @param username Credencial original del intento fallido.
     * @param password Credencial original del intento fallido.
     */
    fun guardarIpYReintentarLogin(ip: String, username: String, password: String) {
        viewModelScope.launch {
            val baseUrl = "http://$ip:8080/api/v1/"
            sessionManager.saveBaseUrl(baseUrl)
            NetworkModule.init(baseUrl)
            repository = AuthRepository(NetworkModule.retrofit.create(AuthApiService::class.java))
            login(username, password)
        }
    }
}
