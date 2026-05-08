package com.staffflow.android.ui.encargado

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.data.remote.api.SaldoApiService
import com.staffflow.android.data.remote.dto.PlanificacionVacApResponse
import com.staffflow.android.data.remote.repository.SaldoRepository
import com.staffflow.android.databinding.FragmentFormAusenciaBinding
import com.staffflow.android.domain.model.TipoAusencia
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Formulario de ausencia planificada (P24).
 *
 * Modo alta   (ausenciaId = -1): E30 POST /ausencias.
 * Modo edicion (ausenciaId > 0): E31 PATCH /ausencias/{id}.
 * Eliminar    (ausenciaId > 0, procesado=false): E32 DELETE con dialogo previo (Decision 26).
 * Modo rango  (fechaDesde + fechaHasta presentes): POST /ausencias/rango.
 *   - empleadoId, fechaDesde y fechaHasta vienen bloqueados desde P23.
 *   - 409 con fechas conflictivas → dialogo "¿Sobrescribir?".
 *
 * Si procesado=true el formulario se muestra en modo solo lectura.
 *
 * Argumentos de navegacion esperados (Bundle):
 *   ausenciaId  Long     -1 = alta | >0 = edicion   (obligatorio)
 *   procesado   Boolean  false por defecto           (obligatorio en edicion)
 *   empleadoId  Long     pre-rellena el campo        (opcional)
 *   fecha       String   yyyy-MM-dd                  (opcional, edicion)
 *   fechaDesde  String   yyyy-MM-dd                  (opcional, modo rango)
 *   fechaHasta  String   yyyy-MM-dd                  (opcional, modo rango)
 *   tipoAusencia String  nombre TipoAusencia         (opcional, edicion)
 *   observaciones String                             (opcional, edicion)
 */
class FormAusenciaFragment : Fragment() {

    private var _binding: FragmentFormAusenciaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FormAusenciaViewModel by viewModels()

    private val saldoRepository by lazy {
        SaldoRepository(NetworkModule.retrofit.create(SaldoApiService::class.java))
    }

    // Datos del rango (presentes solo cuando se navega desde seleccion en P23)
    private var esRango = false
    private var esFestivo = false
    private var empleadoIdRango = -1L
    private var fechaDesdeRango = ""
    private var fechaHastaRango = ""

    // Controla si el botón Guardar está habilitado según la disponibilidad de saldo
    private var puedeGuardarPorSaldo = true

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormAusenciaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: Bundle()
        val ausenciaId = args.getLong("ausenciaId", -1L)
        val procesado = args.getBoolean("procesado", false)

        // esFestivo se puede activar tanto en modo rango (add) como en modo edición
        esFestivo = args.getBoolean("esFestivo", false)

        // Detectar modo rango
        val fechaDesde = args.getString("fechaDesde")
        val fechaHasta = args.getString("fechaHasta")
        if (fechaDesde != null && fechaHasta != null) {
            esRango = true
            empleadoIdRango = args.getLong("empleadoId", -1L)
            fechaDesdeRango = fechaDesde
            fechaHastaRango = fechaHasta
        }

        viewModel.init(ausenciaId, procesado)

        configurarDropdown()
        preRellenarCampos(args)
        configurarModo()
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

    private fun configurarDropdown() {
        val tipos = if (esFestivo) {
            listOf(TipoAusencia.FESTIVO_NACIONAL, TipoAusencia.FESTIVO_LOCAL).map { tipoAusenciaLabel(it) }
        } else {
            TipoAusencia.values()
                .filter { it != TipoAusencia.FESTIVO_NACIONAL && it != TipoAusencia.FESTIVO_LOCAL }
                .map { tipoAusenciaLabel(it) }
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tipos)
        binding.actvTipoAusencia.setAdapter(adapter)
        if (esFestivo) {
            binding.actvTipoAusencia.setText(tipoAusenciaLabel(TipoAusencia.FESTIVO_NACIONAL), false)
        }
    }

