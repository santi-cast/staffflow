package com.staffflow.android.ui.encargado

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.staffflow.android.data.remote.dto.DetallePresenciaResponse
import com.staffflow.android.databinding.ItemEventoDiaBinding
import com.staffflow.android.databinding.ItemPresenciaBinding
import com.staffflow.android.domain.model.EstadoPresencia
import com.staffflow.android.domain.model.TipoFichaje

/**
 * Adapter del RecyclerView del parte diario (P17).
 *
 * Muestra una seccion por empleado activo con todos sus eventos del dia:
 *   - Cabecera: nombre + estado actual (color segun EstadoPresencia)
 *   - Fichaje del dia (si existe): hora entrada → hora salida / En curso
 *   - Cada pausa del dia (activas y completadas)
 *   - Ausencia planificada (si existe y estado = AUSENCIA_PLANIFICADA)
 *   - "Sin registros" si no hay ningun evento
 *
 * Cada evento es clicable. Los callbacks notifican a ParteDiarioFragment
 * para navegar a FormFichajeFragment o FormAusenciaFragment.
 *
 * @param onFichajeClick  callback al tocar un fichaje (id, empleadoId, tipo, horaEntrada, horaSalida)
 * @param onPausaClick    callback al tocar una pausa  (id, empleadoId, tipoPausa, horaInicio, horaFin)
 * @param onAusenciaClick callback al tocar una ausencia planificada (id, empleadoId)
 */
