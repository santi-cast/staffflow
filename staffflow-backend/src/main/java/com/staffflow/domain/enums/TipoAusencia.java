package com.staffflow.domain.enums;

/**
 * Tipo de ausencia en la planificación de ausencias.
 *
 * Usado en la tabla planificacion_ausencias. Representa ausencias conocidas
 * con antelación que serán convertidas automáticamente en fichajes por el
 * proceso diario (@Scheduled 23:55).
 *
 * Distinción clave (decisión de diseño nº7):
 *   - Ausencia PLANIFICADA → va a planificacion_ausencias con este enum.
 *   - Ausencia JUSTIFICADA IMPREVISTA (baja médica, etc.) → se registra
 *     directamente como fichaje manual con el TipoFichaje correcto.
 *     NO pasa por planificacion_ausencias.
 *
 * Por eso TipoAusencia no incluye BAJA_MEDICA ni AUSENCIA_INJUSTIFICADA:
 * esos tipos solo existen en TipoFichaje.
 *
 * Valores alineados con la columna ENUM de la tabla planificacion_ausencias (DDL v4).
 * Referencia: RF-25, RF-26, RF-27, RF-28, RF-29.
 */
public enum TipoAusencia {

    /** Festivo nacional aplicable a todos los empleados activos.
     *  Cuando empleado_id = NULL en planificacion_ausencias, el proceso diario
     *  crea fichaje de este tipo para todos los empleados activos. */
    FESTIVO_NACIONAL,

    /** Festivo local (municipal o autonómico). Mismo comportamiento que FESTIVO_NACIONAL
     *  respecto al campo empleado_id = NULL para festivos globales. */
    FESTIVO_LOCAL,

    /** Vacaciones anuales planificadas con antelación. */
    VACACIONES,

    /** Asunto propio planificado. */
    ASUNTO_PROPIO,

    /** Permiso retribuido planificado (conocido con antelación: matrimonio, etc.). */
    PERMISO_RETRIBUIDO,

    /**
     * Día libre compensatorio planificado.
     * Decisión de diseño nº13: cubre tanto la compensación por saldo de horas
     * positivo como el día libre por haber trabajado un festivo.
     * Mismo valor que en TipoFichaje para coherencia semántica entre tablas.
     */
    DIA_LIBRE_COMPENSATORIO,

    /**
     * Día libre semanal del empleado (descanso semanal obligatorio).
     * Permite planificar explícitamente un día libre para un empleado,
     * por ejemplo si la empresa abre un sábado y ese empleado no trabaja.
     * Mismo valor que en TipoFichaje para coherencia semántica entre tablas.
     */
    DIA_LIBRE
}
