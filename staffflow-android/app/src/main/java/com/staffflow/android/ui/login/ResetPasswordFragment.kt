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
import com.staffflow.android.databinding.FragmentResetPasswordBinding
import kotlinx.coroutines.launch

/**
 * Pantalla de restablecimiento de contraseña (P05).
 *
 * **v1.0 — no operativo:** en v1 este flujo entrega una contraseña temporal
 * de 8 caracteres por email (E04). El token UUID de 30 minutos descrito a
 * continuación pertenece al andamiaje reservado para v2.0 (ver memoria TFG,
 * bloque B10 Vías Futuras → Reset password con token UUID).
 *
 * En v1 el deep link `staffflow://reset-password?token={token}` NO está
 * activo: el AndroidManifest no declara intent-filter para el esquema
 * `staffflow://`, y por tanto este fragment no se invoca desde ningún flujo
 * de producción. La declaración del deep link en `nav_graph.xml` queda
 * aislada y reservada para v2.0.
 *
 * Comportamiento previsto (v2.0): el token llegaría en
 * `arguments?.getString("token")` desde el deep link configurado en
 * `nav_graph.xml`.
 *
 * OK   -> Snackbar "Contraseña restablecida" + navegar a P02 (action_reset_to_login).
 * Error -> mostrar mensaje del backend (por ejemplo "Token expirado") + botón volver al login.
 */
class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResetPasswordViewModel by viewModels()

    private val token: String? get() = arguments?.getString("token")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (token.isNullOrBlank()) {
            mostrarError(getString(R.string.reset_password_error_token_invalido))
            return
        }

        configurarListeners()
        observarViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun configurarListeners() {
        binding.btnRestablecer.setOnClickListener {
            val nueva   = binding.etPasswordNueva.text.toString()
            val repetir = binding.etRepetirNueva.text.toString()

            binding.tilPasswordNueva.error  = null
            binding.tilRepetirNueva.error   = null

            if (nueva.isBlank() || repetir.isBlank()) {
                Snackbar.make(binding.root, getString(R.string.reset_password_error_campos_vacios), Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (nueva != repetir) {
                binding.tilRepetirNueva.error = getString(R.string.reset_password_error_no_coinciden)
                return@setOnClickListener
            }
            viewModel.restablecer(token!!, nueva)
        }

        binding.btnVolverLogin.setOnClickListener {
            findNavController().navigate(R.id.action_reset_to_login)
        }
    }

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { estado ->
                    val cargando = estado is ResetPasswordViewModel.UiState.Loading
                    binding.progressIndicator.isVisible = cargando
                    binding.btnRestablecer.isEnabled = !cargando

                    when (estado) {
                        is ResetPasswordViewModel.UiState.Restablecido -> {
                            Snackbar.make(binding.root, getString(R.string.reset_password_exito), Snackbar.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_reset_to_login)
                        }
                        is ResetPasswordViewModel.UiState.Error -> {
                            mostrarError(estado.mensaje)
                            viewModel.limpiarError()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        binding.layoutFormulario.isVisible = false
        binding.layoutError.isVisible = true
        binding.tvErrorMensaje.text = mensaje
    }
}
