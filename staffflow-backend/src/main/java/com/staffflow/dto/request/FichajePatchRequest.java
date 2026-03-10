package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoFichaje;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Campos modificables de un fichaje existente mediante actualización parcial.
 * Usado en E26 (PATCH /api/v1/fichajes/{id}), accesible por ADMIN y ENCARGADO.
 * Todos los campos son opcionales: el servicio solo actualiza los que
 * lleguen con valor no null (patrón PATCH, decisión nº3).
 * empleadoId y fecha no son modificables: identifican unívocamente
 * el fichaje y cambiarlos violaría la trazabilidad (RNF-L01, RD-ley 8/2019).
 *
 * @author Santiago Castillo
 */
@Data
public class FichajePatchRequest {

    // Cambio de tipo: recalcula el impacto en SaldoAnual (RF-35, RF-36).
    private TipoFichaje tipo;

    // Corrección manual de hora de entrada por ADMIN o ENCARGADO.
    private LocalDateTime horaEntrada;

    // Corrección manual de hora de salida por ADMIN o ENCARGADO.
    private LocalDateTime horaSalida;

    private String observaciones;
}