package com.staffflow.android.ui.admin

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
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
 * Modo alta   (usuarioId = -1): E08 POST /usuarios + E13 POST /empleados
 *   en cadena dentro del ViewModel cuando rol != ADMIN.
 * Modo edicion (usuarioId > 0): E11 PATCH (email, rol).
 *
 * El campo username NUNCA es editable a mano: en modo alta se autorrellena
 * desde sugerirUsername(rol) del ViewModel siguiendo el prefijo del rol
 * (emp/encargado/admin + numero correlativo) para garantizar coherencia
 * con la convencion del sistema; en modo edicion el backend no admite
 * cambiarlo via PATCH y el campo aparece disabled. Password en modo alta
 * se introduce directamente; en modo edicion se puede cambiar pulsando
 * btnCambiarPassword que abre un dialogo con campo visible (E66). El estado
 * activo NO se edita por el campo `activo`: el boton btnCambiarEstado alterna
 * entre "Desactivar" (E12 DELETE) y "Activar" (E67 PATCH /reactivar) segun
 * usuario.activo, ambos con confirmacion via MaterialAlertDialog. Patron
 * simetrico al de DetalleEmpleadoFragment (P15).
 *
 * Argumentos de navegacion esperados (Bundle):
 *   usuarioId  Long  -1 = alta | >0 = edicion
 *
 * Cabecera de empleado asociado (solo edicion + rol != ADMIN): se carga via E68
 * GET /empleados/by-usuario/{usuarioId} y se renderiza como un MaterialButton
 * TextButton con el texto "Empleado: Nombre Apellido1 Apellido2 (EMP-XXX)" y
 * un icono de lapiz a la derecha que abre P14 DetalleEmpleadoFragment. Patron
 * simetrico al boton "Usuario: {username}" de la cabecera de P14. Si el usuario
 * es ADMIN o el endpoint devuelve 404, el boton permanece oculto.
 */
class FormUsuarioFragment : Fragment() {

    private var _binding: FragmentFormUsuarioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormUsuarioViewModel by viewModels()

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

    /**
     * Cache local del estado activo del usuario cargado en modo edicion.
     * Lo refrescamos en SuccessAlta y lo consultamos en btnCambiarEstado para
     * decidir si abrir el dialogo de desactivar o el de activar (E12 vs E67).
     * Mantener un campo local evita depender de uiState (que cambia a Loading
     * durante las llamadas) en el momento del click. Null = aun no cargado.
     */
    private var usuarioActivo: Boolean? = null

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
        // Username nunca editable a mano: en alta lo autogenera el ViewModel
        // a partir del rol; en edicion el backend no admite cambiarlo via PATCH.
        // Se deshabilita el TextInputLayout completo para que el campo aparezca
        // visualmente atenuado (gris) en ambos modos.
        binding.tilUsername.isEnabled = false
        // btnCambiarPassword solo visible en modo edicion (E66). En alta la
        // contrasena se introduce directamente en tilPassword.
        binding.btnCambiarPassword.isVisible = esEdicion
        // btnCambiarEstado arranca oculto. En modo edicion se hace visible al
        // cargar el usuario, con texto y color segun usuario.activo (ver
        // SuccessAlta en procesarEstado). En modo alta nunca se muestra.
        binding.btnCambiarEstado.isVisible = false
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

        binding.btnCambiarEstado.setOnClickListener {
            // Decidir desactivar vs activar segun el estado del usuario cargado.
            // Mismo patron que DetalleEmpleadoFragment (P15).
            when (usuarioActivo) {
                true  -> mostrarDialogoDesactivar()
                false -> mostrarDialogoActivar()
                null  -> Unit // usuario aun no cargado, ignorar click
            }
        }

