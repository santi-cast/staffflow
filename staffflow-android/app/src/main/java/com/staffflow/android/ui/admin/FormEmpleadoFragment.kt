package com.staffflow.android.ui.admin

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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.R
import com.staffflow.android.data.remote.dto.EmpleadoResponse
import com.staffflow.android.databinding.FragmentFormEmpleadoBinding
import com.staffflow.android.domain.model.CategoriaEmpleado
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Formulario de edicion de empleado (P15). Solo accesible para ADMIN.
 *
 * Patron F - formulario edit.
 * Flujo: E15 GET /empleados/{id} para precargar + E16 PATCH /empleados/{id}
 * para guardar los campos modificables (nombre, apellidos, dni, categoria,
 * jornada, vacaciones, asuntos propios, fechaAlta).
 *
 * fechaAlta se edita via MaterialDatePicker sin restriccion de rango: el
 * ADMIN puede corregir tanto fechas pasadas como futuras. El backend (E16)
 * acepta cualquier fecha al editar.
 *
 * dni se edita como TextInputLayout normal (max 9 chars, textCapCharacters).
 * Validacion en cliente: longitud 9 y no vacio. El backend valida unicidad
 * (409) y formato/letra de control.
 *
 * El alta de empleado se hace siempre desde P29 (FormUsuarioFragment) junto
 * al alta del usuario. No se admite empleadoId = -1 en esta pantalla.
 *
 * Argumento de navegacion:
 *   empleadoId: Long (debe ser > 0)
 *
 * GUARDAR deshabilita el boton y muestra LinearProgressIndicator durante la llamada.
 * OK: notifica al fragment destino (P14 DetalleEmpleadoFragment) via
 *     FragmentResult con KEY_RESULTADO y vuelve atras. P14 muestra el Snackbar
 *     de confirmacion para que sobreviva al popBackStack.
 * Error 409 (duplicado): Snackbar con el mensaje del backend (queda en esta pantalla).
 */
class FormEmpleadoFragment : Fragment() {

    private var _binding: FragmentFormEmpleadoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormEmpleadoViewModel by viewModels()

    private val categorias = CategoriaEmpleado.entries.toList()
    private val categoriaLabels = listOf("Operario", "Administrativo", "Técnico", "Encargado", "Otro")

