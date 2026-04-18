package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.SaldoApiService
import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.data.remote.dto.SaldoResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de saldos anuales.
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
 *
 * E41 requiere JWT con rol EMPLEADO.
 * E38 y E39 requieren JWT con rol ADMIN o ENCARGADO.
 * El AuthInterceptor adjunta el token automaticamente.
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

    /**
     * E38 - Saldo anual de un empleado concreto.
     * P26 (SaldoIndividualFragment) llama a este metodo al cargar o cambiar el ano.
     */
    suspend fun getSaldoEmpleado(empleadoId: Long, anio: Int? = null): Result<SaldoResponse> =
        safeApiCall { api.getSaldoEmpleado(empleadoId, anio) }

    /**
     * E39 - Saldos anuales de todos los empleados activos.
     * P27 (SaldosGlobalesFragment) llama a este metodo al cargar o cambiar el ano.
     */
    suspend fun getSaldosGlobal(anio: Int? = null): Result<List<SaldoResponse>> =
        safeApiCall { api.getSaldosGlobal(anio) }

    /**
     * E40 - Recalcula el saldo anual de un empleado concreto.
     * P26 (SaldoIndividualFragment) llama a este metodo desde el boton "Recalcular saldo".
     */
    suspend fun recalcularSaldo(empleadoId: Long, anio: Int): Result<MensajeResponse> =
        safeApiCall { api.recalcularSaldo(empleadoId, anio) }
}
