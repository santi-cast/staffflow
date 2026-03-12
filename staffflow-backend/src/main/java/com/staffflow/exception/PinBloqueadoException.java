package com.staffflow.exception;

/**
 * Excepción lanzada cuando un dispositivo supera los 5 intentos
 * fallidos de PIN y queda bloqueado temporalmente (RNF-S05).
 *
 * <p>HTTP 423 Locked: el recurso (el terminal/dispositivo) está
 * bloqueado. El bloqueo dura 30 segundos y es por dispositivo,
 * no por empleado (decisión de diseño nº16).
 *
 * <p>GlobalExceptionHandler captura esta excepción y devuelve
 * la respuesta 423 con el mensaje correspondiente.
 *
 * <p>Se implementa como RuntimeException para no obligar a los
 * servicios a declarar throws en su firma — sigue el patrón
 * de excepciones no verificadas de Spring.
 *
 * @author Santiago Castillo
 */
public class PinBloqueadoException extends RuntimeException {

    /**
     * Crea la excepción con el mensaje que se enviará al cliente.
     *
     * @param mensaje descripción del bloqueo (ej: "Terminal bloqueado. Intente en 30 segundos")
     */
    public PinBloqueadoException(String mensaje) {
        super(mensaje);
    }
}