    /** Formato visible para la fecha de alta editable (campo tilFechaAlta). */
    private val fmtFechaAltaVisible = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * Fecha de alta seleccionada en el picker. Se inicializa al precargar el
     * empleado (E15). Si la precarga viene sin fecha valida se usa hoy como
     * fallback para que el picker tenga una referencia razonable.
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
        binding.etFechaAlta.setOnClickListener { abrirSelectorFechaAlta() }
        binding.tilFechaAlta.setEndIconOnClickListener { abrirSelectorFechaAlta() }
    }

    /**
     * Abre un MaterialDatePicker SIN restriccion de rango: el ADMIN puede
     * elegir cualquier fecha (pasada o futura) para corregir errores de alta.
     *
     * MaterialDatePicker trabaja en UTC: la seleccion se convierte a
     * LocalDate usando ZoneOffset.UTC para evitar saltos de dia segun la TZ
     * del dispositivo (mismo patron que P29 FormUsuarioFragment).
     *
     * Al cerrar el picker con una fecha distinta de la original cargada de
     * E15, se muestra un dialogo de advertencia: "afecta a los informes
     * historicos". Confirmar aplica el cambio al formulario; cancelar
     * descarta la seleccion y deja el campo como estaba. El TextInputLayout
     * ya muestra un helperText permanente como primera red de seguridad y
     * el dialogo final del GUARDAR vuelve a listar el cambio en el resumen
     * "antes -> despues" (triple confirmacion).
     */
    private fun abrirSelectorFechaAlta() {
        val seleccionUtcMillis = fechaAltaSeleccionada
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.form_empleado_hint_fecha_alta)
            .setSelection(seleccionUtcMillis)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val fechaElegida = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.of("UTC"))
                .toLocalDate()
            if (fechaElegida == fechaAltaSeleccionada) {
                return@addOnPositiveButtonClickListener
            }
            confirmarCambioFechaAlta(fechaElegida)
        }
        picker.show(parentFragmentManager, "fechaAltaPicker")
    }

    /**
     * Muestra un dialogo de advertencia antes de aplicar el cambio de fecha
     * de alta al formulario. Solo se dispara cuando la fecha elegida en el
     * picker difiere de la actualmente mostrada (que arranca como la fecha
     * original cargada de E15).
     *
     * Confirmar -> actualiza fechaAltaSeleccionada y refresca el campo.
     * Cancelar  -> no hace nada, el campo conserva el valor previo.
     */
    private fun confirmarCambioFechaAlta(fechaElegida: java.time.LocalDate) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.form_empleado_aviso_fecha_alta_titulo)
            .setMessage(R.string.form_empleado_aviso_fecha_alta_mensaje)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                fechaAltaSeleccionada = fechaElegida
                actualizarTextoFechaAlta()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun actualizarTextoFechaAlta() {
        binding.etFechaAlta.setText(fechaAltaSeleccionada.format(fmtFechaAltaVisible))
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
            is FormEmpleadoViewModel.UiState.Success -> finalizarConMensaje(R.string.form_empleado_resultado_editado)
            is FormEmpleadoViewModel.UiState.Error   -> {
                Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                viewModel.limpiarError()
            }
            else -> Unit
        }
    }

    /**
     * Envia un mensaje de resultado al fragment destino (P14 DetalleEmpleado-
     * Fragment) via FragmentResult y vuelve atras. P14 lo recibe en
     * setFragmentResultListener(KEY_RESULTADO) y muestra Snackbar.
     *
     * Mismo patron que P29 -> P28 (FormUsuarioFragment -> UsuariosFragment),
     * elegido por ser idiomatico de Android Navigation y sobrevivir al
     * popBackStack (un Snackbar lanzado aqui se mata con la transicion).
     *
     * @param mensajeRes recurso de string con el texto a mostrar en P14
     */
    private fun finalizarConMensaje(mensajeRes: Int) {
        setFragmentResult(
            KEY_RESULTADO,
            bundleOf(ARG_MENSAJE_RES to mensajeRes)
        )
        findNavController().popBackStack()
    }

    /** Precarga los campos del formulario con los datos del empleado. */
    private fun prerellenarCampos(e: EmpleadoResponse) {
        rellenarCabecera(e)

        binding.etNombre.setText(e.nombre)
        binding.etApellido1.setText(e.apellido1)
        binding.etApellido2.setText(e.apellido2 ?: "")
        binding.etDni.setText(e.dni)
        binding.etJornadaSemanal.setText(e.jornadaSemanalHoras.toString())
        binding.etVacaciones.setText(e.diasVacacionesAnuales.toString())
        binding.etAsuntos.setText(e.diasAsuntosPropiosAnuales.toString())

        val index = categorias.indexOf(e.categoria)
        if (index >= 0) binding.actvCategoria.setText(categoriaLabels[index], false)

        // Precarga de la fecha de alta editable. Si el backend mando null o un
        // valor no parseable, se deja el default (hoy) como referencia visual.
        parsearFechaAltaIso(e.fechaAlta)?.let { fechaAltaSeleccionada = it }
        actualizarTextoFechaAlta()
    }

    /**
     * Rellena la cabecera read-only con numeroEmpleado, username, email y rol.
     * Patron item_ficha_fila (label + valor en linea).
     *
     * E15 devuelve username, email y rol solo cuando el llamante es ADMIN
     * (Opcion A). P15 solo es accesible a ADMIN, asi que en el caso normal
     * los tres campos vienen rellenos. Si por defensa llegan null se muestra
     * un guion para que la fila no se vea rota.
     *
     * fechaAlta ya no vive en la cabecera: se edita en el cuerpo del formulario
     * via MaterialDatePicker (tilFechaAlta).
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
    }

    /**
     * Parsea la fechaAlta tal como llega del backend (ISO-8601 yyyy-MM-dd).
     * Devuelve null si el valor es nulo, blanco o no se puede parsear; el
     * llamante decide el fallback.
     */
    private fun parsearFechaAltaIso(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return try {
            LocalDate.parse(iso)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Recoger datos del formulario y guardar
    // ------------------------------------------------------------------

    private fun intentarGuardar() {
        val nombre    = binding.etNombre.text?.toString().orEmpty().trim()
        val apellido1 = binding.etApellido1.text?.toString().orEmpty().trim()
        val apellido2 = binding.etApellido2.text?.toString().orEmpty().trim()
        val dni       = binding.etDni.text?.toString().orEmpty().trim().uppercase()
        val categoriaLabel = binding.actvCategoria.text?.toString().orEmpty()
        val categoriaIndex = categoriaLabels.indexOf(categoriaLabel)
        val categoria = if (categoriaIndex >= 0) categorias[categoriaIndex] else null

        val jornadaSemanalStr = binding.etJornadaSemanal.text?.toString().orEmpty().trim()
        val vacacionesStr     = binding.etVacaciones.text?.toString().orEmpty().trim()
        val asuntosStr        = binding.etAsuntos.text?.toString().orEmpty().trim()

        if (nombre.isBlank() || apellido1.isBlank() || dni.isBlank() || categoria == null ||
            jornadaSemanalStr.isBlank() || vacacionesStr.isBlank() || asuntosStr.isBlank()) {
            Snackbar.make(binding.root, getString(R.string.form_empleado_error_campos), Snackbar.LENGTH_SHORT).show()
            return
        }

        if (dni.length != 9) {
            Snackbar.make(binding.root, "El DNI debe tener 9 caracteres", Snackbar.LENGTH_SHORT).show()
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

        val estado = FormEmpleadoViewModel.EstadoFormulario(
            nombre = nombre,
            apellido1 = apellido1,
            apellido2 = apellido2.ifBlank { null },
            dni = dni,
            categoria = categoria,
            jornadaSemanalHoras = jornadaSemanal,
            diasVacaciones = vacaciones,
            diasAsuntos = asuntos,
            fechaAlta = fechaAltaSeleccionada
        )

        val cambios = viewModel.construirResumenCambios(estado)
        if (cambios.isEmpty()) {
            Snackbar.make(binding.root, "Sin cambios", Snackbar.LENGTH_SHORT).show()
            return
        }

        mostrarDialogoConfirmacion(cambios, estado)
    }

    /**
     * Muestra un MaterialAlertDialog con el resumen "antes -> despues" de
     * todos los campos modificados. Confirmar dispara el PATCH; cancelar
     * deja el formulario intacto.
     *
     * El mensaje se construye como texto plano multilinea: cada cambio en
     * una linea "Etiqueta: antes -> despues", y si el cambio trae nota
     * (caso fechaAlta -> "Afecta a informes historicos") se añade en linea
     * aparte entre parentesis para diferenciarla visualmente.
     */
    private fun mostrarDialogoConfirmacion(
        cambios: List<FormEmpleadoViewModel.Cambio>,
        estado: FormEmpleadoViewModel.EstadoFormulario
    ) {
        val mensaje = cambios.joinToString(separator = "\n\n") { c ->
            buildString {
                append(c.etiqueta).append(": ").append(c.antes).append(" → ").append(c.despues)
                c.nota?.let { append("\n  (").append(it).append(")") }
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirmar cambios")
            .setMessage(mensaje)
            .setPositiveButton("Guardar") { _, _ -> viewModel.guardar(estado) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        /**
         * Clave del FragmentResult que envia P15 a P14 al terminar una edicion
         * con exito. DetalleEmpleadoFragment debe registrar
         * setFragmentResultListener con esta misma clave.
         */
        const val KEY_RESULTADO = "empleadoResultado"

        /**
         * Argumento dentro del Bundle del FragmentResult: id del recurso
         * de string con el mensaje localizado a mostrar en P14 (Int).
         */
        const val ARG_MENSAJE_RES = "mensajeRes"
    }
}
