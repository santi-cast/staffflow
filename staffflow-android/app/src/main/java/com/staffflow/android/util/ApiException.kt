package com.staffflow.android.util

/**
 * Wrapper que transporta un [ApiError] tipado dentro de `Result.failure(...)`.
 *
 * El `message` se construye preservando los strings legacy que producía la
 * versión antigua de `safeApiCall`, de modo que los consumidores que todavía
 * hacen string matching (ver inventario) sigan funcionando hasta que se migren
 * en slices posteriores.
 */
class ApiException(val error: ApiError) : Exception(toLegacyMessage(error)) {

    private companion object {
        fun toLegacyMessage(error: ApiError): String = when (error) {
            is ApiError.Network -> "Sin conexion con el servidor"
            ApiError.Timeout -> "Sin conexion con el servidor"
            is ApiError.Unauthorized -> error.mensaje ?: "Error 401"
            is ApiError.Forbidden -> error.mensaje ?: "Error 403"
            is ApiError.NotFound -> error.mensaje ?: "Error 404"
            is ApiError.Conflict -> error.mensaje ?: "Error 409"
            is ApiError.RangoConflicto -> "Conflicto en rango"
            is ApiError.PinBloqueado -> error.mensaje ?: "Error 423"
            is ApiError.Validation -> error.mensaje
            is ApiError.Server -> error.mensaje ?: "Error ${error.code}"
            is ApiError.Unknown -> error.cause.message ?: "Error desconocido"
        }
    }
}
