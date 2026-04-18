package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.AuthApiService
import com.staffflow.android.data.remote.dto.LoginRequest
import com.staffflow.android.data.remote.dto.LoginResponse
import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.data.remote.dto.PasswordChangeRequest
import com.staffflow.android.data.remote.dto.PasswordRecoveryRequest
import com.staffflow.android.data.remote.dto.PasswordResetRequest
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de autenticacion.
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
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

    /**
     * E03 - Cambiar contraseña del usuario autenticado.
     * Requiere JWT. Error 401 si passwordActual incorrecto.
     */
    suspend fun cambiarPassword(request: PasswordChangeRequest): Result<MensajeResponse> =
        safeApiCall { api.cambiarPassword(request) }

    /**
     * E04 - Solicitar recuperacion de contraseña por email.
     * Sin JWT. El backend siempre devuelve 200 (anti-enumeracion).
     */
    suspend fun recuperarPassword(request: PasswordRecoveryRequest): Result<MensajeResponse> =
        safeApiCall { api.recuperarPassword(request) }

    /**
     * E05 - Restablecer contraseña con token del email.
     * Sin JWT. Error 400 si el token ha expirado o es invalido.
     */
    suspend fun restablecerPassword(request: PasswordResetRequest): Result<MensajeResponse> =
        safeApiCall { api.restablecerPassword(request) }
}
