package com.staffflow.android.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.UsuarioResponse
import com.staffflow.android.databinding.ItemUsuarioBinding
import com.staffflow.android.domain.model.Rol

/**
 * Adapter del RecyclerView de lista de usuarios (P28).
 *
 * Cada item muestra username, email, rol legible y estado activo/inactivo.
 * El borde izquierdo indica el estado: activo (#4CAF50) / inactivo (#9E9E9E).
 *
 * @param onClick Callback llamado al pulsar un item. UsuariosFragment navega a P29 edicion.
 */
class UsuarioAdapter(
    private val onClick: (UsuarioResponse) -> Unit
) : ListAdapter<UsuarioResponse, UsuarioAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemUsuarioBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUsuarioBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvUsername.text  = item.username
            tvEmail.text     = item.email
            tvRol.text       = rolLegible(item.rol)
            tvEstado.text    = if (item.activo) "Activo" else "Inactivo"

            val colorBorde = if (item.activo) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
            viewBorde.setBackgroundColor(colorBorde)
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun rolLegible(rol: Rol): String = when (rol) {
        Rol.ADMIN     -> "Admin"
        Rol.ENCARGADO -> "Encargado"
        Rol.EMPLEADO  -> "Empleado"
    }

    class DiffCallback : DiffUtil.ItemCallback<UsuarioResponse>() {
        override fun areItemsTheSame(oldItem: UsuarioResponse, newItem: UsuarioResponse) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UsuarioResponse, newItem: UsuarioResponse) =
            oldItem == newItem
    }
}
