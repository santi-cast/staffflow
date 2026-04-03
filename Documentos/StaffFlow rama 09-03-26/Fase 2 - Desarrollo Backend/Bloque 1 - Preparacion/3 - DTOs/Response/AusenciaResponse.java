package com.staffflow.dto.response;

import com.staffflow.domain.enums.TipoAusencia;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representación de una ausencia planificada.
 * Usado en E30 (POST), E31 (GET lista), E32 (GET por empleado),
 * E33 (GET por id) y E34 (PATCH /api/v1/ausencias).
 * ADMIN y ENCARGADO acceden a todas las ausencias.
 * EMPLEADO solo puede consultar las suyas (decisión nº10 y nº14).
 *
 * Si empleadoId es NULL el registro es un festivo global que
 * aplica a todos los empleados activos ese día (RF-26, decisión nº7).
 * Solo se permite DELETE si procesado = false (decisión nº2).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AusenciaResponse {

    private Long id;

    // NULL = festivo global que aplica a todos los empleados activos.
    // Con valor = ausencia individual del empleado indicado (RF-26).
    private Long empleadoId;

    private LocalDate fecha;

    // Determina el tipo de fichaje que generará el proceso nocturno (RF-26).
    private TipoAusencia tipoAusencia;

    // false = pendiente de procesar por ProcesoDiario @Scheduled 00:01.
    // true = fichaje ya generado. Una vez procesado no se puede eliminar
    // ni modificar el tipo (decisión nº2, RNF-L01).
    private Boolean procesado;

    // ID del usuario que planificó la ausencia (RNF-L01).
    private Long usuarioId;

    private String observaciones;

    private LocalDateTime fechaCreacion;
}