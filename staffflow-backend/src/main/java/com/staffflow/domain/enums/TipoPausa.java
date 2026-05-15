package com.staffflow.domain.enums;

/**
 * Tipo de pausa registrada durante una jornada laboral.
 *
 * Usado en la tabla pausas. Una pausa queda activa (hora_fin = NULL) mientras
 * el empleado está en pausa, y se cierra al reanudar la jornada.
 * La duración se calcula con Math.floor al cerrar (minutos completos).
 *
 * El valor PERSONAL fue eliminado y reemplazado por AUSENCIA_RETRIBUIDA,
 * que describe con mayor precisión el concepto laboral correspondiente
 * (gestión médica, trámites oficiales u otras ausencias breves retribuidas
 * dentro de la jornada).
 *
 * Valores alineados con la columna ENUM de la tabla pausas.
 * Referencia: RF-22, RF-23, RF-24, RF-48, RF-49.
 */
public enum TipoPausa {

    /** Pausa para comida o descanso de mediodía. */
    COMIDA,

    /** Pausa breve de descanso (café, etc.). */
    DESCANSO,

    /**
     * Ausencia breve retribuida dentro de la jornada: gestión médica,
     * trámite oficial u otras ausencias de corta duración con derecho
     * a retribución. Reemplaza al valor PERSONAL anterior.
     */
    AUSENCIA_RETRIBUIDA,

    /** Otros motivos de pausa no clasificados en las categorías anteriores. */
    OTROS
}
