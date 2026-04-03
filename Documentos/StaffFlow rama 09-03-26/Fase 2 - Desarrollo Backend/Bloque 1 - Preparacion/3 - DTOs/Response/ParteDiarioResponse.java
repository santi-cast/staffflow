package com.staffflow.dto.response;

import com.staffflow.domain.enums.EstadoPresencia;
import com.staffflow.domain.enums.TipoFichaje;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representación del estado de presencia de un empleado en un momento dado.
 * Usado en E35 (GET /api/v1/presencia/hoy), E36 (GET por empleado)
 * y E37 (GET /api/v1/presencia/resumen).
 * Accesible por ADMIN y ENCARGADO (decisión nº14).
 *
 * El estado se calcula en tiempo real en PresenciaService consultando
 * fichajes, pausas y planificacion_ausencias para la fecha actual.
 * No se persiste en ninguna tabla (RF-30, RF-31).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParteDiarioResponse {

    private Long empleadoId;

    // Datos identificativos del empleado para mostrar en el parte
    // sin necesidad de una segunda petición al servidor.
    private String nombre;

    private String apellido1;

    // NULL si el empleado no tiene segundo apellido.
    private String apellido2;

    // Estado calculado en tiempo real. Determina el icono y color
    // que muestra Android en el panel de presencia (P32).
    private EstadoPresencia estado;

    // NULL si el empleado no ha iniciado jornada (AUSENCIA_PLANIFICADA,
    // AUSENCIA_REGISTRADA o SIN_JUSTIFICAR).
    private LocalDateTime horaEntrada;

    // NULL si la jornada sigue en curso o no ha comenzado.
    private LocalDateTime horaSalida;

    // true si el empleado tiene una pausa activa en este momento.
    // Complementa el estado EN_PAUSA para facilitar la lógica en Android.
    private Boolean pausaActiva;

    // NULL si no hay fichaje para hoy (AUSENCIA_PLANIFICADA, SIN_JUSTIFICAR).
    // Con valor: permite al ENCARGADO identificar el tipo de jornada
    // sin necesidad de consultar el fichaje completo.
    private TipoFichaje fichajeTipo;
}