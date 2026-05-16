package com.staffflow.android.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Tests JUnit puros (sin Robolectric ni Android) para [mapToApiError] y
 * los mensajes legacy de [ApiException].
 *
 * Construimos respuestas Retrofit con [Response.error] y un cuerpo JSON
 * simulando exactamente las claves que devuelve GlobalExceptionHandler.
 */
class ApiErrorMapperTest {

    private val jsonMediaType = "application/json".toMediaType()

    private fun errorResponse(code: Int, body: String): Response<Any> {
        return Response.error(code, body.toResponseBody(jsonMediaType))
    }

    // ----- Excepciones de red -----

    @Test
    fun `IOException no timeout mapea a Network`() {
        val cause = IOException("conn refused")
        val result = mapToApiError(throwable = cause)
        assertTrue("Esperaba Network, fue $result", result is ApiError.Network)
        assertEquals(cause, (result as ApiError.Network).cause)
    }

    @Test
    fun `SocketTimeoutException mapea a Timeout`() {
        val result = mapToApiError(throwable = SocketTimeoutException("read timeout"))
        assertEquals(ApiError.Timeout, result)
    }

    // ----- HTTP errors -----

    @Test
    fun `401 con error mensaje mapea a Unauthorized`() {
        val r = errorResponse(401, """{"error":"Token invalido"}""")
        val result = mapToApiError(response = r)
        assertEquals(ApiError.Unauthorized("Token invalido"), result)
    }

    @Test
    fun `403 con error mensaje mapea a Forbidden`() {
        val r = errorResponse(403, """{"error":"Sin permisos"}""")
        val result = mapToApiError(response = r)
        assertEquals(ApiError.Forbidden("Sin permisos"), result)
    }

    @Test
    fun `404 con error mensaje mapea a NotFound`() {
        val r = errorResponse(404, """{"error":"No hay datos de saldo para 2026"}""")
        val result = mapToApiError(response = r)
        assertEquals(ApiError.NotFound("No hay datos de saldo para 2026"), result)
    }

    @Test
    fun `409 con fechasConflictivas mapea a RangoConflicto`() {
        val body = """{"error":"Conflicto","fechasConflictivas":["2026-05-15","2026-05-16"]}"""
        val r = errorResponse(409, body)
        val result = mapToApiError(response = r)
        assertTrue("Esperaba RangoConflicto, fue $result", result is ApiError.RangoConflicto)
        assertEquals(listOf("2026-05-15", "2026-05-16"), (result as ApiError.RangoConflicto).fechas)
    }

    @Test
    fun `409 sin fechasConflictivas mapea a Conflict generico con campo`() {
        val body = """{"error":"Email duplicado","campo":"email"}"""
        val r = errorResponse(409, body)
        val result = mapToApiError(response = r)
        assertEquals(ApiError.Conflict("Email duplicado", "email"), result)
    }

    @Test
    fun `423 mapea a PinBloqueado`() {
        val r = errorResponse(423, """{"error":"Dispositivo bloqueado"}""")
        val result = mapToApiError(response = r)
        assertEquals(ApiError.PinBloqueado("Dispositivo bloqueado"), result)
    }

    @Test
    fun `400 con campo mapea a Validation`() {
        val body = """{"error":"PIN debe tener 4 digitos","campo":"pin"}"""
        val r = errorResponse(400, body)
        val result = mapToApiError(response = r)
        assertEquals(ApiError.Validation("PIN debe tener 4 digitos", "pin"), result)
    }

    @Test
    fun `500 mapea a Server con codigo`() {
        val r = errorResponse(500, """{"error":"Error interno"}""")
        val result = mapToApiError(response = r)
        assertEquals(ApiError.Server(500, "Error interno"), result)
    }

    @Test
    fun `excepcion generica mapea a Unknown`() {
        val cause = RuntimeException("x")
        val result = mapToApiError(throwable = cause)
        assertTrue("Esperaba Unknown, fue $result", result is ApiError.Unknown)
        assertEquals(cause, (result as ApiError.Unknown).cause)
    }

    @Test
    fun `404 con body vacio mapea a NotFound con mensaje null`() {
        val r = errorResponse(404, "")
        val result = mapToApiError(response = r)
        assertTrue("Esperaba NotFound, fue $result", result is ApiError.NotFound)
        assertNull((result as ApiError.NotFound).mensaje)
    }

    // ----- Mensajes legacy de ApiException -----

    @Test
    fun `ApiException Network preserva mensaje legacy Sin conexion`() {
        val ex = ApiException(ApiError.Network(IOException()))
        assertEquals("Sin conexion con el servidor", ex.message)
    }

    @Test
    fun `ApiException NotFound preserva el mensaje original`() {
        val ex = ApiException(ApiError.NotFound("No hay datos de saldo X"))
        assertEquals("No hay datos de saldo X", ex.message)
    }

    @Test
    fun `ApiException RangoConflicto produce mensaje legacy Conflicto en rango`() {
        val ex = ApiException(ApiError.RangoConflicto(listOf("2026-05-15")))
        assertEquals("Conflicto en rango", ex.message)
    }
}
