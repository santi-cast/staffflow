package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta del terminal al registrar la salida del empleado (E49).
 * Incluye el resumen completo de la jornada para que el empleado pueda
 * verificar entrada, salida, pausas y tiempo trabajado antes de irse.
 *
 * jornadaEfectivaMinutos = Math.ceil(minutos_brutos - totalPausasMinutos).
 * El cálculo se realiza en TerminalService al registrar la salida.
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalSalidaResponse {

    /** Nombre del empleado, para confirmación visual en el terminal. */
    private String nombre;

    /** Hora de entrada registrada hoy. */
    private LocalDateTime horaEntrada;

    /** Hora en que se registró la salida. */
    private LocalDateTime horaSalida;

    /** Total de minutos en pausa durante la jornada (sin contar ausencia retribuida). */
    private Integer totalPausasMinutos;

    /** Número de pausas registradas en el día. */
    private Integer numeroPausas;

    /**
     * Minutos efectivos trabajados en la jornada.
     * Calculado como Math.ceil(horaSalida - horaEntrada en minutos - totalPausasMinutos).
     */
    private Integer jornadaEfectivaMinutos;

    /** Mensaje de confirmación para mostrar en pantalla del terminal. */
    private String mensaje;
}
