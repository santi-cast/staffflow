package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.TerminalApiService
import com.staffflow.android.data.remote.dto.TerminalBloqueoResponse
import com.staffflow.android.data.remote.dto.TerminalEntradaResponse
import com.staffflow.android.data.remote.dto.TerminalEstadoResponse
import com.staffflow.android.data.remote.dto.TerminalPausaResponse
import com.staffflow.android.data.remote.dto.TerminalPinRequest
import com.staffflow.android.data.remote.dto.TerminalPausaRequest
import com.staffflow.android.data.remote.dto.TerminalSalidaResponse
import com.staffflow.android.util.safeApiCall

/**
 * Repositorio para los endpoints del terminal de fichaje (E48-E51).
 *
 * Todos los metodos son suspendibles y devuelven Result<T>.
 * El ViewModel consume Result.onSuccess / Result.onFailure.
 *
 * Los errores HTTP se parsean como ErrorResponse via Gson en safeApiCall.
 * Errores frecuentes:
 *   404 -> PIN no reconocido
 *   409 -> estado incompatible (ya ficho, pausa activa, sin entrada abierta)
 *   423 -> dispositivo bloqueado
 *
 * @param api Instancia de TerminalApiService creada por NetworkModule.retrofit.
 */
class TerminalRepository(private val api: TerminalApiService) {

    /**
     * E52 - Consulta el estado de la jornada del empleado para el dia actual.
     * Sin JWT. Llamado desde P06 antes de mostrar los botones de accion.
     */
    suspend fun obtenerEstado(request: TerminalPinRequest): Result<TerminalEstadoResponse> =
        safeApiCall { api.obtenerEstado(request) }

    /**
     * E48 - Registra la entrada de un empleado por PIN.
     * Sin JWT. P01 llama a este metodo al pulsar FICHAR ENTRADA.
     */
    suspend fun registrarEntrada(request: TerminalPinRequest): Result<TerminalEntradaResponse> =
        safeApiCall { api.registrarEntrada(request) }

    /**
     * E49 - Registra la salida de un empleado por PIN.
     * Sin JWT. P01 llama a este metodo al pulsar FICHAR SALIDA.
     */
    suspend fun registrarSalida(request: TerminalPinRequest): Result<TerminalSalidaResponse> =
        safeApiCall { api.registrarSalida(request) }

    /**
     * E50 - Inicia una pausa para el empleado identificado por PIN.
     * Sin JWT. El tipo de pausa lo selecciona el usuario en P07.
     * P01 llama a este metodo tras recibir el FragmentResult de P07.
     */
    suspend fun iniciarPausa(request: TerminalPausaRequest): Result<TerminalPausaResponse> =
        safeApiCall { api.iniciarPausa(request) }

    /**
     * E51 - Finaliza la pausa activa del empleado identificado por PIN.
     * Sin JWT. P01 llama a este metodo al pulsar FINALIZAR PAUSA.
     */
    suspend fun finalizarPausa(request: TerminalPinRequest): Result<TerminalPausaResponse> =
        safeApiCall { api.finalizarPausa(request) }

    /**
     * E53 - Consulta si hay algún dispositivo bloqueado por intentos fallidos de PIN.
     * Requiere JWT (ENCARGADO o ADMIN). Llamado desde ParteDiarioViewModel al cargar.
     */
    suspend fun hayTerminalBloqueado(): Result<TerminalBloqueoResponse> =
        safeApiCall { api.getBloqueo() }

    /**
     * E54 - Desbloquea el terminal reseteando todos los contadores de intentos fallidos.
     * Requiere JWT (ENCARGADO o ADMIN). Devuelve { "bloqueado": false } tras el reset.
     */
    suspend fun desbloquearTerminal(): Result<TerminalBloqueoResponse> =
        safeApiCall { api.deleteBloqueo() }
}
