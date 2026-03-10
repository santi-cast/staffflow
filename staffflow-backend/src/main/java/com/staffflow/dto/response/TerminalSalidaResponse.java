package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta del servidor tras registrar la salida de un empleado
 * desde el terminal compartido con PIN.
 * Usado en E49 (POST /api/v1/terminal/salida), endpoint público sin JWT
 * porque el terminal no tiene sesión de usuario (decisión nº21).
 *
 * Se incluye la jornada efectiva para que el empleado pueda ver
 * en la pantalla del terminal cuánto tiempo ha trabajado ese día,
 * sin necesidad de una segunda petición al servidor.
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalSalidaResponse {

    private Long empleadoId;

    // Nombre y primer apellido para mostrar en la pantalla del terminal.
    // Solo se exponen los datos mínimos necesarios (RNF-S01).
    private String nombre;

    private String apellido1;

    // Hora exacta en que se registró la salida.
    private LocalDateTime horaSalida;

    // (horaSalida - horaEntrada) - totalPausasMinutos. Math.floor.
    // Se muestra en el terminal como resumen de la jornada completada.
    private Integer jornadaEfectivaMinutos;

    // Mensaje de confirmación para mostrar en el terminal.
    // Ejemplo: "Hasta mañana, Juan García. Jornada: 7h 58min."
    private String mensaje;
}