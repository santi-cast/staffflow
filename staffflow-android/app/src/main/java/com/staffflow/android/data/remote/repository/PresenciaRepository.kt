package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.PresenciaApiService
import com.staffflow.android.data.remote.dto.ParteDiarioResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de presencia.
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
 *
 * Requiere JWT con rol ENCARGADO o ADMIN. El AuthInterceptor lo adjunta automaticamente.
 *
 * @param api Instancia de PresenciaApiService creada por NetworkModule.retrofit.
 */
class PresenciaRepository(private val api: PresenciaApiService) {

    /**
     * E35 - Parte diario de presencia para una fecha concreta.
     * P17 (ParteDiarioFragment) llama a este metodo al cargar o cambiar la fecha.
     * @param fecha Fecha en formato "yyyy-MM-dd". null = hoy (el backend usa LocalDate.now()).
     */
    suspend fun getParteDiario(fecha: String? = null): Result<ParteDiarioResponse> =
        safeApiCall { api.getParteDiario(fecha) }
}
