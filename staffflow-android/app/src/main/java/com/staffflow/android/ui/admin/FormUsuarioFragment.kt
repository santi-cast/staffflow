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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentFormUsuarioBinding
import com.staffflow.android.domain.model.CategoriaEmpleado
import com.staffflow.android.domain.model.Rol
import kotlinx.coroutines.launch

/**
 * Formulario de usuario (P29). Solo ADMIN.
 *
 * Modo alta   (usuarioId = -1): E08 POST /usuarios.
 *   Al crear EMPLEADO o ENCARGADO: Snackbar con accion "Crear perfil" -> P15.
 * Modo edicion (usuarioId > 0): E11 PATCH (email, rol, activo).
 *   Username y password no son editables en modo edicion.
 * Desactivar (modoEdicion && activo=true): E12 DELETE (baja logica) con dialogo.
 *
 * Argumentos de navegacion esperados (Bundle):
 *   usuarioId  Long  -1 = alta | >0 = edicion
 */
class FormUsuarioFragment : Fragment() {

    private var _binding: FragmentFormUsuarioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormUsuarioViewModel by viewModels()

    /** Ultima sugerencia aplicada. Permite detectar si el usuario edito el campo a mano. */
    private var lastSuggestion: String? = null

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormUsuarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val usuarioId = arguments?.getLong("usuarioId", -1L) ?: -1L
        viewModel.init(usuarioId)

        configurarDropdown()
        configurarModo()
        configurarListeners()
        observarViewModel()
        // Sugerir username inicial solo en modo alta (rol por defecto: EMPLEADO)
        if (!viewModel.modoEdicion) viewModel.sugerirUsername(Rol.EMPLEADO)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ------------------------------------------------------------------
    // Configuracion
    // ------------------------------------------------------------------

    private fun configurarDropdown() {
        val roles = Rol.values().map { rolLabel(it) }
        val rolesAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roles)
        binding.actvRol.setAdapter(rolesAdapter)
        // Valor por defecto: EMPLEADO
        binding.actvRol.setText(rolLabel(Rol.EMPLEADO), false)

