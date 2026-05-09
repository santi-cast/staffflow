package com.staffflow.android.data.remote.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Interfaz Retrofit para los endpoints de informes (ADMIN y ENCARGADO).
 *
 * Endpoints cubiertos:
 *   E42 GET /informes/horas/{empleadoId}?desde=&hasta=&formato=html
 *       -> ResponseBody (HTML para PrintManager + WebView)
 *   E43 GET /informes/horas?desde=&hasta=&formato=html
 *       -> ResponseBody (HTML informe global para WebView)
 *   E44 GET /informes/saldos?anio=&formato=html
 *       -> ResponseBody (HTML informe saldos para WebView)
 *   E45 GET /informes/pdf/horas/{empleadoId}?desde=&hasta=
 *       -> ResponseBody (PDF byte[] -> Intent ACTION_VIEW)
 *   E46 GET /informes/pdf/horas?desde=&hasta=
 *       -> ResponseBody (PDF byte[] global)
 *   E47 GET /informes/pdf/saldos?anio=
 *       -> ResponseBody (PDF byte[] saldos anuales)
 *   E57 GET /informes/pdf/vacaciones?empleadoId=&anio=
 *       -> ResponseBody (PDF byte[] vacaciones de un empleado)
 *
 * Los endpoints PDF usan @Streaming para evitar cargar el binario completo
 * en memoria antes de escribirlo al fichero temporal (FileProvider).
 * Los HTML no son @Streaming porque necesitan leerse como String.
 *
 * Requiere JWT con rol ADMIN o ENCARGADO.
 */
interface InformeApiService {

    /**
     * E58 - Informe de horas del empleado autenticado en HTML.
     * Solo accesible con rol EMPLEADO. P10 (MisFichajesFragment) lo usa para
     * mostrar la vista de fichajes en WebView.
     */
    @GET("informes/me/horas")
    suspend fun getMisHorasHtml(
        @Query("desde") desde: String,
        @Query("hasta") hasta: String
    ): Response<ResponseBody>

    /**
     * E42 - Informe de horas de un empleado en HTML (para PrintManager y WebView).
     * El parametro formato=html es obligatorio aqui (el defecto del backend es json).
     */
    @GET("informes/horas/{empleadoId}")
    suspend fun getInformeHorasHtml(
        @Path("empleadoId") empleadoId: Long,
        @Query("desde") desde: String,
        @Query("hasta") hasta: String,
        @Query("formato") formato: String = "html"
    ): Response<ResponseBody>

    /**
     * E43 - Informe de horas global de todos los empleados en HTML (para WebView).
     */
    @GET("informes/horas")
    suspend fun getInformeHorasGlobalHtml(
        @Query("desde") desde: String,
        @Query("hasta") hasta: String,
        @Query("formato") formato: String = "html"
    ): Response<ResponseBody>

    /**
     * E44 - Informe de saldos anuales en HTML (para WebView).
     * Incluye vacaciones, asuntos propios y horas de todos los empleados activos.
     */
    @GET("informes/saldos")
    suspend fun getInformeSaldosHtml(
        @Query("anio") anio: Int,
        @Query("formato") formato: String = "html"
    ): Response<ResponseBody>

    /**
     * E45 - PDF del informe de horas de un empleado en un periodo.
     * Abre con Intent ACTION_VIEW via FileProvider.
     */
    @Streaming
    @GET("informes/pdf/horas/{empleadoId}")
    suspend fun getPdfHorasEmpleado(
        @Path("empleadoId") empleadoId: Long,
        @Query("desde") desde: String,
        @Query("hasta") hasta: String
    ): Response<ResponseBody>

    /**
     * E46 - PDF del informe de horas global de todos los empleados.
     */
    @Streaming
    @GET("informes/pdf/horas")
    suspend fun getPdfHorasGlobal(
        @Query("desde") desde: String,
        @Query("hasta") hasta: String
    ): Response<ResponseBody>

    /**
     * E47 - PDF del informe de saldos anuales.
     */
    @Streaming
    @GET("informes/pdf/saldos")
    suspend fun getPdfSaldos(@Query("anio") anio: Int): Response<ResponseBody>

    /**
     * E57 - PDF del informe de vacaciones de un empleado en un anno.
     */
    @Streaming
    @GET("informes/pdf/vacaciones")
    suspend fun getPdfVacaciones(
        @Query("empleadoId") empleadoId: Long,
        @Query("anio") anio: Int
    ): Response<ResponseBody>

    /**
     * E59 - Tabla HTML semanal con fichajes, pausas y ausencias de todos los empleados.
     * ResumenSemanalFragment la carga en WebView con intercepciones staffflow://.
     */
    @GET("informes/semana")
    suspend fun getInformeSemana(
        @Query("desde") desde: String,
        @Query("hasta") hasta: String
    ): Response<ResponseBody>

    /**
     * E60 - Tabla HTML de ausencias de todos los empleados en un rango.
     * AusenciasFragment la carga en WebView con intercepciones staffflow://.
     */
    @GET("informes/ausencias")
    suspend fun getInformeAusenciasGlobal(
        @Query("desde") desde: String,
        @Query("hasta") hasta: String
    ): Response<ResponseBody>
}
