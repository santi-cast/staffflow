package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta del servidor tras registrar la entrada de un empleado
 * desde el terminal compartido con PIN.
 * Usado en E48 (POST /api/v1/terminal/entrada), endpoint público sin JWT
 * porque el terminal no tiene sesión de usuario (decisión nº21).
 *
 * Los datos del empleado se incluyen para que el terminal muestre
 * una confirmación visual personalizada tras el fichaje,
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
public class TerminalEntradaResponse {

    private Long empleadoId;

    // Nombre y primer apellido para mostrar en la pantalla del terminal.
    // Solo se exponen los datos mínimos necesarios (RNF-S01).
    private String nombre;

    private String apellido1;

    // Hora exacta en que se registró la entrada.
    // Se muestra en el terminal como confirmación del fichaje.
    private LocalDateTime horaEntrada;

    // Mensaje de confirmación para mostrar en el terminal.
    // Ejemplo: "Bienvenido, Juan García. Entrada registrada a las 08:02."
    private String mensaje;
}