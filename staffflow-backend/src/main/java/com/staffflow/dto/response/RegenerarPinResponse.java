package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta del endpoint E65 — POST /api/v1/empleados/{id}/regenerar-pin.
 *
 * Devuelve UNA SOLA VEZ el PIN de terminal regenerado para el empleado.
 * El valor {@code pinTerminal} debe entregarse al empleado en persona;
 * no se almacena ni se puede volver a consultar por API (decisión D-018).
 *
 * Campos:
 *   - empleadoId: ID del empleado al que pertenece el PIN regenerado.
 *   - pinTerminal: cadena de exactamente 4 dígitos numéricos generada
 *     de forma segura y persistida en BD.
 *
 * @author Santiago Castillo
 * @see com.staffflow.service.EmpleadoService#regenerarPin(Long)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegenerarPinResponse {

    /** ID del empleado cuyo PIN fue regenerado. */
    private Long empleadoId;

    /** Nuevo PIN de terminal de 4 dígitos numéricos. Devuelto una única vez. */
    private String pinTerminal;
}
