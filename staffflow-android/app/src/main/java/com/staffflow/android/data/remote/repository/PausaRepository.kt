package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.PausaApiService
import com.staffflow.android.data.remote.dto.PausaPatchRequest
import com.staffflow.android.data.remote.dto.PausaRequest
import com.staffflow.android.data.remote.dto.PausaResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de pausas (E27-E29).
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
 *
 * Requiere JWT con rol ADMIN o ENCARGADO.
 * El AuthInterceptor adjunta el token automaticamente.
 *
 * @param api Instancia de PausaApiService creada por NetworkModule.retrofit.
 */
class PausaRepository(private val api: PausaApiService) {

    /**
     * E27 - Crea una pausa manual.
     * P20 (FormFichajeFragment) en variante pausa llama a este metodo.
     */
    suspend fun crearPausa(request: PausaRequest): Result<PausaResponse> =
        safeApiCall { api.crearPausa(request) }

    /**
     * E28 - Actualiza parcialmente una pausa.
     * P20 (FormFichajeFragment) en modo edicion variante pausa llama a este metodo.
     */
    suspend fun actualizarPausa(id: Long, request: PausaPatchRequest): Result<PausaResponse> =
        safeApiCall { api.actualizarPausa(id, request) }

    /**
     * E29 - Lista pausas con filtros opcionales.
     * P19 (FichajesFragment) puede mostrar pausas de un fichaje concreto.
     */
    suspend fun listarPausas(
        empleadoId: Long? = null,
        desde: String? = null,
        hasta: String? = null,
        tipoPausa: String? = null
    ): Result<List<PausaResponse>> =
        safeApiCall { api.listarPausas(empleadoId, desde, hasta, tipoPausa) }
}
