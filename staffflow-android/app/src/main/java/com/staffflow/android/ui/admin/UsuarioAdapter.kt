package com.staffflow.android.ui.admin

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
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
 * Cada item muestra username, email y rol legible. El username lleva el
 * lapiz de affordance (tap en la fila navega a P29 en modo edicion).
 *
 * Indicacion del estado activo/inactivo (doble señal visual, simetrica a
 * EmpleadoAdapter):
 *   - Borde izquierdo: activo (#4CAF50, verde claro) / inactivo (#9E9E9E, gris).
 *   - Texto " · Inactivo" en rojo (#C62828) en la linea del rol, solo si
 *     activo=false. Sigue la regla "marca la excepcion, no la norma": los
 *     activos no muestran texto adicional para no saturar la lista (los
 *     inactivos son la minoria del total).
 *
 * @param onClick Callback llamado al pulsar un item. UsuariosFragment navega a P29 edicion.
 */
class UsuarioAdapter(
    private val onClick: (UsuarioResponse) -> Unit
) : ListAdapter<UsuarioResponse, UsuarioAdapter.ViewHolder>(DiffCallback()) {

    private companion object {
        /**
         * Lapiz (U+270E) usado como affordance de "tocar para editar". Mismo
         * caracter que usa PresenciaAdapter y el informe semanal del backend
         * (InformeService) para indicar que la celda es editable.
         */
        const val LAPIZ = "\u270E"

        /** Rojo de "estado inactivo". Coherente con EmpleadoAdapter y tvEstado de P14. */
        const val ROJO_INACTIVO  = "#C62828"
    }

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
            tvUsername.text  = "${item.username} $LAPIZ"
            tvEmail.text     = item.email
            // Marcar la excepcion: si esta inactivo, añadir " · Inactivo" en rojo
            // tras el rol. Los activos no muestran texto adicional (patron
            // simetrico a EmpleadoAdapter).
            val rolLegible = rolLegible(item.rol)
            if (item.activo) {
                tvRol.text = rolLegible
            } else {
                val sufijo = " · ${holder.itemView.context.getString(R.string.usuarios_item_inactivo)}"
                val texto = android.text.SpannableStringBuilder(rolLegible).apply {
                    append(sufijo)
                    setSpan(
                        android.text.style.ForegroundColorSpan(Color.parseColor(ROJO_INACTIVO)),
                        rolLegible.length,
                        rolLegible.length + sufijo.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                tvRol.text = texto
            }

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
