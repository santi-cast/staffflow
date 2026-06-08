package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.EmpleadoRequest
import com.staffflow.android.data.remote.dto.EmpleadoPatchRequest
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.data.remote.dto.RegenerarPinResponse
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
 *   E13 POST  /empleados                       -> EmpleadoResponse 201    (ADMIN, ENCARGADO)
 *   E14 GET   /empleados                       -> List<EmpleadoResponse>  (ADMIN, ENCARGADO)
 *   E15 GET   /empleados/{id}                  -> EmpleadoResponse        (ADMIN, ENCARGADO)
 *   E16 PATCH /empleados/{id}                  -> EmpleadoResponse        (ADMIN, ENCARGADO)
 *   E17 PATCH /empleados/{id}/baja             -> MensajeResponse         (ADMIN, ENCARGADO)
 *   E18 PATCH /empleados/{id}/reactivar        -> MensajeResponse         (ADMIN, ENCARGADO)
 *   E21 GET   /empleados/me                    -> EmpleadoResponse        (EMPLEADO)
 *   E65 POST  /empleados/{id}/regenerar-pin    -> RegenerarPinResponse    (ADMIN, ENCARGADO)
 *   E68 GET   /empleados/by-usuario/{usuarioId}-> EmpleadoResponse        (ADMIN)
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
     * E17 - PATCH /empleados/{id}/baja.
     * Desactiva al empleado (baja logica: pone activo=false). El backend
     * mantiene el termino "baja" en la URL por compatibilidad de la API
     * publicada; el cliente Android usa "desactivar" en UI por ser un
     * termino mas amplio que cubre excedencias, bajas medicas y permisos.
     * Error 404 si el empleado no existe.
     */
    @PATCH("empleados/{id}/baja")
    suspend fun desactivar(@Path("id") id: Long): Response<MensajeResponse>

    /**
     * E18 - PATCH /empleados/{id}/reactivar.
     * Activa al empleado (pone activo=true). Aplica a empleados que estaban
     * dados de baja previamente (excedencia finalizada, regreso de baja
     * medica, etc.).
     * Error 404 si el empleado no existe.
     * Error 409 si el empleado ya esta activo.
     */
    @PATCH("empleados/{id}/reactivar")
    suspend fun activar(@Path("id") id: Long): Response<MensajeResponse>

    /**
     * E65 - POST /empleados/{id}/regenerar-pin.
     * Regenera el PIN del empleado y lo devuelve en claro UNA sola vez.
     * Error 404 si el empleado no existe.
     */
    @POST("empleados/{id}/regenerar-pin")
    suspend fun regenerarPin(@Path("id") id: Long): Response<RegenerarPinResponse>

    /**
     * E21 - Devuelve el perfil del empleado autenticado.
     * Solo accesible con rol EMPLEADO. HTTP 403 para ADMIN y ENCARGADO.
     */
    @GET("empleados/me")
    suspend fun getMiPerfil(): Response<EmpleadoResponse>

    /**
     * E68 - GET /empleados/by-usuario/{usuarioId}.
     * Devuelve el empleado vinculado a un usuario dado (relación 1:1
     * empleado→usuario garantizada por UNIQUE sobre usuario_id).
     * Alimenta la cabecera read-only de P29 (FormUsuarioFragment).
     *
     * Solo accesible al rol ADMIN.
     * Error 404 si no existe empleado vinculado (caso esperado cuando
     * el usuario tiene rol ADMIN; el ViewModel lo trata como ausencia
     * silenciosa de cabecera).
     */
    @GET("empleados/by-usuario/{usuarioId}")
    suspend fun getEmpleadoPorUsuario(@Path("usuarioId") usuarioId: Long): Response<EmpleadoResponse>
}
