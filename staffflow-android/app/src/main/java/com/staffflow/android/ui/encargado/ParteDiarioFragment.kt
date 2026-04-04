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
import com.google.android.material.datepicker.MaterialDatePicker
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentParteDiarioBinding
import com.staffflow.android.data.remote.dto.ParteDiarioResponse
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 * Selector de fecha en toolbar: MaterialDatePicker.
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
        adapter = PresenciaAdapter { detalle ->
            // Tap en fila -> P14 DetalleEmpleadoFragment (Bloque 3)
            findNavController().navigate(R.id.action_parte_diario_to_detalle_empleado)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun configurarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_parte_diario, menu)
                menu.findItem(R.id.action_fecha)?.title = formatearFechaDisplay(viewModel.fecha.value)
            }
            override fun onMenuItemSelected(item: MenuItem): Boolean {
                if (item.itemId == R.id.action_fecha) {
                    mostrarSelectorFecha()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun configurarListeners() {
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.reintentar()
        }

        binding.chipSinJustificar.setOnClickListener {
            findNavController().navigate(R.id.action_parte_diario_to_sin_justificar)
        }
    }

    // ------------------------------------------------------------------
    // Selector de fecha
    // ------------------------------------------------------------------

    private fun mostrarSelectorFecha() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.parte_diario_selector_fecha_titulo)
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { millis ->
            val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
            viewModel.setFecha(fecha)
            requireActivity().invalidateOptionsMenu()
        }

        datePicker.show(parentFragmentManager, "date_picker")
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
        binding.chipFichados.text = "Fichados: ${data.fichados}"
        binding.chipEnPausa.text = "En pausa: ${data.enPausa}"
        binding.chipAusencias.text = "Ausencias: ${data.ausencias}"
        binding.chipSinJustificar.text = "Sin justificar: ${data.sinJustificar}"

        // Destacar chip sin justificar si hay empleados
        binding.chipSinJustificar.isEnabled = data.sinJustificar > 0

        // Lista
        adapter.submitList(data.detalle)
    }

    // ------------------------------------------------------------------
    // Helpers de formato
    // ------------------------------------------------------------------

    /** Convierte "yyyy-MM-dd" a "dd/MM/yyyy" para mostrar en la toolbar. */
    private fun formatearFechaDisplay(fechaIso: String): String {
        return try {
            val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfOut = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdfOut.format(sdfIn.parse(fechaIso)!!)
        } catch (e: Exception) {
            fechaIso
        }
    }
}
