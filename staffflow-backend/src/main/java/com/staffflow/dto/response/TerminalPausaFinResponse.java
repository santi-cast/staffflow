package com.staffflow.dto.response;

import com.staffflow.domain.enums.TipoPausa;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta del servidor tras registrar el fin de una pausa
 * desde el terminal compartido con PIN.
 * Usado en E51 (POST /api/v1/terminal/pausa/finalizar), endpoint público sin JWT
 * porque el terminal no tiene sesión de usuario.
 *
 * Al finalizar la pausa el servicio calcula duracionMinutos con Math.floor
 * (redondeo a la baja, beneficia al empleado) y actualiza
 * totalPausasMinutos en el fichaje del día (E28, D-004).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalPausaFinResponse {

    private Long empleadoId;

    // Nombre y primer apellido para mostrar en la pantalla del terminal.
    // Solo se exponen los datos mínimos necesarios (RNF-S01).
    private String nombre;

    private String apellido1;

    // Hora exacta en que se registró el fin de la pausa.
    private LocalDateTime horaFin;

    // Duración calculada con Math.floor (minutos completos).
    // Se muestra en el terminal como resumen de la pausa completada.
    private Integer duracionMinutos;

    // Tipo de pausa finalizada. Se muestra en el terminal como confirmación.
    private TipoPausa tipoPausa;

    // Mensaje de confirmación para mostrar en el terminal.
    // Ejemplo: "Pausa finalizada. Duración: 32 min. Bienvenido de vuelta, Juan García."
    private String mensaje;
}