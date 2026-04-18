package com.staffflow.android.ui.empleado

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
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.databinding.FragmentMiPerfilBinding
import com.staffflow.android.domain.model.CategoriaEmpleado
import kotlinx.coroutines.launch

/**
 * Perfil del empleado autenticado (P08). Solo lectura.
 *
 * Endpoint: E21 GET /empleados/me
 *
 * Tres estados:
 *   Loading -> progressIndicator centrado
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Success -> datos del empleado en cards
 *
 * Boton "Cambiar contrasena" navega a P04 (CambiarPasswordFragment).
 * NOTA: el wireframe mostraba "NSS" — en v1.0 el campo es numeroEmpleado (D-030).
 */
class MiPerfilFragment : Fragment() {

    private var _binding: FragmentMiPerfilBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MiPerfilViewModel by viewModels()

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiPerfilBinding.inflate(inflater, container, false)
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
        binding.btnCambiarPassword.setOnClickListener {
            findNavController().navigate(R.id.cambiarPasswordFragment)
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

    private fun procesarEstado(estado: MiPerfilViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is MiPerfilViewModel.UiState.Loading
        binding.layoutError.isVisible       = estado is MiPerfilViewModel.UiState.Error
        binding.scrollContenido.isVisible   = estado is MiPerfilViewModel.UiState.Success

        when (estado) {
            is MiPerfilViewModel.UiState.Success -> rellenarDatos(estado.empleado)
            is MiPerfilViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            else -> Unit
        }
    }

    private fun rellenarDatos(e: EmpleadoResponse) {
        binding.tvNombreCompleto.text = buildString {
            append(e.nombre)
            append(" ")
            append(e.apellido1)
            e.apellido2?.let { append(" $it") }
        }
        binding.tvNumeroEmpleado.text = e.numeroEmpleado
        binding.tvDni.text            = e.dni
        binding.tvCategoria.text      = categoriaLabel(e.categoria)
        binding.tvJornada.text        = getString(R.string.mi_perfil_horas_semanales, e.jornadaSemanalHoras)
        binding.tvVacaciones.text     = getString(R.string.mi_perfil_dias_anio, e.diasVacacionesAnuales)
        binding.tvAsuntos.text        = getString(R.string.mi_perfil_dias_anio, e.diasAsuntosPropiosAnuales)
    }

    private fun categoriaLabel(categoria: CategoriaEmpleado): String = when (categoria) {
        CategoriaEmpleado.OPERARIO      -> "Operario"
        CategoriaEmpleado.ADMINISTRATIVO -> "Administrativo"
        CategoriaEmpleado.TECNICO       -> "Técnico"
        CategoriaEmpleado.ENCARGADO     -> "Encargado"
        CategoriaEmpleado.OTRO          -> "Otro"
    }
}
