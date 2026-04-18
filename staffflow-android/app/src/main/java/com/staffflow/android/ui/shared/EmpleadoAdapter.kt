package com.staffflow.android.ui.shared

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.databinding.ItemEmpleadoBinding
import com.staffflow.android.domain.model.CategoriaEmpleado

/**
 * Adapter del RecyclerView de lista de empleados (P13).
 *
 * Usa ListAdapter con DiffUtil para actualizaciones eficientes.
 * Cada item muestra nombre completo, numero de empleado y categoria.
 *
 * Color del borde izquierdo segun el campo activo:
 *   activo=true  -> colorPrimary del tema (#6750A4 en Material3 predeterminado)
 *   activo=false -> gris (#9E9E9E), indica que el empleado esta dado de baja
 *
 * @param onClick Callback llamado al pulsar un item. EmpleadosFragment navega a P14.
 */
class EmpleadoAdapter(
    private val onClick: (EmpleadoResponse) -> Unit
) : ListAdapter<EmpleadoResponse, EmpleadoAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemEmpleadoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmpleadoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.binding.tvNombre.text = buildString {
            append(item.nombre)
            append(" ")
            append(item.apellido1)
            item.apellido2?.let { append(" $it") }
        }
        holder.binding.tvNumeroEmpleado.text = item.numeroEmpleado
        holder.binding.tvCategoria.text = nombreCategoria(item.categoria)
        holder.binding.viewBorde.setBackgroundColor(
            if (item.activo) Color.parseColor("#6750A4")
            else Color.parseColor("#9E9E9E")
        )
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun nombreCategoria(categoria: CategoriaEmpleado): String = when (categoria) {
        CategoriaEmpleado.OPERARIO              -> "Operario"
        CategoriaEmpleado.ADMINISTRATIVO        -> "Administrativo"
        CategoriaEmpleado.TECNICO               -> "Técnico"
        CategoriaEmpleado.ENCARGADO             -> "Encargado"
        CategoriaEmpleado.OTRO                  -> "Otro"
    }

    class DiffCallback : DiffUtil.ItemCallback<EmpleadoResponse>() {
        override fun areItemsTheSame(oldItem: EmpleadoResponse, newItem: EmpleadoResponse) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: EmpleadoResponse, newItem: EmpleadoResponse) =
            oldItem == newItem
    }
}
