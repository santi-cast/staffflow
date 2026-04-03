package com.staffflow.dto.request;

import com.staffflow.domain.enums.TipoAusencia;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * Datos para planificar una ausencia o festivo.
 * Usado en E30 (POST /api/v1/ausencias), accesible por ADMIN y ENCARGADO.
 * Si empleadoId es null el registro se trata como festivo global:
 * el proceso nocturno generará un fichaje del tipo indicado para
 * todos los empleados activos ese día (RF-26, decisión nº7).
 * Solo se permite DELETE si procesado = false (decisión nº2).
 *
 * @author Santiago Castillo
 */
@Data
public class AusenciaRequest {

    // nullable: NULL = festivo global que aplica a todos los empleados activos.
    // Con valor = ausencia individual del empleado indicado (RF-26).
    private Long empleadoId;

    // Un registro por día. El modelo de rango fue descartado por complicar
    // innecesariamente el proceso nocturno (decisión nº7).
    @NotNull
    private LocalDate fecha;

    // Determina el tipo de fichaje que generará el proceso nocturno (RF-26).
    @NotNull
    private TipoAusencia tipoAusencia;

    private String observaciones;
}