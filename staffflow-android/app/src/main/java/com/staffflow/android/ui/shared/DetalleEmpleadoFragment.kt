package com.staffflow.android.ui.shared

import android.graphics.Color
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.databinding.FragmentDetalleEmpleadoBinding
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
        binding.chipRegenerarPin.setOnClickListener {
            mostrarDialogConfirmarRegenerarPin()
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
        binding.tvNumeroEmpleado.text = e.numeroEmpleado
        if (e.activo) {
            binding.tvEstado.text = getString(R.string.detalle_empleado_activo)
            binding.tvEstado.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            binding.tvEstado.text = getString(R.string.detalle_empleado_baja)
            binding.tvEstado.setTextColor(Color.parseColor("#C62828"))
        }

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

        // Jornada
        binding.filaJornadaSemanal.tvLabel.text = "Jornada semanal"
        binding.filaJornadaSemanal.tvValor.text = "${e.jornadaSemanalHoras} h/semana"
        binding.filaJornadaDiaria.tvLabel.text  = "Jornada diaria"
        binding.filaJornadaDiaria.tvValor.text  = "${"%.2f".format(e.jornadaDiariaMinutos / 60.0)} h/día"
        binding.filaVacaciones.tvLabel.text     = "Vacaciones"
        binding.filaVacaciones.tvValor.text     = "${e.diasVacacionesAnuales} días/año"
        binding.filaAsuntosPropios.tvLabel.text = "Asuntos propios"
        binding.filaAsuntosPropios.tvValor.text = "${e.diasAsuntosPropiosAnuales} días/año"
    }

    /**
     * Aplica el gating por rol a los chips de accion:
     *   Editar         -> solo ADMIN
     *   Regenerar PIN  -> ADMIN o ENCARGADO
     */
    private fun aplicarGatingPorRol(rol: Rol?) {
        binding.chipEditar.isVisible = rol == Rol.ADMIN
        binding.chipRegenerarPin.isVisible = rol == Rol.ADMIN || rol == Rol.ENCARGADO
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
