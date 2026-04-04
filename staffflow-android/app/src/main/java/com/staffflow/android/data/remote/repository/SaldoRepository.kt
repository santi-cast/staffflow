package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.SaldoApiService
import com.staffflow.android.data.remote.dto.SaldoResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de saldos anuales.
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
 *
 * Requiere JWT valido. El AuthInterceptor lo adjunta automaticamente.
 *
 * @param api Instancia de SaldoApiService creada por NetworkModule.retrofit.
 */
class SaldoRepository(private val api: SaldoApiService) {

    /**
     * E41 - Saldo anual del empleado autenticado.
     * P09 (MiSaldoFragment) llama a este metodo al cargar o cambiar el ano.
     * @param anio Año a consultar. null = año actual (el backend usa LocalDate.now()).
     */
    suspend fun getMiSaldo(anio: Int? = null): Result<SaldoResponse> =
        safeApiCall { api.getMiSaldo(anio) }
}
