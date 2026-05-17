package com.staffflow.exception;

/**
 * Excepción lanzada cuando se intenta crear o actualizar un recurso
 * con un dato que viola una restricción de unicidad del dominio.
 *
 * <p>HTTP 409 Conflict: el estado actual del servidor impide completar
 * la petición porque el dato ya existe en otro registro.
 *
 * <p>Ejemplos de uso:
 *   - Username duplicado al crear usuario (E08)
 *   - Email duplicado al crear o editar usuario (E08, E11)
 *   - DNI duplicado al crear empleado (E13)
 *   - Código NFC duplicado al crear o editar empleado (E13, E16)
 *   - Solapamiento al crear o modificar fichaje (E22, E23)
 *   - Reactivar empleado que ya está activo (E18)
 *
 * <p>Se implementa como RuntimeException para no obligar a los
 * servicios a declarar throws en su firma — sigue el patrón
 * de excepciones no verificadas de Spring.
 *
 * <p>GlobalExceptionHandler captura esta excepción y devuelve
 * HTTP 409 con el mensaje descriptivo al cliente.
 *
 * @author Santiago Castillo
 */
public class ConflictException extends RuntimeException {

    /**
     * Crea la excepción con el mensaje que se enviará al cliente.
     *
     * @param mensaje descripción del conflicto (ej: "El email 'x@y.com' ya está registrado")
     */
    public ConflictException(String mensaje) {
        super(mensaje);
    }
}
