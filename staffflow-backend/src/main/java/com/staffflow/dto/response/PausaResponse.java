package com.staffflow.dto.response;

import com.staffflow.domain.enums.TipoPausa;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representación de una pausa dentro de la jornada laboral de un empleado.
 * Usado en E27 (POST crear manual), E28 (PATCH cerrar/modificar)
 * y E29 (GET lista con filtros).
 * ADMIN y ENCARGADO acceden a todas las pausas.
 * EMPLEADO solo puede consultar las suyas.
 *
 * Una pausa activa se identifica por horaFin = NULL.
 * Sin DELETE físico bajo ningún concepto (RNF-L01).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PausaResponse {

    private Long id;

    // ID del empleado. Se devuelve el ID, no el objeto completo,
    // para mantener los DTOs planos y evitar referencias circulares.
    private Long empleadoId;

    private LocalDate fecha;

    private LocalDateTime horaInicio;

    // NULL = pausa activa en curso. Se rellena al finalizar (E51).
    // El cliente Android usa este campo para detectar si hay pausa activa.
    private LocalDateTime horaFin;

    // NULL hasta cerrar la pausa. Math.floor al calcular (beneficia al empleado).
    // Se usa para actualizar totalPausasMinutos en Fichaje (E28).
    private Integer duracionMinutos;

    // Determina cómo se contabiliza la pausa en SaldoAnual (RF-35).
    private TipoPausa tipoPausa;

    // ID del usuario que registró la pausa (RNF-L01).
    private Long usuarioId;

    private String observaciones;

    private LocalDateTime fechaCreacion;
}