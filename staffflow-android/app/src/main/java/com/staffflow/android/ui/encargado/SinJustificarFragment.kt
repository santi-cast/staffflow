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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentSinJustificarBinding
import kotlinx.coroutines.launch

/**
 * Lista de empleados sin justificar para una fecha (P18).
 *
 * Acceso: P17 ParteDiarioFragment -> chip "Sin justificar: N".
 * La fecha se pasa via Bundle("fecha"). Si no viene, el backend usa hoy.
 *
 * 4 estados: Loading (skeleton) / Error / Empty / Success (RecyclerView).
 * Lista de solo lectura.
 */
class SinJustificarFragment : Fragment() {

    private var _binding: FragmentSinJustificarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SinJustificarViewModel by viewModels()
    private var fecha: String? = null
    private lateinit var adapter: SinJustificarAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSinJustificarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fecha = arguments?.getString("fecha")

        adapter = SinJustificarAdapter { empleadoId ->
            val args = android.os.Bundle().apply {
                putString("variante", "FICHAJE")
                putLong("fichajeId", -1L)
                putLong("empleadoId", empleadoId)
                fecha?.let { putString("fecha", it) }
            }
            findNavController().navigate(R.id.action_sin_justificar_to_form_fichaje, args)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }

        viewModel.init(fecha)

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

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { estado ->
                    binding.layoutSkeleton.isVisible = estado is SinJustificarViewModel.UiState.Loading
                    binding.layoutError.isVisible    = estado is SinJustificarViewModel.UiState.Error
                    binding.layoutVacio.isVisible    = estado is SinJustificarViewModel.UiState.Empty
                    binding.recyclerView.isVisible   = estado is SinJustificarViewModel.UiState.Success

                    when (estado) {
                        is SinJustificarViewModel.UiState.Success -> adapter.submitList(estado.lista)
                        is SinJustificarViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
                        else -> Unit
                    }
                }
            }
        }
    }
}
