package com.staffflow.service;

import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.dto.request.TerminalPausaRequest;
import com.staffflow.dto.request.TerminalPinRequest;
import com.staffflow.dto.response.TerminalEntradaResponse;
import com.staffflow.dto.response.TerminalPausaFinResponse;
import com.staffflow.dto.response.TerminalPausaInicioResponse;
import com.staffflow.dto.response.TerminalSalidaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Servicio del terminal físico de fichaje por PIN.
 *
 * <p>Cubre los endpoints E48-E51 (Grupo 12). No usa JWT: la autenticación
 * se realiza exclusivamente por PIN de 4 dígitos. La búsqueda del empleado
 * por PIN usa el índice UNIQUE pin_terminal para cumplir RNF-R03 (&lt;100ms).</p>
 *
 * <p>El bloqueo por dispositivo se gestiona en este servicio: 5 intentos
 * fallidos consecutivos bloquean el terminal 30 segundos (RNF-S05,
 * decisión nº16). El bloqueo es por dispositivoId, no por empleado.</p>
 *
 * <p>Reglas de negocio del terminal:
 * <ul>
 *   <li>Entrada (E48): el empleado debe estar activo y no haber fichado hoy.</li>
 *   <li>Salida (E49): debe existir fichaje de entrada hoy y no haber pausa activa.</li>
 *   <li>Inicio pausa (E50): debe existir fichaje de entrada hoy y no haber pausa activa.</li>
 *   <li>Fin pausa (E51): debe existir pausa activa (horaFin = NULL) para hoy.</li>
 * </ul></p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.EmpleadoRepository
 * @see com.staffflow.domain.repository.FichajeRepository
 * @see com.staffflow.domain.repository.PausaRepository
 */
@Service
@RequiredArgsConstructor
public class TerminalService {

    private final EmpleadoRepository empleadoRepository;
    private final FichajeRepository fichajeRepository;
    private final PausaRepository pausaRepository;

    /**
     * Registra la entrada del empleado desde el terminal (E48).
     *
     * <p>Identifica al empleado por PIN. Verifica que está activo y que
     * no existe fichaje para hoy (UNIQUE empleado_id + fecha). Gestiona
     * el bloqueo por dispositivoId si el PIN es incorrecto (RNF-S05).</p>
     *
     * @param request      PIN y dispositivoId del terminal
     * @return confirmación de entrada con hora registrada y nombre del empleado
     */
    public TerminalEntradaResponse registrarEntrada(TerminalPinRequest request) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }

    /**
     * Registra la salida del empleado desde el terminal (E49).
     *
     * <p>Verifica que existe fichaje de entrada hoy y que no hay pausa
     * activa (horaFin = NULL). Si hay pausa activa devuelve HTTP 409
     * indicando que debe cerrarla primero. Calcula jornadaEfectivaMinutos
     * con Math.floor descontando las pausas no retribuidas.</p>
     *
     * @param request PIN y dispositivoId del terminal
     * @return confirmación de salida con hora y jornada efectiva en minutos
     */
    public TerminalSalidaResponse registrarSalida(TerminalPinRequest request) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }

    /**
     * Inicia una pausa desde el terminal (E50).
     *
     * <p>El empleado selecciona el tipo de pausa antes de introducir el PIN.
     * Verifica que existe fichaje de entrada hoy y que no hay otra pausa
     * activa. Crea la pausa con horaFin = NULL.</p>
     *
     * @param request PIN, tipo de pausa y dispositivoId del terminal
     * @return confirmación de inicio de pausa con hora y tipo registrados
     */
    public TerminalPausaInicioResponse iniciarPausa(TerminalPausaRequest request) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }

    /**
     * Cierra la pausa activa del empleado desde el terminal (E51).
     *
     * <p>Calcula duracionMinutos con Math.floor (redondeo a la baja,
     * beneficia al empleado). Si la pausa es no retribuida, actualiza
     * totalPausasMinutos del fichaje del día. Las pausas AUSENCIA_RETRIBUIDA
     * se contabilizan en horasAusenciaRetribuida del saldo anual.</p>
     *
     * @param request PIN y dispositivoId del terminal
     * @return confirmación de fin de pausa con duración en minutos
     */
    public TerminalPausaFinResponse finalizarPausa(TerminalPinRequest request) {
        // TODO: implementar en Bloque 5
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 5");
    }
}