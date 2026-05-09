package com.staffflow.android.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.databinding.FragmentFormEmpleadoBinding
import com.staffflow.android.domain.model.CategoriaEmpleado
import kotlinx.coroutines.launch

/**
 * Formulario de alta y edicion de empleado (P15). Solo accesible para ADMIN.
 *
 * Patron F - formulario create/edit.
 * Modo alta   (empleadoId = -1): E13 POST /empleados.
 * Modo edicion (empleadoId > 0): E15 para precargar + E16 PATCH /empleados/{id}.
 *
 * Argumentos de navegacion:
 *   empleadoId: Long (-1 por defecto = modo alta)
 *   usuarioId:  Long (-1 por defecto = ADMIN introduce el ID manualmente)
 *
 * En modo edicion los campos DNI, numero, fecha alta y usuarioId se ocultan
 * ya que no son modificables via PATCH.
 *
 * GUARDAR deshabilita el boton y muestra LinearProgressIndicator durante la llamada.
 * OK: navega atras (popBackStack).
 * Error 409 (duplicado): Snackbar con el mensaje del backend.
 */
class FormEmpleadoFragment : Fragment() {

    private var _binding: FragmentFormEmpleadoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormEmpleadoViewModel by viewModels()

    private val categorias = CategoriaEmpleado.entries.toList()
    private val categoriaLabels = listOf("Operario", "Administrativo", "Técnico", "Encargado", "Otro")

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormEmpleadoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val empleadoId = arguments?.getLong("empleadoId") ?: -1L
        val usuarioId  = arguments?.getLong("usuarioId")  ?: -1L
        viewModel.init(empleadoId, usuarioId)
        configurarCategoriasDropdown()
        configurarModo(viewModel.modoEdicion, usuarioId)
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

    private fun configurarCategoriasDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categoriaLabels
        )
        binding.actvCategoria.setAdapter(adapter)
    }

    /**
     * Ajusta la visibilidad de los campos segun el modo y el usuarioId prerellenado.
     * En modo edicion: oculta campos no modificables via PATCH.
     * En modo alta sin usuarioId: muestra el campo para introducirlo manualmente.
     */
    private fun configurarModo(modoEdicion: Boolean, usuarioId: Long) {
        requireActivity().title = if (modoEdicion)
            getString(R.string.form_empleado_titulo_edicion)
        else
            getString(R.string.form_empleado_titulo_alta)

        // En modo edicion: DNI no es modificable via PATCH
        binding.tilDni.isVisible = !modoEdicion

        // tilUsuarioId eliminado: P15 solo se alcanza desde P29 (usuarioId llega pre-llenado)
    }

    private fun configurarListeners() {
        binding.btnGuardar.setOnClickListener { intentarGuardar() }
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { procesarEstado(it) } }
                launch { viewModel.empleado.collect { emp -> emp?.let { prerellenarCampos(it) } } }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    private fun procesarEstado(estado: FormEmpleadoViewModel.UiState) {
        val cargando = estado is FormEmpleadoViewModel.UiState.Loading
        binding.btnGuardar.isEnabled    = !cargando
        binding.progressIndicator.isVisible = cargando

        when (estado) {
            is FormEmpleadoViewModel.UiState.Success -> findNavController().popBackStack()
            is FormEmpleadoViewModel.UiState.Error   -> {
                Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                viewModel.limpiarError()
            }
            else -> Unit
        }
    }

    /** Precarga los campos del formulario con los datos del empleado (modo edicion). */
    private fun prerellenarCampos(e: EmpleadoResponse) {
        binding.etNombre.setText(e.nombre)
        binding.etApellido1.setText(e.apellido1)
        binding.etApellido2.setText(e.apellido2 ?: "")
        binding.etJornadaSemanal.setText(e.jornadaSemanalHoras.toString())
        binding.etVacaciones.setText(e.diasVacacionesAnuales.toString())
        binding.etAsuntos.setText(e.diasAsuntosPropiosAnuales.toString())

        val index = categorias.indexOf(e.categoria)
        if (index >= 0) binding.actvCategoria.setText(categoriaLabels[index], false)
    }

    // ------------------------------------------------------------------
    // Recoger datos del formulario y guardar
    // ------------------------------------------------------------------

    private fun intentarGuardar() {
        val nombre    = binding.etNombre.text?.toString().orEmpty().trim()
        val apellido1 = binding.etApellido1.text?.toString().orEmpty().trim()
        val apellido2 = binding.etApellido2.text?.toString().orEmpty().trim()
        val categoriaLabel = binding.actvCategoria.text?.toString().orEmpty()
        val categoriaIndex = categoriaLabels.indexOf(categoriaLabel)
        val categoria = if (categoriaIndex >= 0) categorias[categoriaIndex] else null

        val jornadaSemanalStr = binding.etJornadaSemanal.text?.toString().orEmpty().trim()
        val vacacionesStr     = binding.etVacaciones.text?.toString().orEmpty().trim()
        val asuntosStr        = binding.etAsuntos.text?.toString().orEmpty().trim()

        if (nombre.isBlank() || apellido1.isBlank() || categoria == null ||
            jornadaSemanalStr.isBlank() || vacacionesStr.isBlank() || asuntosStr.isBlank()) {
            Snackbar.make(binding.root, getString(R.string.form_empleado_error_campos), Snackbar.LENGTH_SHORT).show()
            return
        }

        val jornadaSemanal = jornadaSemanalStr.toDoubleOrNull() ?: run {
            Snackbar.make(binding.root, "Introduce un número válido para la jornada semanal", Snackbar.LENGTH_SHORT).show()
            return
        }
        val vacaciones = vacacionesStr.toIntOrNull() ?: run {
            Snackbar.make(binding.root, "Introduce un número válido para los días de vacaciones", Snackbar.LENGTH_SHORT).show()
            return
        }
        val asuntos = asuntosStr.toIntOrNull() ?: run {
            Snackbar.make(binding.root, "Introduce un número válido para los días de asuntos propios", Snackbar.LENGTH_SHORT).show()
            return
        }

        val dni = binding.etDni.text?.toString().orEmpty().trim()

        viewModel.guardar(
            usuarioIdInput      = null,  // siempre viene de P29 via argumento de navegacion
            nombre              = nombre,
            apellido1           = apellido1,
            apellido2           = apellido2.ifBlank { null },
            dni                 = dni,
            categoria           = categoria,
            jornadaSemanalHoras = jornadaSemanal,
            diasVacaciones      = vacaciones,
            diasAsuntos         = asuntos
        )
    }
}
