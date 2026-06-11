package com.staffflow.android.domain.model

/**
 * Tipo de jornada registrada en un fichaje.
 *
 * NORMAL es el caso habitual. El resto son ausencias o jornadas especiales
 * que el sistema genera automaticamente o que ADMIN/ENCARGADO registran
 * manualmente (E22, E23).
 *
 * Se usa para serializar y deserializar el tipo de fichaje en los DTO y
 * para mostrar etiquetas y colores en las vistas que pintan fichajes
 * (P09 Mi hoy, P11 detalle dia, P17 parte diario) y en el selector de
 * tipo de los formularios de fichaje del encargado (E22 alta, E23
 * edicion). Gson deserializa el valor String del backend directamente a
 * este enum.
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
