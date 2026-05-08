package com.staffflow.android.ui.empleado

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.staffflow.android.data.remote.dto.PausaResponse
import com.staffflow.android.databinding.ItemDiaSemanaBinding
import com.staffflow.android.domain.model.TipoAusencia
import com.staffflow.android.domain.model.TipoFichaje
import com.staffflow.android.domain.model.TipoPausa
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Adapter del RecyclerView de vista semanal de fichajes (P10).
 *
 * Muestra un item por dia del rango seleccionado con tres variantes:
 *   ConFichaje  -> cabecera + tipo + horario + duracion
 *   ConAusencia -> cabecera + tipo ausencia (sin horario)
 *   Vacio       -> cabecera + "Sin actividad"
 */
class DiaSemanaAdapter : ListAdapter<DiaSemanaItem, DiaSemanaAdapter.ViewHolder>(DiffCallback()) {

    private val fmtDia = DateTimeFormatter.ofPattern("EEEE · d 'de' MMMM", Locale("es"))

    class ViewHolder(val binding: ItemDiaSemanaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiaSemanaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DiaSemanaItem.ConFichaje -> bindFichaje(holder.binding, item)
            is DiaSemanaItem.ConAusencia -> bindAusencia(holder.binding, item)
            is DiaSemanaItem.Vacio -> bindVacio(holder.binding, item)
        }
    }

    private fun bindFichaje(binding: ItemDiaSemanaBinding, item: DiaSemanaItem.ConFichaje) {
        binding.tvDia.text = formatearDia(item.fecha)
        binding.tvTipo.text = tipoFichajeLegible(item.fichaje.tipo)

        val tieneHoras = item.fichaje.horaEntrada != null
        binding.layoutHorario.visibility = if (tieneHoras) View.VISIBLE else View.GONE

        if (tieneHoras) {
            binding.tvHorario.text = buildString {
                append(soloHora(item.fichaje.horaEntrada) ?: "--:--:--")
                append(" → ")
                append(soloHora(item.fichaje.horaSalida) ?: "--:--:--")
            }
            binding.tvDuracion.text = formatearMinutos(item.fichaje.jornadaEfectivaMinutos)
        }

        if (item.pausas.isNotEmpty()) {
            binding.tvPausas.visibility = View.VISIBLE
            binding.tvPausas.text = item.pausas.joinToString("\n") { formatearPausa(it) }
        } else {
            binding.tvPausas.visibility = View.GONE
        }
    }

    private fun bindAusencia(binding: ItemDiaSemanaBinding, item: DiaSemanaItem.ConAusencia) {
        binding.tvDia.text = formatearDia(item.fecha)
        binding.tvTipo.text = tipoAusenciaLegible(item.ausencia.tipoAusencia)
        binding.layoutHorario.visibility = View.GONE
    }

    private fun bindVacio(binding: ItemDiaSemanaBinding, item: DiaSemanaItem.Vacio) {
        binding.tvDia.text = formatearDia(item.fecha)
        binding.tvTipo.text = "Sin actividad"
        binding.layoutHorario.visibility = View.GONE
    }

    private fun formatearDia(fecha: LocalDate): String =
        fecha.format(fmtDia).replaceFirstChar { it.uppercaseChar() }

    private fun formatearMinutos(minutos: Int): String {
        if (minutos <= 0) return "0h 00min"
        val h = minutos / 60
        val m = minutos % 60
        return "${h}h ${m.toString().padStart(2, '0')}min"
    }

    private fun formatearPausa(pausa: PausaResponse): String = buildString {
        append("· ")
        append(tipoPausaLegible(pausa.tipoPausa))
        append(" ")
        append(soloHora(pausa.horaInicio) ?: "--:--:--")
        append(" → ")
        append(soloHora(pausa.horaFin) ?: "en curso")
        pausa.duracionMinutos?.let { append(" (${it}min)") }
    }

    /** Extrae HH:mm:ss de un datetime ISO ("2026-03-10T08:30:00" → "08:30:00") o lo devuelve tal cual si ya es hora. */
    private fun soloHora(valor: String?): String? {
        if (valor == null) return null
        return if (valor.contains("T")) valor.substringAfter("T").take(8) else valor.take(8)
    }

    private fun tipoPausaLegible(tipo: TipoPausa): String = when (tipo) {
        TipoPausa.COMIDA              -> "Comida"
        TipoPausa.DESCANSO            -> "Descanso"
        TipoPausa.AUSENCIA_RETRIBUIDA -> "Ausencia retribuida"
        TipoPausa.OTROS               -> "Otros"
    }

    private fun tipoFichajeLegible(tipo: TipoFichaje): String = when (tipo) {
        TipoFichaje.NORMAL                    -> "Normal"
        TipoFichaje.FESTIVO_NACIONAL          -> "Festivo nacional"
        TipoFichaje.FESTIVO_LOCAL             -> "Festivo local"
        TipoFichaje.VACACIONES                -> "Vacaciones"
        TipoFichaje.ASUNTO_PROPIO             -> "Asunto propio"
        TipoFichaje.PERMISO_RETRIBUIDO        -> "Permiso retribuido"
        TipoFichaje.BAJA_MEDICA               -> "Baja médica"
        TipoFichaje.DIA_LIBRE_COMPENSATORIO   -> "Día libre compensatorio"
        TipoFichaje.AUSENCIA_INJUSTIFICADA    -> "Ausencia injustificada"
        TipoFichaje.DIA_LIBRE                 -> "Día libre"
    }

    private fun tipoAusenciaLegible(tipo: TipoAusencia): String = when (tipo) {
        TipoAusencia.FESTIVO_NACIONAL         -> "Festivo nacional"
        TipoAusencia.FESTIVO_LOCAL            -> "Festivo local"
        TipoAusencia.VACACIONES               -> "Vacaciones"
        TipoAusencia.ASUNTO_PROPIO            -> "Asunto propio"
        TipoAusencia.PERMISO_RETRIBUIDO       -> "Permiso retribuido"
        TipoAusencia.DIA_LIBRE_COMPENSATORIO  -> "Día libre compensatorio"
        TipoAusencia.DIA_LIBRE                -> "Día libre"
    }

    class DiffCallback : DiffUtil.ItemCallback<DiaSemanaItem>() {
        override fun areItemsTheSame(oldItem: DiaSemanaItem, newItem: DiaSemanaItem) =
            oldItem.fecha == newItem.fecha

        override fun areContentsTheSame(oldItem: DiaSemanaItem, newItem: DiaSemanaItem) =
            oldItem == newItem
    }
}
