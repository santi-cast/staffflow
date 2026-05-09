package com.staffflow.android.ui.admin

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentUsuariosBinding
import kotlinx.coroutines.launch

/**
 * Lista de usuarios del sistema (P28). Solo ADMIN.
 *
 * Endpoint: E09 GET /usuarios
 *
 * Cuatro estados:
 *   Loading -> skeleton list
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Empty   -> icono + mensaje sin datos
 *   Success -> RecyclerView con pull-to-refresh
 *
 * FAB (+) navega a P29 (FormUsuarioFragment) en modo alta.
 * Tap en fila -> P29 en modo edicion con usuarioId.
 * onResume() llama reintentar() para refrescar al volver de P29.
 */
class UsuariosFragment : Fragment() {

    private var _binding: FragmentUsuariosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UsuariosViewModel by viewModels()
    private lateinit var adapter: UsuarioAdapter

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsuariosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecyclerView()
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
        adapter = UsuarioAdapter { usuario ->
            val args = Bundle().apply { putLong("usuarioId", usuario.id) }
            findNavController().navigate(R.id.action_usuarios_to_form_usuario, args)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun configurarListeners() {
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.reintentar() }
        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_usuarios_to_form_usuario)
        }
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

    private fun procesarEstado(estado: UsuariosViewModel.UiState) {
        if (estado !is UsuariosViewModel.UiState.Loading) {
            binding.swipeRefresh.isRefreshing = false
        }

        binding.layoutSkeleton.isVisible = estado is UsuariosViewModel.UiState.Loading
        binding.layoutError.isVisible    = estado is UsuariosViewModel.UiState.Error
        binding.layoutVacio.isVisible    = estado is UsuariosViewModel.UiState.Empty
        binding.swipeRefresh.isVisible   = estado is UsuariosViewModel.UiState.Success

        when (estado) {
            is UsuariosViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            is UsuariosViewModel.UiState.Success -> adapter.submitList(estado.usuarios)
            else -> Unit
        }
    }
}
