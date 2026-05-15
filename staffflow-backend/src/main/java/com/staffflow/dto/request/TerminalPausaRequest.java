package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoPausa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PIN, tipo de pausa e identificador de dispositivo para E50.
 * Usado en E50 (POST /api/v1/terminal/pausa/iniciar), endpoint público
 * sin JWT porque el terminal no tiene sesión de usuario.
 *
 * El tipo de pausa determina cómo se contabiliza en SaldoAnual (RF-35):
 * las pausas AUSENCIA_RETRIBUIDA no descuentan de jornadaEfectivaMinutos.
 * El empleado selecciona el tipo en el teclado táctil antes de introducir
 * el PIN.
 *
 * El campo dispositivoId identifica el terminal físico para el bloqueo
 * por dispositivo (RNF-S05, D-021). Mismo mecanismo que TerminalPinRequest.
 *
 * Nota: E51 (finalizar pausa) usa TerminalPinRequest, no este DTO,
 * porque no necesita tipoPausa — el tipo se recupera de la pausa activa
 * ya almacenada en base de datos.
 *
 * @author Santiago Castillo
 */
@Data
public class TerminalPausaRequest {

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
     * Tipo de pausa seleccionado por el empleado antes de introducir el PIN.
     * Determina el impacto en SaldoAnual al cerrar la pausa (RF-35).
     * @NotNull permite cualquier valor del enum TipoPausa pero no null.
     */
    @NotNull
    private TipoPausa tipoPausa;

    /**
     * Identificador del dispositivo terminal desde el que se inicia la pausa.
     * Necesario para el bloqueo por dispositivo (RNF-S05, D-021).
     */
    @NotBlank
    private String dispositivoId;
}
