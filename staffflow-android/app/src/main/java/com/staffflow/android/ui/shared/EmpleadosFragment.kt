package com.staffflow.android.ui.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
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
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentEmpleadosBinding
import kotlinx.coroutines.launch

/**
 * Lista de empleados (P13). Accesible para ENCARGADO y ADMIN.
 *
 * Patron E - lista con busqueda. Endpoint: E14 GET /empleados?q=
 *
 * Cuatro estados:
 *   Loading -> skeleton list
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Empty   -> icono + "No hay empleados registrados"
 *   Success -> RecyclerView con pull-to-refresh
 *
 * SearchView en toolbar con debounce de 300ms (llamada al API por cada busqueda).
 * FAB (+): visible solo para ADMIN, navega a P15 (FormEmpleadoFragment).
 * Tap en fila -> P14 (action_empleados_to_detalle) con el empleadoId como argumento.
 */
class EmpleadosFragment : Fragment() {

    private var _binding: FragmentEmpleadosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmpleadosViewModel by viewModels()
    private lateinit var adapter: EmpleadoAdapter

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmpleadosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarMenu()
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
        adapter = EmpleadoAdapter { empleado ->
            val args = Bundle().apply { putLong("empleadoId", empleado.id) }
            findNavController().navigate(R.id.action_empleados_to_detalle, args)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun configurarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_empleados, menu)
                val searchItem = menu.findItem(R.id.action_search)
                val searchView = searchItem?.actionView as? SearchView ?: return
                searchView.queryHint = getString(R.string.empleados_buscar_hint)
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?) = false
                    override fun onQueryTextChange(newText: String?): Boolean {
                        viewModel.buscar(newText.orEmpty())
                        return true
                    }
                })
                // Al colapsar el SearchView recargamos la lista completa
                searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                    override fun onMenuItemActionExpand(item: MenuItem) = true
                    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                        viewModel.reintentar()
                        return true
                    }
                })
            }

            override fun onMenuItemSelected(item: MenuItem) = false
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun configurarListeners() {
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.reintentar() }
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

    private fun procesarEstado(estado: EmpleadosViewModel.UiState) {
        if (estado !is EmpleadosViewModel.UiState.Loading) {
            binding.swipeRefresh.isRefreshing = false
        }

        binding.layoutSkeleton.isVisible = estado is EmpleadosViewModel.UiState.Loading
        binding.layoutError.isVisible    = estado is EmpleadosViewModel.UiState.Error
        binding.layoutVacio.isVisible    = estado is EmpleadosViewModel.UiState.Empty
        binding.swipeRefresh.isVisible   = estado is EmpleadosViewModel.UiState.Success

        when (estado) {
            is EmpleadosViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            is EmpleadosViewModel.UiState.Success -> adapter.submitList(estado.empleados)
            else -> Unit
        }
    }

}
