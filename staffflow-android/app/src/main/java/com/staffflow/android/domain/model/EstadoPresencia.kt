package com.staffflow.android.domain.model

/**
 * Estado de presencia de un empleado en el dia actual.
 *
 * Devuelto en DetallePresenciaResponse (E35, E37) y usado en P17
 * (parte diario) y P12 (mi hoy) para colorear las filas segun estado:
 *
 *   JORNADA_INICIADA     -> verde
 *   EN_PAUSA             -> naranja
 *   JORNADA_COMPLETADA   -> verde oscuro
 *   AUSENCIA_REGISTRADA  -> azul
 *   AUSENCIA_PLANIFICADA -> azul claro
 *   SIN_JUSTIFICAR       -> rojo
 */
enum class EstadoPresencia {
    JORNADA_INICIADA,
    EN_PAUSA,
    JORNADA_COMPLETADA,
    AUSENCIA_REGISTRADA,
    AUSENCIA_PLANIFICADA,
    SIN_JUSTIFICAR
}
