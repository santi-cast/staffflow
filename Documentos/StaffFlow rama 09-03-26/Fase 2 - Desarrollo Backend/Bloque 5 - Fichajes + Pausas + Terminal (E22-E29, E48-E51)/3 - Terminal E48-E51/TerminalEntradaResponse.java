package com.staffflow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Respuesta del terminal al registrar la entrada del empleado (E48).
 * Contiene los datos mínimos que el terminal muestra en pantalla:
 * nombre del empleado para confirmación visual y hora de entrada registrada.
 *
 * No expone empleadoId ni pinTerminal por seguridad: el terminal
 * solo necesita confirmar la acción al empleado presente físicamente.
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalEntradaResponse {

    /** Nombre del empleado, para confirmación visual en el terminal. */
    private String nombre;

    /** Hora en que se registró la entrada. */
    private LocalDateTime horaEntrada;

    /** Mensaje de confirmación para mostrar en pantalla del terminal. */
    private String mensaje;
}