        binding.btnCambiarPassword.setOnClickListener {
            mostrarDialogoCambiarPassword()
        }
    }

    // ------------------------------------------------------------------
    // Dialogos de confirmacion antes de cambiar estado (Decision 26)
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

    private fun mostrarDialogoActivar() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.form_usuario_dialogo_activar_titulo))
            .setMessage(getString(R.string.form_usuario_dialogo_activar_mensaje))
            .setNegativeButton(getString(R.string.form_usuario_dialogo_cancelar), null)
            .setPositiveButton(getString(R.string.form_usuario_dialogo_confirmar)) { _, _ ->
                viewModel.reactivar()
            }
            .show()
    }

    /**
     * Configura el texto y el color del boton bimodal Desactivar/Activar
     * segun el estado actual del usuario cargado. Se invoca desde SuccessAlta
     * en procesarEstado(). Patron simetrico al de DetalleEmpleadoFragment (P15).
     *
     * Activo   -> "Desactivar" outlined rojo  (#C62828)
     * Inactivo -> "Activar"    outlined verde (#2E7D32)
     */
    private fun configurarBotonCambiarEstado(activo: Boolean) {
        if (activo) {
            binding.btnCambiarEstado.text = getString(R.string.form_usuario_desactivar)
            val rojo = Color.parseColor("#C62828")
            binding.btnCambiarEstado.setTextColor(rojo)
            binding.btnCambiarEstado.strokeColor = android.content.res.ColorStateList.valueOf(rojo)
        } else {
            binding.btnCambiarEstado.text = getString(R.string.form_usuario_activar)
            val verde = Color.parseColor("#2E7D32")
            binding.btnCambiarEstado.setTextColor(verde)
            binding.btnCambiarEstado.strokeColor = android.content.res.ColorStateList.valueOf(verde)
        }
    }

    // ------------------------------------------------------------------
    // Dialogo de cambio de contrasena (E66)
    // ------------------------------------------------------------------

    /**
     * Muestra un dialogo con un campo TextInputLayout para que el ADMIN
     * introduzca la nueva contrasena del usuario en edicion.
     *
     * La contrasena se muestra siempre en claro (sin toggle de visibilidad)
     * porque el caso de uso es helpdesk: el admin necesita leer la contrasena
     * para comunicarsela al empleado, no hay motivo para ocultarla.
     *
     * Validacion inline: si la contrasena tiene menos de 8 caracteres se
     * muestra error en el propio campo y el dialogo NO se cierra.
     * Si supera la validacion se llama a viewModel.resetearPassword().
     */
    private fun mostrarDialogoCambiarPassword() {
        val dialogView = layoutInflater.inflate(
            R.layout.dialog_cambiar_password, null
        )
        val tilNuevaPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(
            R.id.tilNuevaPassword
        )
        val etNuevaPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.etNuevaPassword
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.form_usuario_dialogo_password_titulo))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.form_usuario_dialogo_cancelar), null)
            .setPositiveButton(getString(R.string.form_usuario_dialogo_password_confirmar), null)
            .create()

        // El listener del boton positivo se asigna manualmente para poder
        // validar sin cerrar el dialogo si la contrasena es demasiado corta.
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nuevaPassword = etNuevaPassword.text.toString()
                if (nuevaPassword.length < 8) {
                    tilNuevaPassword.error = getString(R.string.form_usuario_dialogo_password_error_corta)
                    return@setOnClickListener
                }
                tilNuevaPassword.error = null
                dialog.dismiss()
                viewModel.resetearPassword(nuevaPassword)
            }
        }
        dialog.show()
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
                        // El campo no es editable a mano (focusable=false en layout),
                        // por tanto cualquier sugerencia recibida en modo alta
                        // sobrescribe siempre el contenido del input.
                        if (suggestion != null) {
                            binding.etUsername.setText(suggestion)
                        }
                    }
                }
                launch {
                    viewModel.cabeceraEmpleado.collect { cabecera ->
                        pintarCabeceraEmpleado(cabecera)
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
        binding.btnCambiarPassword.isEnabled = !cargando
        binding.btnCambiarEstado.isEnabled = !cargando

        when (estado) {
            is FormUsuarioViewModel.UiState.Success      -> finalizarConMensaje(
                if (viewModel.modoEdicion) R.string.form_usuario_resultado_actualizado
                else R.string.form_usuario_resultado_creado
            )
            is FormUsuarioViewModel.UiState.Desactivado  -> finalizarConMensaje(
                R.string.form_usuario_resultado_desactivado
            )
            is FormUsuarioViewModel.UiState.Reactivado   -> finalizarConMensaje(
                R.string.form_usuario_resultado_reactivado
            )

            is FormUsuarioViewModel.UiState.PasswordReseteado -> {
                // No navega atras: el admin puede seguir editando otros campos.
                Snackbar.make(
                    binding.root,
                    getString(R.string.form_usuario_resultado_password_reseteado),
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.limpiarPasswordReseteado()
            }

            is FormUsuarioViewModel.UiState.SuccessAlta  -> {
                // Solo se emite en modo edicion: pre-rellenar campos al cargar.
                // btnCambiarEstado se configura segun usuario.activo: texto y color
                // alternan entre "Desactivar" (rojo, E12) y "Activar" (verde, E67).
                binding.etUsername.setText(estado.usuario.username)
                binding.etEmail.setText(estado.usuario.email)
                binding.actvRol.setText(rolLabel(estado.usuario.rol), false)
                usuarioActivo = estado.usuario.activo
                binding.btnCambiarEstado.isVisible = true
                configurarBotonCambiarEstado(estado.usuario.activo)
                mostrarFechaCreacion(estado.usuario.fechaCreacion)
            }

            is FormUsuarioViewModel.UiState.Error -> {
                Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                viewModel.limpiarError()
            }

            is FormUsuarioViewModel.UiState.UsernameDuplicadoEnAlta -> {
                // Conflicto de username detectado por el backend (carrera entre
                // dos admins simultaneos en la misma maquina o en clientes
                // distintos). Avisamos al usuario, regeneramos automaticamente
                // el username con el siguiente prefijo libre y dejamos el resto
                // del formulario intacto para que solo tenga que dar Guardar.
                Snackbar.make(
                    binding.root,
                    getString(R.string.form_usuario_username_duplicado, estado.mensaje),
                    Snackbar.LENGTH_LONG
                ).show()
                viewModel.sugerirUsername(estado.rol)
                viewModel.limpiarError()
            }

            else -> Unit
        }
    }

    /**
     * Envia un mensaje de resultado al fragment destino (P28 UsuariosFragment)
     * via FragmentResult y vuelve atras. UsuariosFragment lo recibe en
     * setFragmentResultListener("usuarioResultado") y muestra Snackbar.
     *
     * Mismo patron que TipoPausaFragment -> ConfirmacionFragment, elegido
     * por ser idiomatico de Android Navigation y sobrevivir al popBackStack
     * (un Snackbar lanzado aqui se mata con la transicion de fragment).
     *
     * @param mensajeRes recurso de string con el texto a mostrar en P28
     */
    private fun finalizarConMensaje(mensajeRes: Int) {
        setFragmentResult(
            KEY_RESULTADO,
            bundleOf(ARG_MENSAJE_RES to mensajeRes)
        )
        findNavController().popBackStack()
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
    // Cabecera read-only del empleado asociado (E68)
    // ------------------------------------------------------------------

    /**
     * Pinta el boton "Empleado: Nombre Apellido1 Apellido2 (EMP-XXX)" con
     * icono de lapiz cuando el ViewModel tiene un empleado cargado. Si
     * cabecera es null, oculta el boton. Al pulsarlo navega a P14 con
     * bundleOf("empleadoId" to ...) usando la action
     * action_form_usuario_to_detalle_empleado del nav_graph.
     *
     * Simetrico al boton "Usuario: {username}" de la cabecera de P14.
     */
    private fun pintarCabeceraEmpleado(cabecera: CabeceraEmpleado?) {
        if (cabecera == null) {
            binding.btnEditarEmpleado.isVisible = false
            binding.btnEditarEmpleado.setOnClickListener(null)
            return
        }
        binding.btnEditarEmpleado.text = getString(
            R.string.form_usuario_cabecera_empleado,
            cabecera.nombreCompleto,
            cabecera.numeroEmpleado
        )
        binding.btnEditarEmpleado.isVisible = true
        binding.btnEditarEmpleado.setOnClickListener {
            findNavController().navigate(
                R.id.action_form_usuario_to_detalle_empleado,
                bundleOf("empleadoId" to cabecera.empleadoId)
            )
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

    companion object {
        /**
         * Clave del FragmentResult que envia P29 a P28 al terminar una operacion
         * con exito (alta, edicion, desactivacion o reactivacion). UsuariosFragment
         * debe registrar setFragmentResultListener con esta misma clave.
         */
        const val KEY_RESULTADO = "usuarioResultado"

        /**
         * Argumento dentro del Bundle del FragmentResult: id del recurso
         * de string con el mensaje localizado a mostrar en P28 (Int).
         */
        const val ARG_MENSAJE_RES = "mensajeRes"
    }
}
