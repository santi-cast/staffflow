package com.staffflow.android.ui.encargado

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.staffflow.android.data.remote.dto.AusenciaResponse
import com.staffflow.android.databinding.ItemAusenciaBinding
import com.staffflow.android.domain.model.TipoAusencia

/**
 * Adapter del RecyclerView de lista de ausencias (P23).
 *
 * Usa ListAdapter con DiffUtil para actualizaciones eficientes.
 * Cada item muestra fecha, tipo legible y estado (pendiente/procesada).
 *
 * Color del borde izquierdo segun procesado:
 *   procesado=false -> colorPrimary (#6750A4): pendiente de procesar
 *   procesado=true  -> gris (#9E9E9E): ya generada en fichaje
 *
 * @param onClick Callback llamado al pulsar un item. AusenciasFragment navega a P24.
 */
class AusenciaAdapter(
    private val onClick: (AusenciaResponse) -> Unit
) : ListAdapter<AusenciaResponse, AusenciaAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemAusenciaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAusenciaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.binding.tvFecha.text = formatearFecha(item.fecha)
        holder.binding.tvTipo.text = tipoLegible(item.tipoAusencia)
        holder.binding.tvEstado.text = if (item.procesado) "Procesada" else "Pendiente"
        holder.binding.viewBorde.setBackgroundColor(
            if (!item.procesado) Color.parseColor("#6750A4")
            else Color.parseColor("#9E9E9E")
        )
        holder.itemView.setOnClickListener { onClick(item) }
    }

    /** Convierte "yyyy-MM-dd" a "dd/MM/yyyy". */
    private fun formatearFecha(fecha: String): String {
        val partes = fecha.split("-")
        return if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else fecha
    }

    private fun tipoLegible(tipo: TipoAusencia): String = when (tipo) {
        TipoAusencia.FESTIVO_NACIONAL         -> "Festivo nacional"
        TipoAusencia.FESTIVO_LOCAL            -> "Festivo local"
        TipoAusencia.VACACIONES               -> "Vacaciones"
        TipoAusencia.ASUNTO_PROPIO            -> "Asunto propio"
        TipoAusencia.PERMISO_RETRIBUIDO       -> "Permiso retribuido"
        TipoAusencia.DIA_LIBRE_COMPENSATORIO  -> "Día libre compensatorio"
        TipoAusencia.DIA_LIBRE                -> "Día libre"
    }

    class DiffCallback : DiffUtil.ItemCallback<AusenciaResponse>() {
        override fun areItemsTheSame(oldItem: AusenciaResponse, newItem: AusenciaResponse) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AusenciaResponse, newItem: AusenciaResponse) =
            oldItem == newItem
    }
}
