package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta del terminal al iniciar (E50) o finalizar (E51) una pausa.
 *
 * En E50 (iniciar): horaInicioPausa tiene valor; horaFinPausa y duracionPausaMinutos son null.
 * En E51 (finalizar): los tres campos tienen valor (inicio, fin y duración).
 * El mismo DTO sirve para ambos endpoints — los campos no usados llegan null.
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalPausaResponse {

    /** Nombre del empleado, para confirmación visual en el terminal. */
    private String nombre;

    /** Hora de inicio de la pausa. Disponible en E50 y E51. */
    private LocalDateTime horaInicioPausa;

    /** Hora de fin de la pausa. Solo se rellena en E51 (finalizar). Null en E50. */
    private LocalDateTime horaFinPausa;

    /**
     * Duración de la pausa en minutos. Solo se rellena en E51 (finalizar).
     * Calculado con Math.floor (beneficia al empleado).
     * Null en E50 (iniciar).
     */
    private Integer duracionPausaMinutos;

    /** Mensaje de confirmación para mostrar en pantalla del terminal. */
    private String mensaje;
}