    private fun preRellenarCampos(args: Bundle) {
        if (esRango) {
            if (!esFestivo) binding.etEmpleadoId.setText(empleadoIdRango.toString())
            binding.etFecha.setText(fechaDesdeRango)
            binding.etFechaHasta.setText(fechaHastaRango)
            binding.tilFecha.hint = getString(R.string.form_ausencia_hint_fecha_desde)
            binding.tilFechaHasta.isVisible = true
        } else {
            args.getLong("empleadoId", -1L).takeIf { it > 0L }?.let {
                binding.etEmpleadoId.setText(it.toString())
            }
            args.getString("fecha")?.let { binding.etFecha.setText(it) }
        }
        args.getString("observaciones")?.let { binding.etObservaciones.setText(it) }
        args.getString("tipoAusencia")?.let { nombre ->
            TipoAusencia.values().find { it.name == nombre }?.let {
                binding.actvTipoAusencia.setText(tipoAusenciaLabel(it), false)
            }
        }
    }

    private fun configurarModo() {
        val procesado = viewModel.procesado
        val modoEdicion = viewModel.modoEdicion

        binding.bannerProcesado.isVisible = procesado
        binding.btnGuardar.isVisible = !procesado
        binding.btnEliminar.isVisible = modoEdicion && !procesado

        if (procesado) {
            binding.etEmpleadoId.isEnabled = false
            binding.etFecha.isEnabled = false
            binding.etFechaHasta.isEnabled = false
            binding.actvTipoAusencia.isEnabled = false
            binding.etObservaciones.isEnabled = false
        } else if (modoEdicion) {
            binding.etEmpleadoId.isEnabled = false
            binding.etFecha.isEnabled = false
        } else if (esFestivo) {
            // Modo festivo: campo empleado oculto, fechas bloqueadas
            binding.tilEmpleadoId.isVisible = false
            binding.etFecha.isEnabled = false
            binding.etFechaHasta.isEnabled = false
        } else if (esRango) {
            // Campos de rango bloqueados: vienen prefijados desde la seleccion
            binding.etEmpleadoId.isEnabled = false
            binding.etFecha.isEnabled = false
            binding.etFechaHasta.isEnabled = false
        }
    }

    private fun configurarListeners() {
        // Listener del dropdown de tipo: carga disponibilidad en modo rango (no festivo)
        binding.actvTipoAusencia.setOnItemClickListener { _, _, _, _ ->
            if (!esRango || esFestivo) return@setOnItemClickListener
            val tipoStr = binding.actvTipoAusencia.text.toString()
            val tipo = TipoAusencia.values().find { tipoAusenciaLabel(it) == tipoStr }
            when (tipo) {
                TipoAusencia.VACACIONES, TipoAusencia.ASUNTO_PROPIO ->
                    viewModel.cargarPlanificacionVacAp(empleadoIdRango, fechaDesdeRango)
                else -> {
                    viewModel.ocultarBannerVacAp()
                }
            }
        }

        binding.btnGuardar.setOnClickListener {
            val tipo = TipoAusencia.values().find {
                tipoAusenciaLabel(it) == binding.actvTipoAusencia.text.toString()
            } ?: TipoAusencia.VACACIONES
            val observaciones = binding.etObservaciones.text.toString().trim().ifBlank { null }

            if (esRango || esFestivo) {
                val empId = if (esFestivo) null else empleadoIdRango
                viewModel.guardarRango(empId, fechaDesdeRango, fechaHastaRango, tipo, observaciones)
            } else {
                val empleadoId = binding.etEmpleadoId.text.toString().toLongOrNull()
                val fecha = binding.etFecha.text.toString().trim()
                binding.btnGuardar.isEnabled = false
                try {
                    viewModel.guardar(empleadoId, fecha, tipo, observaciones)
                } finally {
                    binding.btnGuardar.isEnabled = true
                }
            }
        }

        binding.btnEliminar.setOnClickListener {
            mostrarDialogoConfirmacion()
        }
    }

    // ------------------------------------------------------------------
    // Dialogo de confirmacion antes de eliminar (Decision 26)
    // ------------------------------------------------------------------

