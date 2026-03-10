package com.staffflow.dto.response;

import com.staffflow.domain.enums.TipoFichaje;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representación de un fichaje diario de un empleado.
 * Usado en E22 (POST), E23 (GET lista), E24 (GET por empleado),
 * E25 (GET por id) y E26 (PATCH /api/v1/fichajes).
 * ADMIN y ENCARGADO acceden a todos los fichajes.
 * EMPLEADO solo puede consultar los suyos (decisión nº10 y nº14).
 *
 * Los fichajes son inmutables tras su creación salvo corrección
 * manual con observaciones obligatorias (RNF-L01, RD-ley 8/2019).
 * Sin DELETE físico bajo ningún concepto (decisión nº1).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FichajeResponse {

    private Long id;

    // ID del empleado. Se devuelve el ID, no el objeto completo,
    // para mantener los DTOs planos y evitar referencias circulares.
    private Long empleadoId;

    private LocalDate fecha;

    // Determina cómo se contabiliza el día en SaldoAnual (RF-35, RF-36).
    private TipoFichaje tipo;

    // NULL en ausencias planificadas (VACACIONES, BAJA_MEDICA...).
    // NULL si la jornada aún no ha comenzado (decisión nº7).
    private LocalDateTime horaEntrada;

    // NULL si la jornada sigue en curso (fichaje abierto).
    private LocalDateTime horaSalida;

    // Suma de duracionMinutos de todas las pausas del día.
    // Se actualiza al cerrar cada pausa (E28).
    private Integer totalPausasMinutos;

    // (horaSalida - horaEntrada) - totalPausasMinutos. Math.floor.
    // 0 si la jornada no tiene horas registradas (ausencias).
    private Integer jornadaEfectivaMinutos;

    // ID del usuario que registró o modificó el fichaje (RNF-L01).
    private Long usuarioId;

    // Obligatorio en correcciones manuales para garantizar trazabilidad.
    private String observaciones;

    private LocalDateTime fechaCreacion;
}