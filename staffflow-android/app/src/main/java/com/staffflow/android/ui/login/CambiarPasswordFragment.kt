package com.staffflow.android.ui.login

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
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentCambiarPasswordBinding
import kotlinx.coroutines.launch

/**
 * Pantalla de cambio de contraseña para el usuario autenticado (P04).
 *
 * Acceso: Drawer -> Ajustes -> Cambiar contraseña (todos los roles).
 * Llama a E03 PUT /auth/password con la contraseña actual y la nueva.
 * Validacion local: passwordNueva debe coincidir con repetirNueva.
 * OK -> Snackbar "Contraseña actualizada" + popBackStack.
 * Error 401 -> mensaje de error visible.
 */
class CambiarPasswordFragment : Fragment() {

    private var _binding: FragmentCambiarPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CambiarPasswordViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCambiarPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarListeners()
        observarViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun configurarListeners() {
        binding.btnGuardar.setOnClickListener {
            val actual  = binding.etPasswordActual.text.toString()
            val nueva   = binding.etPasswordNueva.text.toString()
            val repetir = binding.etRepetirNueva.text.toString()

            binding.tilPasswordActual.error  = null
            binding.tilPasswordNueva.error   = null
            binding.tilRepetirNueva.error    = null

            if (actual.isBlank() || nueva.isBlank() || repetir.isBlank()) {
                Snackbar.make(binding.root, getString(R.string.cambiar_password_error_campos_vacios), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (nueva != repetir) {
                binding.tilRepetirNueva.error = getString(R.string.cambiar_password_error_no_coinciden)
                return@setOnClickListener
            }
            viewModel.guardar(actual, nueva)
        }
    }

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { estado ->
                    val cargando = estado is CambiarPasswordViewModel.UiState.Loading
                    binding.progressIndicator.isVisible = cargando
                    binding.btnGuardar.isEnabled = !cargando

                    when (estado) {
                        is CambiarPasswordViewModel.UiState.Guardado -> {
                            Snackbar.make(binding.root, getString(R.string.cambiar_password_exito), Snackbar.LENGTH_SHORT).show()
                            findNavController().popBackStack()
                        }
                        is CambiarPasswordViewModel.UiState.Error -> {
                            Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                            viewModel.limpiarError()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