    private fun mostrarDialogoConfirmacion() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.form_ausencia_dialogo_eliminar_titulo))
            .setMessage(getString(R.string.form_ausencia_dialogo_eliminar_mensaje))
            .setNegativeButton(getString(R.string.form_ausencia_dialogo_cancelar), null)
            .setPositiveButton(getString(R.string.form_ausencia_dialogo_confirmar)) { _, _ ->
                viewModel.eliminar()
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Dialogo de conflicto en rango (409) — ofrece sobrescribir
    // ------------------------------------------------------------------

    private fun mostrarDialogoConflicto(fechas: List<String>) {
        val tipo = TipoAusencia.values().find {
            tipoAusenciaLabel(it) == binding.actvTipoAusencia.text.toString()
        } ?: TipoAusencia.VACACIONES
        val observaciones = binding.etObservaciones.text.toString().trim().ifBlank { null }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.form_ausencia_dialogo_conflicto_titulo))
            .setMessage(getString(R.string.form_ausencia_dialogo_conflicto_mensaje, fechas.size))
            .setNegativeButton(getString(R.string.form_ausencia_dialogo_cancelar)) { _, _ ->
                viewModel.resetUiState()
            }
            .setPositiveButton(getString(R.string.form_ausencia_dialogo_conflicto_sobrescribir)) { _, _ ->
                val empId = if (esFestivo) null else empleadoIdRango
                viewModel.guardarRango(empId, fechaDesdeRango, fechaHastaRango, tipo, observaciones, sobrescribir = true)
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Observacion del ViewModel
    // ------------------------------------------------------------------

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect           { procesarEstado(it) } }
                launch { viewModel.planificacionVacAp.collect { procesarPlanificacionVacAp(it) } }
            }
        }
    }

    // ------------------------------------------------------------------
    // Actualizacion de la UI
    // ------------------------------------------------------------------

    private fun procesarEstado(estado: FormAusenciaViewModel.UiState) {
        binding.progressIndicator.isVisible = estado is FormAusenciaViewModel.UiState.Loading
        binding.btnGuardar.isEnabled = puedeGuardarPorSaldo
                && estado !is FormAusenciaViewModel.UiState.Loading
                && !viewModel.procesado
        binding.btnEliminar.isEnabled = estado !is FormAusenciaViewModel.UiState.Loading

        when (estado) {
            is FormAusenciaViewModel.UiState.Success  -> if (esRango) { viewModel.resetUiState(); findNavController().popBackStack() } else ofrecerRecalcular()
            is FormAusenciaViewModel.UiState.Deleted  -> {
                viewModel.resetUiState()
                Toast.makeText(requireContext(), getString(R.string.form_ausencia_eliminada), Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            is FormAusenciaViewModel.UiState.Conflicto -> mostrarDialogoConflicto(estado.fechas)
            is FormAusenciaViewModel.UiState.Error -> {
                Snackbar.make(binding.root, estado.mensaje, Snackbar.LENGTH_LONG).show()
                viewModel.limpiarError()
            }
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Dialogo de recalcular saldo tras guardar/eliminar (E40)
    // ------------------------------------------------------------------

    private fun ofrecerRecalcular() {
        viewModel.resetUiState()
        val empleadoId = if (esRango) empleadoIdRango
                         else binding.etEmpleadoId.text.toString().toLongOrNull() ?: -1L
        val fecha = if (esRango) fechaDesdeRango else binding.etFecha.text.toString()
        val anio = fecha.take(4).toIntOrNull()

        if (empleadoId <= 0L || anio == null) {
            findNavController().popBackStack()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.form_guardado_titulo)
            .setMessage(R.string.form_guardado_recalcular_mensaje)
            .setPositiveButton(R.string.saldo_recalcular) { _, _ ->
                lifecycleScope.launch {
                    saldoRepository.recalcularSaldo(empleadoId, anio)
                    findNavController().popBackStack()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                findNavController().popBackStack()
            }
            .setCancelable(false)
            .show()
    }

    // ------------------------------------------------------------------
    // Banner de días pendientes de planificar (vac / AP)
    // ------------------------------------------------------------------

    private fun procesarPlanificacionVacAp(estado: FormAusenciaViewModel.PlanificacionVacApResult) {
        when (estado) {
            is FormAusenciaViewModel.PlanificacionVacApResult.Idle -> {
                binding.bannerVacAp.isVisible = false
                puedeGuardarPorSaldo = true
            }
            is FormAusenciaViewModel.PlanificacionVacApResult.Loading -> {
                binding.bannerVacAp.isVisible = false
            }
            is FormAusenciaViewModel.PlanificacionVacApResult.Success -> {
                val tipoStr = binding.actvTipoAusencia.text.toString()
                val tipo = TipoAusencia.values().find { tipoAusenciaLabel(it) == tipoStr }
                val esVacaciones = tipo == TipoAusencia.VACACIONES
                actualizarBannerVacAp(estado.data, esVacaciones)
            }
        }
        // Sincronizar botón Guardar con el estado de UiState actual
        binding.btnGuardar.isEnabled = puedeGuardarPorSaldo
                && viewModel.uiState.value !is FormAusenciaViewModel.UiState.Loading
                && !viewModel.procesado
    }

    private fun actualizarBannerVacAp(data: PlanificacionVacApResponse, esVacaciones: Boolean) {
        val info = if (esVacaciones) data.vacaciones else data.asuntosPropios
        val tipo = if (esVacaciones) "vacaciones" else "asuntos propios"
        val anio = fechaDesdeRango.take(4).toIntOrNull() ?: return
        val diasRango = calcularDiasRango()

        val colorAttr: Int
        val texto = when {
            info.pendientesPlanificar <= 0 -> {
                puedeGuardarPorSaldo = false
                colorAttr = com.google.android.material.R.attr.colorTertiaryContainer
                val participio = if (esVacaciones) "planificadas" else "planificados"
                "${tipo.replaceFirstChar { it.uppercase() }} completamente $participio ✅"
            }
            diasRango > info.pendientesPlanificar -> {
                puedeGuardarPorSaldo = false
                colorAttr = com.google.android.material.R.attr.colorErrorContainer
                "⚠️  Solo quedan ${info.pendientesPlanificar} días pendientes de planificar, el rango seleccionado tiene $diasRango días"
            }
            data.anioFuturoSinCierre -> {
                puedeGuardarPorSaldo = true
                colorAttr = com.google.android.material.R.attr.colorSecondaryContainer
                "⚠️  Hasta el cierre de ${anio - 1} solo puedes planificar ${info.disponibles} días de derecho de $anio.\n" +
                "Los días pendientes de ${anio - 1} se añadirán automáticamente al hacer el cierre anual.\n" +
                "ℹ️  ${info.pendientesPlanificar} días pendientes de planificar en $anio, seleccionados $diasRango días para programar"
            }
            else -> {
                puedeGuardarPorSaldo = true
                colorAttr = com.google.android.material.R.attr.colorSecondaryContainer
                "ℹ️  ${info.pendientesPlanificar} días pendientes de planificar en $anio, seleccionados $diasRango días para programar"
            }
        }

        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(colorAttr, typedValue, true)
        binding.bannerVacAp.setCardBackgroundColor(typedValue.data)
        val textColorAttr = when (colorAttr) {
            com.google.android.material.R.attr.colorTertiaryContainer -> com.google.android.material.R.attr.colorOnTertiaryContainer
            com.google.android.material.R.attr.colorErrorContainer -> com.google.android.material.R.attr.colorOnErrorContainer
            else -> com.google.android.material.R.attr.colorOnSecondaryContainer
        }
        requireContext().theme.resolveAttribute(textColorAttr, typedValue, true)
        binding.tvBannerVacAp.setTextColor(typedValue.data)
        binding.tvBannerVacAp.text = texto
        binding.bannerVacAp.isVisible = true
    }

    private fun calcularDiasRango(): Int = try {
        val d = LocalDate.parse(fechaDesdeRango)
        val h = LocalDate.parse(fechaHastaRango)
        (h.toEpochDay() - d.toEpochDay() + 1).toInt()
    } catch (e: Exception) { 0 }

    // ------------------------------------------------------------------
    // Etiquetas legibles para el dropdown
    // ------------------------------------------------------------------

    private fun tipoAusenciaLabel(tipo: TipoAusencia): String = when (tipo) {
        TipoAusencia.FESTIVO_NACIONAL        -> "Festivo nacional"
        TipoAusencia.FESTIVO_LOCAL           -> "Festivo local"
        TipoAusencia.VACACIONES              -> "Vacaciones"
        TipoAusencia.ASUNTO_PROPIO           -> "Asunto propio"
        TipoAusencia.PERMISO_RETRIBUIDO      -> "Permiso retribuido"
        TipoAusencia.DIA_LIBRE_COMPENSATORIO -> "Día libre compensatorio"
        TipoAusencia.DIA_LIBRE               -> "Día libre"
    }
}