        val categorias = CategoriaEmpleado.values().map { categoriaLabel(it) }
        val categoriasAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categorias)
        binding.actvCategoria.setAdapter(categoriasAdapter)
    }

    private fun configurarModo() {
        val esEdicion = viewModel.modoEdicion
        binding.tilPassword.isVisible = !esEdicion
        binding.etUsername.isEnabled = !esEdicion
        binding.tilActivo.isVisible = esEdicion
        binding.btnDesactivar.isVisible = esEdicion
        // En edicion: el perfil de empleado se gestiona desde P14/P15, no aqui
        binding.layoutPerfilEmpleado.isVisible = !esEdicion
    }

    private fun configurarListeners() {
        // Al cambiar el rol: mostrar/ocultar seccion de empleado y sugerir username
        binding.actvRol.setOnItemClickListener { _, _, _, _ ->
            val rol = rolFromLabel(binding.actvRol.text.toString())
            binding.layoutPerfilEmpleado.isVisible = (rol != Rol.ADMIN)
            viewModel.sugerirUsername(rol)
        }

        binding.btnGuardar.setOnClickListener {
            binding.btnGuardar.isEnabled = false
            try {
                val rol = rolFromLabel(binding.actvRol.text.toString())
                if (viewModel.modoEdicion) {
                    viewModel.actualizar(
                        email = binding.etEmail.text.toString().trim(),
                        rol = rol,
                        activo = binding.switchActivo.isChecked
                    )
                } else {
                    viewModel.crear(
                        username = binding.etUsername.text.toString().trim(),
                        password = binding.etPassword.text.toString().trim(),
                        email = binding.etEmail.text.toString().trim(),
                        rol = rol,
                        nombre = binding.etNombre.text.toString().trim(),
                        apellido1 = binding.etApellido1.text.toString().trim(),
                        apellido2 = binding.etApellido2.text.toString().trim().ifBlank { null },
                        dni = binding.etDni.text.toString().trim(),
                        categoria = categoriaFromLabel(binding.actvCategoria.text.toString()),
                        jornadaSemanalHoras = binding.etJornadaSemanal.text.toString().toDoubleOrNull(),
                        diasVacaciones = binding.etVacaciones.text.toString().toIntOrNull(),
                        diasAsuntos = binding.etAsuntos.text.toString().toIntOrNull()
                    )
                }
            } finally {
                binding.btnGuardar.isEnabled = true
            }
        }

        binding.btnDesactivar.setOnClickListener {
            mostrarDialogoDesactivar()
        }
    }

    // ------------------------------------------------------------------
    // Dialogo de confirmacion antes de desactivar (Decision 26)
    // ------------------------------------------------------------------

    private fun mostrarDialogoDesactivar() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.form_usuario_dialogo_desactivar_titulo))
            .setMessage(getString(R.string.form_usuario_dialogo_desactivar_mensaje))
            .setNegativeButton(getString(R.string.form_usuario_dialogo_cancelar), null)
            .setPositiveButton(getString(R.string.form_usuario_dialogo_confirmar)) { _, _ ->
                viewModel.desactivar()
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { procesarEstado(it) } }
                launch {
                    viewModel.usernameSugerido.collect { suggestion ->
                        if (suggestion == null) return@collect
                        val current = binding.etUsername.text.toString()
                        // Aplicar solo si el campo esta vacio o tiene la sugerencia anterior
                        if (current.isEmpty() || current == lastSuggestion) {
                            binding.etUsername.setText(suggestion)
                            lastSuggestion = suggestion
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    private fun procesarEstado(estado: FormUsuarioViewModel.UiState) {
        val cargando = estado is FormUsuarioViewModel.UiState.Loading ||
                       estado is FormUsuarioViewModel.UiState.Cargando
        binding.progressIndicator.isVisible = cargando
        binding.btnGuardar.isEnabled = !cargando

        when (estado) {
            is FormUsuarioViewModel.UiState.Success      -> findNavController().popBackStack()
            is FormUsuarioViewModel.UiState.Desactivado  -> findNavController().popBackStack()

            is FormUsuarioViewModel.UiState.SuccessAlta  -> {
                // Solo se emite en modo edicion: pre-rellenar campos al cargar
                binding.etUsername.setText(estado.usuario.username)
                binding.etEmail.setText(estado.usuario.email)
                binding.actvRol.setText(rolLabel(estado.usuario.rol), false)
                binding.switchActivo.isChecked = estado.usuario.activo
                binding.btnDesactivar.isVisible = estado.usuario.activo
            }

            is FormUsuarioViewModel.UiState.Error -> {
                Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                viewModel.limpiarError()
            }

            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Helpers de etiquetas para dropdowns
    // ------------------------------------------------------------------

    private fun rolLabel(rol: Rol): String = when (rol) {
        Rol.ADMIN     -> "Admin"
        Rol.ENCARGADO -> "Encargado"
        Rol.EMPLEADO  -> "Empleado"
    }

    private fun rolFromLabel(label: String): Rol =
        Rol.values().find { rolLabel(it) == label } ?: Rol.EMPLEADO

    private fun categoriaLabel(c: CategoriaEmpleado): String = when (c) {
        CategoriaEmpleado.OPERARIO       -> "Operario"
        CategoriaEmpleado.ADMINISTRATIVO -> "Administrativo"
        CategoriaEmpleado.TECNICO        -> "Técnico"
        CategoriaEmpleado.ENCARGADO      -> "Encargado"
        CategoriaEmpleado.OTRO           -> "Otro"
    }

    private fun categoriaFromLabel(label: String): CategoriaEmpleado? =
        CategoriaEmpleado.values().find { categoriaLabel(it) == label }
}
