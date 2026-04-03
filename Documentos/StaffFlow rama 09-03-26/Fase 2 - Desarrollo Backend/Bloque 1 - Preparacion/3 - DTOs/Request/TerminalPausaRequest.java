package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoPausa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PIN y tipo de pausa introducidos por el empleado en el terminal compartido.
 * Usado en E50 (POST /api/v1/terminal/pausa/iniciar) y
 * E51 (POST /api/v1/terminal/pausa/finalizar), endpoints públicos sin JWT
 * porque el terminal no tiene sesión de usuario (decisión nº21).
 * El tipo de pausa determina cómo se contabiliza en SaldoAnual (RF-35).
 * Mismo mecanismo de bloqueo por dispositivo que {@link TerminalPinRequest}
 * (RNF-S05, decisión nº16).
 *
 * @author Santiago Castillo
 */
@Data
public class TerminalPausaRequest {

    // Exactamente 4 dígitos numéricos. @Pattern refuerza @Size
    // descartando valores como "    " (4 espacios) que pasarían @Size.
    @NotBlank
    @Size(min = 4, max = 4)
    @Pattern(regexp = "\\d{4}")
    private String pin;

    // @NotNull permite enviar cualquier valor del enum TipoPausa pero no null.
    // Determina el impacto en SaldoAnual al cerrar la pausa (RF-35).
    @NotNull
    private TipoPausa tipoPausa;
}