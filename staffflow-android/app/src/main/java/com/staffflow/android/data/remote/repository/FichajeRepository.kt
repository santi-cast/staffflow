package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.FichajeApiService
import com.staffflow.android.data.remote.dto.FichajePatchRequest
import com.staffflow.android.data.remote.dto.FichajeRequest
import com.staffflow.android.data.remote.dto.FichajeResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de fichajes (E22-E25).
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
 *
 * Requiere JWT con rol ADMIN o ENCARGADO. El AuthInterceptor adjunta el token
 * automaticamente.
 *
 * @param api Instancia de FichajeApiService creada por NetworkModule.retrofit.
 */
class FichajeRepository(private val api: FichajeApiService) {

    /**
     * E22 - Crea un fichaje manual.
     * P20 (FormFichajeFragment) en variante fichaje llama a este metodo.
     */
    suspend fun crearFichaje(request: FichajeRequest): Result<FichajeResponse> =
        safeApiCall { api.crearFichaje(request) }

    /**
     * E23 - Actualiza parcialmente un fichaje.
     * P20 (FormFichajeFragment) en modo edicion variante fichaje llama a este metodo.
     */
    suspend fun actualizarFichaje(id: Long, request: FichajePatchRequest): Result<FichajeResponse> =
        safeApiCall { api.actualizarFichaje(id, request) }

    /**
     * E24 - Lista fichajes con filtros opcionales.
     * P16 (DetalleDiaFragment) lo usa para cargar los fichajes de un empleado en un dia concreto.
     * @param empleadoId null = todos los empleados
     * @param desde      Fecha inicio "yyyy-MM-dd"
     * @param hasta      Fecha fin "yyyy-MM-dd"
     * @param tipo       Nombre del enum TipoFichaje como String
     */
    suspend fun listarFichajes(
        empleadoId: Long? = null,
        desde: String? = null,
        hasta: String? = null,
        tipo: String? = null
    ): Result<List<FichajeResponse>> =
        safeApiCall { api.listarFichajes(empleadoId, desde, hasta, tipo) }

    /**
     * E25 - Lista fichajes incompletos (sin hora de salida).
     * Sin uso actual en el cliente; se conserva por simetria con el contrato del backend.
     * @param fecha null = hoy (backend usa su LocalDate.now())
     */
    suspend fun listarIncompletos(fecha: String? = null): Result<List<FichajeResponse>> =
        safeApiCall { api.listarIncompletos(fecha) }
}
