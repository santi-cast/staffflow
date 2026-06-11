package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.EmpleadoApiService
import com.staffflow.android.data.remote.dto.EmpleadoPatchRequest
import com.staffflow.android.data.remote.dto.EmpleadoRequest
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.data.remote.dto.RegenerarPinResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de empleados (E13-E18, E21, E65, E68).
 *
 * Todos los metodos son suspendibles y devuelven Result<T>. Los fallos
 * viajan como ApiException cuyo `error: ApiError` permite when exhaustivo
 * (ver util/ApiError.kt). ApiException.message preserva los mensajes
 * historicos para consumidores que aun leen el string crudo.
 *
 * Requiere JWT. La mayoria de endpoints son ADMIN o ENCARGADO; getMiPerfil (E21)
 * es EMPLEADO o ENCARGADO. El detalle de roles por endpoint vive en la cabecera
 * de EmpleadoApiService.
 * El AuthInterceptor adjunta el token automaticamente.
 *
 * @param api Instancia de EmpleadoApiService creada por NetworkModule.retrofit.
 */
class EmpleadoRepository(private val api: EmpleadoApiService) {

    /**
     * E13 - Crea un nuevo empleado.
     * P29 (FormUsuarioFragment) en modo alta combinada usuario + empleado
     * llama a este metodo tras crear el usuario via E08.
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
     * E17 - Desactiva al empleado (baja logica: activo=false).
     * P14 (DetalleEmpleadoFragment) llama a este metodo desde el boton
     * "Desactivar" tras confirmacion del usuario. Solo accesible a ADMIN.
     */
    suspend fun desactivar(id: Long): Result<MensajeResponse> =
        safeApiCall { api.desactivar(id) }

    /**
     * E18 - Activa al empleado (activo=true).
     * P14 (DetalleEmpleadoFragment) llama a este metodo desde el boton
     * "Activar" tras confirmacion del usuario. Solo accesible a ADMIN.
     */
    suspend fun activar(id: Long): Result<MensajeResponse> =
        safeApiCall { api.activar(id) }

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

    /**
     * E68 - Obtiene el empleado vinculado a un usuario dado por su usuarioId.
     * P29 (FormUsuarioFragment) lo llama en modo edición cuando el usuario
     * cargado tiene rol distinto de ADMIN, para mostrar la cabecera
     * read-only "Nombre Apellido1 Apellido2 (EMP-XXX)" y permitir saltar
     * a P14. Un 404 viaja como ApiError.NotFound y el ViewModel lo trata
     * como ausencia silenciosa de cabecera (caso típico: usuario ADMIN
     * sin empleado asociado).
     */
    suspend fun getEmpleadoPorUsuario(usuarioId: Long): Result<EmpleadoResponse> =
        safeApiCall { api.getEmpleadoPorUsuario(usuarioId) }
}
