package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta estándar del servidor ante cualquier error.
 * Generada por GlobalExceptionHandler para todos los códigos
 * de error HTTP: 400 (Bad Request), 401 (Unauthorized),
 * 403 (Forbidden), 404 (Not Found), 409 (Conflict),
 * 423 (Locked — bloqueo terminal por fuerza bruta, RNF-S05),
 * 500 (Internal Server Error).
 *
 * Centralizar el formato de error en un único DTO garantiza
 * que el cliente Android siempre recibe la misma estructura
 * independientemente del tipo de error, facilitando el manejo
 * genérico de errores en Retrofit (decisión de arquitectura).
 *
 * Alternativa descartada: devolver solo el mensaje como String.
 * Se prefiere ErrorResponse porque incluye el código HTTP y el
 * timestamp, facilitando el debugging en Swagger UI y los logs.
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    // Código HTTP numérico. Ejemplo: 404, 409, 423.
    // Se incluye en el cuerpo además de en la cabecera HTTP
    // para facilitar el manejo en el cliente Android.
    private Integer status;

    // Nombre estándar del error HTTP. Ejemplo: "Not Found", "Conflict".
    private String error;

    // Descripción legible del problema. Se muestra al usuario
    // o se registra en logs según el tipo de error.
    // Ejemplo: "No existe ningún empleado con id 42."
    private String mensaje;

    // Momento exacto en que se produjo el error.
    // Útil para correlacionar errores con logs del servidor.
    private LocalDateTime timestamp;
}