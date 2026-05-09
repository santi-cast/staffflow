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
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.EmpresaResponse
import com.staffflow.android.databinding.FragmentEmpresaBinding
import kotlinx.coroutines.launch

/**
 * Configuracion de la empresa (P30). Solo ADMIN.
 *
 * Patron C (dato unico) + formulario de edicion.
 * Endpoints: E06 GET /empresa (carga) | E07 PUT /empresa (guarda).
 *
 * Tres estados:
 *   Loading -> progressIndicator centrado
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Success -> formulario con los datos de la empresa
 *
 * Al guardar correctamente muestra Snackbar "Guardado" y recarga los datos.
 */
class EmpresaFragment : Fragment() {

    private var _binding: FragmentEmpresaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmpresaViewModel by viewModels()

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmpresaBinding.inflate(inflater, container, false)
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

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarListeners() {
        binding.btnReintentar.setOnClickListener { viewModel.reintentar() }
        binding.btnGuardar.setOnClickListener {
            binding.btnGuardar.isEnabled = false
            try {
                viewModel.guardar(
                    nombreEmpresa = binding.etNombreEmpresa.text.toString().trim(),
                    cif           = binding.etCif.text.toString().trim(),
                    direccion     = binding.etDireccion.text.toString(),
                    email         = binding.etEmail.text.toString(),
                    telefono      = binding.etTelefono.text.toString(),
                    logoPath      = null  // logoPath no editable en v1.0
                )
            } finally {
                binding.btnGuardar.isEnabled = true
            }
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

    private fun procesarEstado(estado: EmpresaViewModel.UiState) {
        val cargando = estado is EmpresaViewModel.UiState.Loading
        val guardando = estado is EmpresaViewModel.UiState.Saving

        binding.progressIndicator.isVisible = cargando
        binding.layoutError.isVisible       = estado is EmpresaViewModel.UiState.Error
        binding.scrollContenido.isVisible   = estado is EmpresaViewModel.UiState.Success ||
                                              guardando ||
                                              estado is EmpresaViewModel.UiState.Saved
        binding.btnGuardar.isEnabled        = !guardando
        binding.progressGuardando.isVisible = guardando

        when (estado) {
            is EmpresaViewModel.UiState.Success -> rellenarCampos(estado.empresa)
            is EmpresaViewModel.UiState.Saved   -> {
                rellenarCampos(estado.empresa)
                Snackbar.make(binding.root, getString(R.string.empresa_guardado), Snackbar.LENGTH_SHORT).show()
            }
            is EmpresaViewModel.UiState.Error -> {
                binding.tvErrorMensaje.text = estado.mensaje
            }
            else -> Unit
        }
    }

    private fun rellenarCampos(empresa: EmpresaResponse) {
        binding.etNombreEmpresa.setText(empresa.nombreEmpresa)
        binding.etCif.setText(empresa.cif)
        binding.etDireccion.setText(empresa.direccion.orEmpty())
        binding.etEmail.setText(empresa.email.orEmpty())
        binding.etTelefono.setText(empresa.telefono.orEmpty())
    }
}
