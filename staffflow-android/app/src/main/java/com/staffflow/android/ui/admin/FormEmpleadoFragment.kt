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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Formulario de edicion de empleado (P15). Solo accesible para ADMIN.
 *
 * Patron F - formulario edit.
 * Flujo: E15 GET /empleados/{id} para precargar + E16 PATCH /empleados/{id}
 * para guardar los campos modificables (nombre, apellidos, categoria,
 * jornada, vacaciones, asuntos propios).
 *
 * El alta de empleado se hace siempre desde P29 (FormUsuarioFragment) junto
 * al alta del usuario. No se admite empleadoId = -1 en esta pantalla.
 *
 * Argumento de navegacion:
 *   empleadoId: Long (debe ser > 0)
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

    /** Formato de presentacion para la fecha de alta del empleado (read-only en la cabecera ficha). */
    private val fmtFechaAlta = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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
        viewModel.init(empleadoId)
        configurarCategoriasDropdown()
        requireActivity().title = getString(R.string.form_empleado_titulo_edicion)
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

    /** Precarga los campos del formulario con los datos del empleado. */
    private fun prerellenarCampos(e: EmpleadoResponse) {
        rellenarCabecera(e)

        binding.etNombre.setText(e.nombre)
        binding.etApellido1.setText(e.apellido1)
        binding.etApellido2.setText(e.apellido2 ?: "")
        binding.etJornadaSemanal.setText(e.jornadaSemanalHoras.toString())
        binding.etVacaciones.setText(e.diasVacacionesAnuales.toString())
        binding.etAsuntos.setText(e.diasAsuntosPropiosAnuales.toString())

        val index = categorias.indexOf(e.categoria)
        if (index >= 0) binding.actvCategoria.setText(categoriaLabels[index], false)
    }

    /**
     * Rellena la cabecera read-only con numeroEmpleado, username, email, rol
     * y fechaAlta. Patron item_ficha_fila (label + valor en linea).
     *
     * E15 devuelve username, email y rol solo cuando el llamante es ADMIN
     * (Opcion A). P15 solo es accesible a ADMIN, asi que en el caso normal
     * los tres campos vienen rellenos. Si por defensa llegan null se muestra
     * un guion para que la fila no se vea rota.
     *
     * fechaAlta llega en ISO-8601 desde el backend; se formatea como
     * "dd/MM/yyyy" para presentacion. Si el parseo falla se muestra un guion
     * (defensa silenciosa: el dato es metadato informativo, no justifica
     * romper la pantalla).
     */
    private fun rellenarCabecera(e: EmpleadoResponse) {
        binding.filaNumeroEmpleado.tvLabel.text = getString(R.string.form_empleado_cabecera_numero_empleado)
        binding.filaNumeroEmpleado.tvValor.text = e.numeroEmpleado

        binding.filaUsuario.tvLabel.text = getString(R.string.form_empleado_cabecera_usuario)
        binding.filaUsuario.tvValor.text = e.username ?: "—"

        binding.filaEmail.tvLabel.text = getString(R.string.form_empleado_cabecera_email)
        binding.filaEmail.tvValor.text = e.email ?: "—"

        binding.filaRol.tvLabel.text = getString(R.string.form_empleado_cabecera_rol)
        binding.filaRol.tvValor.text = e.rol?.name ?: "—"

        binding.filaFechaAlta.tvLabel.text = getString(R.string.form_empleado_cabecera_fecha_alta)
        binding.filaFechaAlta.tvValor.text = formatearFechaAlta(e.fechaAlta)
    }

    /**
     * Formatea la fecha de alta del empleado (ISO-8601) como "dd/MM/yyyy".
     * Devuelve "—" si el valor llega vacio o el parseo falla.
     */
    private fun formatearFechaAlta(iso8601: String?): String {
        if (iso8601.isNullOrBlank()) return "—"
        return try {
            LocalDate.parse(iso8601).format(fmtFechaAlta)
        } catch (_: DateTimeParseException) {
            "—"
        }
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

        viewModel.guardar(
            nombre              = nombre,
            apellido1           = apellido1,
            apellido2           = apellido2.ifBlank { null },
            categoria           = categoria,
            jornadaSemanalHoras = jornadaSemanal,
            diasVacaciones      = vacaciones,
            diasAsuntos         = asuntos
        )
    }
}
