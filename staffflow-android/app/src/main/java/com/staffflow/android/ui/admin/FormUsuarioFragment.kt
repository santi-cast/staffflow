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
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.databinding.FragmentFormUsuarioBinding
import com.staffflow.android.domain.model.CategoriaEmpleado
import com.staffflow.android.domain.model.Rol
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Formulario de usuario (P29). Solo ADMIN.
 *
 * Modo alta   (usuarioId = -1): E08 POST /usuarios.
 *   Al crear EMPLEADO o ENCARGADO: Snackbar con accion "Crear perfil" -> P15.
 * Modo edicion (usuarioId > 0): E11 PATCH (email, rol).
 *   Username y password no son editables en modo edicion.
 *   El estado activo NO se edita aqui (el backend ignora el campo en PATCH):
 *   para desactivar se usa el boton "Desactivar" (E12 DELETE) con confirmacion.
 *   Reactivar usuarios desactivados no esta soportado en v1.0 (no hay endpoint).
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

    /** Formato de presentacion para la fecha de creacion del usuario. */
    private val fmtFechaCreacion = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /** Formato visible para la fecha de alta del empleado (input editable). */
    private val fmtFechaAltaVisible = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** Formato ISO-8601 que se envia al backend en EmpleadoRequest.fechaAlta. */
    private val fmtFechaAltaIso = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Fecha de alta seleccionada por el admin (LocalDate). Default = hoy.
     * Solo se usa en modo alta cuando el rol no es ADMIN. Se envia al backend
     * como String ISO-8601 ("yyyy-MM-dd") y debe ser >= hoy.
     */
    private var fechaAltaSeleccionada: LocalDate = LocalDate.now()

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
        configurarFechaAlta()
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
        // btnDesactivar arranca oculto y se hace visible solo si el usuario cargado
        // resulta estar activo (ver observarEstado()). En modo alta nunca se muestra.
        binding.btnDesactivar.isVisible = false
        // En edicion: el perfil de empleado se gestiona desde P14/P15, no aqui
        binding.layoutPerfilEmpleado.isVisible = !esEdicion
    }

    /**
     * Inicializa el campo de fecha de alta del empleado.
     * Precarga "hoy" como default y delega el click al MaterialDatePicker.
     * El EditText es no-focusable (ver layout) para forzar el dialogo.
     */
    private fun configurarFechaAlta() {
        actualizarTextoFechaAlta()
        binding.etFechaAlta.setOnClickListener { abrirSelectorFechaAlta() }
        binding.tilFechaAlta.setEndIconOnClickListener { abrirSelectorFechaAlta() }
    }

    private fun actualizarTextoFechaAlta() {
        binding.etFechaAlta.setText(fechaAltaSeleccionada.format(fmtFechaAltaVisible))
    }

    /**
     * Abre un MaterialDatePicker limitado a fechas iguales o posteriores a hoy
     * (DateValidatorPointForward.now()). El backend valida lo mismo y responde
     * HTTP 400 si llega una fecha anterior, pero filtrar en cliente evita el
     * ida-y-vuelta innecesario.
     */
    private fun abrirSelectorFechaAlta() {
        val hoyUtcMillis = LocalDate.now()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val seleccionUtcMillis = fechaAltaSeleccionada
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.from(hoyUtcMillis))
            .setStart(hoyUtcMillis)
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.form_usuario_hint_fecha_alta)
            .setSelection(seleccionUtcMillis)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            // MaterialDatePicker devuelve siempre en UTC: convertir a LocalDate
            // usando ZoneOffset.UTC para evitar saltos de dia por TZ del dispositivo.
            fechaAltaSeleccionada = java.time.Instant.ofEpochMilli(millis)
                .atZone(ZoneId.of("UTC"))
                .toLocalDate()
            actualizarTextoFechaAlta()
        }
        picker.show(parentFragmentManager, "fechaAltaPicker")
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
                        rol = rol
                    )
                } else {
                    // Solo enviamos fechaAlta si el rol crea perfil de empleado.
                    // Para ADMIN no aplica (no tiene ficha de empleado).
                    val fechaAltaIso = if (rol != Rol.ADMIN) {
                        fechaAltaSeleccionada.format(fmtFechaAltaIso)
                    } else {
                        null
                    }
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
                        diasAsuntos = binding.etAsuntos.text.toString().toIntOrNull(),
                        fechaAlta = fechaAltaIso
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
                // Solo se emite en modo edicion: pre-rellenar campos al cargar.
                // btnDesactivar solo visible si el usuario esta activo (si ya esta
                // inactivo no hay accion posible: reactivar no esta soportado en v1).
                binding.etUsername.setText(estado.usuario.username)
                binding.etEmail.setText(estado.usuario.email)
                binding.actvRol.setText(rolLabel(estado.usuario.rol), false)
                binding.btnDesactivar.isVisible = estado.usuario.activo
                mostrarFechaCreacion(estado.usuario.fechaCreacion)
            }

            is FormUsuarioViewModel.UiState.Error -> {
                Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                viewModel.limpiarError()
            }

            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Fecha de creacion (solo modo edicion)
    // ------------------------------------------------------------------

    /**
     * Muestra la fecha de creacion del usuario formateada como "dd/MM/yyyy HH:mm".
     * Si el valor llega vacio o el parseo del ISO-8601 falla, el TextView se
     * deja oculto (defensa silenciosa: la fecha es metadato informativo, no
     * justifica romper la pantalla).
     */
    private fun mostrarFechaCreacion(iso8601: String?) {
        if (iso8601.isNullOrBlank()) {
            binding.tvFechaCreacion.isVisible = false
            return
        }
        try {
            val fecha = LocalDateTime.parse(iso8601).format(fmtFechaCreacion)
            binding.tvFechaCreacion.text = getString(R.string.form_usuario_creado_el, fecha)
            binding.tvFechaCreacion.isVisible = true
        } catch (_: DateTimeParseException) {
            binding.tvFechaCreacion.isVisible = false
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
