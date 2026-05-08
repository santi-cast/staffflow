package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoAusencia;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * Datos para planificar un rango de ausencias consecutivas (E30b).
 *
 * Crea un registro de PlanificacionAusencia por cada día del rango
 * [fechaDesde, fechaHasta] inclusive. El modelo de datos no cambia
 * (un registro por día) — este DTO es una API de conveniencia.
 *
 * Si sobrescribir=false y algún día del rango ya tiene una ausencia
 * con procesado=false, el servicio devuelve HTTP 409 con la lista de
 * fechas conflictivas. Si sobrescribir=true, elimina los registros
 * existentes con procesado=false antes de crear los nuevos.
 *
 * @author Santiago Castillo
 */
@Data
public class AusenciaRangoRequest {

    private Long empleadoId;

    @NotNull
    private LocalDate fechaDesde;

    @NotNull
    private LocalDate fechaHasta;

    @NotNull
    private TipoAusencia tipoAusencia;

    private String observaciones;

    // false por defecto: pregunta antes de sobrescribir
    private boolean sobrescribir = false;
}
