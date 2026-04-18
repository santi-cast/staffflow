package com.staffflow.android.ui.encargado

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentInformesBinding
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Pantalla de informes con 3 tabs (P28). ADMIN y ENCARGADO.
 *
 * Tab 0 - Horas individual:
 *   Seleccionar empleado (dropdown) + rango fechas -> HTML auto-carga en WebView (E42).
 *   "Imprimir informe" -> E45 PDF -> Intent ACTION_VIEW (visor del sistema).
 *
 * Tab 1 - Horas global:
 *   Seleccionar rango fechas -> HTML auto-carga en WebView (E43).
 *   "Imprimir informe" -> E46 PDF -> Intent ACTION_VIEW.
 *
 * Tab 2 - Saldos / Vacaciones:
 *   Cambiar año -> HTML saldos auto-carga en WebView (E44).
 *   "Imprimir saldos"     -> E47 PDF -> Intent ACTION_VIEW.
 *   Seleccionar empleado + "Imprimir vacaciones" -> E53 PDF -> Intent ACTION_VIEW.
 */
class InformesFragment : Fragment() {

    private var _binding: FragmentInformesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InformesViewModel by viewModels()

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Rangos de fechas por tab
    private var desdeIndividual: String = LocalDate.now().withDayOfMonth(1).format(fmt)
    private var hastaIndividual: String = LocalDate.now().format(fmt)
    private var desdeGlobal: String     = LocalDate.now().withDayOfMonth(1).format(fmt)
    private var hastaGlobal: String     = LocalDate.now().format(fmt)
    private var anioSaldos: Int         = LocalDate.now().year

