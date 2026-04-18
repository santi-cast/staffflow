package com.staffflow.android.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.staffflow.android.MainActivity
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentLoginBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de login JWT (P02).
 *
 * Patron B - Autenticacion. Endpoint: E01 POST /auth/login.
 *
 * Flujo de exito (Decision 24-C):
 *   1. LoginViewModel persiste sesion en DataStore y actualiza NetworkModule.authToken.
 *   2. LoginFragment llama a MainActivity.refreshDrawerMenu() para cargar grupos por rol.
 *   3. LoginFragment llama a MainActivity.navigateToInitialDestination(rol):
 *        ADMIN     -> P13 EmpleadosFragment
 *        ENCARGADO -> P17 ParteDiarioFragment
 *        EMPLEADO  -> P09 MiSaldoFragment
 *      La accion usa popUpTo(terminalFragment, inclusive=true) para limpiar el back stack.
 *
 * Errores:
 *   401 -> mensaje inline en tilPassword.error
 *   423 -> "Demasiados intentos. Espera 30 segundos." en tilPassword.error
 *   Red  -> mensaje de sin conexion en tilPassword.error
 *
 * Decision 25: btnLogin queda deshabilitado durante la llamada de red.
 */
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarFormulario()
        observarViewModel()
        // Mostrar "Volver al terminal" solo si hay una pantalla anterior en la pila
        if (findNavController().previousBackStackEntry != null) {
            binding.btnVolverTerminal.isVisible = true
            binding.btnVolverTerminal.setOnClickListener { findNavController().popBackStack() }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.etUsername.text?.clear()
        binding.etPassword.text?.clear()
        limpiarError()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarFormulario() {
        // Limpiar error al modificar los campos
        binding.etUsername.setOnFocusChangeListener { _, _ -> limpiarError() }
        binding.etPassword.setOnFocusChangeListener { _, _ -> limpiarError() }

        // Accion IME "Done" en el campo password dispara el login
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                ejecutarLogin()
                true
            } else false
        }

        binding.btnLogin.setOnClickListener { ejecutarLogin() }

        binding.btnOlvidePassword.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_recovery)
        }
    }

    private fun ejecutarLogin() {
        val username = binding.etUsername.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""

        if (username.isEmpty() || password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.login_error_campos_vacios)
            return
        }

        viewModel.login(username, password)
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

    private fun procesarEstado(estado: LoginUiState) {
        val cargando = estado is LoginUiState.Loading
        // Decision 25: deshabilitar boton durante la llamada
        binding.btnLogin.isEnabled = !cargando
        binding.progressIndicator.isVisible = cargando
        binding.btnOlvidePassword.isEnabled = !cargando

        when (estado) {
            is LoginUiState.Idle -> limpiarError()
            is LoginUiState.Loading -> limpiarError()
            is LoginUiState.Error -> {
                binding.tilPassword.error = estado.mensaje
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(2000)
                    limpiarError()
                    binding.etUsername.text?.clear()
                    binding.etPassword.text?.clear()
                    binding.etUsername.requestFocus()
                }
            }
            is LoginUiState.Exito -> {
                viewModel.resetEstado()
                // Coordinar la transicion con MainActivity
                (requireActivity() as? MainActivity)?.let { main ->
                    main.refreshDrawerMenu()
                    main.navigateToInitialDestination(estado.rol)
                }
            }
        }
    }

    private fun limpiarError() {
        binding.tilPassword.error = null
    }
}
