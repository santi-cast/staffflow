package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoPausa;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Datos para registrar manualmente una pausa dentro de la jornada de un empleado.
 * Usado en E27 (POST /api/v1/pausas), accesible por ADMIN y ENCARGADO.
 * Para iniciar o finalizar una pausa desde el terminal se usan
 * {@link TerminalPausaRequest} (E50, E51, decisión nº8).
 * Sin DELETE físico una vez creada (RNF-L01, decisión nº1).
 *
 * @author Santiago Castillo
 */
@Data
public class PausaRequest {

    @NotNull
    private Long empleadoId;

    @NotNull
    private LocalDate fecha;

    @NotNull
    private LocalDateTime horaInicio;

    // nullable: NULL = pausa activa en curso. Se rellena al cerrar la pausa (E51).
    // Si se registra manualmente una pausa ya cerrada, horaFin debe informarse.
    private LocalDateTime horaFin;

    // Determina cómo se contabiliza la pausa en SaldoAnual (RF-35).
    @NotNull
    private TipoPausa tipoPausa;

    private String observaciones;
}