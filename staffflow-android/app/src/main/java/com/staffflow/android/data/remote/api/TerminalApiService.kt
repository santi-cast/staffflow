package com.staffflow.android.data.remote.api

import com.staffflow.android.data.remote.dto.TerminalBloqueoResponse
import com.staffflow.android.data.remote.dto.TerminalEntradaResponse
import com.staffflow.android.data.remote.dto.TerminalEstadoResponse
import com.staffflow.android.data.remote.dto.TerminalPausaResponse
import com.staffflow.android.data.remote.dto.TerminalPinRequest
import com.staffflow.android.data.remote.dto.TerminalPausaRequest
import com.staffflow.android.data.remote.dto.TerminalSalidaResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Interfaz Retrofit para los endpoints del terminal de fichaje.
 *
 * Todos los endpoints son PUBLICOS (sin JWT). La identificacion
 * del empleado se hace por PIN de 4 digitos.
 *
 * Endpoints cubiertos:
 *   E52 POST /terminal/estado          -> TerminalEstadoResponse (bienvenida P06)
 *   E48 POST /terminal/entrada         -> TerminalEntradaResponse
 *   E49 POST /terminal/salida          -> TerminalSalidaResponse
 *   E50 POST /terminal/pausa/iniciar   -> TerminalPausaResponse
 *   E51 POST /terminal/pausa/finalizar -> TerminalPausaResponse
 *
 * Errores comunes:
 *   404 PIN no reconocido
 *   409 operacion no permitida en el estado actual (ya ficho, pausa activa, etc.)
 *   423 dispositivo bloqueado por exceso de intentos fallidos
 */
interface TerminalApiService {

    /**
     * E52 - Consulta el estado de la jornada del empleado para el dia actual.
     * Sin JWT. Llamado desde P06 antes de mostrar los botones de accion.
     * Devuelve nombre y estado (SIN_ENTRADA / EN_JORNADA / EN_PAUSA / JORNADA_CERRADA).
     */
    @POST("terminal/estado")
    suspend fun obtenerEstado(@Body request: TerminalPinRequest): Response<TerminalEstadoResponse>

    /**
     * E48 - Registra la entrada de un empleado por PIN.
     * Sin JWT. Devuelve nombre, horaEntrada y mensaje de bienvenida.
     */
    @POST("terminal/entrada")
    suspend fun registrarEntrada(@Body request: TerminalPinRequest): Response<TerminalEntradaResponse>

    /**
     * E49 - Registra la salida de un empleado por PIN.
     * Sin JWT. Devuelve nombre, horaSalida y jornadaEfectivaMinutos.
     */
    @POST("terminal/salida")
    suspend fun registrarSalida(@Body request: TerminalPinRequest): Response<TerminalSalidaResponse>

    /**
     * E50 - Inicia una pausa para el empleado identificado por PIN.
     * Sin JWT. El tipo de pausa viene de P07 (TipoPausaFragment).
     * Devuelve TerminalPausaResponse con horaInicioPausa (duracionNull).
     */
    @POST("terminal/pausa/iniciar")
    suspend fun iniciarPausa(@Body request: TerminalPausaRequest): Response<TerminalPausaResponse>

    /**
     * E51 - Finaliza la pausa activa del empleado identificado por PIN.
     * Sin JWT. Devuelve TerminalPausaResponse con duracionPausaMinutos (horaInicioNull).
     */
    @POST("terminal/pausa/finalizar")
    suspend fun finalizarPausa(@Body request: TerminalPinRequest): Response<TerminalPausaResponse>

    /**
     * E53 - Consulta si hay algún dispositivo de terminal bloqueado por intentos fallidos.
     * Requiere JWT (ENCARGADO o ADMIN). Llamado desde ParteDiarioFragment al cargar.
     */
    @GET("terminal/bloqueo")
    suspend fun getBloqueo(): Response<TerminalBloqueoResponse>

    /**
     * E54 - Desbloquea el terminal reseteando todos los contadores de intentos fallidos.
     * Requiere JWT (ENCARGADO o ADMIN). Devuelve { "bloqueado": false } tras el reset.
     */
    @DELETE("terminal/bloqueo")
    suspend fun deleteBloqueo(): Response<TerminalBloqueoResponse>
}
