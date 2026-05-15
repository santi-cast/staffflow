package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representación de un empleado sin justificación de presencia para un día dado.
 *
 * Usado en E36 (GET /api/v1/presencia/sin-justificar, RF-31).
 * Accesible por ADMIN y ENCARGADO.
 *
 * Un empleado aparece en esta lista cuando no tiene ningún registro
 * para la fecha indicada: ni fichaje, ni ausencia registrada en la
 * tabla fichajes, ni ausencia planificada en planificacion_ausencias
 * con procesado=false. Son los empleados que requieren atención
 * inmediata del encargado.
 *
 * Se devuelven solo los datos identificativos mínimos porque el
 * encargado solo necesita saber quién está sin justificar para
 * tomar acción, no el detalle completo del empleado.
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SinJustificarResponse {

    private Long empleadoId;

    private String nombre;

    private String apellido1;

    // NULL si el empleado no tiene segundo apellido.
    private String apellido2;
}
