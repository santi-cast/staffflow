package com.staffflow.android.ui.encargado

import android.net.Uri
import android.os.Bundle
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
import com.staffflow.android.databinding.FragmentResumenSemanalBinding
import kotlinx.coroutines.launch

/**
 * Resumen semanal de todos los empleados activos (P19 rediseñado).
 *
 * Patron WebView con navegacion semanal < / >.
 * Endpoint: E-Semana GET /api/v1/informes/semana?desde=&hasta=
 *
 * Intercepta URLs staffflow:// generadas por InformeService para
 * navegar a los formularios de edicion:
 *   staffflow://fichaje/{id}  -> formFichajeFragment (variante=FICHAJE)
 *   staffflow://pausa/{id}    -> formFichajeFragment (variante=PAUSA)
 *   staffflow://ausencia/{id} -> formAusenciaFragment
 *
 * Args del Bundle que pasa a formFichajeFragment:
 *   fichajeId / pausaId, variante, empleadoId, fecha,
 *   tipo / tipoPausa, horaEntrada+horaSalida / horaInicio+horaFin
 *
 * Args del Bundle que pasa a formAusenciaFragment:
 *   ausenciaId, empleadoId, fecha, tipoAusencia, procesado
 */
class ResumenSemanalFragment : Fragment() {

    private var _binding: FragmentResumenSemanalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResumenSemanalViewModel by viewModels()

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResumenSemanalBinding.inflate(inflater, container, false)
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
        binding.webView.settings.useWideViewPort     = true
        binding.webView.settings.loadWithOverviewMode = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                if (uri.scheme == "staffflow") {
                    when (uri.host) {
                        "fichaje"  -> navegarAFormFichaje(uri, "FICHAJE")
                        "pausa"    -> navegarAFormFichaje(uri, "PAUSA")
                        "ausencia" -> navegarAFormAusencia(uri)
                    }
                    return true
                }
                return false
            }
        }
    }

    private fun configurarListeners() {
        binding.btnSemanaAnterior.setOnClickListener { viewModel.semanaAnterior() }
        binding.btnSemanaSiguiente.setOnClickListener { viewModel.semanaSiguiente() }
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect    { procesarEstado(it) } }
                launch { viewModel.semanaLabel.collect { binding.tvSemanaLabel.text = it } }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    private fun procesarEstado(estado: ResumenSemanalViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is ResumenSemanalViewModel.UiState.Loading
        binding.layoutError.isVisible       = estado is ResumenSemanalViewModel.UiState.Error
        binding.webView.isVisible           = estado is ResumenSemanalViewModel.UiState.Success

        when (estado) {
            is ResumenSemanalViewModel.UiState.Error ->
                binding.tvErrorMensaje.text = estado.mensaje
            is ResumenSemanalViewModel.UiState.Success ->
                binding.webView.loadDataWithBaseURL(null, estado.html, "text/html", "UTF-8", null)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Navegacion desde URLs staffflow://
    // ------------------------------------------------------------------

    /**
     * Navega a formFichajeFragment con los args extraidos de la URL.
     *
     * URL fichaje: staffflow://fichaje/{id}?variante=FICHAJE&empleadoId=&fecha=&tipo=&horaEntrada=&horaSalida=
     * URL pausa:   staffflow://pausa/{id}?variante=PAUSA&empleadoId=&fecha=&tipoPausa=&horaInicio=&horaFin=
     */
    private fun navegarAFormFichaje(uri: Uri, variante: String) {
        val id = uri.pathSegments.firstOrNull()?.toLongOrNull() ?: return
        val args = Bundle().apply {
            if (variante == "FICHAJE") putLong("fichajeId", id) else putLong("pausaId", id)
            putString("variante", variante)
            uri.getQueryParameter("empleadoId")?.toLongOrNull()?.let { putLong("empleadoId", it) }
            uri.getQueryParameter("fecha")?.let          { putString("fecha", it) }
            uri.getQueryParameter("tipo")?.let           { putString("tipo", it) }
            uri.getQueryParameter("tipoPausa")?.let      { putString("tipoPausa", it) }
            uri.getQueryParameter("horaEntrada")?.let    { putString("horaEntrada", it) }
            uri.getQueryParameter("horaSalida")?.let     { putString("horaSalida", it) }
            uri.getQueryParameter("horaInicio")?.let     { putString("horaInicio", it) }
            uri.getQueryParameter("horaFin")?.let        { putString("horaFin", it) }
        }
        findNavController().navigate(R.id.action_resumen_to_form_fichaje, args)
    }

    /**
     * Navega a formAusenciaFragment con los args extraidos de la URL.
     *
     * URL ausencia: staffflow://ausencia/{id}?empleadoId=&fecha=&tipoAusencia=&procesado=
     */
    private fun navegarAFormAusencia(uri: Uri) {
        val id = uri.pathSegments.firstOrNull()?.toLongOrNull() ?: return
        val args = Bundle().apply {
            putLong("ausenciaId", id)
            putBoolean("procesado", uri.getQueryParameter("procesado")?.toBoolean() ?: false)
            uri.getQueryParameter("empleadoId")?.toLongOrNull()?.let { putLong("empleadoId", it) }
            uri.getQueryParameter("fecha")?.let         { putString("fecha", it) }
            uri.getQueryParameter("tipoAusencia")?.let  { putString("tipoAusencia", it) }
        }
        findNavController().navigate(R.id.action_resumen_to_form_ausencia, args)
    }
}
