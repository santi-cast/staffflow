package com.staffflow.exception;

/**
 * Excepción lanzada cuando un dispositivo supera los 5 intentos
 * fallidos de PIN y queda bloqueado hasta desbloqueo manual (RNF-S05).
 *
 * <p>HTTP 423 Locked: el recurso (el terminal/dispositivo) está
 * bloqueado. El bloqueo es por dispositivoId y persiste hasta que un ADMIN/ENCARGADO
 * lo libera vía E54 (DELETE /api/v1/terminal/bloqueo), un PIN exitoso reinicia el
 * contador o el servidor se reinicia. El bloqueo es por dispositivo, no por empleado.
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
     * @param mensaje descripción del bloqueo (ej: "Dispositivo bloqueado por 5 intentos fallidos de PIN. Contacta con el encargado")
     */
    public PinBloqueadoException(String mensaje) {
        super(mensaje);
    }
}
