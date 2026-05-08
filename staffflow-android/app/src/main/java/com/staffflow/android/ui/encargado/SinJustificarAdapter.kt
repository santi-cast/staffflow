package com.staffflow.android.ui.encargado

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.staffflow.android.data.remote.dto.SinJustificarResponse
import com.staffflow.android.databinding.ItemSinJustificarBinding

/**
 * Adapter de la lista de empleados sin justificar (P18).
 *
 * Lista de solo lectura. Muestra nombre completo de cada empleado.
 */
class SinJustificarAdapter(
    private val onEmpleadoClick: (empleadoId: Long) -> Unit
) : ListAdapter<SinJustificarResponse, SinJustificarAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemSinJustificarBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SinJustificarResponse) {
            val apellido2 = if (!item.apellido2.isNullOrBlank()) " ${item.apellido2}" else ""
            binding.tvNombreCompleto.text = "${item.nombre} ${item.apellido1}$apellido2"
            binding.root.setOnClickListener { onEmpleadoClick(item.empleadoId) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSinJustificarBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<SinJustificarResponse>() {
        override fun areItemsTheSame(old: SinJustificarResponse, new: SinJustificarResponse) =
            old.empleadoId == new.empleadoId
        override fun areContentsTheSame(old: SinJustificarResponse, new: SinJustificarResponse) =
            old == new
    }
}
