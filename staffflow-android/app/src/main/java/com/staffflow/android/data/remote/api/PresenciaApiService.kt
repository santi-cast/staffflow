package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.ParteDiarioResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaz Retrofit para los endpoints de presencia.
 *
 * Endpoints cubiertos:
 *   E35 GET /presencia/parte-diario?fecha= -> ParteDiarioResponse  (ADMIN, ENCARGADO)
 *
 * Requiere JWT con rol ENCARGADO o ADMIN. El token lo adjunta AuthInterceptor.
 *
 * ParteDiarioResponse incluye los totales del dia (fichados, enPausa, ausencias,
 * sinJustificar) y la lista detalle con un DetallePresenciaResponse por empleado.
 * P17 usa los totales para los chips resumen y el detalle para el RecyclerView.
 */
interface PresenciaApiService {

    /**
     * E35 - Parte diario de presencia para una fecha concreta.
     * @param fecha Fecha en formato "yyyy-MM-dd". Si es null el backend usa hoy.
     */
    @GET("presencia/parte-diario")
    suspend fun getParteDiario(@Query("fecha") fecha: String? = null): Response<ParteDiarioResponse>
}
