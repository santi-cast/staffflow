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
import com.staffflow.android.databinding.FragmentRecoveryBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de recuperacion de contraseña (P03).
 *
 * El usuario introduce su email y pulsa el boton. El backend siempre responde
 * con un mensaje informativo (anti-enumeracion: no revela si el email existe).
 * Tras pulsar se oculta el formulario y se muestra el mensaje de confirmacion.
 *
 * Acceso: P02 LoginFragment -> boton "¿Olvidaste tu contraseña?"
 */
class RecoveryFragment : Fragment() {

    private var _binding: FragmentRecoveryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecoveryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecoveryBinding.inflate(inflater, container, false)
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

    private fun iniciarCuentaAtras() {
        viewLifecycleOwner.lifecycleScope.launch {
            for (i in 5 downTo 1) {
                binding.tvCuentaAtras.text = getString(R.string.confirmacion_volviendo, i)
                delay(1000)
            }
            findNavController().popBackStack()
        }
    }

    private fun configurarListeners() {
        binding.btnEnviar.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isBlank()) {
                binding.tilEmail.error = getString(R.string.recovery_error_email_vacio)
                return@setOnClickListener
            }
            binding.tilEmail.error = null
            viewModel.enviar(email)
        }
    }

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { estado ->
                    val cargando = estado is RecoveryViewModel.UiState.Loading
                    binding.progressIndicator.isVisible = cargando
                    binding.btnEnviar.isEnabled = !cargando

                    when (estado) {
                        is RecoveryViewModel.UiState.Enviado -> {
                            binding.layoutFormulario.isVisible = false
                            binding.layoutConfirmacion.isVisible = true
                            iniciarCuentaAtras()
                        }
                        is RecoveryViewModel.UiState.Error -> {
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
