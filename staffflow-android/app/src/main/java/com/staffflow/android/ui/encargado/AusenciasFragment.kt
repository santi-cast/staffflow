package com.staffflow.android.ui.encargado

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentAusenciasBinding
import kotlinx.coroutines.launch

/**
 * Resumen de ausencias de todos los empleados activos (P23). Accesible para ENCARGADO y ADMIN.
 *
 * Patron WebView con navegacion semanal < / >.
 * Endpoint: E-ausencias-global GET /api/v1/informes/ausencias?desde=&hasta=
 *
 * Intercepta URLs staffflow://ausencia/{id} generadas por InformeService para
 * navegar al formulario de edicion de ausencias (P24).
 *
 * FAB (+) navega a P24 en modo alta.
 */
class AusenciasFragment : Fragment() {

    private var _binding: FragmentAusenciasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AusenciasViewModel by viewModels()

    // Seleccion activa desde la tabla WebView
    private data class Seleccion(val empId: Long, val fechaDesde: String, val fechaHasta: String)
    private var seleccionActiva: Seleccion? = null

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
        configurarWebView()
        configurarListeners()
        observarViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarWebView() {
        binding.webView.settings.javaScriptEnabled    = true
        binding.webView.settings.useWideViewPort      = true
        binding.webView.settings.loadWithOverviewMode = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                if (uri.scheme == "staffflow") {
                    when (uri.host) {
                        "fichaje"   -> navegarAFormFichaje(uri)
                        "ausencia"  -> navegarAFormAusencia(uri)
                        "seleccion" -> procesarSeleccion(uri)
                        "empleado"  -> procesarClickEmpleado(uri)
                    }
                    return true
                }
                return false
            }
        }
    }

    private fun configurarListeners() {
        binding.btnAnadirAusencia.isEnabled = false
        binding.btnAnadirFestivo.isEnabled = false
        binding.btnMesAnterior.setOnClickListener { viewModel.mesAnterior() }
        binding.btnSemanaAnterior.setOnClickListener { viewModel.semanaAnterior() }
        binding.btnSemanaSiguiente.setOnClickListener { viewModel.semanaSiguiente() }
        binding.btnMesSiguiente.setOnClickListener { viewModel.mesSiguiente() }
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
        binding.btnAnadirAusencia.setOnClickListener {
            val sel = seleccionActiva ?: return@setOnClickListener
            val args = Bundle().apply {
                putLong("empleadoId", sel.empId)
                putString("fechaDesde", sel.fechaDesde)
                putString("fechaHasta", sel.fechaHasta)
            }
            findNavController().navigate(R.id.action_ausencias_to_form_ausencia, args)
        }
        binding.btnAnadirFestivo.setOnClickListener {
            val sel = seleccionActiva ?: return@setOnClickListener
            val args = Bundle().apply {
                putString("fechaDesde", sel.fechaDesde)
                putString("fechaHasta", sel.fechaHasta)
                putBoolean("esFestivo", true)
            }
            findNavController().navigate(R.id.action_ausencias_to_form_ausencia, args)
        }
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect        { procesarEstado(it) } }
                launch { viewModel.semanaLabel.collect    { binding.tvSemanaLabel.text = it } }
                launch { viewModel.infoSeleccion.collect  { procesarInfoSeleccion(it) } }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    private fun procesarEstado(estado: AusenciasViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is AusenciasViewModel.UiState.Loading
        binding.layoutError.isVisible       = estado is AusenciasViewModel.UiState.Error
        binding.webView.isVisible           = estado is AusenciasViewModel.UiState.Success

        when (estado) {
            is AusenciasViewModel.UiState.Error ->
                binding.tvErrorMensaje.text = estado.mensaje
            is AusenciasViewModel.UiState.Success ->
                binding.webView.loadDataWithBaseURL(null, estado.html, "text/html", "UTF-8", null)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Navegacion desde URLs staffflow://
    // ------------------------------------------------------------------

    /**
     * Navega a formFichajeFragment con los args extraidos de la URL.
     * URL: staffflow://fichaje/{id}?variante=FICHAJE&empleadoId=&fecha=&tipo=&horaEntrada=&horaSalida=
     */
    private fun navegarAFormFichaje(uri: Uri) {
        val id = uri.pathSegments.firstOrNull()?.toLongOrNull() ?: return
        val args = Bundle().apply {
            putLong("fichajeId", id)
            putString("variante", "FICHAJE")
            uri.getQueryParameter("empleadoId")?.toLongOrNull()?.let { putLong("empleadoId", it) }
            uri.getQueryParameter("fecha")?.let      { putString("fecha", it) }
            uri.getQueryParameter("tipo")?.let       { putString("tipo", it) }
            uri.getQueryParameter("horaEntrada")?.let { putString("horaEntrada", it) }
            uri.getQueryParameter("horaSalida")?.let  { putString("horaSalida", it) }
        }
        findNavController().navigate(R.id.action_ausencias_to_form_ausencia, args)
    }

    /**
     * Procesa un cambio de seleccion notificado por el JS de la tabla.
     * URL: staffflow://seleccion/vacia
     *      staffflow://seleccion/{empId}/{fechaDesde}/{fechaHasta}/{consecutivas}
     */
    private fun procesarSeleccion(uri: Uri) {
        val segmentos = uri.pathSegments
        if (segmentos.firstOrNull() == "vacia" || segmentos.isEmpty()) {
            seleccionActiva = null
            binding.btnAnadirAusencia.isEnabled = false
            binding.btnAnadirAusencia.text = getString(R.string.ausencias_anadir)
            binding.btnAnadirFestivo.isEnabled = false
            binding.btnAnadirFestivo.text = getString(R.string.ausencias_anadir_festivo)
            viewModel.ocultarInfoSeleccion()
            return
        }
        val empId       = segmentos.getOrNull(0)?.toLongOrNull() ?: return
        val fechaDesde  = segmentos.getOrNull(1) ?: return
        val fechaHasta  = segmentos.getOrNull(2) ?: return
        val consecutivas = segmentos.getOrNull(3)?.toBoolean() ?: true

        if (!consecutivas) {
            Toast.makeText(requireContext(),
                getString(R.string.ausencias_seleccion_no_consecutiva),
                Toast.LENGTH_SHORT).show()
            seleccionActiva = null
            binding.btnAnadirAusencia.isEnabled = false
            binding.btnAnadirAusencia.text = getString(R.string.ausencias_anadir)
            binding.btnAnadirFestivo.isEnabled = false
            binding.btnAnadirFestivo.text = getString(R.string.ausencias_anadir_festivo)
            viewModel.ocultarInfoSeleccion()
            return
        }

        seleccionActiva = Seleccion(empId, fechaDesde, fechaHasta)
        val dias = calcularDias(fechaDesde, fechaHasta)
        binding.btnAnadirAusencia.isEnabled = true
        binding.btnAnadirAusencia.text = getString(R.string.ausencias_anadir_con_seleccion, dias)
        binding.btnAnadirFestivo.isEnabled = true
        binding.btnAnadirFestivo.text = getString(R.string.ausencias_anadir_festivo_con_seleccion, dias)
    }

    private fun calcularDias(desde: String, hasta: String): Int {
        return try {
            val d = java.time.LocalDate.parse(desde)
            val h = java.time.LocalDate.parse(hasta)
            (h.toEpochDay() - d.toEpochDay() + 1).toInt()
        } catch (e: Exception) { 1 }
    }

    private fun procesarClickEmpleado(uri: Uri) {
        val empId = uri.pathSegments.getOrNull(0)?.toLongOrNull() ?: return
        val fechaDesde = uri.pathSegments.getOrNull(1) ?: return
        viewModel.cargarInfoSeleccion(empId, fechaDesde)
    }

    private fun procesarInfoSeleccion(estado: AusenciasViewModel.InfoSeleccionState) {
        when (estado) {
            is AusenciasViewModel.InfoSeleccionState.Oculta,
            is AusenciasViewModel.InfoSeleccionState.Cargando -> {
                binding.tvInfoSeleccion.isVisible = false
            }
            is AusenciasViewModel.InfoSeleccionState.Visible -> {
                binding.tvInfoSeleccion.text =
                    "Empleado seleccionado — Pendiente por planificar en ${estado.anio}: " +
                    "Vacaciones ${estado.vacaciones} días · AP ${estado.asuntosPropios} días"
                binding.tvInfoSeleccion.isVisible = true
            }
        }
    }

    /**
     * Navega a formAusenciaFragment con los args extraidos de la URL.
     * URL: staffflow://ausencia/{id}?empleadoId=&fecha=&tipoAusencia=&procesado=
     */
    private fun navegarAFormAusencia(uri: Uri) {
        val id = uri.pathSegments.firstOrNull()?.toLongOrNull() ?: return
        val args = Bundle().apply {
            putLong("ausenciaId", id)
            putBoolean("procesado", uri.getQueryParameter("procesado")?.toBoolean() ?: false)
            putBoolean("esFestivo", uri.getQueryParameter("esFestivo")?.toBoolean() ?: false)
            uri.getQueryParameter("empleadoId")?.toLongOrNull()?.let { putLong("empleadoId", it) }
            uri.getQueryParameter("fecha")?.let        { putString("fecha", it) }
            uri.getQueryParameter("tipoAusencia")?.let { putString("tipoAusencia", it) }
        }
        findNavController().navigate(R.id.action_ausencias_to_form_ausencia, args)
    }
}
