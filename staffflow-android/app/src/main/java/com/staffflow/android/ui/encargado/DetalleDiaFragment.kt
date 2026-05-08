package com.staffflow.android.ui.encargado

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.AusenciaResponse
import com.staffflow.android.data.remote.dto.FichajeResponse
import com.staffflow.android.data.remote.dto.PausaResponse
import com.staffflow.android.databinding.FragmentDetalleDiaBinding
import com.staffflow.android.databinding.ItemEventoDiaBinding
import com.staffflow.android.domain.model.TipoFichaje
import com.staffflow.android.domain.model.TipoAusencia
import com.staffflow.android.domain.model.TipoPausa
import kotlinx.coroutines.launch

/**
 * Detalle de jornada de un empleado para un dia concreto (P-DetalleDia).
 *
 * Accesible para ENCARGADO y ADMIN desde ParteDiarioFragment al tocar un empleado.
 * Carga en paralelo el fichaje y las pausas del dia via E24 y E29.
 *
 * Muestra en orden cronologico:
 *   - Fichaje del dia (entrada, tipo, salida o "En curso") -> editar via FormFichajeFragment
 *   - Cada pausa (tipo, inicio, fin o "Activa", duracion)  -> editar via FormFichajeFragment
 *   - FAB: crear pausa manual si hay fichaje abierto sin pausa activa
 *   - Boton "Crear fichaje": si no hay ningún registro ese dia
 *
 * Argumentos de navegacion:
 *   empleadoId    Long    ID del empleado
 *   nombreEmpleado String Nombre completo (para el titulo de la toolbar)
 *   fecha         String  Fecha en formato "yyyy-MM-dd"
 */
class DetalleDiaFragment : Fragment() {

    private var _binding: FragmentDetalleDiaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetalleDiaViewModel by viewModels()

    private var empleadoId: Long = -1L
    private var fecha: String = ""

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetalleDiaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        empleadoId = arguments?.getLong("empleadoId") ?: -1L
        fecha = arguments?.getString("fecha") ?: ""
        val nombreEmpleado = arguments?.getString("nombreEmpleado") ?: ""

        requireActivity().title = nombreEmpleado

