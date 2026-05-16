package com.staffflow.android.util

/**
 * Jerarquía sellada de errores de la capa de red.
 *
 * Mapea 1:1 al contrato de errores del backend (GlobalExceptionHandler).
 * Cada variante representa una causa diferenciable que los ViewModels pueden
 * discriminar con un `when` exhaustivo en lugar de string matching del mensaje.
 *
 * Convive transitoriamente con el contrato legacy: `ApiException(this).message`
 * preserva los strings históricos para no romper los consumidores actuales.
 */
sealed interface ApiError {

    /** Falla de E/S de red (sin conexión, host inalcanzable, DNS, etc.) salvo timeout. */
    data class Network(val cause: Throwable) : ApiError

    /** Timeout de socket. Separado de Network porque algunos flujos lo tratan distinto. */
    data object Timeout : ApiError

    /** HTTP 401. Token ausente, inválido o expirado. */
    data class Unauthorized(val mensaje: String?) : ApiError

    /** HTTP 403. Autenticado pero sin permisos para el recurso. */
    data class Forbidden(val mensaje: String?) : ApiError

    /** HTTP 404. Recurso no encontrado. */
    data class NotFound(val mensaje: String?) : ApiError

    /** HTTP 409 genérico. Conflicto de estado (duplicado, transición inválida, etc.). */
    data class Conflict(val mensaje: String?, val campo: String?) : ApiError

    /**
     * HTTP 409 específico de POST /ausencias/rango con sobrescribir=false.
     * El backend devuelve la lista de fechas en conflicto en `fechasConflictivas`.
     */
    data class RangoConflicto(val fechas: List<String>) : ApiError

    /** HTTP 423. Terminal o recurso bloqueado por política (ej. fuerza bruta PIN). */
    data class PinBloqueado(val mensaje: String?) : ApiError

    /** HTTP 400. Validación de entrada fallida; `campo` indica el atributo ofensor si aplica. */
    data class Validation(val mensaje: String, val campo: String?) : ApiError

    /** HTTP 5xx u otro código no mapeado. */
    data class Server(val code: Int, val mensaje: String?) : ApiError

    /** Cualquier otra excepción no categorizada. */
    data class Unknown(val cause: Throwable) : ApiError
}
