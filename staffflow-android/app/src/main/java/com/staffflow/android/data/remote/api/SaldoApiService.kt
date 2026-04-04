package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.SaldoResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaz Retrofit para los endpoints de saldos anuales.
 *
 * Endpoints cubiertos:
 *   E41 GET /saldos/me?anio= -> SaldoResponse  (EMPLEADO)
 *
 * Requiere JWT. El token lo adjunta AuthInterceptor en NetworkModule.
 *
 * SaldoResponse incluye nested classes para vacaciones, asuntos propios
 * y horas. El campo saldoHoras puede ser negativo (deficit).
 * P09 muestra saldoHoras en verde si >= 0 y en rojo si < 0.
 */
interface SaldoApiService {

    /**
     * E41 - Saldo anual del empleado autenticado.
     * @param anio Año a consultar. Si es null el backend usa el año actual.
     */
    @GET("saldos/me")
    suspend fun getMiSaldo(@Query("anio") anio: Int? = null): Response<SaldoResponse>
}
