package com.staffflow.android.ui.empleado

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.CalendarConstraints
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentMisAusenciasBinding
import com.staffflow.android.ui.encargado.AusenciaAdapter
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Lista de ausencias planificadas del empleado autenticado (P11). Solo lectura.
 *
 * Endpoint: E34 GET /ausencias/me
 *
 * Cuatro estados:
 *   Loading -> skeleton list
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Empty   -> icono + mensaje sin datos
 *   Success -> RecyclerView con pull-to-refresh
 *
 * Selector de rango de fechas en toolbar (MaterialDatePicker rango).
 * No tiene FAB — el empleado no puede crear ni editar ausencias directamente.
 */
class MisAusenciasFragment : Fragment() {

    private var _binding: FragmentMisAusenciasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MisAusenciasViewModel by viewModels()
    private lateinit var adapter: AusenciaAdapter

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMisAusenciasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarMenu()
        configurarListeners()
        observarViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarRecyclerView() {
        // Solo lectura: el tap no navega a ningún lado
        adapter = AusenciaAdapter { /* no-op */ }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun configurarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_mis_ausencias, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                if (item.itemId == R.id.action_rango_fechas_mis_ausencias) {
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
    }

    // ------------------------------------------------------------------
    // Selector de rango de fechas
    // ------------------------------------------------------------------

    private fun mostrarSelectorRango() {
        val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder
            .dateRangePicker()
            .setTitleText(getString(R.string.mis_ausencias_selector_rango_titulo))
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

        picker.show(parentFragmentManager, "rango_mis_ausencias")
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

    private fun procesarEstado(estado: MisAusenciasViewModel.UiState) {
        if (estado !is MisAusenciasViewModel.UiState.Loading) {
            binding.swipeRefresh.isRefreshing = false
        }

        binding.layoutSkeleton.isVisible = estado is MisAusenciasViewModel.UiState.Loading
        binding.layoutError.isVisible    = estado is MisAusenciasViewModel.UiState.Error
        binding.layoutVacio.isVisible    = estado is MisAusenciasViewModel.UiState.Empty
        binding.swipeRefresh.isVisible   = estado is MisAusenciasViewModel.UiState.Success

        when (estado) {
            is MisAusenciasViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            is MisAusenciasViewModel.UiState.Success -> adapter.submitList(estado.ausencias)
            else -> Unit
        }
    }
}
