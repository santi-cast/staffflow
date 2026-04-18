package com.staffflow.android.ui.encargado

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.CalendarConstraints
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentAusenciasBinding
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Lista de ausencias planificadas (P23). Accesible para ENCARGADO y ADMIN.
 *
 * Patron D - lista paginada. Endpoint: E33 GET /ausencias
 *
 * Cuatro estados:
 *   Loading -> skeleton list
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Empty   -> icono + mensaje sin datos
 *   Success -> RecyclerView con pull-to-refresh
 *
 * Si se recibe empleadoId como argumento de navegacion (desde P14), filtra por ese empleado.
 * FAB (+) navega a P24 (FormAusenciaFragment) en modo alta.
 * Selector de rango de fechas en toolbar.
 * Tap en fila -> P24 en modo edicion con los datos de la ausencia.
 */
class AusenciasFragment : Fragment() {

    private var _binding: FragmentAusenciasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AusenciasViewModel by viewModels()
    private lateinit var adapter: AusenciaAdapter

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAusenciasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarMenu()
        configurarListeners()
        observarViewModel()
        val empleadoId = arguments?.getLong("empleadoId", -1L) ?: -1L
        viewModel.init(empleadoId)
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
        adapter = AusenciaAdapter { ausencia ->
            val args = Bundle().apply {
                putLong("ausenciaId", ausencia.id)
                ausencia.empleadoId?.let { putLong("empleadoId", it) }
                putString("fecha", ausencia.fecha)
                putString("tipoAusencia", ausencia.tipoAusencia.name)
                putBoolean("procesado", ausencia.procesado)
                ausencia.observaciones?.let { putString("observaciones", it) }
            }
            findNavController().navigate(R.id.action_ausencias_to_form_ausencia, args)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun configurarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_ausencias, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                if (item.itemId == R.id.action_rango_fechas_ausencias) {
                    mostrarSelectorRango()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun configurarListeners() {
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.reintentar() }
        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_ausencias_to_form_ausencia)
        }
    }

    // ------------------------------------------------------------------
    // Selector de rango de fechas
    // ------------------------------------------------------------------

    private fun mostrarSelectorRango() {
        val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder
            .dateRangePicker()
            .setTitleText(getString(R.string.ausencias_selector_rango_titulo))
            .setCalendarConstraints(CalendarConstraints.Builder().setFirstDayOfWeek(Calendar.MONDAY).build())
            .build()

        picker.addOnPositiveButtonClickListener { rango ->
            val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val desde = java.time.Instant.ofEpochMilli(rango.first)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
            val hasta = java.time.Instant.ofEpochMilli(rango.second)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
            viewModel.setRangoFechas(desde, hasta)
        }

        picker.show(parentFragmentManager, "rango_ausencias")
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

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    private fun procesarEstado(estado: AusenciasViewModel.UiState) {
        if (estado !is AusenciasViewModel.UiState.Loading) {
            binding.swipeRefresh.isRefreshing = false
        }

        binding.layoutSkeleton.isVisible = estado is AusenciasViewModel.UiState.Loading
        binding.layoutError.isVisible    = estado is AusenciasViewModel.UiState.Error
        binding.layoutVacio.isVisible    = estado is AusenciasViewModel.UiState.Empty
        binding.swipeRefresh.isVisible   = estado is AusenciasViewModel.UiState.Success

        when (estado) {
            is AusenciasViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            is AusenciasViewModel.UiState.Success -> adapter.submitList(estado.ausencias)
            else -> Unit
        }
    }
}
