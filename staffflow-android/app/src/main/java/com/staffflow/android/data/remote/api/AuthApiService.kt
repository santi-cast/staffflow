package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.LoginRequest
import com.staffflow.android.data.remote.dto.LoginResponse
import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.data.remote.dto.PasswordChangeRequest
import com.staffflow.android.data.remote.dto.PasswordRecoveryRequest
import com.staffflow.android.data.remote.dto.PasswordResetRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * Interfaz Retrofit para los endpoints de autenticacion.
 *
 * Endpoints cubiertos:
 *   E01 POST /auth/login             -> LoginResponse
 *   E03 PUT  /auth/password          -> MensajeResponse  (JWT requerido)
 *   E04 POST /auth/password/recovery -> MensajeResponse  (sin JWT, anti-enumeracion)
 *   E05 POST /auth/password/reset    -> MensajeResponse  (sin JWT, token por email)
 *
 * Errores comunes:
 *   401 credenciales invalidas / token incorrecto
 *   400 token expirado o invalido (E05)
 *   423 demasiados intentos (E01)
 */
interface AuthApiService {

    /**
     * E01 - Autenticacion con username y password.
     * Sin JWT. Devuelve token JWT, rol, username y empleadoId (null si ADMIN).
     */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    /**
     * E03 - Cambiar contraseña del usuario autenticado.
     * Requiere JWT. Error 401 si passwordActual es incorrecto.
     */
    @PUT("auth/password")
    suspend fun cambiarPassword(@Body request: PasswordChangeRequest): Response<MensajeResponse>

    /**
     * E04 - Solicitar recuperacion de contraseña por email.
     * Sin JWT. El backend siempre devuelve 200 para evitar enumeracion de usuarios.
     */
    @POST("auth/password/recovery")
    suspend fun recuperarPassword(@Body request: PasswordRecoveryRequest): Response<MensajeResponse>

    /**
     * E05 - Restablecer contraseña con token recibido por email.
     * Sin JWT. Error 400 si el token ha expirado o es invalido.
     */
    @POST("auth/password/reset")
    suspend fun restablecerPassword(@Body request: PasswordResetRequest): Response<MensajeResponse>
}
