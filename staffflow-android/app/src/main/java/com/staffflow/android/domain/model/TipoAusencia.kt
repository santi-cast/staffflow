package com.staffflow.android.domain.model

/**
 * Tipo de ausencia planificada.
 *
 * Se usa en AusenciaRequest (E30) al crear una ausencia y en
 * AusenciaResponse al listarlas (E33, E34).
 *
 * Cuando el proceso de cierre diario ejecuta (@Scheduled), las ausencias
 * planificadas con procesado=false generan automaticamente un fichaje
 * del tipo equivalente. Una vez procesadas (procesado=true) no se pueden
 * modificar ni eliminar desde la app (E31, E32).
 */
enum class TipoAusencia {
    FESTIVO_NACIONAL,
    FESTIVO_LOCAL,
    VACACIONES,
    ASUNTO_PROPIO,
    PERMISO_RETRIBUIDO,
    DIA_LIBRE_COMPENSATORIO,
    DIA_LIBRE
}
