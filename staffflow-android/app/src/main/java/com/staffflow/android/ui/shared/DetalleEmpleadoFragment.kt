package com.staffflow.android.ui.shared

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.databinding.FragmentDetalleEmpleadoBinding
import com.staffflow.android.ui.admin.FormEmpleadoFragment
import com.staffflow.android.domain.model.CategoriaEmpleado
import com.staffflow.android.domain.model.Rol
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Detalle de un empleado (P14).
 *
 * Patron C - dato unico, solo lectura con acciones.
 * Endpoint: E15 GET /empleados/{id}
 *
 * Tres estados:
 *   Loading -> CircularProgressIndicator centrado
 *   Error   -> icono nube + mensaje + boton Reintentar
 *   Success -> ScrollView con cards de datos y chips de acciones
 *
 * Recibe empleadoId como argumento de navegacion (Long).
 *
 * Chips de accion:
 *   Ver saldo      -> P25 (action_detalle_to_saldo_individual)
 *   Ver fichajes   -> P21 InformeFichajesEmpleado (action_detalle_to_informe_fichajes)
 *   Ver ausencias  -> action_detalle_to_ausencias
 *   Editar         -> P15 (FormEmpleadoFragment).        Solo ADMIN.
 *   Regenerar PIN  -> E65 POST /empleados/{id}/regenerar-pin.
 *                     ADMIN o ENCARGADO. Confirmacion + dialog con PIN nuevo.
 *                     Se deshabilita si el empleado esta inactivo.
 *
 * Boton bimodal en el header (debajo del estado), solo ADMIN:
 *   Activo   -> "Desactivar" (rojo)  -> E17 PATCH /empleados/{id}/baja
 *   Inactivo -> "Activar"    (verde) -> E18 PATCH /empleados/{id}/reactivar
 *   En ambos casos: dialog de confirmacion + Snackbar de resultado.
 *   El backend mantiene "baja/reactivar" en URL por compatibilidad de la
 *   API publicada; la UI adopta "desactivar/activar" por cubrir mas casos
 *   (excedencias, bajas medicas, permisos sin sueldo).
 *
 * Feedback de operaciones: P15 (FormEmpleadoFragment) envia un FragmentResult
 * con clave FormEmpleadoFragment.KEY_RESULTADO al terminar una edicion con
 * exito. Este fragment registra setFragmentResultListener y muestra el
 * mensaje en un Snackbar para que sobreviva al popBackStack. Mismo patron
 * P29 -> P28 para usuarios.
 */
class DetalleEmpleadoFragment : Fragment() {

    private var _binding: FragmentDetalleEmpleadoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetalleEmpleadoViewModel by viewModels()

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetalleEmpleadoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val empleadoId = arguments?.getLong("empleadoId") ?: -1L
        viewModel.init(empleadoId)
        configurarListeners()
        configurarResultadoEdicion()
        observarViewModel()
    }

    /**
     * Recibe el resultado de P15 (FormEmpleadoFragment) cuando termina una
     * edicion con exito y muestra un Snackbar con el mensaje localizado. El
     * Bundle trae ARG_MENSAJE_RES con el resId del string.
     *
     * Lanzar el Snackbar aqui (no en P15) garantiza que sobreviva al
     * popBackStack: la transicion de fragments mataria un Snackbar lanzado
     * desde el form al volver atras.
     */
    private fun configurarResultadoEdicion() {
        setFragmentResultListener(FormEmpleadoFragment.KEY_RESULTADO) { _, bundle ->
            val mensajeRes = bundle.getInt(FormEmpleadoFragment.ARG_MENSAJE_RES)
            if (mensajeRes != 0) {
                Snackbar.make(binding.root, getString(mensajeRes), Snackbar.LENGTH_SHORT).show()
            }
        }
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
        binding.chipVerSaldo.setOnClickListener {
            val args = Bundle().apply {
                putLong("empleadoId", arguments?.getLong("empleadoId") ?: -1L)
            }
            findNavController().navigate(R.id.action_detalle_to_saldo_individual, args)
        }
        binding.chipVerFichajes.setOnClickListener {
            val args = Bundle().apply {
                putLong("empleadoId", arguments?.getLong("empleadoId") ?: -1L)
            }
            findNavController().navigate(R.id.action_detalle_to_informe_fichajes, args)
        }
        binding.chipVerAusencias.setOnClickListener {
            val args = Bundle().apply {
                putLong("empleadoId", arguments?.getLong("empleadoId") ?: -1L)
            }
            findNavController().navigate(R.id.action_detalle_to_ausencias, args)
        }
        binding.chipEditar.setOnClickListener {
            val args = Bundle().apply {
                putLong("empleadoId", arguments?.getLong("empleadoId") ?: -1L)
            }
            findNavController().navigate(R.id.action_detalle_to_form_empleado, args)
        }
        // Boton "Usuario: {username}" -> P29 FormUsuarioFragment.
        // Solo navega si hay empleado cargado y trae usuarioId (lo trae siempre
        // en E15; el gating por rol y por username!=null vive en aplicarGatingPorRol).
        binding.btnEditarUsuario.setOnClickListener {
            val estado = viewModel.uiState.value
            if (estado is DetalleEmpleadoViewModel.UiState.Success) {
                val args = Bundle().apply {
                    putLong("usuarioId", estado.empleado.usuarioId)
                }
                findNavController().navigate(R.id.action_detalle_to_form_usuario, args)
            }
        }
        binding.chipRegenerarPin.setOnClickListener {
            mostrarDialogConfirmarRegenerarPin()
        }
        binding.btnCambiarEstado.setOnClickListener {
            // Decidir desactivar vs activar segun el estado actual del empleado
            val estado = viewModel.uiState.value
            if (estado is DetalleEmpleadoViewModel.UiState.Success) {
                if (estado.empleado.activo) {
                    mostrarDialogConfirmarDesactivar()
                } else {
                    mostrarDialogConfirmarActivar()
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { procesarEstado(it) } }
                launch { viewModel.rol.collect { aplicarGatingPorRol(it) } }
                launch {
                    viewModel.eventoRegenerarPin.collect { evento ->
                        when (evento) {
                            is RegenerarPinEvento.Cargando -> {
                                binding.chipRegenerarPin.isEnabled = false
                            }
                            is RegenerarPinEvento.Exito -> {
                                binding.chipRegenerarPin.isEnabled = true
                                mostrarDialogPinRegenerado(evento.pin)
                            }
                            is RegenerarPinEvento.Error -> {
                                binding.chipRegenerarPin.isEnabled = true
                                val msg = when (evento.codigo) {
                                    "404" -> getString(R.string.regenerar_pin_error_404)
                                    "red" -> getString(R.string.regenerar_pin_error_red)
                                    else -> getString(R.string.regenerar_pin_error_generico)
                                }
                                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.eventoCambioEstado.collect { evento ->
                        when (evento) {
                            is CambioEstadoEmpleadoEvento.Cargando -> {
                                binding.btnCambiarEstado.isEnabled = false
                            }
                            is CambioEstadoEmpleadoEvento.Exito -> {
                                binding.btnCambiarEstado.isEnabled = true
                                val msg = if (evento.activado) {
                                    getString(R.string.detalle_empleado_activar_ok)
                                } else {
                                    getString(R.string.detalle_empleado_desactivar_ok)
                                }
                                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                            }
                            is CambioEstadoEmpleadoEvento.Error -> {
                                binding.btnCambiarEstado.isEnabled = true
                                val msg = when (evento.codigo) {
                                    "404" -> getString(R.string.detalle_empleado_estado_error_404)
                                    "409" -> getString(R.string.detalle_empleado_estado_error_409)
                                    "red" -> getString(R.string.detalle_empleado_estado_error_red)
                                    else -> getString(R.string.detalle_empleado_estado_error_generico)
                                }
                                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    private fun procesarEstado(estado: DetalleEmpleadoViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is DetalleEmpleadoViewModel.UiState.Loading
        binding.layoutError.isVisible       = estado is DetalleEmpleadoViewModel.UiState.Error
        binding.scrollContenido.isVisible   = estado is DetalleEmpleadoViewModel.UiState.Success

        when (estado) {
            is DetalleEmpleadoViewModel.UiState.Error   -> binding.tvErrorMensaje.text = estado.mensaje
            is DetalleEmpleadoViewModel.UiState.Success -> mostrarDatos(estado.empleado)
            else -> Unit
        }
    }

    private fun mostrarDatos(e: EmpleadoResponse) {
        // Header
        binding.tvNombreCompleto.text = buildString {
            append(e.nombre)
            append(" ")
            append(e.apellido1)
            e.apellido2?.let { append(" $it") }
        }
        binding.tvNumeroEmpleado.text =
            getString(R.string.detalle_empleado_numero_empleado, e.numeroEmpleado)
        if (e.activo) {
            binding.tvEstado.text = getString(R.string.detalle_empleado_activo)
            binding.tvEstado.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            binding.tvEstado.text = getString(R.string.detalle_empleado_inactivo)
            binding.tvEstado.setTextColor(Color.parseColor("#C62828"))
        }

        // Boton bimodal Desactivar/Activar (solo ADMIN; ver aplicarGatingPorRol)
        configurarBotonCambiarEstado(e.activo)

        // Regenerar PIN se deshabilita si el empleado esta inactivo.
        // No tiene sentido regenerar el PIN de alguien que no puede fichar;
        // ademas evita ruido si vuelve activo con un PIN distinto al recordado.
        binding.chipRegenerarPin.isEnabled = e.activo

        // Datos personales
        binding.filaDni.tvLabel.text  = "DNI"
        binding.filaDni.tvValor.text  = e.dni
        binding.filaFechaAlta.tvLabel.text = "Fecha de alta"
        binding.filaFechaAlta.tvValor.text = LocalDate.parse(e.fechaAlta)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        binding.filaCategoria.tvLabel.text = "Categoría"
        binding.filaCategoria.tvValor.text = nombreCategoria(e.categoria)

        // Email y PIN: visibles solo para ADMIN
        val esAdmin = viewModel.rol.value == Rol.ADMIN

        val email = e.email
        binding.filaEmail.root.isVisible = email != null && esAdmin
        if (email != null && esAdmin) {
            binding.filaEmail.tvLabel.text = "Email"
            binding.filaEmail.tvValor.text = email
        }

        val pin = e.pinTerminal
        binding.filaPinTerminal.root.isVisible = pin != null && esAdmin
        if (pin != null && esAdmin) {
            binding.filaPinTerminal.tvLabel.text = getString(R.string.detalle_empleado_pin)
            binding.filaPinTerminal.tvValor.text = pin
        }

        // Boton "Usuario: {username}" -> P29. Solo visible para ADMIN y solo
        // si la respuesta trae username (E15 lo expone con valor cuando llama
        // un ADMIN; ENCARGADO recibe null por Opcion A, asi que el boton se
        // oculta tambien por dato). Texto dinamico con el username actual.
        val username = e.username
        binding.btnEditarUsuario.isVisible = username != null && esAdmin
        if (username != null && esAdmin) {
            binding.btnEditarUsuario.text =
                getString(R.string.detalle_empleado_btn_editar_usuario, username)
        }

        // Jornada
        binding.filaJornadaSemanal.tvLabel.text = "Jornada semanal"
        binding.filaJornadaSemanal.tvValor.text = "${formatearHoras(e.jornadaSemanalHoras)} h/semana"
        binding.filaJornadaDiaria.tvLabel.text  = "Jornada diaria"
        binding.filaJornadaDiaria.tvValor.text  = "${"%.2f".format(e.jornadaDiariaMinutos / 60.0)} h/día"
        binding.filaVacaciones.tvLabel.text     = "Vacaciones"
        binding.filaVacaciones.tvValor.text     = "${e.diasVacacionesAnuales} días/año"
        binding.filaAsuntosPropios.tvLabel.text = "Asuntos propios"
        binding.filaAsuntosPropios.tvValor.text = "${e.diasAsuntosPropiosAnuales} días/año"
    }

    /**
     * Formatea horas como entero cuando no tiene parte decimal ("40") y como
     * decimal con un dígito cuando si la tiene ("37.5"). Evita mostrar "40.0"
     * en jornadas enteras manteniendo legibilidad para fracciones como 37.5h.
     */
    private fun formatearHoras(horas: Double): String =
        if (horas % 1.0 == 0.0) horas.toInt().toString() else "%.1f".format(horas)

    /**
     * Aplica el gating por rol a los chips de accion y a los botones de la
     * cabecera:
     *   Editar             -> solo ADMIN
     *   Regenerar PIN      -> ADMIN o ENCARGADO
     *   Desactivar/Activar -> solo ADMIN (E17/E18)
     *   Editar usuario     -> solo ADMIN (ademas se exige username != null en
     *                         mostrarDatos, porque ENCARGADO recibe username=null
     *                         por Opcion A y no podria editar usuarios igualmente).
     */
    private fun aplicarGatingPorRol(rol: Rol?) {
        binding.chipEditar.isVisible = rol == Rol.ADMIN
        binding.chipRegenerarPin.isVisible = rol == Rol.ADMIN || rol == Rol.ENCARGADO
        binding.btnCambiarEstado.isVisible = rol == Rol.ADMIN
        // btnEditarUsuario se gatea en mostrarDatos cruzando rol + username
        // disponible, asi que aqui no se toca: en una emision de rol sin
        // datos del empleado todavia, dejarlo GONE evita un flash inicial.
        if (rol != Rol.ADMIN) {
            binding.btnEditarUsuario.isVisible = false
        }
    }

    /**
     * Configura el texto y el color del boton bimodal Desactivar/Activar
     * segun el estado actual del empleado. Se invoca desde mostrarDatos()
     * cada vez que el ViewModel emite UiState.Success, asi que tras una
     * llamada exitosa a E17 o E18 el boton se redibuja automaticamente
     * (el ViewModel actualiza uiState con el nuevo valor de `activo`).
     *
     * Activo   -> "Desactivar" outlined rojo  (#C62828, mismo que tvEstado inactivo)
     * Inactivo -> "Activar"    outlined verde (#2E7D32, mismo que tvEstado activo)
     */
    private fun configurarBotonCambiarEstado(activo: Boolean) {
        if (activo) {
            binding.btnCambiarEstado.text = getString(R.string.detalle_empleado_btn_desactivar)
            val rojo = Color.parseColor("#C62828")
            binding.btnCambiarEstado.setTextColor(rojo)
            binding.btnCambiarEstado.strokeColor = android.content.res.ColorStateList.valueOf(rojo)
        } else {
            binding.btnCambiarEstado.text = getString(R.string.detalle_empleado_btn_activar)
            val verde = Color.parseColor("#2E7D32")
            binding.btnCambiarEstado.setTextColor(verde)
            binding.btnCambiarEstado.strokeColor = android.content.res.ColorStateList.valueOf(verde)
        }
    }

    // ------------------------------------------------------------------
    // Dialogs E17 / E18 - Desactivar / Activar
    // ------------------------------------------------------------------

    private fun mostrarDialogConfirmarDesactivar() {
        val empleadoId = arguments?.getLong("empleadoId") ?: -1L
        val nombre = nombreEmpleadoActual()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.detalle_empleado_desactivar_dialog_titulo, nombre))
            .setMessage(R.string.detalle_empleado_desactivar_dialog_mensaje)
            .setNegativeButton(R.string.form_ausencia_dialogo_cancelar, null)
            .setPositiveButton(R.string.detalle_empleado_btn_desactivar) { _, _ ->
                viewModel.desactivar(empleadoId)
            }
            .show()
    }

    private fun mostrarDialogConfirmarActivar() {
        val empleadoId = arguments?.getLong("empleadoId") ?: -1L
        val nombre = nombreEmpleadoActual()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.detalle_empleado_activar_dialog_titulo, nombre))
            .setMessage(R.string.detalle_empleado_activar_dialog_mensaje)
            .setNegativeButton(R.string.form_ausencia_dialogo_cancelar, null)
            .setPositiveButton(R.string.detalle_empleado_btn_activar) { _, _ ->
                viewModel.activar(empleadoId)
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Dialogs E65 - Regenerar PIN
    // ------------------------------------------------------------------

    private fun mostrarDialogConfirmarRegenerarPin() {
        val empleadoId = arguments?.getLong("empleadoId") ?: -1L
        val nombre = nombreEmpleadoActual()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.regenerar_pin_dialog_titulo)
            .setMessage(getString(R.string.regenerar_pin_dialog_mensaje, nombre))
            .setPositiveButton(R.string.form_ausencia_dialogo_cancelar, null)
            .setNegativeButton(R.string.regenerar_pin_dialog_confirmar) { _, _ ->
                viewModel.regenerarPin(empleadoId)
            }
            .show()
    }

    private fun mostrarDialogPinRegenerado(pin: String) {
        val nombre = nombreEmpleadoActual()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.regenerar_pin_resultado_titulo)
            .setMessage(getString(R.string.regenerar_pin_resultado_mensaje, nombre, pin))
            .setPositiveButton(R.string.cerrar, null)
            .show()
    }

    /**
     * Devuelve el nombre del empleado del estado actual del ViewModel.
     * Si no hay Success cargado todavia, usa un fallback generico.
     */
    private fun nombreEmpleadoActual(): String {
        val estado = viewModel.uiState.value
        return if (estado is DetalleEmpleadoViewModel.UiState.Success) {
            val e = estado.empleado
            buildString {
                append(e.nombre)
                append(" ")
                append(e.apellido1)
                e.apellido2?.let { append(" $it") }
            }
        } else {
            "este empleado"
        }
    }

    private fun nombreCategoria(categoria: CategoriaEmpleado): String = when (categoria) {
        CategoriaEmpleado.OPERARIO       -> "Operario"
        CategoriaEmpleado.ADMINISTRATIVO -> "Administrativo"
        CategoriaEmpleado.TECNICO        -> "Técnico"
        CategoriaEmpleado.ENCARGADO      -> "Encargado"
        CategoriaEmpleado.OTRO           -> "Otro"
    }
}
