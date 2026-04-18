package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.FichajePatchRequest
import com.staffflow.android.data.remote.dto.FichajeRequest
import com.staffflow.android.data.remote.dto.FichajeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interfaz Retrofit para los endpoints de fichajes.
 *
 * Endpoints cubiertos:
 *   E22 POST /fichajes                    -> FichajeResponse 201  (ADMIN, ENCARGADO)
 *   E23 PATCH /fichajes/{id}              -> FichajeResponse      (ADMIN, ENCARGADO)
 *   E24 GET  /fichajes                    -> List<FichajeResponse> (ADMIN, ENCARGADO)
 *   E25 GET  /fichajes/incompletos        -> List<FichajeResponse> (ADMIN, ENCARGADO)
 *   E26 GET  /fichajes/me                 -> List<FichajeResponse> (EMPLEADO)
 *
 * Requiere JWT. El token lo adjunta AuthInterceptor en NetworkModule.
 * Las observaciones son OBLIGATORIAS en E22 y E23 (RNF-L02).
 * ENCARGADO solo puede gestionar fichajes del dia actual (D-026).
 */
interface FichajeApiService {

    /**
     * E22 - Crea un fichaje manual.
     * observaciones obligatorias (RNF-L02).
     * Error 404 si empleadoId no existe | 409 si ya existe fichaje ese dia.
     */
    @POST("fichajes")
    suspend fun crearFichaje(@Body request: FichajeRequest): Response<FichajeResponse>

    /**
     * E23 - Actualiza parcialmente un fichaje.
     * observaciones OBLIGATORIAS aunque sea PATCH (RNF-L02).
     * Error 404 si no existe | 409 conflicto de datos.
     */
    @PATCH("fichajes/{id}")
    suspend fun actualizarFichaje(
        @Path("id") id: Long,
        @Body request: FichajePatchRequest
    ): Response<FichajeResponse>

    /**
     * E24 - Lista fichajes con filtros opcionales.
     * @param empleadoId Filtra por empleado concreto
     * @param desde      Fecha inicio "yyyy-MM-dd"
     * @param hasta      Fecha fin "yyyy-MM-dd"
     * @param tipo       Valor del enum TipoFichaje
     */
    @GET("fichajes")
    suspend fun listarFichajes(
        @Query("empleadoId") empleadoId: Long? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("tipo") tipo: String? = null
    ): Response<List<FichajeResponse>>

    /**
     * E25 - Lista fichajes incompletos (sin hora de salida).
     * @param fecha Fecha a consultar "yyyy-MM-dd". null = hoy.
     */
    @GET("fichajes/incompletos")
    suspend fun listarIncompletos(
        @Query("fecha") fecha: String? = null
    ): Response<List<FichajeResponse>>

    /**
     * E26 - Devuelve los fichajes del empleado autenticado.
     * Solo accesible con rol EMPLEADO.
     * @param desde Fecha inicio "yyyy-MM-dd"
     * @param hasta Fecha fin "yyyy-MM-dd"
     * @param tipo  Valor del enum TipoFichaje
     */
    @GET("fichajes/me")
    suspend fun getMisFichajes(
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("tipo") tipo: String? = null
    ): Response<List<FichajeResponse>>
}
