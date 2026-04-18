package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.data.remote.dto.UsuarioPatchRequest
import com.staffflow.android.data.remote.dto.UsuarioRequest
import com.staffflow.android.data.remote.dto.UsuarioResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interfaz Retrofit para los endpoints de gestion de usuarios (solo ADMIN).
 *
 * Endpoints cubiertos:
 *   E08 POST /usuarios              -> UsuarioResponse 201  (ADMIN)
 *   E09 GET  /usuarios?rol=&activo= -> List<UsuarioResponse> (ADMIN)
 *   E10 GET  /usuarios/{id}         -> UsuarioResponse       (ADMIN)
 *   E11 PATCH /usuarios/{id}        -> UsuarioResponse       (ADMIN)
 *   E12 DELETE /usuarios/{id}       -> MensajeResponse       (ADMIN, baja logica)
 *
 * Requiere JWT con rol ADMIN. El AuthInterceptor adjunta el token.
 * E12 es baja logica (activo=false), no DELETE fisico.
 */
interface UsuarioApiService {

    /**
     * E08 - Crea un nuevo usuario del sistema.
     * Error 409 si username o email ya existen.
     */
    @POST("usuarios")
    suspend fun crearUsuario(@Body request: UsuarioRequest): Response<UsuarioResponse>

    /**
     * E09 - Lista usuarios con filtros opcionales.
     * @param rol    Filtra por rol (ADMIN | ENCARGADO | EMPLEADO)
     * @param activo Filtra por estado activo/inactivo
     */
    @GET("usuarios")
    suspend fun listarUsuarios(
        @Query("rol") rol: String? = null,
        @Query("activo") activo: Boolean? = null
    ): Response<List<UsuarioResponse>>

    /**
     * E10 - Devuelve el detalle de un usuario concreto.
     * Error 404 si no existe.
     */
    @GET("usuarios/{id}")
    suspend fun obtenerUsuario(@Path("id") id: Long): Response<UsuarioResponse>

    /**
     * E11 - Actualiza parcialmente un usuario (email, rol, activo).
     * Error 409 si email o username ya existen en otro usuario.
     */
    @PATCH("usuarios/{id}")
    suspend fun actualizarUsuario(
        @Path("id") id: Long,
        @Body request: UsuarioPatchRequest
    ): Response<UsuarioResponse>

    /**
     * E12 - Desactiva un usuario (baja logica, activo=false).
     * No elimina el registro fisicamente.
     */
    @DELETE("usuarios/{id}")
    suspend fun desactivarUsuario(@Path("id") id: Long): Response<MensajeResponse>
}
