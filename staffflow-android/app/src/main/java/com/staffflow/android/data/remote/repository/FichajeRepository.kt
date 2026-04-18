package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.FichajeApiService
import com.staffflow.android.data.remote.dto.FichajePatchRequest
import com.staffflow.android.data.remote.dto.FichajeRequest
import com.staffflow.android.data.remote.dto.FichajeResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de fichajes (E22-E26).
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
 *
 * Requiere JWT con rol ADMIN o ENCARGADO (excepto getMisFichajes que requiere EMPLEADO).
 * El AuthInterceptor adjunta el token automaticamente.
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
     * P19 (FichajesFragment) llama a este metodo al cargar y al filtrar.
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
     * P19 (FichajesFragment) puede mostrar incompletos del dia actual.
     * @param fecha null = hoy (backend usa su LocalDate.now())
     */
    suspend fun listarIncompletos(fecha: String? = null): Result<List<FichajeResponse>> =
        safeApiCall { api.listarIncompletos(fecha) }

    /**
     * E26 - Devuelve los fichajes del empleado autenticado.
     * P10 (MisFichajesFragment) llama a este metodo al cargar y al filtrar.
     */
    suspend fun getMisFichajes(
        desde: String? = null,
        hasta: String? = null,
        tipo: String? = null
    ): Result<List<FichajeResponse>> =
        safeApiCall { api.getMisFichajes(desde, hasta, tipo) }
}
