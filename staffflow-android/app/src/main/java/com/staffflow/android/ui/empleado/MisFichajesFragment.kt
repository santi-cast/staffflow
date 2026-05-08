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
 * Vista de fichajes del empleado autenticado (P10) — variante WebView.
 *
 * Endpoint: GET /api/v1/informes/me/horas?desde=&hasta=&formato=html
 *
 * Muestra el informe HTML del backend en un WebView.
 * Rango por defecto: semana actual de lunes a domingo.
 * El chip "chipPeriodo" abre MaterialDatePicker para seleccionar cualquier rango.
 */
class MisFichajesFragment : Fragment() {

    private var _binding: FragmentMisFichajesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MisFichajesViewModel by viewModels()

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

        picker.show(parentFragmentManager, "rango_mis_fichajes")
    }

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { procesarEstado(it) } }
                launch { viewModel.rangoLabel.collect { binding.chipPeriodo.text = "$it ▾" } }
            }
        }
    }

    private fun procesarEstado(estado: MisFichajesViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is MisFichajesViewModel.UiState.Loading
        binding.layoutError.isVisible       = estado is MisFichajesViewModel.UiState.Error
        binding.webView.isVisible           = estado is MisFichajesViewModel.UiState.Success

        when (estado) {
            is MisFichajesViewModel.UiState.Error -> binding.tvErrorMensaje.text = estado.mensaje
            is MisFichajesViewModel.UiState.Success ->
                binding.webView.loadDataWithBaseURL(null, estado.html, "text/html", "UTF-8", null)
            else -> Unit
        }
    }
}
