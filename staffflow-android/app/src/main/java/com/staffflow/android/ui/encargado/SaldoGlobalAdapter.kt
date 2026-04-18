package com.staffflow.android.ui.encargado

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.staffflow.android.data.remote.dto.SaldoResponse
import com.staffflow.android.databinding.ItemSaldoGlobalBinding

/**
 * Adapter para la lista de saldos globales (P27).
 *
 * Cada fila muestra: nombre del empleado (viene en SaldoResponse.nombreCompleto),
 * vacaciones disponibles, asuntos propios disponibles y saldo de horas.
 * El saldo de horas se colorea verde (>= 0) o rojo (< 0).
 *
 * @param onItemClick Llamado con el empleadoId al pulsar una fila.
 */
class SaldoGlobalAdapter(
    private val onItemClick: (empleadoId: Long) -> Unit
) : ListAdapter<SaldoResponse, SaldoGlobalAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SaldoResponse>() {
            override fun areItemsTheSame(a: SaldoResponse, b: SaldoResponse) =
                a.empleadoId == b.empleadoId
            override fun areContentsTheSame(a: SaldoResponse, b: SaldoResponse) = a == b
        }
    }

    inner class ViewHolder(private val binding: ItemSaldoGlobalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SaldoResponse, position: Int) {
            binding.tvNombre.text = item.nombreCompleto
            binding.tvVacaciones.text = "${item.vacaciones.disponibles}d"
            binding.tvAsuntos.text = "${item.asuntosPropios.disponibles}d"

            val saldo = item.horas.saldoHoras
            val signo = if (saldo >= 0) "+" else ""
            binding.tvSaldoHoras.text = "$signo${String.format("%.1f", saldo)}h"

            val colorSaldo = if (saldo >= 0)
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorTertiary, 0)
            else
                MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError, 0)
            binding.tvSaldoHoras.setTextColor(colorSaldo)

            // Filas alternas: blanco / azul muy claro (igual que PDF)
            binding.root.setBackgroundColor(
                if (position % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFF7F8FC.toInt()
            )

            binding.root.setOnClickListener { onItemClick(item.empleadoId) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSaldoGlobalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}
