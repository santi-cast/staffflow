package com.staffflow.android.util

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.staffflow.android.data.remote.dto.RangoConflictResponse
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Traduce excepciones y respuestas HTTP fallidas a [ApiError].
 *
 * Función pura: NO depende de contexto Android ni de coroutines.
 * Testeable con JUnit puro.
 *
 * @param throwable excepción capturada en `safeApiCall` (null si el caso es HTTP error).
 * @param response  respuesta Retrofit no exitosa (null si el caso es excepción).
 */
fun mapToApiError(throwable: Throwable? = null, response: Response<*>? = null): ApiError {
    if (response != null && !response.isSuccessful) {
        return mapHttpError(response)
    }
    return when (throwable) {
        is SocketTimeoutException -> ApiError.Timeout
        is IOException -> ApiError.Network(throwable)
        null -> ApiError.Unknown(IllegalStateException("mapToApiError invocado sin throwable ni response"))
        else -> ApiError.Unknown(throwable)
    }
}

private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

private fun mapHttpError(response: Response<*>): ApiError {
    val code = response.code()
    val bodyString = try { response.errorBody()?.string().orEmpty() } catch (_: IOException) { "" }

    // Parseo flexible: el body es Map<String, Any?> con claves error, campo, fechasConflictivas...
    val gson = Gson()
    val map: Map<String, Any?> = try {
        if (bodyString.isBlank()) emptyMap() else gson.fromJson(bodyString, mapType) ?: emptyMap()
    } catch (_: JsonSyntaxException) {
        emptyMap()
    }
    val error = map["error"] as? String
    val campo = map["campo"] as? String

    return when (code) {
        401 -> ApiError.Unauthorized(error)
        403 -> ApiError.Forbidden(error)
        404 -> ApiError.NotFound(error)
        409 -> {
            // Si el body trae fechasConflictivas, es el 409 de rango.
            val rango = try { gson.fromJson(bodyString, RangoConflictResponse::class.java) } catch (_: JsonSyntaxException) { null }
            val fechas = rango?.fechasConflictivas
            if (!fechas.isNullOrEmpty()) ApiError.RangoConflicto(fechas) else ApiError.Conflict(error, campo)
        }
        423 -> ApiError.PinBloqueado(error)
        400 -> ApiError.Validation(error ?: "Solicitud invalida", campo)
        in 500..599 -> ApiError.Server(code, error)
        else -> ApiError.Server(code, error)
    }
}
