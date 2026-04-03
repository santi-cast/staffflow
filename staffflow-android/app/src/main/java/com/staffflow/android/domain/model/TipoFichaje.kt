package com.staffflow.android.domain.model

/**
 * Tipo de jornada registrada en un fichaje.
 *
 * NORMAL es el caso habitual. El resto son ausencias o jornadas especiales
 * que el sistema genera automaticamente o que ADMIN/ENCARGADO registran
 * manualmente (E22, E23).
 *
 * Se usa como filtro en P10 (mis fichajes) y P19 (lista fichajes).
 * Gson deserializa el valor String del backend directamente a este enum.
 */
enum class TipoFichaje {
    NORMAL,
    FESTIVO_NACIONAL,
    FESTIVO_LOCAL,
    VACACIONES,
    ASUNTO_PROPIO,
    PERMISO_RETRIBUIDO,
    BAJA_MEDICA,
    DIA_LIBRE_COMPENSATORIO,
    DIA_LIBRE,
    AUSENCIA_INJUSTIFICADA
}