    // IDs seleccionados en los dropdowns de empleado
    private var empleadoIdIndividual: Long? = null
    private var empleadoIdVacaciones: Long? = null

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInformesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarTabs()
        configurarListeners()
        observarViewModel()
        observarEmpleados()
        mostrarTab(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    private fun configurarTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.resetear()
                ocultarWebViews()
                mostrarTab(tab.position)
                // Al entrar al tab de saldos, mostrar el HTML del año actual
                if (tab.position == 2) viewModel.verSaldos(anioSaldos)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun mostrarTab(index: Int) {
        binding.tabHorasIndividual.isVisible = index == 0
        binding.tabHorasGlobal.isVisible     = index == 1
        binding.tabSaldos.isVisible          = index == 2
    }

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    private fun configurarListeners() {
        // -- Tab 0: Horas individual --
        binding.btnRangoIndividual.setOnClickListener {
            mostrarRangoPicker { desde, hasta ->
                desdeIndividual = desde
                hastaIndividual = hasta
                binding.tvRangoIndividual.text = "$desde  —  $hasta"
                // Auto-cargar HTML si ya hay empleado seleccionado
                empleadoIdIndividual?.let { id ->
                    ocultarWebViews()
                    viewModel.verHorasEmpleado(id, desdeIndividual, hastaIndividual)
                }
            }
        }
        binding.btnImprimirIndividual.setOnClickListener {
            val id = empleadoIdIndividual ?: return@setOnClickListener
            viewModel.descargarPdfHorasEmpleado(id, desdeIndividual, hastaIndividual)
        }

        // -- Tab 1: Horas global --
        binding.btnRangoGlobal.setOnClickListener {
            mostrarRangoPicker { desde, hasta ->
                desdeGlobal = desde
                hastaGlobal = hasta
                binding.tvRangoGlobal.text = "$desde  —  $hasta"
                // Auto-cargar HTML al confirmar fechas
                ocultarWebViews()
                viewModel.verHorasGlobal(desdeGlobal, hastaGlobal)
            }
        }
        binding.btnImprimirGlobal.setOnClickListener {
            viewModel.descargarPdfHorasGlobal(desdeGlobal, hastaGlobal)
        }

        // -- Tab 2: Saldos --
        binding.btnSelectorAnio.setOnClickListener {
            mostrarSelectorAnio()
        }
        binding.btnImprimirSaldos.setOnClickListener {
            viewModel.descargarPdfSaldos(anioSaldos)
        }
        binding.btnImprimirVacaciones.setOnClickListener {
            val id = empleadoIdVacaciones ?: return@setOnClickListener
            viewModel.descargarPdfVacaciones(id, anioSaldos)
        }
    }

    private fun ocultarWebViews() {
        binding.webViewIndividual.visibility = View.GONE
        binding.webViewGlobal.visibility     = View.GONE
        binding.webViewSaldos.visibility     = View.GONE
    }

    // ------------------------------------------------------------------
    // Dropdown de empleados
    // ------------------------------------------------------------------

    private fun observarEmpleados() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.empleados.collect { lista -> configurarDropdowns(lista) }
            }
        }
    }

    private fun configurarDropdowns(lista: List<EmpleadoItem>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, lista)

        with(binding.actvEmpleadoIndividual) {
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                empleadoIdIndividual = adapter.getItem(position)?.id
                // Auto-cargar HTML al seleccionar empleado
                empleadoIdIndividual?.let { id ->
                    ocultarWebViews()
                    viewModel.verHorasEmpleado(id, desdeIndividual, hastaIndividual)
                }
            }
        }

        with(binding.actvEmpleadoVacaciones) {
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                empleadoIdVacaciones = adapter.getItem(position)?.id
            }
        }
    }

    // ------------------------------------------------------------------
    // Selectores de fecha y año
    // ------------------------------------------------------------------

    private fun mostrarRangoPicker(onSeleccion: (String, String) -> Unit) {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.informes_selector_rango_titulo))
            .setCalendarConstraints(CalendarConstraints.Builder().setFirstDayOfWeek(Calendar.MONDAY).build())
            .build()
        picker.addOnPositiveButtonClickListener { rango ->
            val desde = Instant.ofEpochMilli(rango.first)
                .atZone(ZoneId.systemDefault()).toLocalDate().format(fmt)
            val hasta = Instant.ofEpochMilli(rango.second)
                .atZone(ZoneId.systemDefault()).toLocalDate().format(fmt)
            onSeleccion(desde, hasta)
        }
        picker.show(parentFragmentManager, "rango_informes")
    }

    private fun mostrarSelectorAnio() {
        val anioActual = LocalDate.now().year
        val opciones = arrayOf(
            (anioActual - 2).toString(),
            (anioActual - 1).toString(),
            anioActual.toString(),
            (anioActual + 1).toString()
        )
        val seleccionActual = opciones.indexOfFirst { it.toInt() == anioSaldos }.coerceAtLeast(2)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.informes_selector_anio_titulo))
            .setSingleChoiceItems(opciones, seleccionActual) { dialog, which ->
                anioSaldos = opciones[which].toInt()
                binding.btnSelectorAnio.text = opciones[which]
                dialog.dismiss()
                // Auto-cargar HTML de saldos al cambiar el año
                ocultarWebViews()
                viewModel.verSaldos(anioSaldos)
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

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    private fun procesarEstado(estado: InformesViewModel.UiState) {
        val cargando = estado is InformesViewModel.UiState.Loading
        binding.progressIndicator.isVisible = cargando
        setbotonesHabilitados(!cargando)

        when (estado) {
            is InformesViewModel.UiState.PdfListo       -> abrirPdf(estado.bytes, estado.nombreFichero)
            is InformesViewModel.UiState.HtmlVistaListo -> mostrarHtmlEnPantalla(estado.html)
            is InformesViewModel.UiState.Error -> {
                Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                viewModel.resetear()
            }
            else -> Unit
        }
    }

    private fun setbotonesHabilitados(habilitados: Boolean) {
        binding.btnImprimirIndividual.isEnabled = habilitados
        binding.btnImprimirGlobal.isEnabled     = habilitados
        binding.btnImprimirSaldos.isEnabled     = habilitados
        binding.btnImprimirVacaciones.isEnabled = habilitados
    }

    // ------------------------------------------------------------------
    // Visualizacion HTML en WebView (auto-carga)
    // ------------------------------------------------------------------

    private fun mostrarHtmlEnPantalla(html: String) {
        val tabIndex = binding.tabLayout.selectedTabPosition
        val webView = when (tabIndex) {
            0    -> binding.webViewIndividual
            1    -> binding.webViewGlobal
            2    -> binding.webViewSaldos
            else -> binding.webViewIndividual
        }
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        webView.visibility = View.VISIBLE
        viewModel.resetear()
    }

    // ------------------------------------------------------------------
    // Abrir PDF con Intent ACTION_VIEW (visor del sistema)
    // ------------------------------------------------------------------

    private fun abrirPdf(bytes: ByteArray, nombreFichero: String) {
        try {
            val file = File(requireContext().cacheDir, nombreFichero)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(binding.root, getString(R.string.informes_error_abrir_pdf), Snackbar.LENGTH_LONG).show()
        } finally {
            viewModel.resetear()
        }
    }
}
