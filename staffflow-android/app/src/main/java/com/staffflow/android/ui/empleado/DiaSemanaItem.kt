package com.staffflow.android.ui.empleado

import com.staffflow.android.data.remote.dto.AusenciaResponse
import com.staffflow.android.data.remote.dto.FichajeResponse
import com.staffflow.android.data.remote.dto.PausaResponse
import java.time.LocalDate

/**
 * Representa un dia del rango seleccionado en MisFichajesFragment (P10).
 *
 * ConFichaje  -> el cierre diario ya genero un fichaje para ese dia.
 * ConAusencia -> hay una ausencia planificada pendiente de procesar.
 * Vacio       -> no hay registro ni ausencia para ese dia.
 */
sealed class DiaSemanaItem {
    abstract val fecha: LocalDate

    data class ConFichaje(
        override val fecha: LocalDate,
        val fichaje: FichajeResponse,
        val pausas: List<PausaResponse> = emptyList()
    ) : DiaSemanaItem()

    data class ConAusencia(
        override val fecha: LocalDate,
        val ausencia: AusenciaResponse
    ) : DiaSemanaItem()

    data class Vacio(override val fecha: LocalDate) : DiaSemanaItem()
}
