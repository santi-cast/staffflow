package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.data.remote.dto.SaldoResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interfaz Retrofit para los endpoints de saldos anuales.
 *
 * Endpoints cubiertos:
 *   E41 GET  /saldos/me              -> SaldoResponse        (EMPLEADO)
 *   E38 GET  /saldos/{id}            -> SaldoResponse        (ADMIN, ENCARGADO)
 *   E39 GET  /saldos                 -> List<SaldoResponse>  (ADMIN, ENCARGADO)
 *   E40 POST /saldos/{id}/recalcular -> MensajeResponse      (ADMIN, ENCARGADO)
 *
 * Requiere JWT. El token lo adjunta AuthInterceptor en NetworkModule.
 *
 * SaldoResponse incluye nested classes para vacaciones, asuntos propios
 * y horas. El campo saldoHoras puede ser negativo (deficit).
 * P09 y P26 muestran saldoHoras en verde si >= 0 y en rojo si < 0.
 */
interface SaldoApiService {

    /**
     * E41 - Saldo anual del empleado autenticado.
     * P09 (MiSaldoFragment) llama a este metodo al cargar o cambiar el año.
     * @param anio Año a consultar. Si es null el backend usa el año actual.
     */
    @GET("saldos/me")
    suspend fun getMiSaldo(@Query("anio") anio: Int? = null): Response<SaldoResponse>

    /**
     * E38 - Saldo anual de un empleado concreto.
     * P26 (SaldoIndividualFragment) llama a este metodo.
     * @param empleadoId Id del empleado a consultar.
     * @param anio       Año a consultar. Si es null el backend usa el año actual.
     */
    @GET("saldos/{empleadoId}")
    suspend fun getSaldoEmpleado(
        @Path("empleadoId") empleadoId: Long,
        @Query("anio") anio: Int? = null
    ): Response<SaldoResponse>

    /**
     * E39 - Saldos anuales de todos los empleados activos.
     * P27 (SaldosGlobalesFragment) llama a este metodo al cargar o cambiar el año.
     * @param anio Año a consultar. Si es null el backend usa el año actual.
     */
    @GET("saldos")
    suspend fun getSaldosGlobal(@Query("anio") anio: Int? = null): Response<List<SaldoResponse>>

    /**
     * E40 - Recalcula el saldo anual de un empleado concreto.
     * P26 (SaldoIndividualFragment) llama a este metodo desde el boton "Recalcular saldo".
     * @param empleadoId Id del empleado a recalcular.
     * @param anio       Año a recalcular.
     */
    @POST("saldos/{empleadoId}/recalcular")
    suspend fun recalcularSaldo(
        @Path("empleadoId") empleadoId: Long,
        @Query("anio") anio: Int
    ): Response<MensajeResponse>
}
