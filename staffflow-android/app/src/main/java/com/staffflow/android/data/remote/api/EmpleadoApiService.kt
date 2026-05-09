package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.EmpleadoRequest
import com.staffflow.android.data.remote.dto.EmpleadoPatchRequest
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.data.remote.dto.MensajeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interfaz Retrofit para los endpoints de empleados.
 *
 * Endpoints cubiertos:
 *   E13 POST /empleados              -> EmpleadoResponse 201  (ADMIN, ENCARGADO)
 *   E14 GET  /empleados              -> List<EmpleadoResponse> (ADMIN, ENCARGADO)
 *   E15 GET  /empleados/{id}         -> EmpleadoResponse      (ADMIN, ENCARGADO)
 *   E16 PATCH /empleados/{id}        -> EmpleadoResponse      (ADMIN, ENCARGADO)
 *   E17 PATCH /empleados/{id}/baja   -> MensajeResponse       (ADMIN, ENCARGADO)
 *   E18 PATCH /empleados/{id}/reactivar -> MensajeResponse    (ADMIN, ENCARGADO)
 *   E21 GET  /empleados/me           -> EmpleadoResponse      (EMPLEADO)
 *
 * Requiere JWT. El token lo adjunta AuthInterceptor en NetworkModule.
 * El PIN del empleado nunca se incluye en ninguna respuesta por seguridad.
 */
interface EmpleadoApiService {

    /**
     * E13 - Crea un nuevo empleado vinculado a un usuario existente.
     * Error 409 si DNI, numeroEmpleado o PIN ya existen.
     */
    @POST("empleados")
    suspend fun crearEmpleado(@Body request: EmpleadoRequest): Response<EmpleadoResponse>

    /**
     * E14 - Lista todos los empleados con filtros opcionales.
     * @param activo  null = todos | true = activos | false = dados de baja
     * @param q       Busqueda libre por nombre, apellidos o DNI
     * @param categoria Filtra por CategoriaEmpleado (OPERARIO, TECNICO, etc.)
     */
    @GET("empleados")
    suspend fun listarEmpleados(
        @Query("activo") activo: Boolean? = null,
        @Query("q") q: String? = null,
        @Query("categoria") categoria: String? = null
    ): Response<List<EmpleadoResponse>>

    /**
     * E15 - Obtiene el detalle de un empleado por su id.
     * Error 404 si no existe.
     */
    @GET("empleados/{id}")
    suspend fun getEmpleado(@Path("id") id: Long): Response<EmpleadoResponse>

    /**
     * E16 - Actualiza parcialmente un empleado (PATCH semantics).
     * Solo se envian los campos que se quieren modificar.
     * Error 404 si no existe | 409 si DNI o PIN duplicados.
     */
    @PATCH("empleados/{id}")
    suspend fun actualizarEmpleado(
        @Path("id") id: Long,
        @Body request: EmpleadoPatchRequest
    ): Response<EmpleadoResponse>

    /**
     * E21 - Devuelve el perfil del empleado autenticado.
     * Solo accesible con rol EMPLEADO. HTTP 403 para ADMIN y ENCARGADO.
     */
    @GET("empleados/me")
    suspend fun getMiPerfil(): Response<EmpleadoResponse>
}