class PresenciaAdapter(
    private val onFichajeClick: (fichajeId: Long, empleadoId: Long, tipo: TipoFichaje, horaEntrada: String?, horaSalida: String?) -> Unit,
    private val onPausaClick:   (pausaId: Long, empleadoId: Long, tipoPausa: String, horaInicio: String?, horaFin: String?) -> Unit,
    private val onAusenciaClick: (ausenciaId: Long, empleadoId: Long) -> Unit,
    private val onSinJustificarClick: (empleadoId: Long) -> Unit,
    private val onRegistrarSalidaClick: (fichajeId: Long, empleadoId: Long, tipo: TipoFichaje, horaEntrada: String?, pausaActiva: Boolean) -> Unit
) : ListAdapter<DetallePresenciaResponse, PresenciaAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(val binding: ItemPresenciaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPresenciaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item  = getItem(position)
        val color = colorParaEstado(item.estado)

        // --- Cabecera ---
        holder.binding.tvNombre.text = buildString {
            append(item.nombre)
            append(" ")
            append(item.apellido1)
            item.apellido2?.let { append(" $it") }
        }
        holder.binding.tvEstado.text = nombreEstado(item.estado)
        holder.binding.tvEstado.setTextColor(color)
        holder.binding.viewBordeEstado.setBackgroundColor(color)

        // --- Eventos ---
        val container = holder.binding.containerEventos
        container.removeAllViews()

        var hayEventos = false

        // Fichaje
        if (item.fichajeId != null) {
            hayEventos = true
            val horaEntrada = soloHora(item.horaEntrada)
            val horaSalida  = soloHora(item.horaSalida)
            val tipo        = item.fichajeTipo ?: TipoFichaje.NORMAL
            if (horaSalida != null) {
                val pausasSeg = item.pausas
                    ?.filter { it.horaFin != null }
                    ?.sumOf { calcularSegundos(it.horaInicio, it.horaFin) } ?: 0L
                val duracion = formatearSegundos(
                    maxOf(0L, calcularSegundos(horaEntrada, horaSalida) - pausasSeg))
                agregarEvento(holder, "Jornada · ${tipoFichajeNombre(tipo)}",
                    "$horaEntrada → $horaSalida", duracion, colorFichaje(tipo, completada = true)) {
                    onFichajeClick(item.fichajeId, item.empleadoId, tipo, horaEntrada, horaSalida)
                }
            } else if (horaEntrada != null) {
                agregarEvento(holder, "Inicio jornada · ${tipoFichajeNombre(tipo)}",
                    horaEntrada, "", colorFichaje(tipo, completada = false)) {
                    onFichajeClick(item.fichajeId, item.empleadoId, tipo, horaEntrada, null)
                }
                agregarAccion(holder, "+ Registrar salida", Color.parseColor("#FFC107")) {
                    onRegistrarSalidaClick(item.fichajeId, item.empleadoId, tipo, horaEntrada, item.pausaActiva)
                }
            } else {
                // Ausencia de día entero: sin horas, solo el tipo
                agregarEvento(holder, tipoFichajeNombre(tipo),
                    "Día completo", "", colorFichaje(tipo, completada = true)) {
                    onFichajeClick(item.fichajeId, item.empleadoId, tipo, null, null)
                }
            }
        }

        // Pausas
        item.pausas?.forEach { pausa ->
            if (pausa.id != null) {
                hayEventos = true
                val activa     = pausa.horaFin == null
                val colorPausa = if (activa) Color.parseColor("#FFC107")
                                 else        Color.parseColor("#4CAF50")
                if (!activa) {
                    val duracion = formatearSegundos(calcularSegundos(pausa.horaInicio, pausa.horaFin))
                    agregarEvento(holder, "Pausa · ${tipoPausaNombre(pausa.tipoPausa)}",
                        "${pausa.horaInicio ?: "--"} → ${pausa.horaFin}", duracion, colorPausa) {
                        onPausaClick(pausa.id, item.empleadoId, pausa.tipoPausa ?: "", pausa.horaInicio, pausa.horaFin)
                    }
                } else {
                    agregarEvento(holder, "Inicio pausa · ${tipoPausaNombre(pausa.tipoPausa)}",
                        pausa.horaInicio ?: "--", "", colorPausa) {
                        onPausaClick(pausa.id, item.empleadoId, pausa.tipoPausa ?: "", pausa.horaInicio, null)
                    }
                    agregarAccion(holder, "+ Registrar fin de pausa", Color.parseColor("#FFC107")) {
                        onPausaClick(pausa.id, item.empleadoId, pausa.tipoPausa ?: "", pausa.horaInicio, null)
                    }
                }
            }
        }

        // Ausencia planificada
        if (item.ausenciaId != null && item.estado == EstadoPresencia.AUSENCIA_PLANIFICADA) {
            hayEventos = true
            agregarEvento(holder, "Ausencia planificada", "Pendiente de procesar",
                "", Color.parseColor("#9C27B0")) {
                onAusenciaClick(item.ausenciaId, item.empleadoId)
            }
        }

        // Añadir registro (SIN_JUSTIFICAR)
        if (!hayEventos) {
            val tv = TextView(holder.itemView.context).apply {
                text = "+ Añadir registro"
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(Color.parseColor("#1976D2"))
                setPadding(0, 16, 0, 8)
                isClickable = true
                isFocusable = true
                setOnClickListener { onSinJustificarClick(item.empleadoId) }
            }
            container.addView(tv)
        }
    }

    // ------------------------------------------------------------------
    // Helper: infla item_evento_dia y lo añade al container
    // ------------------------------------------------------------------

    private fun agregarEvento(
        holder: ViewHolder,
        label: String,
        valor: String,
        duracion: String,
        color: Int,
        onClick: () -> Unit
    ) {
        val evento = ItemEventoDiaBinding.inflate(
            LayoutInflater.from(holder.itemView.context),
            holder.binding.containerEventos,
            false
        )
        evento.viewIndicador.setBackgroundColor(color)
        evento.tvLabel.text    = label
        evento.tvValor.text    = valor
        evento.tvDuracion.text = duracion
        evento.root.setOnClickListener { onClick() }
        holder.binding.containerEventos.addView(evento.root)
    }

    private fun agregarAccion(holder: ViewHolder, texto: String, color: Int, onClick: () -> Unit) {
        val tv = TextView(holder.itemView.context).apply {
            text = texto
            setTextColor(color)
            textSize = 13f
            setPadding(56, 4, 16, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        holder.binding.containerEventos.addView(tv)
    }

    // ------------------------------------------------------------------
    // Helpers de color y texto
    // ------------------------------------------------------------------

    private fun colorFichaje(tipo: TipoFichaje, completada: Boolean): Int = when {
        completada -> Color.parseColor("#4CAF50")
        else       -> Color.parseColor("#FFC107")
    }

    private fun colorParaEstado(estado: EstadoPresencia): Int = when (estado) {
        EstadoPresencia.JORNADA_INICIADA      -> Color.parseColor("#FFC107")
        EstadoPresencia.EN_PAUSA              -> Color.parseColor("#FFC107")
        EstadoPresencia.JORNADA_COMPLETADA    -> Color.parseColor("#4CAF50")
        EstadoPresencia.AUSENCIA_REGISTRADA,
        EstadoPresencia.AUSENCIA_PLANIFICADA  -> Color.parseColor("#4CAF50")
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

    private fun tipoFichajeNombre(tipo: TipoFichaje): String = when (tipo) {
        TipoFichaje.NORMAL                  -> "Normal"
        TipoFichaje.FESTIVO_NACIONAL        -> "Festivo nacional"
        TipoFichaje.FESTIVO_LOCAL           -> "Festivo local"
        TipoFichaje.VACACIONES              -> "Vacaciones"
        TipoFichaje.ASUNTO_PROPIO           -> "Asunto propio"
        TipoFichaje.PERMISO_RETRIBUIDO      -> "Permiso retribuido"
        TipoFichaje.BAJA_MEDICA             -> "Baja médica"
        TipoFichaje.DIA_LIBRE_COMPENSATORIO -> "Día libre compensatorio"
        TipoFichaje.AUSENCIA_INJUSTIFICADA  -> "Ausencia injustificada"
        TipoFichaje.DIA_LIBRE               -> "Día libre"
    }

    /**
     * Calcula la diferencia en segundos entre dos horas "HH:mm:ss".
     * Devuelve 0 si alguno es null o el formato es invalido.
     */
    private fun calcularSegundos(inicio: String?, fin: String?): Long {
        if (inicio == null || fin == null) return 0L
        return try {
            val (hI, mI, sI) = inicio.split(":").map { it.toLong() }
            val (hF, mF, sF) = fin.split(":").map { it.toLong() }
            val diff = (hF * 3600 + mF * 60 + sF) - (hI * 3600 + mI * 60 + sI)
            maxOf(0L, diff)
        } catch (e: Exception) { 0L }
    }

    /** Formatea segundos totales como "Xh YYmin ZZs". */
    private fun formatearSegundos(totalSeg: Long): String {
        val h = totalSeg / 3600
        val m = (totalSeg % 3600) / 60
        val s = totalSeg % 60
        return buildString {
            if (h > 0) append("${h}h ")
            append("${m.toString().padStart(2, '0')}min ")
            append("${s.toString().padStart(2, '0')}s")
        }.trim()
    }

    /** Extrae solo HH:mm:ss de un ISO datetime ("2026-04-18T08:30:00" -> "08:30:00"). */
    private fun soloHora(valor: String?): String? {
        if (valor == null) return null
        return if (valor.contains("T")) valor.substringAfter("T").take(8) else valor.take(8)
    }

    private fun tipoPausaNombre(tipo: String?): String = when (tipo) {
        "COMIDA"              -> "Comida"
        "DESCANSO"            -> "Descanso"
        "AUSENCIA_RETRIBUIDA" -> "Ausencia retribuida"
        "OTROS"               -> "Otros"
        else                  -> tipo ?: "--"
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
