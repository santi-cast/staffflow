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
 * Interfaz Retrofit para los endpoints de autenticación.
 *
 * Endpoints cubiertos:
 *   E01 POST /auth/login             -> LoginResponse
 *   E03 PUT  /auth/password          -> MensajeResponse  (JWT requerido)
 *   E04 POST /auth/password/recovery -> MensajeResponse  (sin JWT, anti-enumeración)
 *   E05 POST /auth/password/reset    -> MensajeResponse  (sin JWT, ver nota inferior)
 *
 * Errores comunes:
 *   401 credenciales inválidas / token incorrecto
 *   400 token expirado o inválido (E05)
 *   423 demasiados intentos (E01)
 *
 * Nota sobre E05 (**v1.0 — no operativo**): el método [restablecerPassword]
 * pertenece al andamiaje reservado para v2.0. En v1 invocarlo devuelve
 * siempre HTTP 400 porque la base de datos nunca tiene tokens válidos. Ver
 * memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID, y
 * el KDoc del propio método [restablecerPassword].
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
     *
     * **v1.0 — no operativo:** en v1 este flujo entrega una contraseña temporal
     * de 8 caracteres por email (E04). El token UUID de 30 minutos descrito a
     * continuación pertenece al andamiaje reservado para v2.0 (ver memoria TFG,
     * bloque B10 Vías Futuras → Reset password con token UUID).
     *
     * En v1 invocar este método devuelve siempre HTTP 400: la base de datos
     * nunca contiene tokens válidos porque ningún flujo de producción escribe
     * `resetToken` en la entidad `Usuario`.
     *
     * Comportamiento previsto (v2.0): sin JWT. Error 400 si el token ha
     * expirado o es inválido.
     */
    @POST("auth/password/reset")
    suspend fun restablecerPassword(@Body request: PasswordResetRequest): Response<MensajeResponse>
}
