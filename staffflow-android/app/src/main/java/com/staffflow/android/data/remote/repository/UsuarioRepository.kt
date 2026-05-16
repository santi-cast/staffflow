package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.UsuarioApiService
import com.staffflow.android.data.remote.dto.MensajeResponse
import com.staffflow.android.data.remote.dto.UsuarioPatchRequest
import com.staffflow.android.data.remote.dto.UsuarioRequest
import com.staffflow.android.data.remote.dto.UsuarioResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints de gestion de usuarios (E08-E12).
 *
 * Solo accesible con rol ADMIN.
 * Todos los metodos son suspendibles y devuelven Result<T>. Los fallos
 * viajan como ApiException cuyo `error: ApiError` permite when exhaustivo
 * (ver util/ApiError.kt). ApiException.message preserva los mensajes
 * historicos para consumidores que aun leen el string crudo.
 *
 * @param api Instancia de UsuarioApiService creada por NetworkModule.retrofit.
 */
class UsuarioRepository(private val api: UsuarioApiService) {

    /**
     * E08 - Crea un nuevo usuario del sistema.
     * P29 (FormUsuarioFragment) en modo alta llama a este metodo.
     */
    suspend fun crearUsuario(request: UsuarioRequest): Result<UsuarioResponse> =
        safeApiCall { api.crearUsuario(request) }

    /**
     * E09 - Lista usuarios con filtros opcionales.
     * P28 (UsuariosFragment) llama a este metodo al cargar.
     */
    suspend fun listarUsuarios(
        rol: String? = null,
        activo: Boolean? = null
    ): Result<List<UsuarioResponse>> =
        safeApiCall { api.listarUsuarios(rol, activo) }

    /**
     * E10 - Obtiene el detalle de un usuario concreto.
     * P29 en modo edicion llama a este metodo para pre-rellenar el formulario.
     */
    suspend fun obtenerUsuario(id: Long): Result<UsuarioResponse> =
        safeApiCall { api.obtenerUsuario(id) }

    /**
     * E11 - Actualiza parcialmente un usuario.
     * P29 en modo edicion llama a este metodo al guardar.
     */
    suspend fun actualizarUsuario(id: Long, request: UsuarioPatchRequest): Result<UsuarioResponse> =
        safeApiCall { api.actualizarUsuario(id, request) }

    /**
     * E12 - Desactiva un usuario (baja logica).
     * P29 (FormUsuarioFragment) con el boton Desactivar llama a este metodo.
     */
    suspend fun desactivarUsuario(id: Long): Result<MensajeResponse> =
        safeApiCall { api.desactivarUsuario(id) }
}
