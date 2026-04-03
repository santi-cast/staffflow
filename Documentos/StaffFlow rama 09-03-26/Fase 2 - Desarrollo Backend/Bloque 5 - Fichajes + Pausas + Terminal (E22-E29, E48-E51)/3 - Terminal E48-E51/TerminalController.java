package com.staffflow.controller;

import com.staffflow.dto.request.TerminalPausaRequest;
import com.staffflow.dto.request.TerminalPinRequest;
import com.staffflow.dto.response.TerminalEntradaResponse;
import com.staffflow.dto.response.TerminalPausaResponse;
import com.staffflow.dto.response.TerminalSalidaResponse;
import com.staffflow.service.TerminalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller del terminal de fichaje por PIN.
 *
 * Ruta base: /api/v1/terminal
 * Seguridad: SIN JWT. Todos los endpoints son PÚBLICOS (rol PÚBLICO).
 *   El terminal físico no tiene sesión de usuario. La autenticación se
 *   realiza exclusivamente por PIN de 4 dígitos (decisión nº21).
 *   La cadena de seguridad de Spring Security debe permitir estos
 *   endpoints sin autenticación (SecurityConfig).
 *
 * Endpoints implementados:
 *   E48 POST /api/v1/terminal/entrada         → registrar entrada  (RF-46)
 *   E49 POST /api/v1/terminal/salida          → registrar salida   (RF-47)
 *   E50 POST /api/v1/terminal/pausa/iniciar   → iniciar pausa      (RF-48)
 *   E51 POST /api/v1/terminal/pausa/finalizar → finalizar pausa    (RF-49)
 *
 * El bloqueo por dispositivo (RNF-S05) se gestiona en TerminalService:
 *   HTTP 423 tras 5 intentos fallidos de PIN desde el mismo dispositivoId.
 *
 * El controller no contiene lógica de negocio (RNF-M01).
 */
@Tag(name = "Terminal", description = "Fichaje por PIN desde terminal f\u00edsico. Sin JWT.")
@RestController
@RequestMapping("/api/v1/terminal")
@RequiredArgsConstructor
public class TerminalController {

    private final TerminalService terminalService;

    // ---------------------------------------------------------------
    // E48 — POST /api/v1/terminal/entrada
    // RF-46: Registrar entrada
    // ---------------------------------------------------------------

    /**
     * Registra la entrada del empleado desde el terminal físico (RF-46).
     *
     * El empleado introduce su PIN de 4 dígitos. El sistema busca al
     * empleado por PIN (índice UNIQUE, RNF-R03 < 100ms), verifica que
     * está activo y que no ha fichado ya hoy, y registra la entrada.
     *
     * Códigos HTTP:
     *   200 OK  → entrada registrada
     *   400     → empleado de baja o datos inválidos
     *   404     → PIN no encontrado
     *   409     → ya fichó hoy
     *   423     → dispositivo bloqueado (5 intentos fallidos)
     *
     * @param request pin + dispositivoId
     * @return nombre del empleado, hora de entrada y mensaje de confirmación
     */
    @Operation(summary = "Registrar entrada por PIN",
               description = "Endpoint p\u00fablico. Sin JWT. Registra la entrada desde terminal f\u00edsico.")
    @PostMapping("/entrada")
    public ResponseEntity<TerminalEntradaResponse> entrada(
            @Valid @RequestBody TerminalPinRequest request) {
        return ResponseEntity.ok(terminalService.registrarEntrada(request));
    }

    // ---------------------------------------------------------------
    // E49 — POST /api/v1/terminal/salida
    // RF-47: Registrar salida
    // ---------------------------------------------------------------

    /**
     * Registra la salida del empleado y calcula la jornada efectiva (RF-47).
     *
     * Verifica que no hay pausa activa antes de registrar la salida.
     * La jornada efectiva se calcula como Math.ceil(minutos_brutos - totalPausasMinutos).
     *
     * Códigos HTTP:
     *   200 OK  → salida registrada con jornada efectiva calculada
     *   400     → no hay entrada registrada hoy
     *   409     → ya hay salida registrada / pausa activa pendiente
     *   423     → dispositivo bloqueado
     *
     * @param request pin + dispositivoId
     * @return nombre, hora de salida, jornada efectiva y mensaje
     */
    @Operation(summary = "Registrar salida por PIN",
               description = "Endpoint p\u00fablico. Sin JWT. Registra la salida y calcula jornada efectiva.")
    @PostMapping("/salida")
    public ResponseEntity<TerminalSalidaResponse> salida(
            @Valid @RequestBody TerminalPinRequest request) {
        return ResponseEntity.ok(terminalService.registrarSalida(request));
    }

    // ---------------------------------------------------------------
    // E50 — POST /api/v1/terminal/pausa/iniciar
    // RF-48: Iniciar pausa
    // ---------------------------------------------------------------

    /**
     * Inicia una pausa desde el terminal físico (RF-48).
     *
     * El empleado selecciona el tipo de pausa en el teclado táctil antes
     * de introducir el PIN. Solo puede haber una pausa activa por empleado
     * por día.
     *
     * Códigos HTTP:
     *   200 OK  → pausa iniciada
     *   400     → no hay entrada registrada hoy
     *   409     → ya hay pausa activa
     *   423     → dispositivo bloqueado
     *
     * @param request pin + tipoPausa + dispositivoId
     * @return nombre, hora inicio de pausa y mensaje
     */
    @Operation(summary = "Iniciar pausa por PIN",
               description = "Endpoint p\u00fablico. Sin JWT. Inicia una pausa desde terminal f\u00edsico.")
    @PostMapping("/pausa/iniciar")
    public ResponseEntity<TerminalPausaResponse> iniciarPausa(
            @Valid @RequestBody TerminalPausaRequest request) {
        return ResponseEntity.ok(terminalService.iniciarPausa(request));
    }

    // ---------------------------------------------------------------
    // E51 — POST /api/v1/terminal/pausa/finalizar
    // RF-49: Finalizar pausa
    // ---------------------------------------------------------------

    /**
     * Finaliza la pausa activa del empleado desde el terminal (RF-49).
     *
     * Busca la pausa con horaFin null del empleado hoy. Calcula la
     * duración con Math.floor y actualiza totalPausasMinutos en el fichaje
     * si la pausa no es AUSENCIA_RETRIBUIDA.
     *
     * Códigos HTTP:
     *   200 OK  → pausa finalizada con duración calculada
     *   400     → no hay pausa activa para finalizar
     *   423     → dispositivo bloqueado
     *
     * @param request pin + dispositivoId
     * @return nombre, duración de la pausa y mensaje
     */
    @Operation(summary = "Finalizar pausa por PIN",
               description = "Endpoint p\u00fablico. Sin JWT. Finaliza la pausa activa desde terminal.")
    @PostMapping("/pausa/finalizar")
    public ResponseEntity<TerminalPausaResponse> finalizarPausa(
            @Valid @RequestBody TerminalPinRequest request) {
        return ResponseEntity.ok(terminalService.finalizarPausa(request));
    }
}
