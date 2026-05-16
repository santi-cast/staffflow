package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.EmpleadoApiService
import com.staffflow.android.data.remote.dto.EmpleadoPatchRequest
import com.staffflow.android.data.remote.dto.EmpleadoRequest
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.data.remote.dto.RegenerarPinResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de empleados (E13-E18, E21).
 *
 * Todos los metodos son suspendibles y devuelven Result<T>. Los fallos
 * viajan como ApiException cuyo `error: ApiError` permite when exhaustivo
 * (ver util/ApiError.kt). ApiException.message preserva los mensajes
 * historicos para consumidores que aun leen el string crudo.
 *
 * Requiere JWT con rol ADMIN o ENCARGADO (excepto getMiPerfil que requiere EMPLEADO).
 * El AuthInterceptor adjunta el token automaticamente.
 *
 * @param api Instancia de EmpleadoApiService creada por NetworkModule.retrofit.
 */
class EmpleadoRepository(private val api: EmpleadoApiService) {

    /**
     * E13 - Crea un nuevo empleado.
     * P15 (FormEmpleadoFragment) en modo alta llama a este metodo.
     */
    suspend fun crearEmpleado(request: EmpleadoRequest): Result<EmpleadoResponse> =
        safeApiCall { api.crearEmpleado(request) }

    /**
     * E14 - Lista empleados con filtros opcionales.
     * P13 (EmpleadosFragment) llama a este metodo al cargar y al buscar.
     * @param activo   null = todos | true = activos | false = dados de baja
     * @param q        Texto de busqueda libre (nombre, apellidos, DNI)
     * @param categoria Nombre del enum CategoriaEmpleado como String
     */
    suspend fun listarEmpleados(
        activo: Boolean? = null,
        q: String? = null,
        categoria: String? = null
    ): Result<List<EmpleadoResponse>> =
        safeApiCall { api.listarEmpleados(activo, q, categoria) }

    /**
     * E15 - Obtiene el detalle de un empleado.
     * P14 (DetalleEmpleadoFragment) llama a este metodo al cargar.
     */
    suspend fun getEmpleado(id: Long): Result<EmpleadoResponse> =
        safeApiCall { api.getEmpleado(id) }

    /**
     * E16 - Actualiza parcialmente un empleado.
     * P15 (FormEmpleadoFragment) en modo edicion llama a este metodo.
     */
    suspend fun actualizarEmpleado(id: Long, request: EmpleadoPatchRequest): Result<EmpleadoResponse> =
        safeApiCall { api.actualizarEmpleado(id, request) }

    /**
     * E65 - Regenera el PIN del empleado y devuelve el nuevo PIN en claro
     * (una sola vez). P14 (DetalleEmpleadoFragment) llama a este metodo
     * desde el chip "Regenerar PIN" tras confirmacion.
     */
    suspend fun regenerarPin(id: Long): Result<RegenerarPinResponse> =
        safeApiCall { api.regenerarPin(id) }

    /**
     * E21 - Devuelve el perfil del empleado autenticado.
     * P08 (MiPerfilFragment) llama a este metodo.
     */
    suspend fun getMiPerfil(): Result<EmpleadoResponse> =
        safeApiCall { api.getMiPerfil() }
}
