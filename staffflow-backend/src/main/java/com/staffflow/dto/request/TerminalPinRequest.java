package com.staffflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PIN introducido por el empleado en el terminal compartido.
 * Usado en E48 (POST /api/v1/terminal/entrada) y
 * E49 (POST /api/v1/terminal/salida), endpoints públicos sin JWT
 * porque el terminal no tiene sesión de usuario (decisión nº21).
 * Tras 5 intentos fallidos el dispositivo queda bloqueado 30 segundos:
 * el bloqueo es por dispositivo, no por empleado (RNF-S05, decisión nº16).
 * Devuelve HTTP 423 si el dispositivo está bloqueado.
 *
 * @author Santiago Castillo
 */
@Data
public class TerminalPinRequest {

    // Exactamente 4 dígitos numéricos. @Pattern refuerza @Size
    // descartando valores como "    " (4 espacios) que pasarían @Size.
    @NotBlank
    @Size(min = 4, max = 4)
    @Pattern(regexp = "\\d{4}")
    private String pin;
}