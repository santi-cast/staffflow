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
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentSaldosGlobalesBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Saldos globales de todos los empleados (P26).
 *
 * Patron WebView. Endpoint: E44 GET /informes/saldos?anio=&formato=html
 * Roles: ENCARGADO y ADMIN.
 *
 * Tres estados: Loading | Error | Success (WebView con HTML del informe).
 * El selector de año en la toolbar recarga el informe al cambiar el año.
 */
class SaldosGlobalesFragment : Fragment() {

    private var _binding: FragmentSaldosGlobalesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SaldosGlobalesViewModel by viewModels()

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
        configurarWebView()
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

    private fun configurarWebView() {
        binding.webView.settings.useWideViewPort      = true
        binding.webView.settings.loadWithOverviewMode = true
    }

    private fun configurarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_saldos_globales, menu)
                val chip = menu.findItem(R.id.action_anio)
                    ?.actionView?.findViewById<Chip>(R.id.chipAnio)
                chip?.text = "${viewModel.anio.value} ▾"
                chip?.setOnClickListener { mostrarSelectorAnio() }
            }
            override fun onMenuItemSelected(item: MenuItem) = false
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
        binding.progressIndicator.isVisible = estado is SaldosGlobalesViewModel.UiState.Loading
        binding.layoutError.isVisible       = estado is SaldosGlobalesViewModel.UiState.Error
                                           || estado is SaldosGlobalesViewModel.UiState.Empty
        binding.webView.isVisible           = estado is SaldosGlobalesViewModel.UiState.Success

        when (estado) {
            is SaldosGlobalesViewModel.UiState.Error -> {
                binding.tvErrorMensaje.text = estado.mensaje
                binding.btnReintentar.isVisible = true
            }
            is SaldosGlobalesViewModel.UiState.Empty -> {
                binding.tvErrorMensaje.text = "No hay datos de saldo para el año ${estado.anio}."
                binding.btnReintentar.isVisible = false
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(2500)
                    val anioActual = Calendar.getInstance().get(Calendar.YEAR)
                    viewModel.setAnio(anioActual)
                    requireActivity().invalidateOptionsMenu()
                }
            }
            is SaldosGlobalesViewModel.UiState.Success ->
                binding.webView.loadDataWithBaseURL(null, estado.html, "text/html", "UTF-8", null)
            else -> Unit
        }
    }
}
