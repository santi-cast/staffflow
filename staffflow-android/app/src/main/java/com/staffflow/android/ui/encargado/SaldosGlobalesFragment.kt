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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentSaldosGlobalesBinding
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Saldos globales de todos los empleados (P27).
 *
 * Patron D - lista solo lectura. Endpoint: E39 GET /saldos?anio=
 * Roles: ENCARGADO y ADMIN.
 *
 * Cuatro estados: Loading (skeleton) | Error | Empty | Success.
 * Selector de año en la toolbar: muestra el año actual y permite cambiar.
 * Tap en fila navega a P26 (SaldoIndividualFragment) con el empleadoId.
 *
 * SaldoResponse incluye nombreCompleto para mostrar directamente en cada fila.
 */
class SaldosGlobalesFragment : Fragment() {

    private var _binding: FragmentSaldosGlobalesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SaldosGlobalesViewModel by viewModels()
    private lateinit var adapter: SaldoGlobalAdapter

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSaldosGlobalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
        configurarMenu()
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
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
        adapter = SaldoGlobalAdapter(
            onItemClick = { empleadoId ->
                val bundle = Bundle().apply { putLong("empleadoId", empleadoId) }
                findNavController().navigate(R.id.action_saldos_globales_to_saldo_individual, bundle)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun configurarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_saldos_globales, menu)
                menu.findItem(R.id.action_anio)?.title = viewModel.anio.value.toString()
            }
            override fun onMenuItemSelected(item: MenuItem): Boolean {
                if (item.itemId == R.id.action_anio) {
                    mostrarSelectorAnio()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun mostrarSelectorAnio() {
        val anioActual = Calendar.getInstance().get(Calendar.YEAR)
        val anios = ((anioActual - 2)..(anioActual + 1)).map { it.toString() }.toTypedArray()
        val seleccionado = anios.indexOf(viewModel.anio.value.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.saldo_selector_anio_titulo)
            .setSingleChoiceItems(anios, seleccionado) { dialogo, indice ->
                viewModel.setAnio(anios[indice].toInt())
                requireActivity().invalidateOptionsMenu()
                dialogo.dismiss()
            }
            .show()
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

    private fun procesarEstado(estado: SaldosGlobalesViewModel.UiState) {
        val exito = estado is SaldosGlobalesViewModel.UiState.Success
        binding.layoutHeader.isVisible   = exito
        binding.layoutSkeleton.isVisible = estado is SaldosGlobalesViewModel.UiState.Loading
        binding.layoutError.isVisible    = estado is SaldosGlobalesViewModel.UiState.Error
        binding.layoutVacio.isVisible    = estado is SaldosGlobalesViewModel.UiState.Empty
        binding.recyclerView.isVisible   = exito

        when (estado) {
            is SaldosGlobalesViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            is SaldosGlobalesViewModel.UiState.Success -> adapter.submitList(estado.saldos)
            else -> Unit
        }
    }
}
