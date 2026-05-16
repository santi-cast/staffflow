package com.staffflow.android.util

import retrofit2.Response
import java.io.IOException

/**
 * Ejecuta una llamada Retrofit y normaliza el resultado en [Result].
 *
 * Contrato:
 *   - Exito HTTP 2xx: devuelve `Result.success(body)`. El cuerpo no puede ser
 *     nulo por contrato de los endpoints del backend (Retrofit garantiza no-null
 *     en 2xx para tipos no nullables).
 *   - Fallo HTTP (no 2xx), [IOException] (incluida [java.net.SocketTimeoutException])
 *     o cualquier otra excepcion: devuelve `Result.failure(ApiException(error))`,
 *     donde `error` es un [ApiError] tipado producido por [mapToApiError].
 *
 * Los `ViewModel` pueden hacer `when (error)` exhaustivo sobre la `sealed interface`
 * [ApiError] para reaccionar al caso (mostrar dialogo de PIN bloqueado, lista de
 * fechas en conflicto, mensaje de validacion por campo, etc.).
 *
 * Para compatibilidad transitoria con consumidores que aun leen
 * `exception.message` como string, [ApiException] preserva los mensajes
 * historicos a traves de `ApiException.toLegacyMessage`. Esto permite migrar
 * `Repository` y `ViewModel` de forma incremental sin romper la UI.
 *
 * Uso tipico en `Repository`:
 * ```
 * suspend fun login(request: LoginRequest): Result<LoginResponse> =
 *     safeApiCall { api.login(request) }
 * ```
 *
 * Uso tipado en `ViewModel`:
 * ```
 * repo.login(req).onFailure { e ->
 *     val error = (e as? ApiException)?.error
 *     when (error) {
 *         is ApiError.PinBloqueado -> mostrarBloqueo(error.mensaje)
 *         is ApiError.Unauthorized -> mostrarCredencialesInvalidas()
 *         else -> mostrarMensajeGenerico(e.message)
 *     }
 * }
 * ```
 */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): Result<T> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            Result.success(response.body()!!)
        } else {
            Result.failure(ApiException(mapToApiError(response = response)))
        }
    } catch (e: IOException) {
        Result.failure(ApiException(mapToApiError(throwable = e)))
    } catch (e: Exception) {
        Result.failure(ApiException(mapToApiError(throwable = e)))
    }
}
