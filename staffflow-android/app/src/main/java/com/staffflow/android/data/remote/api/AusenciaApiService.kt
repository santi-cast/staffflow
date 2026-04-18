package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.AusenciaPatchRequest
import com.staffflow.android.data.remote.dto.AusenciaRequest
import com.staffflow.android.data.remote.dto.AusenciaResponse
import com.staffflow.android.data.remote.dto.MensajeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interfaz Retrofit para los endpoints de ausencias planificadas.
 *
 * Endpoints cubiertos:
 *   E30 POST /ausencias          -> AusenciaResponse 201  (ADMIN, ENCARGADO)
 *   E31 PATCH /ausencias/{id}    -> AusenciaResponse      (ADMIN, ENCARGADO)
 *   E32 DELETE /ausencias/{id}   -> MensajeResponse       (ADMIN, ENCARGADO)
 *   E33 GET  /ausencias          -> List<AusenciaResponse> (ADMIN, ENCARGADO)
 *   E34 GET  /ausencias/me       -> List<AusenciaResponse> (EMPLEADO)
 *
 * Requiere JWT. El token lo adjunta AuthInterceptor en NetworkModule.
 * E31 y E32 devuelven 409 si la ausencia ya fue procesada (procesado=true).
 * empleadoId null en E30 indica festivo global (afecta a todos).
 */
interface AusenciaApiService {

    /**
     * E30 - Crea una ausencia planificada.
     * empleadoId null = festivo global.
     * Error 409 si ya existe ausencia para ese empleado en esa fecha.
     */
    @POST("ausencias")
    suspend fun crearAusencia(@Body request: AusenciaRequest): Response<AusenciaResponse>

    /**
     * E31 - Actualiza parcialmente una ausencia no procesada.
     * Error 409 si procesado=true (ya tiene fichaje generado).
     */
    @PATCH("ausencias/{id}")
    suspend fun actualizarAusencia(
        @Path("id") id: Long,
        @Body request: AusenciaPatchRequest
    ): Response<AusenciaResponse>

    /**
     * E32 - Elimina una ausencia no procesada.
     * Error 409 si procesado=true.
     * Solo disponible si procesado=false.
     */
    @DELETE("ausencias/{id}")
    suspend fun eliminarAusencia(@Path("id") id: Long): Response<MensajeResponse>

    /**
     * E33 - Lista ausencias con filtros opcionales.
     * @param empleadoId Filtra por empleado concreto. null = todas.
     * @param desde      Fecha inicio "yyyy-MM-dd"
     * @param hasta      Fecha fin "yyyy-MM-dd"
     * @param procesado  null = todas | true = procesadas | false = pendientes
     */
    @GET("ausencias")
    suspend fun listarAusencias(
        @Query("empleadoId") empleadoId: Long? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("procesado") procesado: Boolean? = null
    ): Response<List<AusenciaResponse>>

    /**
     * E34 - Devuelve las ausencias del empleado autenticado.
     * Solo accesible con rol EMPLEADO.
     * @param desde Fecha inicio "yyyy-MM-dd"
     * @param hasta Fecha fin "yyyy-MM-dd"
     */
    @GET("ausencias/me")
    suspend fun getMisAusencias(
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null
    ): Response<List<AusenciaResponse>>
}
