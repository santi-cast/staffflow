package com.staffflow.domain.enums;

/**
 * Estado de presencia de un empleado en un momento dado del día.
 *
 * Usado dentro de {@link com.staffflow.dto.response.DetallePresenciaResponse}
 * (cada fila del array {@code detalle[]} de
 * {@link com.staffflow.dto.response.ParteDiarioResponse}) para representar
 * la situación de cada empleado en el parte diario de presencia
 * (E35-E37, RF-30, RF-31).
 *
 * El estado se calcula en PresenciaService consultando fichajes,
 * pausas y planificacion_ausencias para la fecha actual.
 * No se persiste en ninguna tabla — es un valor calculado en tiempo
 * real devuelto únicamente en el DTO de respuesta.
 *
 * Lógica de cálculo por orden de prioridad:
 *   1. Si tiene pausa con hora_fin = NULL → EN_PAUSA
 *   2. Si tiene fichaje con hora_entrada y hora_salida → JORNADA_COMPLETADA
 *   3. Si tiene fichaje con hora_entrada y sin hora_salida → JORNADA_INICIADA
 *   4. Si tiene fichaje sin hora_entrada → AUSENCIA_REGISTRADA
 *   5. Si tiene planificacion_ausencias con procesado = false → AUSENCIA_PLANIFICADA
 *   6. Sin ninguno de los anteriores → SIN_JUSTIFICAR
 *
 * Alternativa descartada: String en el DTO — pierde tipado y
 * aumenta el riesgo de valores inconsistentes entre servicio y cliente.
 *
 * Referencia: E35 (GET /api/v1/presencia/parte-diario),
 *             E36 (GET /api/v1/presencia/sin-justificar),
 *             E37 (GET /api/v1/presencia/parte-diario/me).
 *
 * @author Santiago Castillo
 */
public enum EstadoPresencia {

    /** El empleado ha iniciado su jornada y está trabajando en este momento.
     *  Condición: fichaje con hora_entrada != NULL y hora_salida = NULL,
     *  sin pausa activa. */
    JORNADA_INICIADA,

    /** El empleado está en pausa en este momento.
     *  Condición: existe pausa con hora_fin = NULL para hoy. */
    EN_PAUSA,

    /** El empleado ha completado su jornada del día.
     *  Condición efectiva en PresenciaService.clasificarEmpleado:
     *  fichaje con hora_salida != NULL (la rama no comprueba
     *  hora_entrada). En la práctica hora_entrada también != NULL
     *  porque los fichajes con horas parciales sólo se generan vía
     *  ProcesoCierreDiario para ausencias, y en ese caso ambas horas
     *  llegan a NULL → AUSENCIA_REGISTRADA. */
    JORNADA_COMPLETADA,

    /** El empleado tiene registrada una ausencia para hoy
     *  (VACACIONES, BAJA_MEDICA, ASUNTO_PROPIO, etc.).
     *  Condición real: fichaje con hora_entrada = NULL y hora_salida = NULL.
     *  En la práctica coincide con los fichajes generados por
     *  ProcesoCierreDiario para ausencias (tipo != NORMAL), pero el
     *  criterio efectivo en PresenciaService.clasificarEmpleado es la
     *  ausencia de horas, no el valor de {@link TipoFichaje}. */
    AUSENCIA_REGISTRADA,

    /** El empleado tiene planificada una ausencia para la fecha
     *  consultada pero el proceso nocturno aún no la ha convertido
     *  en fichaje. Condición: registro en planificacion_ausencias
     *  con fecha = fecha_consulta y procesado = false. */
    AUSENCIA_PLANIFICADA,

    /** El empleado no tiene fichaje ni ausencia registrada para la
     *  fecha consultada. Requiere atención del ENCARGADO o ADMIN
     *  (RF-31). */
    SIN_JUSTIFICAR
}