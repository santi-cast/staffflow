package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta del terminal al iniciar (E50) o finalizar (E51) una pausa.
 *
 * En E50 (iniciar): horaInicioPausa tiene valor, duracionPausaMinutos es null.
 * En E51 (finalizar): horaInicioPausa es null, duracionPausaMinutos tiene valor.
 * El mismo DTO sirve para ambos endpoints — el campo no usado llega null
 * al cliente y la app Android ignora los campos null.
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalPausaResponse {

    /** Nombre del empleado, para confirmación visual en el terminal. */
    private String nombre;

    /**
     * Hora de inicio de la pausa. Solo se rellena en E50 (iniciar).
     * Null en E51 (finalizar).
     */
    private LocalDateTime horaInicioPausa;

    /**
     * Duración de la pausa en minutos. Solo se rellena en E51 (finalizar).
     * Calculado con Math.floor (beneficia al empleado).
     * Null en E50 (iniciar).
     */
    private Integer duracionPausaMinutos;

    /** Mensaje de confirmación para mostrar en pantalla del terminal. */
    private String mensaje;
}
