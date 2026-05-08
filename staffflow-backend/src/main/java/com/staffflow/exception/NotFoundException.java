package com.staffflow.exception;

/**
 * Excepción lanzada cuando un recurso solicitado no existe en la base de datos.
 *
 * <p>Reemplaza el uso incorrecto de {@link IllegalStateException} como señal de
 * "entidad no encontrada". Separar semántica de estado inválido (500) de ausencia
 * de recurso (404) permite que el cliente distinga ambos casos sin ambigüedad.
 *
 * <p>HTTP 404 Not Found: el recurso identificado por el criterio de búsqueda
 * (id, username, PIN, etc.) no existe en la base de datos.
 *
 * <p>Ejemplos de uso:
 *   - Empleado no encontrado con id: 42 (E13, E14, E15, E16, E17, E18)
 *   - Ausencia no encontrada con id: 99 (E22, E23, E24, E25)
 *   - Fichaje no encontrado con id: 7 (E27, E28, E29)
 *   - Configuración de empresa no encontrada (E07)
 *   - Saldo anual no encontrado para empleado: 5, año: 2025 (E42, E43)
 *
 * <p>Se implementa como {@link RuntimeException} para no obligar a los servicios
 * a declarar {@code throws} en su firma — sigue el patrón de excepciones no
 * verificadas de Spring.
 *
 * <p>{@link GlobalExceptionHandler} captura esta excepción y devuelve HTTP 404
 * con el mensaje descriptivo al cliente.
 *
 * @see GlobalExceptionHandler
 * @see ConflictException
 * @author Santiago Castillo
 */
public class NotFoundException extends RuntimeException {

    /**
     * Crea la excepción con el mensaje que se enviará al cliente.
     *
     * @param mensaje descripción del recurso ausente, en español neutral
     *                (ej: "Empleado no encontrado con id: 42")
     */
    public NotFoundException(String mensaje) {
        super(mensaje);
    }
}
