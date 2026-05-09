package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.EmpresaApiService
import com.staffflow.android.data.remote.dto.EmpresaRequest
import com.staffflow.android.data.remote.dto.EmpresaResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de configuracion de empresa (E06-E07).
 *
 * Solo accesible con rol ADMIN.
 * Todos los metodos son suspendibles y devuelven Result<T>.
 *
 * @param api Instancia de EmpresaApiService creada por NetworkModule.retrofit.
 */
class EmpresaRepository(private val api: EmpresaApiService) {

    /**
     * E06 - Obtiene la configuracion actual de la empresa.
     * P30 (EmpresaFragment) llama a este metodo al cargar.
     */
    suspend fun getEmpresa(): Result<EmpresaResponse> =
        safeApiCall { api.getEmpresa() }

    /**
     * E07 - Actualiza la configuracion de la empresa (PUT completo).
     * P30 (EmpresaFragment) llama a este metodo al guardar.
     */
    suspend fun actualizarEmpresa(request: EmpresaRequest): Result<EmpresaResponse> =
        safeApiCall { api.actualizarEmpresa(request) }
}
