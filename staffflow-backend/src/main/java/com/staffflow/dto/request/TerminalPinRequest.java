package com.staffflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PIN e identificador de dispositivo introducidos en el terminal compartido.
 * Usado en cuatro endpoints públicos sin JWT (el terminal no tiene
 * sesión de usuario):
 *   - E48 POST /api/v1/terminal/entrada
 *   - E49 POST /api/v1/terminal/salida
 *   - E50 POST /api/v1/terminal/pausa/iniciar
 *   - E51 POST /api/v1/terminal/pausa/finalizar
 *
 * El campo dispositivoId identifica el terminal físico desde el que se
 * realiza la operación. Es necesario para el bloqueo por dispositivo
 * (RNF-S05): tras 5 intentos fallidos el dispositivo queda
 * bloqueado y devuelve HTTP 423 hasta que se reinicia el contador.
 * El bloqueo es por dispositivo, no por empleado.
 *
 * @author Santiago Castillo
 */
@Data
public class TerminalPinRequest {

    /**
     * PIN de 4 dígitos numéricos del empleado.
     * Exactamente 4 dígitos. @Pattern refuerza @Size descartando
     * valores como "    " (4 espacios) que pasarían @Size.
     */
    @NotBlank
    @Size(min = 4, max = 4)
    @Pattern(regexp = "\\d{4}")
    private String pin;

    /**
     * Identificador del dispositivo terminal desde el que se ficha.
     * Se usa para acumular intentos fallidos de PIN por dispositivo
     * (RNF-S05). Puede ser el ID de Android o cualquier identificador
     * único del dispositivo físico.
     */
    @NotBlank
    private String dispositivoId;
}
