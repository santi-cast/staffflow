package com.staffflow.android.domain.model

/**
 * Estado de presencia de un empleado en el dia actual.
 *
 * Devuelto en DetallePresenciaResponse (E35, E37) y usado en P17
 * (parte diario, vista del encargado) y P12 (mi hoy, vista del empleado)
 * para colorear las filas segun estado.
 *
 * Semantica de color: el encargado ve en rojo todo lo que requiere accion
 * suya (cerrar jornadas o pausas abiertas, justificar empleados sin
 * registro). El empleado ve en ambar su propia jornada o pausa abierta
 * porque para el es estado normal, no pendiente; solo el "sin justificar"
 * sigue siendo rojo para senalar bloqueo real.
 *
 * Paleta del encargado (PresenciaAdapter.colorParaEstado, P17):
 *
 *   JORNADA_INICIADA     -> rojo  (#F44336)  jornada abierta, pendiente de cerrar
 *   EN_PAUSA             -> rojo  (#F44336)  pausa abierta dentro de jornada abierta
 *   SIN_JUSTIFICAR       -> rojo  (#F44336)  sin registros, pendiente de justificar
 *   JORNADA_COMPLETADA   -> verde (#4CAF50)  jornada cerrada
 *   AUSENCIA_REGISTRADA  -> verde (#4CAF50)  ausencia ya procesada
 *   AUSENCIA_PLANIFICADA -> verde (#4CAF50)  ausencia planificada
 *
 * Paleta del empleado (MiHoyFragment.colorParaEstado, P12):
 *
 *   JORNADA_INICIADA     -> ambar (#FFC107)  jornada en curso (estado normal)
 *   EN_PAUSA             -> ambar (#FFC107)  pausa en curso (estado normal)
 *   SIN_JUSTIFICAR       -> rojo  (#F44336)  pendiente real para el empleado
 *   JORNADA_COMPLETADA   -> verde (#4CAF50)  jornada cerrada
 *   AUSENCIA_REGISTRADA  -> verde (#4CAF50)  ausencia ya procesada
 *   AUSENCIA_PLANIFICADA -> verde (#4CAF50)  ausencia planificada
 */
enum class EstadoPresencia {
    JORNADA_INICIADA,
    EN_PAUSA,
    JORNADA_COMPLETADA,
    AUSENCIA_REGISTRADA,
    AUSENCIA_PLANIFICADA,
    SIN_JUSTIFICAR
}
