package com.staffflow.android.ui.empleado

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.staffflow.android.data.remote.dto.DetallePresenciaResponse
import com.staffflow.android.databinding.FragmentMiHoyBinding
import com.staffflow.android.databinding.ItemEventoDiaBinding
import com.staffflow.android.domain.model.EstadoPresencia
import com.staffflow.android.domain.model.TipoFichaje
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mi hoy (P12). Destino inicial del rol EMPLEADO.
 *
 * Patron C - dato unico, empleado autenticado. Endpoint: E37.
 *
 * Tres estados: Loading | Error | Success.
 * En Success replica el estilo visual del parte diario (P17):
 *   cabecera de estado + eventos del dia (fichaje, pausas, ausencias)
 *   inflados dinamicamente con item_evento_dia. Solo visual, sin edicion.
 */
class MiHoyFragment : Fragment() {

    private var _binding: FragmentMiHoyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MiHoyViewModel by viewModels()

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiHoyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
        actualizarTituloConFecha()
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

    private fun procesarEstado(estado: MiHoyViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is MiHoyViewModel.UiState.Loading
        binding.layoutError.isVisible        = estado is MiHoyViewModel.UiState.Error
        binding.scrollContenido.isVisible    = estado is MiHoyViewModel.UiState.Success

        when (estado) {
            is MiHoyViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            is MiHoyViewModel.UiState.Success -> mostrarPresencia(estado.presencia)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Relleno de datos
    // ------------------------------------------------------------------

    private fun mostrarPresencia(presencia: DetallePresenciaResponse) {
        val color = colorParaEstado(presencia.estado)

        // Cabecera
        binding.tvNombre.text = buildString {
            append(presencia.nombre)
            append(" ")
            append(presencia.apellido1)
            presencia.apellido2?.let { append(" $it") }
        }
        binding.tvEstado.text = nombreEstado(presencia.estado)
        binding.viewBordeEstado.setBackgroundColor(color)

        // Eventos
        binding.containerEventos.removeAllViews()
        var hayEventos = false

        // Fichaje
        if (presencia.fichajeId != null) {
            hayEventos = true
            val horaEntrada = soloHora(presencia.horaEntrada)
            val horaSalida  = soloHora(presencia.horaSalida)
            val tipo        = presencia.fichajeTipo ?: TipoFichaje.NORMAL
            if (horaSalida != null) {
                val pausasSeg = presencia.pausas
                    ?.filter { it.horaFin != null }
                    ?.sumOf { calcularSegundos(it.horaInicio, it.horaFin) } ?: 0L
                val duracion = formatearSegundos(
                    maxOf(0L, calcularSegundos(horaEntrada, horaSalida) - pausasSeg))
                agregarEvento("Jornada · ${tipoFichajeNombre(tipo)}",
                    "$horaEntrada → $horaSalida", duracion, colorFichaje(tipo, completada = true))
            } else {
                agregarEvento("Inicio jornada · ${tipoFichajeNombre(tipo)}",
                    horaEntrada ?: "--:--", "", colorFichaje(tipo, completada = false))
            }
        }

        // Pausas
        presencia.pausas?.forEach { pausa ->
            hayEventos = true
            val activa     = pausa.horaFin == null
            val colorPausa = if (activa) Color.parseColor("#FFC107")
                             else        Color.parseColor("#4CAF50")
            if (!activa) {
                val duracion = formatearSegundos(calcularSegundos(pausa.horaInicio, pausa.horaFin))
                agregarEvento("Pausa · ${tipoPausaNombre(pausa.tipoPausa)}",
                    "${pausa.horaInicio ?: "--"} → ${pausa.horaFin}", duracion, colorPausa)
            } else {
                agregarEvento("Inicio pausa · ${tipoPausaNombre(pausa.tipoPausa)}",
                    pausa.horaInicio ?: "--", "", colorPausa)
            }
        }

        // Ausencia planificada
        if (presencia.ausenciaId != null && presencia.estado == EstadoPresencia.AUSENCIA_PLANIFICADA) {
            hayEventos = true
            agregarEvento("Ausencia planificada", "Pendiente de procesar",
                "", Color.parseColor("#4CAF50"))
        }

        // Sin registros
        if (!hayEventos) {
            val tv = android.widget.TextView(requireContext()).apply {
                text = "Sin registros"
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(Color.parseColor("#9E9E9E"))
                setPadding(0, 16, 0, 8)
            }
            binding.containerEventos.addView(tv)
        }
    }

    // ------------------------------------------------------------------
    // Helper: infla item_evento_dia y lo añade al container (sin click)
    // ------------------------------------------------------------------

    private fun agregarEvento(label: String, valor: String, duracion: String, color: Int) {
        val evento = ItemEventoDiaBinding.inflate(
            layoutInflater, binding.containerEventos, false)
        evento.viewIndicador.setBackgroundColor(color)
        evento.tvLabel.text    = label
        evento.tvValor.text    = valor
        evento.tvDuracion.text = duracion
        binding.containerEventos.addView(evento.root)
    }

    // ------------------------------------------------------------------
    // Helpers de color, texto y formato
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

    private fun tipoPausaNombre(tipo: String?): String = when (tipo) {
        "COMIDA"              -> "Comida"
        "DESCANSO"            -> "Descanso"
        "AUSENCIA_RETRIBUIDA" -> "Ausencia retribuida"
        "OTROS"               -> "Otros"
        else                  -> tipo ?: "--"
    }

    private fun calcularSegundos(inicio: String?, fin: String?): Long {
        if (inicio == null || fin == null) return 0L
        return try {
            val (hI, mI, sI) = inicio.split(":").map { it.toLong() }
            val (hF, mF, sF) = fin.split(":").map { it.toLong() }
            val diff = (hF * 3600 + mF * 60 + sF) - (hI * 3600 + mI * 60 + sI)
            maxOf(0L, diff)
        } catch (e: Exception) { 0L }
    }

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

    private fun soloHora(valor: String?): String? {
        if (valor == null) return null
        return if (valor.contains("T")) valor.substringAfter("T").take(8) else valor.take(8)
    }

    private fun actualizarTituloConFecha() {
        val fmt = SimpleDateFormat("d 'de' MMMM", Locale("es"))
        requireActivity().title = "Hoy · ${fmt.format(Date())}"
    }
}
