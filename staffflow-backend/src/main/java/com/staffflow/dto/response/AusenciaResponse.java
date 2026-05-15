package com.staffflow.dto.response;

import com.staffflow.domain.enums.TipoAusencia;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representación de una ausencia planificada.
 * Usado en E30 (POST crear), E31 (PATCH modificar), E32 (DELETE eliminar),
 * E33 (GET lista con filtros) y E34 (GET /me ausencias propias).
 * ADMIN y ENCARGADO acceden a todas las ausencias.
 * EMPLEADO solo puede consultar las suyas.
 *
 * Si empleadoId es NULL el registro es un festivo global que
 * aplica a todos los empleados activos ese día (RF-26).
 * Solo se permite DELETE si procesado = false.
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

    // false = pendiente de procesar por ProcesoCierreDiario @Scheduled 23:55.
    // true = fichaje ya generado. Una vez procesado no se puede eliminar
    // ni modificar el tipo (RNF-L01).
    private Boolean procesado;

    // ID del usuario que planificó la ausencia (RNF-L01).
    private Long usuarioId;

    private String observaciones;

    private LocalDateTime fechaCreacion;
}