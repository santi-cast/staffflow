package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.PresenciaApiService
import com.staffflow.android.data.remote.dto.DetallePresenciaResponse
import com.staffflow.android.data.remote.dto.ParteDiarioResponse
import com.staffflow.android.data.remote.dto.SinJustificarResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de presencia.
 *
 * Todos los metodos son suspendibles y devuelven Result<T>. Los fallos
 * viajan como ApiException cuyo `error: ApiError` permite when exhaustivo
 * (ver util/ApiError.kt). ApiException.message preserva los mensajes
 * historicos para consumidores que aun leen el string crudo.
 *
 * E35 requiere JWT con rol ENCARGADO o ADMIN.
 * E37 requiere JWT con rol EMPLEADO.
 * El AuthInterceptor adjunta el token automaticamente.
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

    /**
     * E36 - Empleados sin justificar para una fecha concreta.
     * P18 (SinJustificarFragment) llama a este metodo al cargar.
     * @param fecha Fecha en formato "yyyy-MM-dd". null = hoy.
     */
    suspend fun getSinJustificar(fecha: String? = null): Result<List<SinJustificarResponse>> =
        safeApiCall { api.getSinJustificar(fecha) }

    /**
     * E37 - Estado de presencia del empleado autenticado.
     * P12 (MiHoyFragment) llama a este metodo al cargar y en onResume.
     * @param fecha Fecha en formato "yyyy-MM-dd". null = hoy.
     */
    suspend fun getMiPresencia(fecha: String? = null): Result<DetallePresenciaResponse> =
        safeApiCall { api.getMiPresencia(fecha) }
}
