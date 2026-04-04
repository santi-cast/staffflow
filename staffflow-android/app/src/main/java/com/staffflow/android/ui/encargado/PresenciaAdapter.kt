package com.staffflow.android.ui.encargado

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.staffflow.android.data.remote.dto.DetallePresenciaResponse
import com.staffflow.android.databinding.ItemPresenciaBinding
import com.staffflow.android.domain.model.EstadoPresencia

/**
 * Adapter del RecyclerView del parte diario (P17).
 *
 * Usa ListAdapter con DiffUtil para actualizaciones eficientes.
 * Cada item muestra nombre, estado (con color) y hora de entrada.
 *
 * Color del borde izquierdo y del texto de estado segun EstadoPresencia:
 *   JORNADA_INICIADA              -> verde   (#4CAF50)
 *   EN_PAUSA                      -> naranja (#FF9800)
 *   JORNADA_COMPLETADA            -> verde oscuro (#2E7D32)
 *   AUSENCIA_REGISTRADA/PLANIFICADA -> azul (#1976D2)
 *   SIN_JUSTIFICAR                -> rojo   (#F44336)
 *
 * @param onClick Callback llamado al pulsar un item. ParteDiarioFragment navega a P14.
 */
class PresenciaAdapter(
    private val onClick: (DetallePresenciaResponse) -> Unit
) : ListAdapter<DetallePresenciaResponse, PresenciaAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemPresenciaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPresenciaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val color = colorParaEstado(item.estado)

        holder.binding.tvNombre.text = buildString {
            append(item.nombre)
            append(" ")
            append(item.apellido1)
            item.apellido2?.let { append(" $it") }
        }
        holder.binding.tvEstado.text = nombreEstado(item.estado)
        holder.binding.tvEstado.setTextColor(color)
        holder.binding.viewBordeEstado.setBackgroundColor(color)
        holder.binding.tvHoraEntrada.text = item.horaEntrada ?: "--:--"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun colorParaEstado(estado: EstadoPresencia): Int = when (estado) {
        EstadoPresencia.JORNADA_INICIADA      -> Color.parseColor("#4CAF50")
        EstadoPresencia.EN_PAUSA              -> Color.parseColor("#FF9800")
        EstadoPresencia.JORNADA_COMPLETADA    -> Color.parseColor("#2E7D32")
        EstadoPresencia.AUSENCIA_REGISTRADA,
        EstadoPresencia.AUSENCIA_PLANIFICADA  -> Color.parseColor("#1976D2")
        EstadoPresencia.SIN_JUSTIFICAR        -> Color.parseColor("#F44336")
    }

    private fun nombreEstado(estado: EstadoPresencia): String = when (estado) {
        EstadoPresencia.JORNADA_INICIADA      -> "Jornada iniciada"
        EstadoPresencia.EN_PAUSA              -> "En pausa"
        EstadoPresencia.JORNADA_COMPLETADA    -> "Jornada completada"
        EstadoPresencia.AUSENCIA_REGISTRADA   -> "Ausencia registrada"
        EstadoPresencia.AUSENCIA_PLANIFICADA  -> "Ausencia planificada"
        EstadoPresencia.SIN_JUSTIFICAR        -> "Sin justificar"
    }

    class DiffCallback : DiffUtil.ItemCallback<DetallePresenciaResponse>() {
        override fun areItemsTheSame(
            oldItem: DetallePresenciaResponse,
            newItem: DetallePresenciaResponse
        ) = oldItem.empleadoId == newItem.empleadoId

        override fun areContentsTheSame(
            oldItem: DetallePresenciaResponse,
            newItem: DetallePresenciaResponse
        ) = oldItem == newItem
    }
}
