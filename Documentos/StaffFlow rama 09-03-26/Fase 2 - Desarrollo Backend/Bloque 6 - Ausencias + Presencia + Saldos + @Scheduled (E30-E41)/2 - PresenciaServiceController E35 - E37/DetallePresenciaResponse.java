package com.staffflow.dto.response;

import com.staffflow.domain.enums.EstadoPresencia;
import com.staffflow.domain.enums.TipoFichaje;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Detalle de presencia de un empleado concreto en un día dado.
 *
 * Usado como elemento del array {@code detalle[]} en
 * {@link ParteDiarioResponse} (E35 GET /api/v1/presencia/parte-diario)
 * y como respuesta directa en E37 (GET /api/v1/presencia/parte-diario/me).
 *
 * El estado se calcula en tiempo real en PresenciaService consultando
 * fichajes, pausas y planificacion_ausencias para la fecha indicada.
 * No se persiste en ninguna tabla: es un valor calculado (RF-30, RF-31, RF-54).
 *
 * Lógica de cálculo por orden de prioridad (EstadoPresencia):
 *   1. Pausa con horaFin = null        → EN_PAUSA
 *   2. Fichaje con horaSalida != null  → JORNADA_COMPLETADA
 *   3. Fichaje con horaSalida = null   → JORNADA_INICIADA
 *   4. Fichaje con tipo != NORMAL      → AUSENCIA_REGISTRADA
 *   5. Ausencia planificada procesado=false → AUSENCIA_PLANIFICADA
 *   6. Ninguno de los anteriores       → SIN_JUSTIFICAR
 *
 * Sustituto del ParteDiarioResponse anterior, renombrado para mayor
 * claridad semántica: este DTO es el detalle de un empleado individual,
 * no el parte completo (decisión de refactoring Bloque 6 Tarea 2).
 *
 * Nunca se expone la entidad directamente: siempre se mapea
 * a este DTO en la capa service (regla de arquitectura).
 *
 * @author Santiago Castillo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetallePresenciaResponse {

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
