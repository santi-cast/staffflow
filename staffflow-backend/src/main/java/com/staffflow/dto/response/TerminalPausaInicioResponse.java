package com.staffflow.dto.response;

import com.staffflow.domain.enums.TipoPausa;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta del servidor tras registrar el inicio de una pausa
 * desde el terminal compartido con PIN.
 * Usado en E50 (POST /api/v1/terminal/pausa/iniciar), endpoint público sin JWT
 * porque el terminal no tiene sesión de usuario (decisión nº21).
 *
 * La pausa queda activa (hora_fin = NULL) hasta que el empleado
 * la finalice con E51. El terminal puede detectar una pausa activa
 * comprobando WHERE hora_fin IS NULL (decisión nº1, D-004).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalPausaInicioResponse {

    private Long empleadoId;

    // Nombre y primer apellido para mostrar en la pantalla del terminal.
    // Solo se exponen los datos mínimos necesarios (RNF-S01).
    private String nombre;

    private String apellido1;

    // Hora exacta en que se registró el inicio de la pausa.
    private LocalDateTime horaInicio;

    // Tipo de pausa iniciada. Se muestra en el terminal como confirmación.
    private TipoPausa tipoPausa;

    // Mensaje de confirmación para mostrar en el terminal.
    // Ejemplo: "Pausa de comida iniciada a las 14:00, Juan García."
    private String mensaje;
}