package com.staffflow.android.ui.encargado

import android.content.res.ColorStateList
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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentParteDiarioBinding
import com.staffflow.android.data.remote.dto.ParteDiarioResponse
import kotlinx.coroutines.launch

/**
 * Parte diario de presencia (P17). Destino inicial del rol ENCARGADO.
 *
 * Patron D - lista solo lectura. Endpoint: E35 GET /presencia/parte-diario?fecha=
 *
 * Cuatro estados:
 *   Loading -> skeleton list con 5 items grises (sin spinner)
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Empty   -> icono + "No hay empleados registrados hoy"
 *   Success -> chips de resumen + RecyclerView con pull-to-refresh
 *
 * Chip "Sin justificar: N" -> action_parte_diario_to_sin_justificar (P18).
 * Tap en fila -> action_parte_diario_to_detalle_empleado (P14, Bloque 3).
 */
class ParteDiarioFragment : Fragment() {

    private var _binding: FragmentParteDiarioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParteDiarioViewModel by viewModels()
    private lateinit var adapter: PresenciaAdapter

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParteDiarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarListeners()
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
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarRecyclerView() {
        adapter = PresenciaAdapter(
            onFichajeClick = { fichajeId, empleadoId, tipo, horaEntrada, horaSalida ->
                val args = android.os.Bundle().apply {
                    putString("variante", "FICHAJE")
                    putLong("fichajeId", fichajeId)
                    putLong("empleadoId", empleadoId)
                    putString("fecha", viewModel.fecha.value)
                    putString("tipo", tipo.name)
                    horaEntrada?.let { putString("horaEntrada", it) }
                    horaSalida?.let  { putString("horaSalida", it) }
                }
                findNavController().navigate(R.id.action_parte_diario_to_form_fichaje, args)
            },
            onPausaClick = { pausaId, empleadoId, tipoPausa, horaInicio, horaFin ->
                val args = android.os.Bundle().apply {
                    putString("variante", "PAUSA")
                    putLong("pausaId", pausaId)
                    putLong("empleadoId", empleadoId)
                    putString("fecha", viewModel.fecha.value)
                    putString("tipoPausa", tipoPausa)
                    horaInicio?.let { putString("horaInicio", it) }
                    horaFin?.let    { putString("horaFin", it) }
                }
                findNavController().navigate(R.id.action_parte_diario_to_form_fichaje, args)
            },
            onAusenciaClick = { ausenciaId, empleadoId ->
                val args = android.os.Bundle().apply {
                    putLong("ausenciaId", ausenciaId)
                    putLong("empleadoId", empleadoId)
                    putString("fecha", viewModel.fecha.value)
                    putBoolean("procesado", false)
                }
                findNavController().navigate(R.id.action_parte_diario_to_form_ausencia, args)
            },
            onSinJustificarClick = { empleadoId ->
                val args = android.os.Bundle().apply {
                    putString("variante", "FICHAJE")
                    putLong("fichajeId", -1L)
                    putLong("empleadoId", empleadoId)
                    putString("fecha", viewModel.fecha.value)
                }
                findNavController().navigate(R.id.action_parte_diario_to_form_fichaje, args)
            },
            onRegistrarSalidaClick = { fichajeId, empleadoId, tipo, horaEntrada, pausaActiva ->
                if (pausaActiva) {
                    Snackbar.make(binding.root,
                        "El empleado tiene una pausa activa. Registrá el fin de pausa primero.",
                        Snackbar.LENGTH_LONG).show()
                } else {
                    val args = android.os.Bundle().apply {
                        putString("variante", "FICHAJE")
                        putLong("fichajeId", fichajeId)
                        putLong("empleadoId", empleadoId)
                        putString("fecha", viewModel.fecha.value)
                        putString("tipo", tipo.name)
                        horaEntrada?.let { putString("horaEntrada", it) }
                    }
                    findNavController().navigate(R.id.action_parte_diario_to_form_fichaje, args)
                }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun configurarListeners() {
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.reintentar()
        }

        binding.btnDesbloquearTerminal.setOnClickListener {
            mostrarDialogoDesbloquear()
        }
    }

    private fun mostrarDialogoDesbloquear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.parte_diario_dialogo_desbloquear_titulo)
            .setMessage(R.string.parte_diario_dialogo_desbloquear_mensaje)
            .setPositiveButton(R.string.parte_diario_btn_desbloquear) { _, _ ->
                viewModel.desbloquearTerminal()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { procesarEstado(it) } }
                launch { viewModel.terminalBloqueado.collect { bloqueado ->
                    binding.bannerTerminalBloqueado.isVisible = bloqueado
                }}
            }
        }
    }

    private fun procesarEstado(estado: ParteDiarioViewModel.UiState) {
        // Detener pull-to-refresh si estaba activo
        if (estado !is ParteDiarioViewModel.UiState.Loading) {
            binding.swipeRefresh.isRefreshing = false
        }

        binding.layoutSkeleton.isVisible = estado is ParteDiarioViewModel.UiState.Loading
        binding.layoutError.isVisible    = estado is ParteDiarioViewModel.UiState.Error
        binding.layoutVacio.isVisible    = estado is ParteDiarioViewModel.UiState.Empty
        binding.swipeRefresh.isVisible   = estado is ParteDiarioViewModel.UiState.Success
        binding.scrollChips.isVisible    = estado is ParteDiarioViewModel.UiState.Success

        when (estado) {
            is ParteDiarioViewModel.UiState.Error -> {
                binding.tvErrorMensaje.text = estado.mensaje
            }
            is ParteDiarioViewModel.UiState.Success -> {
                mostrarDatos(estado.data)
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Relleno de datos
    // ------------------------------------------------------------------

    private fun mostrarDatos(data: ParteDiarioResponse) {
        // Chips de resumen
        binding.chipTrabajando.text        = "Trabajando: ${data.trabajando}"
        binding.chipEnPausa.text          = "En pausa: ${data.enPausa}"
        binding.chipAusencias.text        = "Ausencias: ${data.ausencias}"
        binding.chipJornadaCompletada.text = "Completada: ${data.jornadaCompletada}"
        binding.chipSinJustificar.text    = "Sin justificar: ${data.sinJustificar}"

        // Chip estado del día
        if (data.totalEmpleados > 0) {
            binding.chipEstadoDia.isVisible = true
            val diaCompleto = data.sinJustificar == 0 && data.trabajando == 0 && data.enPausa == 0
            if (diaCompleto) {
                binding.chipEstadoDia.text = getString(R.string.parte_diario_chip_dia_completo)
                binding.chipEstadoDia.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
                binding.chipEstadoDia.setTextColor(Color.parseColor("#2E7D32"))
            } else {
                binding.chipEstadoDia.text = getString(R.string.parte_diario_chip_sin_completar)
                binding.chipEstadoDia.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
                binding.chipEstadoDia.setTextColor(Color.parseColor("#C62828"))
            }
        }

        // Lista
        adapter.submitList(data.detalle)
    }

}
