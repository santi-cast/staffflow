package com.staffflow.android.ui.login

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.staffflow.android.MainActivity
import com.staffflow.android.R
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.databinding.DialogCambiarIpBinding
import com.staffflow.android.databinding.FragmentLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

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
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
                // Coordinar la transicion con MainActivity
                (requireActivity() as? MainActivity)?.let { main ->
                    main.refreshDrawerMenu()
                    main.navigateToInitialDestination(estado.rol)
                }
            }
            is LoginUiState.ErrorConexion -> {
                viewModel.resetEstado()
                mostrarDialogoCambiarIp(estado.username, estado.password)
            }
        }
    }

    private fun mostrarDialogoCambiarIp(username: String, password: String) {
        val dialogBinding = DialogCambiarIpBinding.inflate(layoutInflater)

        dialogBinding.etIp.setText(NetworkModule.currentIp)
        dialogBinding.etIp.setSelection(dialogBinding.etIp.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialogo_ip_titulo)
            .setMessage(R.string.dialogo_ip_mensaje)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.dialogo_ip_cancelar, null)
            .setPositiveButton(R.string.dialogo_ip_btn_guardar, null)
            .create()

        dialog.setOnShowListener {
            val btnGuardar = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            btnGuardar.isEnabled = false

            // Al editar la IP, resetear el resultado y deshabilitar guardar
            dialogBinding.etIp.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    dialogBinding.tvResultadoConexion.visibility = View.GONE
                    btnGuardar.isEnabled = false
                }
            })

            dialogBinding.btnProbarConexion.setOnClickListener {
                val ip = dialogBinding.etIp.text?.toString()?.trim() ?: return@setOnClickListener
                dialogBinding.btnProbarConexion.isEnabled = false
                dialogBinding.tvResultadoConexion.isVisible = true
                dialogBinding.tvResultadoConexion.text = getString(R.string.cargando)

                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(ip, 8080), 5000)
                                true
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }
                    dialogBinding.btnProbarConexion.isEnabled = true
                    if (ok) {
                        dialogBinding.tvResultadoConexion.text = getString(R.string.dialogo_ip_resultado_ok)
                        dialogBinding.tvResultadoConexion.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_exito))
                        btnGuardar.isEnabled = true
                    } else {
                        dialogBinding.tvResultadoConexion.text = getString(R.string.dialogo_ip_resultado_error)
                        dialogBinding.tvResultadoConexion.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_error))
                        btnGuardar.isEnabled = false
                    }
                }
            }

            btnGuardar.setOnClickListener {
                val ip = dialogBinding.etIp.text?.toString()?.trim() ?: return@setOnClickListener
                dialog.dismiss()
                viewModel.guardarIpYReintentarLogin(ip, username, password)
            }
        }

        dialog.show()
    }

    private fun limpiarError() {
        binding.tilPassword.error = null
    }
}
