package com.staffflow.domain.enums;

/**
 * Tipo de jornada registrada en un fichaje.
 *
 * Cada fichaje tiene exactamente un tipo que describe la naturaleza de esa jornada.
 * El proceso diario automático (@Scheduled 23:55) asigna el tipo correcto al
 * convertir ausencias planificadas en fichajes.
 *
 * Una versión anterior contenía ENTRADA y SALIDA. Esos valores eran
 * incorrectos: el fichaje es el registro completo del día (con hora_entrada
 * y hora_salida en la misma fila), no eventos separados. Se eliminan para
 * evitar errores de mapeo con Hibernate.
 *
 * Valores alineados con la columna ENUM de la tabla fichajes.
 * Referencia: RF-17, RF-18, RF-19, RNF-L01, RNF-L02.
 */
public enum TipoFichaje {

    /** Jornada laboral ordinaria. */
    NORMAL,

    /** Jornada trabajada en festivo nacional (genera derecho a compensación). */
    FESTIVO_NACIONAL,

    /** Jornada trabajada en festivo local (genera derecho a compensación). */
    FESTIVO_LOCAL,

    /** Día de vacaciones anuales reglamentarias del empleado. */
    VACACIONES,

    /** Día de asunto propio (permiso personal retribuido de corta duración). */
    ASUNTO_PROPIO,

    /** Permiso retribuido por causa justificada (matrimonio, fallecimiento familiar, etc.). */
    PERMISO_RETRIBUIDO,

    /** Baja médica por enfermedad o accidente. */
    BAJA_MEDICA,

    /**
     * Día libre para compensar saldo de horas positivo acumulado,
     * o día libre por haber trabajado un festivo.
     * Ambas situaciones tienen el mismo tratamiento contable, por lo que
     * se unifican en un único valor.
     */
    DIA_LIBRE_COMPENSATORIO,

    /**
     * Día libre semanal del empleado (descanso semanal obligatorio).
     * Generado automáticamente por ProcesoCierreDiario la noche anterior
     * si el día siguiente es sábado o domingo y el empleado no tiene
     * fichaje planificado para ese día.
     * Si la empresa trabaja ese día, el ENCARGADO planifica el día como
     * NORMAL antes del cierre y el proceso no lo sobreescribe.
     */
    DIA_LIBRE,

    /** Ausencia sin justificación registrada. Detectado por RF-31 (parte diario). */
    AUSENCIA_INJUSTIFICADA
}
