package com.staffflow.android.ui.shared

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.databinding.ItemEmpleadoBinding
import com.staffflow.android.domain.model.CategoriaEmpleado

/**
 * Adapter del RecyclerView de lista de empleados (P13).
 *
 * Usa ListAdapter con DiffUtil para actualizaciones eficientes.
 * Cada item muestra nombre completo, numero de empleado y categoria.
 * El nombre lleva el lapiz de affordance (tap en la fila navega a P14
 * detalle, donde un chip lleva a P15 edicion).
 *
 * Indicacion del estado activo/inactivo (doble señal visual):
 *   - Borde izquierdo:
 *       activo=true  -> verde claro (#4CAF50)
 *       activo=false -> gris (#9E9E9E)
 *     Mismos colores que el borde de UsuarioAdapter (P28) para coherencia
 *     visual entre las dos listas de gestion de estado.
 *   - Texto " · Inactivo" en rojo (#C62828) en la linea del numero de
 *     empleado, solo si activo=false. Sigue la regla "marca la excepcion,
 *     no la norma": los activos no muestran texto adicional.
 *
 * @param onClick Callback llamado al pulsar un item. EmpleadosFragment navega a P14.
 */
class EmpleadoAdapter(
    private val onClick: (EmpleadoResponse) -> Unit
) : ListAdapter<EmpleadoResponse, EmpleadoAdapter.ViewHolder>(DiffCallback()) {

    private companion object {
        /**
         * Lapiz (U+270E) usado como affordance de "tocar para editar".
         * Mismo caracter que usan UsuarioAdapter y PresenciaAdapter.
         *
         * Nota: en empleados el tap no abre la edicion directamente; navega
         * primero a P14 (detalle), que contiene el chip "Editar empleado"
         * para llegar a P15 (form de edicion).
         */
        const val LAPIZ = "\u270E"

        /** Rojo de "estado inactivo". Coherente con tvEstado de P14. */
        const val ROJO_INACTIVO = "#C62828"
    }

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
            append(" ")
            append(LAPIZ)
        }
        // Marcar la excepcion: si esta inactivo, añadir " · Inactivo" en rojo
        // tras el numero de empleado. Los activos no muestran texto adicional
        // para no saturar la lista (los inactivos son ~5% del total).
        // Se usa SpannableStringBuilder para colorear solo el sufijo.
        if (item.activo) {
            holder.binding.tvNumeroEmpleado.text = item.numeroEmpleado
        } else {
            val sufijo = " · ${holder.itemView.context.getString(R.string.detalle_empleado_inactivo)}"
            val texto = android.text.SpannableStringBuilder(item.numeroEmpleado).apply {
                val inicio = length
                append(sufijo)
                setSpan(
                    android.text.style.ForegroundColorSpan(Color.parseColor(ROJO_INACTIVO)),
                    inicio,
                    length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            holder.binding.tvNumeroEmpleado.text = texto
        }
        holder.binding.tvCategoria.text = nombreCategoria(item.categoria)
        holder.binding.viewBorde.setBackgroundColor(
            if (item.activo) Color.parseColor("#4CAF50")
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
