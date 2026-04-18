package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.EmpresaRequest
import com.staffflow.android.data.remote.dto.EmpresaResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * Interfaz Retrofit para los endpoints de configuracion de empresa (solo ADMIN).
 *
 * Endpoints cubiertos:
 *   E06 GET /empresa     -> EmpresaResponse (ADMIN)
 *   E07 PUT /empresa     -> EmpresaResponse (ADMIN)
 *
 * Singleton: siempre opera sobre el registro id=1.
 * Requiere JWT con rol ADMIN. El AuthInterceptor adjunta el token.
 * E07 envia el objeto completo (no PATCH).
 */
interface EmpresaApiService {

    /**
     * E06 - Obtiene la configuracion de la empresa.
     */
    @GET("empresa")
    suspend fun getEmpresa(): Response<EmpresaResponse>

    /**
     * E07 - Actualiza la configuracion de la empresa (PUT completo).
     * Error 409 si el CIF ya existe en otro registro.
     */
    @PUT("empresa")
    suspend fun actualizarEmpresa(@Body request: EmpresaRequest): Response<EmpresaResponse>
}
