package com.staffflow.android.util

import com.google.gson.Gson
import com.staffflow.android.data.remote.dto.ErrorResponse
import retrofit2.Response
import java.io.IOException

/**
 * Ejecuta una llamada Retrofit de forma segura y devuelve Result<T>.
 *
 * Casos manejados:
 *   - Exito HTTP (2xx):      Result.success(body)
 *   - Error HTTP (4xx/5xx):  parsea ErrorResponse y devuelve Result.failure
 *                            con el campo "mensaje" del backend
 *   - Sin conexion (IOException): Result.failure("Sin conexion con el servidor")
 *   - Error inesperado:      Result.failure con la excepcion original
 *
 * Uso en Repository:
 *   suspend fun login(request: LoginRequest) = safeApiCall { api.login(request) }
 */
suspend fun <T> safeApiCall(call: suspend () -> Response<T>): Result<T> {
    return try {
        val response = call()
        if (response.isSuccessful) {
            Result.success(response.body()!!)
        } else {
            val errorBody = response.errorBody()?.string()
            val mensaje = try {
                Gson().fromJson(errorBody, ErrorResponse::class.java).error
                    ?: "Error ${response.code()}"
            } catch (e: Exception) {
                "Error ${response.code()}"
            }
            Result.failure(Exception(mensaje))
        }
    } catch (e: IOException) {
        Result.failure(Exception("Sin conexion con el servidor"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
