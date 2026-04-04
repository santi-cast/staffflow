package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.LoginRequest
import com.staffflow.android.data.remote.dto.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interfaz Retrofit para los endpoints de autenticacion.
 *
 * Endpoints cubiertos:
 *   E01 POST /auth/login -> LoginResponse
 *
 * Errores:
 *   401 credenciales invalidas -> mensaje inline bajo el campo password
 *   423 demasiados intentos    -> "Demasiados intentos. Espera 30 segundos."
 *
 * Tras un login exitoso, LoginFragment debe:
 *   1. sessionManager.saveSession(token, rol, username, empleadoId)
 *   2. NetworkModule.authToken = token
 *   3. (activity as MainActivity).refreshDrawerMenu()
 *   4. (activity as MainActivity).navigateToInitialDestination(rol)
 */
interface AuthApiService {

    /**
     * E01 - Autenticacion con username y password.
     * Sin JWT. Devuelve token JWT, rol, username y empleadoId (null si ADMIN).
     */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
