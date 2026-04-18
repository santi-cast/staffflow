package com.staffflow.android.ui.encargado

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.staffflow.android.data.remote.dto.FichajeResponse
import com.staffflow.android.databinding.ItemFichajeBinding
import com.staffflow.android.domain.model.TipoFichaje

/**
 * Adapter del RecyclerView de lista de fichajes (P19).
 *
 * Usa ListAdapter con DiffUtil para actualizaciones eficientes.
 * Cada item muestra fecha, tipo legible, rango horario y duracion de jornada.
 *
 * @param onClick Callback llamado al pulsar un item. FichajesFragment navega a P20 edicion.
 */
class FichajeAdapter(
    private val onClick: (FichajeResponse) -> Unit
) : ListAdapter<FichajeResponse, FichajeAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemFichajeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFichajeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.binding.tvFecha.text = formatearFecha(item.fecha)
        holder.binding.tvTipo.text = tipoLegible(item.tipo)
        holder.binding.tvHorario.text = buildString {
            append(item.horaEntrada ?: "--:--")
            append(" – ")
            append(item.horaSalida ?: "--:--")
        }
        holder.binding.tvDuracion.text = formatearMinutos(item.jornadaEfectivaMinutos)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    /** Convierte "yyyy-MM-dd" a "dd/MM/yyyy". */
    private fun formatearFecha(fecha: String): String {
        val partes = fecha.split("-")
        return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else fecha
    }

    /** Convierte minutos a "Xh YYm". */
    private fun formatearMinutos(minutos: Int): String {
        if (minutos <= 0) return "0h 00m"
        val h = minutos / 60
        val m = minutos % 60
        return "${h}h ${m.toString().padStart(2, '0')}m"
    }

    private fun tipoLegible(tipo: TipoFichaje): String = when (tipo) {
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

    class DiffCallback : DiffUtil.ItemCallback<FichajeResponse>() {
        override fun areItemsTheSame(oldItem: FichajeResponse, newItem: FichajeResponse) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FichajeResponse, newItem: FichajeResponse) =
            oldItem == newItem
    }
}
