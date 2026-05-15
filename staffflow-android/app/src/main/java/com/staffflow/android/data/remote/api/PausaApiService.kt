package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.PausaPatchRequest
import com.staffflow.android.data.remote.dto.PausaRequest
import com.staffflow.android.data.remote.dto.PausaResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interfaz Retrofit para los endpoints de pausas.
 *
 * Endpoints cubiertos:
 *   E27 POST /pausas             -> PausaResponse 201   (ADMIN, ENCARGADO)
 *   E28 PATCH /pausas/{id}       -> PausaResponse       (ADMIN, ENCARGADO)
 *   E29 GET  /pausas             -> List<PausaResponse>  (ADMIN, ENCARGADO)
 *
 * Requiere JWT. El token lo adjunta AuthInterceptor en NetworkModule.
 * ENCARGADO solo puede gestionar pausas del dia actual.
 */
interface PausaApiService {

    /**
     * E27 - Crea una pausa manual dentro de una jornada.
     * Error 409 si ya hay una pausa activa ese dia para ese empleado.
     */
    @POST("pausas")
    suspend fun crearPausa(@Body request: PausaRequest): Response<PausaResponse>

    /**
     * E28 - Actualiza parcialmente una pausa.
     * Solo se envian los campos que se quieren modificar.
     */
    @PATCH("pausas/{id}")
    suspend fun actualizarPausa(
        @Path("id") id: Long,
        @Body request: PausaPatchRequest
    ): Response<PausaResponse>

    /**
     * E29 - Lista pausas con filtros opcionales.
     * @param empleadoId Filtra por empleado concreto
     * @param desde      Fecha inicio "yyyy-MM-dd"
     * @param hasta      Fecha fin "yyyy-MM-dd"
     * @param tipoPausa  Valor del enum TipoPausa
     */
    @GET("pausas")
    suspend fun listarPausas(
        @Query("empleadoId") empleadoId: Long? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("tipoPausa") tipoPausa: String? = null
    ): Response<List<PausaResponse>>
}
