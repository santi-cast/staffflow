package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.DetallePresenciaResponse
import com.staffflow.android.data.remote.dto.ParteDiarioResponse
import com.staffflow.android.data.remote.dto.SinJustificarResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaz Retrofit para los endpoints de presencia.
 *
 * Endpoints cubiertos:
 *   E35 GET /presencia/parte-diario        -> ParteDiarioResponse       (ADMIN, ENCARGADO)
 *   E36 GET /presencia/sin-justificar      -> List<SinJustificarResponse>(ADMIN, ENCARGADO)
 *   E37 GET /presencia/parte-diario/me     -> DetallePresenciaResponse   (EMPLEADO)
 *
 * Requiere JWT. El token lo adjunta AuthInterceptor en NetworkModule.
 */
interface PresenciaApiService {

    /**
     * E35 - Parte diario de presencia para una fecha concreta.
     * @param fecha Fecha en formato "yyyy-MM-dd". Si es null el backend usa hoy.
     */
    @GET("presencia/parte-diario")
    suspend fun getParteDiario(@Query("fecha") fecha: String? = null): Response<ParteDiarioResponse>

    /**
     * E36 - Empleados sin justificar para una fecha concreta.
     * P18 (SinJustificarFragment) usa este endpoint. ADMIN y ENCARGADO.
     * @param fecha Fecha en formato "yyyy-MM-dd". Si es null el backend usa hoy.
     */
    @GET("presencia/sin-justificar")
    suspend fun getSinJustificar(@Query("fecha") fecha: String? = null): Response<List<SinJustificarResponse>>

    /**
     * E37 - Estado de presencia del empleado autenticado para una fecha.
     * Solo accesible con rol EMPLEADO. HTTP 403 para ADMIN y ENCARGADO.
     * P12 (MiHoyFragment) llama a este metodo al cargar y en onResume.
     * @param fecha Fecha en formato "yyyy-MM-dd". Si es null el backend usa hoy.
     */
    @GET("presencia/parte-diario/me")
    suspend fun getMiPresencia(@Query("fecha") fecha: String? = null): Response<DetallePresenciaResponse>
}