        viewModel.init(empleadoId, fecha)
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
        observarViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.reintentar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { procesarEstado(it) }
            }
        }
    }

    private fun procesarEstado(estado: DetalleDiaViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is DetalleDiaViewModel.UiState.Loading
        binding.layoutError.isVisible       = estado is DetalleDiaViewModel.UiState.Error
        binding.scrollContenido.isVisible   = estado is DetalleDiaViewModel.UiState.Success

        when (estado) {
            is DetalleDiaViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            is DetalleDiaViewModel.UiState.Success -> mostrarDetalle(estado.detail)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Construccion dinamica del timeline
    // ------------------------------------------------------------------

    private fun mostrarDetalle(detail: DetalleDiaViewModel.DayDetail) {
        binding.containerEventos.removeAllViews()

        val fichajes  = detail.fichajes
        val pausas    = detail.pausas
        val ausencias = detail.ausencias

        if (fichajes.isEmpty() && pausas.isEmpty() && ausencias.isEmpty()) {
            agregarEstadoVacio()
            binding.fab.isVisible = false
            return
        }

        fichajes.forEach { agregarFichajeCard(it) }
        pausas.forEach { agregarPausaCard(it) }
        ausencias.forEach { agregarAusenciaCard(it) }

        // FAB visible solo si hay algún fichaje abierto (sin salida) y sin pausa activa
        val fichajeAbierto   = fichajes.any { it.horaSalida == null }
        val tienePausaActiva = pausas.any { it.horaFin == null }
        binding.fab.isVisible = fichajes.isNotEmpty() && fichajeAbierto && !tienePausaActiva
        binding.fab.setOnClickListener { navegarACrearPausa() }
    }

    private fun agregarFichajeCard(fichaje: FichajeResponse) {
        val horaEntrada = soloHora(fichaje.horaEntrada)
        val horaSalida  = soloHora(fichaje.horaSalida)
        val valor = "${horaEntrada ?: "--:--"} → ${horaSalida ?: "En curso"}"
        val duracion = if (fichaje.jornadaEfectivaMinutos > 0)
            formatearMinutos(fichaje.jornadaEfectivaMinutos) else ""

        val item = inflarItem(
            label    = "Jornada · ${tipoFichajeNombre(fichaje.tipo)}",
            valor    = valor,
            duracion = duracion,
            color    = Color.parseColor("#4CAF50")
        )
        item.root.setOnClickListener {
            val args = Bundle().apply {
                putString("variante", "FICHAJE")
                putLong("fichajeId", fichaje.id)
                putLong("empleadoId", fichaje.empleadoId)
                putString("fecha", fichaje.fecha)
                putString("tipo", fichaje.tipo.name)
                horaEntrada?.let { putString("horaEntrada", it) }
                horaSalida?.let  { putString("horaSalida", it) }
                fichaje.observaciones?.let { putString("observaciones", it) }
            }
            findNavController().navigate(R.id.action_detalle_dia_to_form_fichaje, args)
        }
        binding.containerEventos.addView(item.root)
    }

    private fun agregarPausaCard(pausa: PausaResponse) {
        val activa    = pausa.horaFin == null
        val horaInicio = soloHora(pausa.horaInicio) ?: pausa.horaInicio
        val horaFin    = soloHora(pausa.horaFin)
        val valor    = "$horaInicio → ${horaFin ?: "Activa"}"
        val duracion = pausa.duracionMinutos?.let { "${it}min" } ?: ""
        val color    = if (activa) Color.parseColor("#F44336") else Color.parseColor("#FF9800")

        val item = inflarItem(
            label    = "Pausa · ${tipoPausaNombre(pausa.tipoPausa)}",
            valor    = valor,
            duracion = duracion,
            color    = color
        )
        item.root.setOnClickListener {
            val args = Bundle().apply {
                putString("variante", "PAUSA")
                putLong("pausaId", pausa.id)
                putLong("empleadoId", pausa.empleadoId)
                putString("fecha", pausa.fecha)
                putString("tipoPausa", pausa.tipoPausa.name)
                putString("horaInicio", horaInicio)
                horaFin?.let             { putString("horaFin", it) }
                pausa.observaciones?.let { putString("observaciones", it) }
            }
            findNavController().navigate(R.id.action_detalle_dia_to_form_fichaje, args)
        }
        binding.containerEventos.addView(item.root)
    }

    private fun agregarAusenciaCard(ausencia: AusenciaResponse) {
        val estado = if (ausencia.procesado) "Procesada" else "Pendiente"
        val item = inflarItem(
            label    = "Ausencia · ${tipoAusenciaNombre(ausencia.tipoAusencia)}",
            valor    = estado,
            duracion = "",
            color    = android.graphics.Color.parseColor("#9C27B0")
        )
        item.root.setOnClickListener {
            val args = Bundle().apply {
                putLong("ausenciaId", ausencia.id)
                putLong("empleadoId", ausencia.empleadoId ?: empleadoId)
                putString("fecha", ausencia.fecha)
                putBoolean("procesado", ausencia.procesado)
            }
            findNavController().navigate(R.id.action_detalle_dia_to_form_ausencia, args)
        }
        binding.containerEventos.addView(item.root)
    }

    private fun agregarEstadoVacio() {
        val tv = TextView(requireContext()).apply {
            text = "Sin registros para este día"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 64, 0, 24)
        }
        binding.containerEventos.addView(tv)

        val btn = MaterialButton(requireContext()).apply {
            text = "Crear fichaje manual"
            setOnClickListener {
                val args = Bundle().apply {
                    putString("variante", "FICHAJE")
                    putLong("empleadoId", empleadoId)
                    putString("fecha", fecha)
                }
                findNavController().navigate(R.id.action_detalle_dia_to_form_fichaje, args)
            }
        }
        binding.containerEventos.addView(btn)
    }

    private fun navegarACrearPausa() {
        val args = Bundle().apply {
            putString("variante", "PAUSA")
            putLong("empleadoId", empleadoId)
            putString("fecha", fecha)
        }
        findNavController().navigate(R.id.action_detalle_dia_to_form_fichaje, args)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun inflarItem(label: String, valor: String, duracion: String, color: Int): ItemEventoDiaBinding {
        val item = ItemEventoDiaBinding.inflate(layoutInflater, binding.containerEventos, false)
        item.viewIndicador.setBackgroundColor(color)
        item.tvLabel.text    = label
        item.tvValor.text    = valor
        item.tvDuracion.text = duracion
        return item
    }

    /** Extrae solo HH:mm:ss de un ISO datetime ("2026-04-18T08:30:00" -> "08:30:00"). */
    private fun soloHora(valor: String?): String? {
        if (valor == null) return null
        return if (valor.contains("T")) valor.substringAfter("T").take(8) else valor.take(8)
    }

    private fun formatearMinutos(minutos: Int): String {
        val h = minutos / 60
        val m = minutos % 60
        return "${h}h ${m.toString().padStart(2, '0')}m"
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

    private fun tipoAusenciaNombre(tipo: TipoAusencia): String = when (tipo) {
        TipoAusencia.FESTIVO_NACIONAL        -> "Festivo nacional"
        TipoAusencia.FESTIVO_LOCAL           -> "Festivo local"
        TipoAusencia.VACACIONES              -> "Vacaciones"
        TipoAusencia.ASUNTO_PROPIO           -> "Asunto propio"
        TipoAusencia.PERMISO_RETRIBUIDO      -> "Permiso retribuido"
        TipoAusencia.DIA_LIBRE_COMPENSATORIO -> "Día libre compensatorio"
        TipoAusencia.DIA_LIBRE               -> "Día libre"
    }

    private fun tipoPausaNombre(tipo: TipoPausa): String = when (tipo) {
        TipoPausa.COMIDA              -> "Comida"
        TipoPausa.DESCANSO            -> "Descanso"
        TipoPausa.AUSENCIA_RETRIBUIDA -> "Ausencia retribuida"
        TipoPausa.OTROS               -> "Otros"
    }
}
