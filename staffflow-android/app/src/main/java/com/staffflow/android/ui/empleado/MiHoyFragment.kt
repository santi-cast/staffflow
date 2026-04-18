package com.staffflow.android.ui.empleado

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
import com.google.android.material.color.MaterialColors
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.DetallePresenciaResponse
import com.staffflow.android.databinding.FragmentMiHoyBinding
import com.staffflow.android.databinding.ItemSaldoFilaBinding
import com.staffflow.android.domain.model.EstadoPresencia
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
 * En Success muestra el estado de la jornada actual del empleado:
 *   SIN_JUSTIFICAR       -> "Sin fichaje de entrada hoy" (solo card estado)
 *   JORNADA_INICIADA     -> hora de entrada (card horario)
 *   EN_PAUSA             -> hora de entrada + indicador de pausa activa
 *   JORNADA_COMPLETADA   -> hora entrada + hora salida + jornada total
 *   AUSENCIA_REGISTRADA
 *   AUSENCIA_PLANIFICADA -> (solo card estado con texto descriptivo)
 *
 * onResume recarga para tener datos frescos al volver de otra pantalla.
 * El ticker del ViewModel refresca automaticamente cada 60s cuando la
 * jornada esta en curso o en pausa.
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
        // Card estado: siempre visible en Success
        binding.tvEstado.text = textoEstado(presencia.estado)
        val colorEstado = colorEstado(presencia.estado)
        binding.tvEstado.setTextColor(colorEstado)

        // Card horario: solo cuando hay hora de entrada
        val horaEntrada = soloHora(presencia.horaEntrada)
        val horaSalida  = soloHora(presencia.horaSalida)

        binding.cardHorario.isVisible = horaEntrada != null

        if (horaEntrada != null) {
            setFila(binding.filaHoraEntrada, getString(R.string.mi_hoy_hora_entrada), horaEntrada)

            binding.filaHoraSalida.root.isVisible  = horaSalida != null
            binding.filaJornadaTotal.root.isVisible = horaSalida != null

            if (horaSalida != null) {
                setFila(binding.filaHoraSalida, getString(R.string.mi_hoy_hora_salida), horaSalida)
                setFila(binding.filaJornadaTotal, getString(R.string.mi_hoy_jornada_total),
                    calcularDuracion(horaEntrada, horaSalida))
            }
        }

        // Card pausa activa: indicador rapido cuando pausaActiva = true
        binding.cardPausa.isVisible = presencia.pausaActiva
        if (presencia.pausaActiva) {
            setFila(binding.filaPausaEstado, getString(R.string.mi_hoy_pausa_estado),
                getString(R.string.mi_hoy_pausa_activa))
        }

        // Card lista de pausas del dia
        mostrarPausas(presencia.pausas.orEmpty())
    }

    private fun setFila(fila: ItemSaldoFilaBinding, label: String, valor: String) {
        fila.tvLabel.text = label
        fila.tvValor.text = valor
    }

    // ------------------------------------------------------------------
    // Lista de pausas
    // ------------------------------------------------------------------

    private fun mostrarPausas(pausas: List<DetallePresenciaResponse.PausaResumen>) {
        binding.cardPausas.isVisible = pausas.isNotEmpty()
        if (pausas.isEmpty()) return

        binding.containerPausas.removeAllViews()
        pausas.forEach { pausa ->
            val fila = ItemSaldoFilaBinding.inflate(layoutInflater, binding.containerPausas, false)
            fila.tvLabel.text = tipoPausaNombre(pausa.tipoPausa)
            val inicio = pausa.horaInicio ?: "--"
            val fin    = pausa.horaFin ?: getString(R.string.mi_hoy_pausa_activa_label)
            val duracion = pausa.duracionMinutos?.let { " (${it}min)" } ?: ""
            fila.tvValor.text = "$inicio – $fin$duracion"
            binding.containerPausas.addView(fila.root)
        }
    }

    // ------------------------------------------------------------------
    // Helpers de formato y color
    // ------------------------------------------------------------------

    /** Muestra el titulo de la pantalla con la fecha de hoy. Ej: "Hoy · 4 de abril". */
    private fun actualizarTituloConFecha() {
        val fmt = SimpleDateFormat("d 'de' MMMM", Locale("es"))
        requireActivity().title = "Hoy · ${fmt.format(Date())}"
    }

    /** Extrae solo HH:mm de un ISO datetime ("2026-04-04T08:30:00" -> "08:30"). */
    private fun soloHora(valor: String?): String? {
        if (valor == null) return null
        return if (valor.contains("T")) valor.substringAfter("T").take(5) else valor
    }

    private fun tipoPausaNombre(tipo: String?): String = when (tipo) {
        "COMIDA"              -> getString(R.string.tipo_pausa_comida)
        "DESCANSO"            -> getString(R.string.tipo_pausa_descanso)
        "AUSENCIA_RETRIBUIDA" -> getString(R.string.tipo_pausa_ausencia_retribuida)
        "OTROS"               -> getString(R.string.tipo_pausa_otros)
        else                  -> tipo ?: "--"
    }

    private fun textoEstado(estado: EstadoPresencia): String = when (estado) {
        EstadoPresencia.SIN_JUSTIFICAR       -> getString(R.string.mi_hoy_estado_sin_justificar)
        EstadoPresencia.JORNADA_INICIADA     -> getString(R.string.mi_hoy_estado_jornada_iniciada)
        EstadoPresencia.EN_PAUSA             -> getString(R.string.mi_hoy_estado_en_pausa)
        EstadoPresencia.JORNADA_COMPLETADA   -> getString(R.string.mi_hoy_estado_jornada_completada)
        EstadoPresencia.AUSENCIA_REGISTRADA  -> getString(R.string.mi_hoy_estado_ausencia_registrada)
        EstadoPresencia.AUSENCIA_PLANIFICADA -> getString(R.string.mi_hoy_estado_ausencia_planificada)
    }

    private fun colorEstado(estado: EstadoPresencia): Int {
        val attr = when (estado) {
            EstadoPresencia.JORNADA_INICIADA,
            EstadoPresencia.JORNADA_COMPLETADA   -> com.google.android.material.R.attr.colorTertiary
            EstadoPresencia.EN_PAUSA             -> androidx.appcompat.R.attr.colorError
            EstadoPresencia.SIN_JUSTIFICAR       -> androidx.appcompat.R.attr.colorError
            else                                  -> com.google.android.material.R.attr.colorOnSurface
        }
        return MaterialColors.getColor(requireView(), attr, 0)
    }

    /**
     * Calcula la duracion entre dos horas en formato "HH:mm".
     * Devuelve la duracion como "Xh Ym" o "--" si el formato es invalido.
     */
    private fun calcularDuracion(horaEntrada: String, horaSalida: String): String {
        return try {
            val (hE, mE) = horaEntrada.split(":").map { it.toInt() }
            val (hS, mS) = horaSalida.split(":").map { it.toInt() }
            val totalMinutos = (hS * 60 + mS) - (hE * 60 + mE)
            if (totalMinutos < 0) "--"
            else "${totalMinutos / 60}h ${totalMinutos % 60}min"
        } catch (e: Exception) {
            "--"
        }
    }
}
