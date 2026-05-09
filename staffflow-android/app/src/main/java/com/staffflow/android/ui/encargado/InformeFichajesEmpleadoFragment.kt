package com.staffflow.android.ui.encargado

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
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentMisFichajesBinding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Informe de fichajes de un empleado específico (P21). WebView. Accesible por ADMIN y ENCARGADO.
 *
 * Endpoint: E42 GET /api/v1/informes/horas/{empleadoId}?desde=&hasta=&formato=html
 *
 * Reutiliza fragment_mis_fichajes.xml. Visualmente idéntico a MisFichajesFragment (P10).
 * Recibe empleadoId como argumento de navegación desde P14 (DetalleEmpleadoFragment).
 */
class InformeFichajesEmpleadoFragment : Fragment() {

    private var _binding: FragmentMisFichajesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InformeFichajesEmpleadoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMisFichajesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val empleadoId = arguments?.getLong("empleadoId", -1L) ?: -1L
        viewModel.init(empleadoId)
        configurarWebView()
        configurarListeners()
        observarViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun configurarWebView() {
        binding.webView.settings.useWideViewPort = true
        binding.webView.settings.loadWithOverviewMode = true
    }

    private fun configurarListeners() {
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
        binding.chipPeriodo.setOnClickListener { mostrarSelectorRango() }
    }

    private fun mostrarSelectorRango() {
        val picker = MaterialDatePicker.Builder
            .dateRangePicker()
            .setTitleText(getString(R.string.mis_fichajes_selector_rango_titulo))
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setFirstDayOfWeek(Calendar.MONDAY)
                    .build()
            )
            .build()

        picker.addOnPositiveButtonClickListener { rango ->
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val zone = ZoneId.systemDefault()
            val desde = Instant.ofEpochMilli(rango.first).atZone(zone).toLocalDate().format(fmt)
            val hasta = Instant.ofEpochMilli(rango.second).atZone(zone).toLocalDate().format(fmt)
            viewModel.setRangoFechas(desde, hasta)
        }

        picker.show(parentFragmentManager, "rango_informe_fichajes")
    }

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { procesarEstado(it) } }
                launch { viewModel.rangoLabel.collect { binding.chipPeriodo.text = "$it ▾" } }
            }
        }
    }

    private fun procesarEstado(estado: InformeFichajesEmpleadoViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is InformeFichajesEmpleadoViewModel.UiState.Loading
        binding.layoutError.isVisible       = estado is InformeFichajesEmpleadoViewModel.UiState.Error
        binding.webView.isVisible           = estado is InformeFichajesEmpleadoViewModel.UiState.Success

        when (estado) {
            is InformeFichajesEmpleadoViewModel.UiState.Error ->
                binding.tvErrorMensaje.text = estado.mensaje
            is InformeFichajesEmpleadoViewModel.UiState.Success ->
                binding.webView.loadDataWithBaseURL(null, estado.html, "text/html", "UTF-8", null)
            else -> Unit
        }
    }
}
