package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.AusenciaApiService
import com.staffflow.android.data.remote.dto.AusenciaPatchRequest
import com.staffflow.android.data.remote.dto.AusenciaRequest
import com.staffflow.android.data.remote.dto.AusenciaResponse
import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.util.safeApiCall

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
    suspend fun eliminarAusencia(id: Long): Result<MensajeResponse> =
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
}
