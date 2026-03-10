package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoPausa;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Campos modificables de una pausa existente mediante actualización parcial.
 * Usado en E29 (PATCH /api/v1/pausas/{id}), accesible por ADMIN y ENCARGADO.
 * Todos los campos son opcionales: el servicio solo actualiza los que
 * lleguen con valor no null (patrón PATCH, decisión nº3).
 * empleadoId y fecha no son modificables: identifican la pausa dentro
 * de la jornada y cambiarlos violaría la trazabilidad (RNF-L01, RD-ley 8/2019).
 *
 * @author Santiago Castillo
 */
@Data
public class PausaPatchRequest {

    // Corrección manual de hora de inicio por ADMIN o ENCARGADO.
    private LocalDateTime horaInicio;

    // Corrección manual de hora de fin. NULL = pausa activa en curso.
    private LocalDateTime horaFin;

    // Cambio de tipo: recalcula el impacto en SaldoAnual (RF-35).
    private TipoPausa tipoPausa;

    private String observaciones;
}