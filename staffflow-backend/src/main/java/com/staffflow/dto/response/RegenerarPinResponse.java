package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta del endpoint E65 — POST /api/v1/empleados/{id}/regenerar-pin.
 *
 * Devuelve el PIN de terminal recién generado para el empleado.
 * El PIN se persiste en la columna {@code empleados.pin_terminal} (lo
 * usa el flujo de fichaje en {@code TerminalService}) y, tras la
 * regeneración, debe transmitirse al empleado por un canal humano
 * seguro: la regeneración produce un valor nuevo distinto del anterior
 * y queda fuera del alcance del propio empleado hasta que se le
 * comunique.
 *
 * Posteriormente, el PIN solo es consultable vía E15
 * (GET /api/v1/empleados/{id}) y solo si el llamante tiene rol ADMIN
 * (Opción A: ENCARGADO recibe {@code pinTerminal = null}).
 *
 * Campos:
 *   - empleadoId: ID del empleado al que pertenece el PIN regenerado.
 *   - pinTerminal: cadena de exactamente 4 dígitos numéricos generada
 *     de forma segura y persistida en BD.
 *
 * @author Santiago Castillo
 * @see com.staffflow.service.EmpleadoService#regenerarPin(Long)
 * @see com.staffflow.service.EmpleadoService#obtenerPorId(Long, org.springframework.security.core.Authentication)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegenerarPinResponse {

    /** ID del empleado cuyo PIN fue regenerado. */
    private Long empleadoId;

    /**
     * Nuevo PIN de terminal de 4 dígitos numéricos. Se persiste en
     * {@code empleados.pin_terminal}; posteriormente solo es consultable
     * por ADMIN vía E15 (Opción A).
     */
    private String pinTerminal;
}
