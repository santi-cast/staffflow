package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.AusenciaApiService
import com.staffflow.android.data.remote.dto.AusenciaPatchRequest
import com.staffflow.android.data.remote.dto.AusenciaRangoRequest
import com.staffflow.android.data.remote.dto.AusenciaRequest
import com.staffflow.android.data.remote.dto.AusenciaResponse
import com.staffflow.android.data.remote.dto.PlanificacionVacApResponse
import com.staffflow.android.util.ApiError
import com.staffflow.android.util.ApiException
import com.staffflow.android.util.safeApiCall
import okhttp3.ResponseBody

/** Lanzada por crearAusenciaRango cuando el backend devuelve 409 con fechas conflictivas. */
class RangoConflictException(val fechas: List<String>) : Exception("Conflicto en rango")

/**
 * Repositorio para los endpoints de ausencias planificadas (E30-E34).
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
 *
 * Requiere JWT con rol ADMIN o ENCARGADO (excepto getMisAusencias que requiere EMPLEADO).
 * El AuthInterceptor adjunta el token automaticamente.
 *
 * @param api Instancia de AusenciaApiService creada por NetworkModule.retrofit.
 */
class AusenciaRepository(private val api: AusenciaApiService) {

    /**
     * E30 - Crea una ausencia planificada.
     * P24 (FormAusenciaFragment) en modo alta llama a este metodo.
     */
    suspend fun crearAusencia(request: AusenciaRequest): Result<AusenciaResponse> =
        safeApiCall { api.crearAusencia(request) }

    /**
     * E63 - Crea ausencias planificadas en un rango de fechas.
     *
     * Compatibilidad transitoria: cuando el backend responde 409 con fechas
     * conflictivas (ApiError.RangoConflicto), se reenvuelve como
     * [RangoConflictException] para preservar el contrato actual de
     * FormAusenciaViewModel. El resto de fallos viajan como
     * ApiException(ApiError) tal como produce safeApiCall.
     */
    suspend fun crearAusenciaRango(request: AusenciaRangoRequest): Result<List<AusenciaResponse>> =
        safeApiCall { api.crearAusenciaRango(request) }
            .recoverCatching { throwable ->
                if (throwable is ApiException && throwable.error is ApiError.RangoConflicto) {
                    throw RangoConflictException(throwable.error.fechas)
                }
                throw throwable
            }

    /**
     * E31 - Actualiza parcialmente una ausencia no procesada.
     * P24 (FormAusenciaFragment) en modo edicion llama a este metodo.
     * Error 409 si procesado=true.
     */
    suspend fun actualizarAusencia(id: Long, request: AusenciaPatchRequest): Result<AusenciaResponse> =
        safeApiCall { api.actualizarAusencia(id, request) }

    /**
     * E32 - Elimina una ausencia no procesada.
     * P24 (FormAusenciaFragment) llama a este metodo tras confirmacion del dialogo.
     * Error 409 si procesado=true.
     */
    suspend fun eliminarAusencia(id: Long): Result<Unit> =
        safeApiCall { api.eliminarAusencia(id) }

    /**
     * E33 - Lista ausencias con filtros opcionales.
     * P23 (AusenciasFragment) llama a este metodo al cargar y al filtrar.
     */
    suspend fun listarAusencias(
        empleadoId: Long? = null,
        desde: String? = null,
        hasta: String? = null,
        procesado: Boolean? = null
    ): Result<List<AusenciaResponse>> =
        safeApiCall { api.listarAusencias(empleadoId, desde, hasta, procesado) }

    /**
     * E34 - Devuelve las ausencias del empleado autenticado.
     * P11 (MisAusenciasFragment) llama a este metodo al cargar y al filtrar.
     */
    suspend fun getMisAusencias(
        desde: String? = null,
        hasta: String? = null
    ): Result<List<AusenciaResponse>> =
        safeApiCall { api.getMisAusencias(desde, hasta) }

    /**
     * E61 - Informe HTML de ausencias del empleado autenticado.
     * P11 (MisAusenciasFragment) WebView — combina planificadas y ejecutadas.
     */
    suspend fun getMisAusenciasInforme(
        desde: String? = null,
        hasta: String? = null,
        filtro: String = "TODAS"
    ): Result<ResponseBody> =
        safeApiCall { api.getMisAusenciasInforme(desde, hasta, filtro) }

    /**
     * E64 - Días pendientes de planificar para vac y AP.
     * Accesible por ADMIN y ENCARGADO. Usado en P24 modo rango.
     */
    suspend fun getPlanificacionVacAp(
        empleadoId: Long,
        anio: Int
    ): Result<PlanificacionVacApResponse> =
        safeApiCall { api.getPlanificacionVacAp(empleadoId, anio) }

    /**
     * E62 - Informe HTML de ausencias de un empleado por id.
     * Accesible por ADMIN y ENCARGADO. Desde P14 chip "Ver ausencias".
     */
    suspend fun getInformeAusenciasEmpleado(
        empleadoId: Long,
        desde: String? = null,
        hasta: String? = null,
        filtro: String = "TODAS"
    ): Result<ResponseBody> =
        safeApiCall { api.getInformeAusenciasEmpleado(empleadoId, desde, hasta, filtro) }
}
