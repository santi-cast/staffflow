package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.AuthApiService
import com.staffflow.android.data.remote.dto.LoginRequest
import com.staffflow.android.data.remote.dto.LoginResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de autenticacion.
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
 *
 * Tras un login exitoso, LoginViewModel debe notificar a LoginFragment
 * para que ejecute la secuencia de post-login en MainActivity:
 *   sessionManager.saveSession -> NetworkModule.authToken = token
 *   -> refreshDrawerMenu -> navigateToInitialDestination(rol)
 *
 * @param api Instancia de AuthApiService creada por NetworkModule.retrofit.
 */
class AuthRepository(private val api: AuthApiService) {

    /**
     * E01 - Login con username y password.
     * Sin JWT. Devuelve LoginResponse con token, rol, username y empleadoId.
     * Errores: 401 credenciales invalidas | 423 demasiados intentos.
     */
    suspend fun login(request: LoginRequest): Result<LoginResponse> =
        safeApiCall { api.login(request) }
}
