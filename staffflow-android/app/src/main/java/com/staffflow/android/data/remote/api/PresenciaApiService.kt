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
     * Endpoint preparado para v2.0 (ver M-047 en MEJORAS_V2.md): el cliente
     * tiene P18 (SinJustificarFragment) y su ViewModel listos para consumirlo,
     * pero P18 no es accesible desde la UI en v1.0. ADMIN y ENCARGADO.
     * @param fecha Fecha en formato "yyyy-MM-dd". Si es null el backend usa hoy.
     */
    @GET("presencia/sin-justificar")
    suspend fun getSinJustificar(@Query("fecha") fecha: String? = null): Response<List<SinJustificarResponse>>

    /**
     * E37 - Estado de presencia del empleado autenticado para una fecha.
     * Accesible con rol EMPLEADO o ENCARGADO (ambos pueden ser personas
     * fisicas con perfil de empleado). HTTP 403 para ADMIN.
     * P12 (MiHoyFragment) llama a este metodo al cargar y en onResume.
     * @param fecha Fecha en formato "yyyy-MM-dd". Si es null el backend usa hoy.
     */
    @GET("presencia/parte-diario/me")
    suspend fun getMiPresencia(@Query("fecha") fecha: String? = null): Response<DetallePresenciaResponse>
}
