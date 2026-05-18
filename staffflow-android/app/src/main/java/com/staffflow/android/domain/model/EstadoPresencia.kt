package com.staffflow.android.domain.model

/**
 * Estado de presencia de un empleado en el dia actual.
 *
 * Devuelto en DetallePresenciaResponse (E35, E37) y usado en P17
 * (parte diario) y P12 (mi hoy) para colorear las filas segun estado.
 *
 * Semantica: el parte diario muestra siempre el dia actual, asi que
 * cualquier jornada o pausa todavia abierta es accion pendiente del
 * encargado y se pinta en rojo. Lo cerrado va en verde.
 *
 * Paleta real aplicada en PresenciaAdapter.colorParaEstado:
 *
 *   JORNADA_INICIADA     -> rojo  (#F44336)  jornada abierta
 *   EN_PAUSA             -> rojo  (#F44336)  pausa abierta dentro de jornada abierta
 *   SIN_JUSTIFICAR       -> rojo  (#F44336)  sin registros
 *   JORNADA_COMPLETADA   -> verde (#4CAF50)  jornada cerrada
 *   AUSENCIA_REGISTRADA  -> verde (#4CAF50)  ausencia ya procesada
 *   AUSENCIA_PLANIFICADA -> verde (#4CAF50)  ausencia planificada (no requiere accion)
 */
enum class EstadoPresencia {
    JORNADA_INICIADA,
    EN_PAUSA,
    JORNADA_COMPLETADA,
    AUSENCIA_REGISTRADA,
    AUSENCIA_PLANIFICADA,
    SIN_JUSTIFICAR
}
