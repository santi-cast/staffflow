package com.staffflow.dto.response;

import com.staffflow.domain.enums.EstadoTerminal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del terminal al consultar el estado del día de un empleado
 * (E52 POST /api/v1/terminal/estado).
 *
 * Mostrada en P06 (ConfirmacionFragment) antes de que el empleado
 * elija la accion a registrar. Permite personalizar el saludo y
 * mostrar el resumen de lo que ya tiene registrado hoy.
 *
 * Campos condicionales según el estado:
 *   SIN_ENTRADA     → horaEntrada, horaSalida, horaInicioPausa y tipoPausa son null
 *   EN_JORNADA      → horaEntrada disponible; el resto null
 *   EN_PAUSA        → horaEntrada y horaInicioPausa disponibles; tipoPausa disponible
 *   JORNADA_CERRADA → horaEntrada y horaSalida disponibles
 *
 * Las horas se devuelven formateadas como "HH:mm" para mostrar directamente.
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalEstadoResponse {

    /** Nombre del empleado para el saludo de bienvenida. */
    private String nombre;

    /** Estado actual de la jornada de hoy. */
    private EstadoTerminal estado;

    /** Hora de entrada registrada hoy en formato "HH:mm". Null si SIN_ENTRADA. */
    private String horaEntrada;

    /** Hora de salida registrada hoy en formato "HH:mm". Solo en JORNADA_CERRADA. */
    private String horaSalida;

    /** Hora de inicio de la pausa activa en formato "HH:mm". Solo en EN_PAUSA. */
    private String horaInicioPausa;

    /** Tipo de la pausa activa (nombre del enum). Solo en EN_PAUSA. */
    private String tipoPausa;
}
