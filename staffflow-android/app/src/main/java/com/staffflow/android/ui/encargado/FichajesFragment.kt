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
import com.google.android.material.datepicker.MaterialDatePicker
import com.staffflow.android.R
import java.util.Calendar
import com.staffflow.android.databinding.FragmentFichajesBinding
import kotlinx.coroutines.launch

/**
 * Lista de fichajes (P19). Accesible para ENCARGADO y ADMIN.
 *
 * Patron D - lista paginada solo lectura. Endpoints: E24 GET /fichajes, E25 GET /fichajes/incompletos
 *
 * Cuatro estados:
 *   Loading -> skeleton list
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Empty   -> icono + mensaje sin datos
 *   Success -> RecyclerView con pull-to-refresh
 *
 * Si se recibe empleadoId como argumento de navegacion (desde P14), filtra por ese empleado.
 * FAB (+) navega a P20 (FormFichajeFragment) en modo alta.
 * Selector de rango de fechas en toolbar (MaterialDatePicker rango).
 * Tap en fila -> P20 en modo edicion con el fichajeId como argumento.
 */
class FichajesFragment : Fragment() {

    private var _binding: FragmentFichajesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FichajesViewModel by viewModels()
    private lateinit var adapter: FichajeAdapter

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFichajesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarMenu()
        configurarListeners()
        observarViewModel()
        // Inicializa con el empleadoId del argumento (o -1 si viene del Drawer)
        val empleadoId = arguments?.getLong("empleadoId", -1L) ?: -1L
        viewModel.init(empleadoId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarRecyclerView() {
        val empleadoId = arguments?.getLong("empleadoId", -1L) ?: -1L
        adapter = FichajeAdapter(mostrarNombre = empleadoId == -1L) { fichaje ->
            // Pasamos todos los campos porque no existe GET /fichajes/{id}
            val args = Bundle().apply {
                putString("variante", "FICHAJE")
                putLong("fichajeId", fichaje.id)
                putLong("empleadoId", fichaje.empleadoId)
                putString("fecha", fichaje.fecha)
                putString("tipo", fichaje.tipo.name)
                fichaje.horaEntrada?.let { putString("horaEntrada", it) }
                fichaje.horaSalida?.let { putString("horaSalida", it) }
                fichaje.observaciones?.let { putString("observaciones", it) }
            }
            findNavController().navigate(R.id.action_fichajes_to_form_fichaje, args)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun configurarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_fichajes, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                if (item.itemId == R.id.action_rango_fechas) {
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
            val args = Bundle().apply { putString("variante", "FICHAJE") }
            findNavController().navigate(R.id.action_fichajes_to_form_fichaje, args)
        }
    }

    // ------------------------------------------------------------------
    // Selector de rango de fechas
    // ------------------------------------------------------------------

    private fun mostrarSelectorRango() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.fichajes_selector_rango_titulo))
            .setCalendarConstraints(CalendarConstraints.Builder().setFirstDayOfWeek(Calendar.MONDAY).build())
            .build()

        picker.addOnPositiveButtonClickListener { rango ->
            val desde = java.time.Instant.ofEpochMilli(rango.first)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val hasta = java.time.Instant.ofEpochMilli(rango.second)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            viewModel.setRangoFechas(desde, hasta)
        }

        picker.show(parentFragmentManager, "rango_fechas")
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

    private fun procesarEstado(estado: FichajesViewModel.UiState) {
        if (estado !is FichajesViewModel.UiState.Loading) {
            binding.swipeRefresh.isRefreshing = false
        }

        binding.layoutSkeleton.isVisible  = estado is FichajesViewModel.UiState.Loading
        binding.layoutError.isVisible     = estado is FichajesViewModel.UiState.Error
        binding.layoutVacio.isVisible     = estado is FichajesViewModel.UiState.Empty
        binding.swipeRefresh.isVisible    = estado is FichajesViewModel.UiState.Success

        when (estado) {
            is FichajesViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            is FichajesViewModel.UiState.Success -> adapter.submitList(estado.fichajes)
            else -> Unit
        }
    }
}
